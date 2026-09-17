{
  description = "Due — a native Android todo app";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
      androidSdk = (pkgs.androidenv.composeAndroidPackages {
        # AGP 8.7 selects 34.0.0 unless a project pins a newer version.
        buildToolsVersions = [ "34.0.0" "37.0.0" ];
        platformVersions = [ "35" ];
      }).androidsdk;
      sdkRoot = "${androidSdk}/libexec/android-sdk";
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.gradle_8
          pkgs.jdk17
          pkgs.fdroidserver
          androidSdk
        ];

        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        JAVA_HOME = pkgs.jdk17;
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/37.0.0/aapt2";
      };
    };
}
