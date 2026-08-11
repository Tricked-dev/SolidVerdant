{ pkgs, ... }:

let
  solidtimeNetwork = "solidverdant-solidtime";
  solidtimeDatabaseContainer = "solidverdant-solidtime-db";
  solidtimeApiContainer = "solidverdant-solidtime-api";
  backendPortableE2eAnnotation = "dev.tricked.solidverdant.e2e.BackendPortable";
  resolvePhysicalAndroidDevice = ''
    device_serial="''${ANDROID_SERIAL:-}"
    if [ -n "$device_serial" ]; then
      [ "$(adb -s "$device_serial" get-state 2>/dev/null)" = "device" ] || {
        printf '%s\n' "ANDROID_SERIAL does not identify a connected, authorized device: $device_serial" >&2
        exit 1
      }
      [ "$(adb -s "$device_serial" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" != "1" ] || {
        printf '%s\n' "Android E2E tasks require a physical device, not an emulator: $device_serial" >&2
        exit 1
      }
    else
      physical_devices=""
      for candidate in $(adb devices | awk '$2 == "device" { print $1 }'); do
        if [ "$(adb -s "$candidate" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" != "1" ]; then
          physical_devices="$physical_devices $candidate"
        fi
      done
      # Intentional word splitting: adb serials contain no whitespace.
      set -- $physical_devices
      [ "$#" -eq 1 ] || {
        printf '%s\n' "Expected exactly one connected physical Android device; found $# (set ANDROID_SERIAL to choose one)" >&2
        exit 1
      }
      device_serial="$1"
    fi
  '';
  assembleAndInstallAndroidE2e = ''
    env -u LD_LIBRARY_PATH ./gradlew --no-daemon assembleDebug assembleDebugAndroidTest
    project_root="''${DEVENV_ROOT:-$PWD}"
    app_apk="$project_root/app/build/outputs/apk/debug/app-debug.apk"
    test_apk="$project_root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    [ -f "$app_apk" ] || { printf '%s\n' "Missing debug APK: $app_apk" >&2; exit 1; }
    [ -f "$test_apk" ] || { printf '%s\n' "Missing instrumentation APK: $test_apk" >&2; exit 1; }
    install_e2e_apk() {
      apk="$1"
      package_name="$2"
      install_output=""
      if install_output=$(adb -s "$device_serial" install -r "$apk" 2>&1); then
        printf '%s\n' "$install_output"
        return 0
      fi
      printf '%s\n' "$install_output" >&2
      case "$install_output" in
        *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
          adb -s "$device_serial" uninstall "$package_name" >/dev/null 2>&1
          adb -s "$device_serial" install -r "$apk"
          ;;
        *)
          return 1
          ;;
      esac
    }
    # E2E builds normally install in place; recover from a different local signing key only when
    # adb reports the specific update-incompatible failure.
    install_e2e_apk "$app_apk" dev.tricked.solidverdant.dev
    install_e2e_apk "$test_apk" dev.tricked.solidverdant.dev.test
  '';
in
{
  android = {
    enable = true;
    platforms.version = [ "36" "37" ];
    buildTools.version = [ "36.0.0" ];
    systemImageTypes = [ "google_apis_playstore" ];
    abis = [ "x86_64" ];
    emulator.enable = true;
    systemImages.enable = true;
  };

  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk21;
  };

  languages.javascript = {
    enable = true;
    bun.enable = true;
    npm.enable = true;
  };

  packages = [
    pkgs.curl
    pkgs.jq
    pkgs.ruby
  ];

  env.SOLIDTIME_E2E_URL = "http://127.0.0.1:18080";
  # macOS's supported container runtime is Apple's `container` CLI. Keep the absolute path because
  # devenv's hermetic PATH does not include /usr/local/bin, while all task invocations still go
  # through this single variable for easy auditing.
  env.SOLIDTIME_CONTAINER_ENGINE = "/usr/local/bin/container";

  tasks."solidtime:engine".exec = ''
    command -v "$SOLIDTIME_CONTAINER_ENGINE" >/dev/null || {
      printf '%s\n' "SOLIDTIME_CONTAINER_ENGINE must name a Docker-compatible CLI" >&2
      exit 1
    }
    "$SOLIDTIME_CONTAINER_ENGINE" system status >/dev/null
  '';

  tasks."solidtime:network" = {
    after = [ "solidtime:engine" ];
    status = ''"$SOLIDTIME_CONTAINER_ENGINE" network inspect ${solidtimeNetwork} >/dev/null 2>&1'';
    exec = ''"$SOLIDTIME_CONTAINER_ENGINE" network create ${solidtimeNetwork} >/dev/null'';
  };

  tasks."solidtime:clean-stale" = {
    after = [ "solidtime:network" ];
    exec = ''
      "$SOLIDTIME_CONTAINER_ENGINE" rm -f \
        ${solidtimeApiContainer} ${solidtimeDatabaseContainer} >/dev/null 2>&1 || true
      "$SOLIDTIME_CONTAINER_ENGINE" network rm ${solidtimeNetwork} >/dev/null 2>&1 || true
      "$SOLIDTIME_CONTAINER_ENGINE" network create ${solidtimeNetwork} >/dev/null
      rm -f /tmp/solidverdant-solidtime-api.sock
    '';
  };

  tasks."solidtime:config" = {
    exec = ''
      set -euo pipefail
      state_dir="$DEVENV_STATE/solidtime"
      laravel_env="$state_dir/laravel.env"
      image="solidtime/solidtime:''${SOLIDTIME_IMAGE_TAG:-latest}"
      mkdir -p "$state_dir"
      chmod 700 "$state_dir"
      rm -f "$state_dir/laravel.env.failed-local"
      if [ -s "$laravel_env" ] && ! grep -q '^DB_HOST="solidverdant-solidtime-db"$' "$laravel_env"; then
        rm "$laravel_env"
      fi
      if [ ! -s "$laravel_env" ]; then
        umask 077
        keys_file="$state_dir/generated-keys.env"
        "$SOLIDTIME_CONTAINER_ENGINE" run --remove "$image" php artisan self-host:generate-keys >"$keys_file"
        grep -q '^APP_KEY=' "$keys_file"
        grep -q '^PASSPORT_PRIVATE_KEY=' "$keys_file"
        grep -q '^PASSPORT_PUBLIC_KEY=' "$keys_file"
        {
          sed -n '/^APP_KEY=/p; /^PASSPORT_PRIVATE_KEY=/p; /^PASSPORT_PUBLIC_KEY=/p' "$keys_file"
          # This is an API-contract test service, not a production-like load test. Solidtime's
          # production middleware applies a shared 200 requests/minute user limit, while the
          # portable Android suite intentionally launches a fresh app thirteen times. Keep the
          # official application and database stack in testing mode so the suite tests request and
          # response compatibility; production 429 behavior is covered by focused client tests.
          printf '%s\n' \
            'APP_NAME="solidtime SolidVerdant E2E"' \
            'APP_ENV="testing"' \
            'APP_DEBUG="false"' \
            'APP_URL="http://127.0.0.1:18080"' \
            'APP_FORCE_HTTPS="false"' \
            'APP_ENABLE_REGISTRATION="false"' \
            'TRUSTED_PROXIES="0.0.0.0/0,2000:0:0:0:0:0:0:0/3"' \
            'LOG_CHANNEL="stderr"' \
            'LOG_LEVEL="warning"' \
            'DB_CONNECTION="pgsql"' \
            'DB_HOST="solidverdant-solidtime-db"' \
            'DB_PORT="5432"' \
            'DB_SSLMODE="disable"' \
            'DB_DATABASE="solidtime"' \
            'DB_USERNAME="solidtime"' \
            'DB_PASSWORD="solidtime-local-e2e"' \
            'MAIL_MAILER="log"' \
            'QUEUE_CONNECTION="sync"' \
            'FILESYSTEM_DISK="local"' \
            'PUBLIC_FILESYSTEM_DISK="public"'
        } >"$laravel_env"
        rm "$keys_file"
      fi
      sed -i 's/^APP_ENV=.*/APP_ENV="testing"/' "$laravel_env"
    '';
  };

  processes."solidtime-db" = {
    after = [ "solidtime:clean-stale" ];
    exec = ''
      "$SOLIDTIME_CONTAINER_ENGINE" rm -f ${solidtimeDatabaseContainer} >/dev/null 2>&1 || true
      cleanup() { "$SOLIDTIME_CONTAINER_ENGINE" stop -t 3 ${solidtimeDatabaseContainer} >/dev/null 2>&1 || true; }
      trap cleanup EXIT INT TERM
      "$SOLIDTIME_CONTAINER_ENGINE" run --remove \
        --name ${solidtimeDatabaseContainer} \
        --network ${solidtimeNetwork} \
        --env POSTGRES_DB=solidtime \
        --env POSTGRES_USER=solidtime \
        --env POSTGRES_PASSWORD=solidtime-local-e2e \
        --env PGDATA=/var/lib/postgresql/data/pgdata \
        --volume solidverdant-solidtime_database:/var/lib/postgresql/data \
        postgres:15 &
      child=$!
      wait "$child"
    '';
    restart.on = "on_failure";
  };

  tasks."solidtime:database-ready" = {
    after = [
      "devenv:processes:solidtime-db@started"
      "solidtime:config"
    ];
    exec = ''
      set -euo pipefail
      ready=0
      for attempt in $(seq 1 90); do
        if "$SOLIDTIME_CONTAINER_ENGINE" exec ${solidtimeDatabaseContainer} \
          pg_isready -q -d solidtime -U solidtime; then
          ready=1
          break
        fi
        sleep 1
      done
      if [ "$ready" -ne 1 ]; then
        printf '%s\n' "Solidtime PostgreSQL did not become ready" >&2
        exit 1
      fi
      # Apple's container network does not provide Docker-style service-name DNS. Resolve the
      # database's current reserved address and put it in the API environment instead.
      db_host=$("$SOLIDTIME_CONTAINER_ENGINE" inspect ${solidtimeDatabaseContainer} |
        jq -r '.[0].status.networks[] | select(.network == "${solidtimeNetwork}") | .ipv4Address' |
        cut -d/ -f1)
      if [ -z "$db_host" ] || [ "$db_host" = "null" ]; then
        printf '%s\n' "Could not resolve the PostgreSQL container address" >&2
        exit 1
      fi
      sed -i "s/^DB_HOST=.*/DB_HOST=\"$db_host\"/" "$DEVENV_STATE/solidtime/laravel.env"
      sed -i 's/^DB_PORT=.*/DB_PORT="5432"/' "$DEVENV_STATE/solidtime/laravel.env"
    '';
  };

  processes."solidtime-api" = {
    after = [
      "solidtime:config"
      "solidtime:database-ready"
    ];
    exec = ''
      image="solidtime/solidtime:''${SOLIDTIME_IMAGE_TAG:-latest}"
      "$SOLIDTIME_CONTAINER_ENGINE" rm -f ${solidtimeApiContainer} >/dev/null 2>&1 || true
      api_socket="/tmp/solidverdant-solidtime-api.sock"
      rm -f "$api_socket"
      proxy_pid=""
      cleanup() {
        if [ -n "$proxy_pid" ]; then
          kill "$proxy_pid" >/dev/null 2>&1 || true
        fi
        "$SOLIDTIME_CONTAINER_ENGINE" stop -t 3 ${solidtimeApiContainer} >/dev/null 2>&1 || true
      }
      trap cleanup EXIT INT TERM
      "$SOLIDTIME_CONTAINER_ENGINE" run --detach --remove \
        --name ${solidtimeApiContainer} \
        --network ${solidtimeNetwork} \
        --publish-socket "$api_socket:/tmp/solidtime-api.sock" \
        --env-file "$DEVENV_STATE/solidtime/laravel.env" \
        --env CONTAINER_MODE=http \
        --env AUTO_DB_MIGRATE=true \
        "$image" >/dev/null
      api_running=0
      for attempt in $(seq 1 90); do
        api_state=$("$SOLIDTIME_CONTAINER_ENGINE" inspect ${solidtimeApiContainer} |
          jq -r '.[0].status.state' || true)
        if [ "$api_state" = "running" ]; then
          api_running=1
          break
        fi
        sleep 1
      done
      if [ "$api_running" -ne 1 ]; then
        printf '%s\n' "Solidtime API container did not stay running" >&2
        exit 1
      fi
      proxy_started=0
      for attempt in $(seq 1 90); do
        if "$SOLIDTIME_CONTAINER_ENGINE" exec -d ${solidtimeApiContainer} php -r '
          $server = stream_socket_server("unix:///tmp/solidtime-api.sock", $errno, $error);
          if ($server === false) { fwrite(STDERR, $error . PHP_EOL); exit(1); }
          if (function_exists("pcntl_signal")) { pcntl_signal(SIGCHLD, SIG_IGN); }
          while (true) {
            $client = @stream_socket_accept($server, -1);
            if ($client === false) { continue; }
            $child = function_exists("pcntl_fork") ? pcntl_fork() : null;
            if ($child === -1) { fclose($client); continue; }
            if ($child > 0) { fclose($client); continue; }
            if ($child === 0) { fclose($server); }
            $upstream = false;
            for ($attempt = 0; $attempt < 90; $attempt++) {
              $upstream = @stream_socket_client("tcp://127.0.0.1:8000", $upstreamErrno, $upstreamError, 1);
              if ($upstream !== false) { break; }
              usleep(100000);
            }
            if ($upstream === false) { fclose($client); continue; }
            stream_set_blocking($client, false);
            stream_set_blocking($upstream, false);
            while (!feof($client) && !feof($upstream)) {
              $read = [$client, $upstream];
              $write = [];
              $except = [];
              if (@stream_select($read, $write, $except, 1) === false) { break; }
              foreach ($read as $source) {
                $data = fread($source, 8192);
                if ($data === false || $data === "") {
                  if (feof($source)) { break 2; }
                  continue;
                }
                $target = $source === $client ? $upstream : $client;
                $offset = 0;
                $length = strlen($data);
                while ($offset < $length) {
                  $written = @fwrite($target, substr($data, $offset));
                  if ($written === false) { break 2; }
                  $offset += $written;
                }
              }
            }
            fclose($upstream);
            fclose($client);
            if ($child === 0) { exit(0); }
          }
        ' >/dev/null 2>&1; then
          proxy_started=1
          break
        fi
        sleep 1
      done
      if [ "$proxy_started" -ne 1 ]; then
        printf '%s\n' "Could not start the Solidtime API socket proxy" >&2
        exit 1
      fi
      # Apple container's published-port bridge is not visible to devenv's readiness probe or
      # adb reverse. Keep the public test URL stable with a host TCP proxy to the published Unix
      # socket; both proxies exist only for this disposable process.
      SOLIDTIME_API_SOCKET="$api_socket" ruby -rsocket -e '
        socket_path = ENV.fetch("SOLIDTIME_API_SOCKET")
        server = TCPServer.new("127.0.0.1", 18080)
        loop do
          socket = server.accept
          Thread.new(socket) do |client|
            upstream = nil
            begin
              20.times do
                begin
                  upstream = UNIXSocket.new(socket_path)
                  break
                rescue Errno::ENOENT, Errno::ECONNREFUSED
                  sleep 0.1
                end
              end
              next if upstream.nil?
              to_upstream = Thread.new { IO.copy_stream(client, upstream); upstream.close_write rescue nil }
              to_client = Thread.new { IO.copy_stream(upstream, client); client.close_write rescue nil }
              to_upstream.join
              to_client.join
            ensure
              client.close rescue nil
              upstream.close rescue nil
            end
          end
        end
      ' &
      proxy_pid=$!
      while true; do
        api_state=$("$SOLIDTIME_CONTAINER_ENGINE" inspect ${solidtimeApiContainer} |
          jq -r '.[0].status.state' || true)
        [ "$api_state" = "running" ] || exit 1
        sleep 1
      done
    '';
    ready = {
      http.get = {
        port = 18080;
        path = "/health-check/up";
      };
      initial_delay = 2;
      period = 2;
      probe_timeout = 3;
      failure_threshold = 90;
    };
    restart.on = "on_failure";
  };

  tasks."solidtime:reset" = {
    after = [ "devenv:processes:solidtime-api@ready" ];
    exec = ''
      set -euo pipefail
      session_file="$DEVENV_STATE/solidtime/session.properties"
      "$SOLIDTIME_CONTAINER_ENGINE" exec ${solidtimeApiContainer} php artisan migrate:fresh --force >/dev/null
      "$SOLIDTIME_CONTAINER_ENGINE" exec ${solidtimeApiContainer} php artisan passport:client \
        --personal --name="SolidVerdant E2E API" --no-interaction >/dev/null
      "$SOLIDTIME_CONTAINER_ENGINE" exec ${solidtimeApiContainer} php artisan admin:user:create \
        "SolidVerdant E2E" "solidverdant-e2e@example.test" --verify-email --no-interaction >/dev/null
      "$SOLIDTIME_CONTAINER_ENGINE" exec ${solidtimeApiContainer} php artisan tinker --execute='
      $user = App\Models\User::where("email", "solidverdant-e2e@example.test")->firstOrFail();
      $user->forceFill(["timezone" => "UTC"])->save();
      $member = App\Models\Member::where("user_id", $user->id)->firstOrFail();
      $client = new App\Models\Client();
      $client->forceFill([
        "name" => "Live Test Client",
        "organization_id" => $member->organization_id,
        "archived_at" => null,
      ])->save();
      $project = new App\Models\Project();
      $project->forceFill([
        "name" => "Live Test Project",
        "color" => "#4F46E5",
        "client_id" => $client->id,
        "organization_id" => $member->organization_id,
        "is_public" => true,
        "is_billable" => true,
        "billable_rate" => null,
        "archived_at" => null,
        "estimated_time" => null,
      ])->save();
      $task = new App\Models\Task();
      $task->forceFill([
        "name" => "Live Test Task",
        "project_id" => $project->id,
        "organization_id" => $member->organization_id,
        "done_at" => null,
        "estimated_time" => null,
      ])->save();
      $tag = new App\Models\Tag();
      $tag->forceFill([
        "name" => "Live Test Tag",
        "organization_id" => $member->organization_id,
      ])->save();
      $token = $user->createToken("SolidVerdant device E2E")->accessToken;
      echo "base_url=http://127.0.0.1:18080/\n";
      echo "access_token=".$token."\n";
      echo "membership_id=".$member->id."\n";
      echo "organization_id=".$member->organization_id."\n";
      echo "user_id=".$user->id."\n";
      echo "timezone=".$user->timezone."\n";' >"$session_file"
      chmod 600 "$session_file"
      grep -q '^access_token=' "$session_file"
      grep -q '^membership_id=' "$session_file"
      grep -q '^user_id=' "$session_file"
      grep -q '^timezone=UTC$' "$session_file"
    '';
  };

  tasks."android:e2e:mock".exec = ''
    set -euo pipefail
    ${resolvePhysicalAndroidDevice}
    ${assembleAndInstallAndroidE2e}
    adb -s "$device_serial" shell am instrument -w \
      -e e2eBackend mock \
      dev.tricked.solidverdant.dev.test/dev.tricked.solidverdant.HiltTestRunner
  '';

  # The default device suite stays on MockWebServer so it remains fast and self-contained.
  tasks."android:e2e" = {
    after = [ "android:e2e:mock" ];
    exec = "true";
  };

  tasks."solidtime:test" = {
    after = [ "solidtime:reset" ];
    exec = ''
      set -euo pipefail
      ${resolvePhysicalAndroidDevice}
      cleanup_session() {
        adb -s "$device_serial" shell rm -f /data/local/tmp/solidtime-live-e2e.properties >/dev/null 2>&1 || true
        adb -s "$device_serial" shell run-as dev.tricked.solidverdant.dev \
          rm -f files/solidtime-live-e2e.properties >/dev/null 2>&1 || true
        adb -s "$device_serial" reverse --remove tcp:18080 >/dev/null 2>&1 || true
      }
      trap cleanup_session EXIT
      ${assembleAndInstallAndroidE2e}
      cleanup_session
      adb -s "$device_serial" reverse tcp:18080 tcp:18080
      adb -s "$device_serial" push "$DEVENV_STATE/solidtime/session.properties" \
        /data/local/tmp/solidtime-live-e2e.properties >/dev/null
      adb -s "$device_serial" shell chmod 644 /data/local/tmp/solidtime-live-e2e.properties
      adb -s "$device_serial" shell run-as dev.tricked.solidverdant.dev mkdir -p files
      adb -s "$device_serial" shell run-as dev.tricked.solidverdant.dev cp \
        /data/local/tmp/solidtime-live-e2e.properties files/solidtime-live-e2e.properties
      adb -s "$device_serial" shell rm -f /data/local/tmp/solidtime-live-e2e.properties
      adb -s "$device_serial" shell run-as dev.tricked.solidverdant.dev \
        chmod 600 files/solidtime-live-e2e.properties
      adb -s "$device_serial" shell am instrument -w \
        -e e2eBackend real \
        -e annotation ${backendPortableE2eAnnotation} \
        dev.tricked.solidverdant.dev.test/dev.tricked.solidverdant.HiltTestRunner
    '';
  };

  tasks."android:e2e:real" = {
    after = [ "solidtime:test" ];
    exec = "true";
  };

  tasks."android:gate".exec = ''
    env -u LD_LIBRARY_PATH ./gradlew --no-daemon \
      spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
  '';

  tasks."android:gate:instrumentation".exec = ''
    env -u LD_LIBRARY_PATH ./gradlew --no-daemon :app:connectedCheck --stacktrace
  '';
}
