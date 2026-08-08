# Real Solidtime API test environment

Devenv manages the official `solidtime/solidtime` API and isolated PostgreSQL containers as two
readiness-checked processes. This catches API contract drift that the in-process MockWebServer
cannot discover.

Credentials and database state are generated below `.devenv/state/solidtime` and are ignored by
Git. The app binds only to `127.0.0.1:18080`.

Enter the consolidated Android/Solidtime environment with `devenv shell` (or let direnv activate
it), then use:

```sh
devenv up
devenv tasks run solidtime:reset
devenv tasks run android:e2e
devenv tasks run android:e2e:real
devenv tasks run android:gate
```

`android:e2e` is the default, fast device suite. It selects the mock backend, assembles both APKs,
and installs them with `adb install -r` before running the full instrumentation suite.
`android:e2e:mock` is the explicit form of the same task.

`solidtime:test` starts its process dependencies, resets the server, selects the real backend, and
runs only tests marked with `@BackendPortable`. It installs the same assembled APKs, uses
`adb reverse` for the loopback connection, moves the short-lived session through temporary device
storage into the app's private storage, immediately removes the temporary copy, and removes the
private copy when the run finishes. The reset session contains the generated
user, membership, organization, UTC timezone, and token; it is written with owner-only permissions
and is never passed through instrumentation arguments. The reset leaves the account empty so each
portable test can create its own fixture through the production API.

`android:e2e:real` is the Android-oriented alias for `solidtime:test`.

Both device tasks require exactly one connected physical device. Set `ANDROID_SERIAL` when more
than one physical device is attached; emulator serials are rejected.

The default image tag is the official stable `latest`. Set `SOLIDTIME_IMAGE_TAG=main` for an
intentional compatibility run against upstream development, or use a release tag when reproducing
a specific deployment.

The API process runs with Solidtime's testing application environment. This keeps the production
200-requests-per-minute user throttle from turning thirteen rapid, isolated app launches into a
suite failure; request/response behavior still comes from the official image. Production 429
classification, retry, and retry-budget behavior are covered by focused client tests.
