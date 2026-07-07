{
  description = "A basic flake with a shell";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  inputs.systems.url = "github:nix-systems/default";
  inputs.flake-utils = {
    url = "github:numtide/flake-utils";
    inputs.systems.follows = "systems";
  };

  outputs =
    { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };
        android = pkgs.androidenv.composeAndroidPackages {
          # 37 enables building (and running the JVM test/screenshot suites) on machines
          # without a host SDK; kaisel keeps using its host SDK via local.properties.
          platformVersions = [ "36" "37" ];
          buildToolsVersions = [ "35.0.0" ];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "google_apis_playstore" ];
          abiVersions = [ "x86_64" ];
        };
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            pkgs.bashInteractive
            pkgs.bun
            pkgs.nodejs
            pkgs.jdk21
            android.androidsdk
          ];

          ANDROID_HOME = "${android.androidsdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
          JAVA_HOME = "${pkgs.jdk21.home}";
          shellHook = ''
            export ANDROID_AVD_HOME="$HOME/.android/avd"
          '';
        };
      }
    );
}
