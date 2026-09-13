Codemagic Android APK build

Overview
This project uses Gradle (Kotlin DSL) to build a native Android APK. Codemagic runs the provided codemagic.yaml workflow to assemble a Debug APK for installation testing. The workflow downloads Gradle 9.3.1 at runtime so builds do not depend on the Gradle version preinstalled on the Codemagic build image.

Local development
- Create a local .env (ignored by git) or set environment variables in the shell before running Gradle.
- Debug builds are automatically signed with Gradle's default debug certificate (no additional setup needed).

Codemagic setup
1. In Codemagic, ensure the environment variables are configured in Project settings.
2. Verify the codemagic.yaml workflow is set to build the APK.

Running the build
- The repo includes codemagic.yaml. Its bootstrap step downloads Gradle 9.3.1, creates the Gradle executable path in `GRADLE_BIN`, and persists that value through Codemagic's `$CM_ENV` environment file for later scripts.
- The Debug build script restores `GRADLE_BIN` from `$CM_ENV`, validates that it points to an executable, and runs:
  `"$GRADLE_BIN" :app:assembleDebug --no-daemon --stacktrace --console=plain`
- Gradle output is saved to the `assemble-debug.log` artifact. The script preserves Gradle's exit status from the logging pipeline through `PIPESTATUS[0]`.
- The installable, debug-keystore-signed artifact is saved as `app/build/outputs/apk/debug/app-debug.apk`. The workflow does not publish an unsigned Release APK.

Verification
- After a successful build, confirm that the Codemagic artifact is named `app-debug.apk`.
- Download it and install it on a connected device or emulator with:
  `adb install -r app-debug.apk`

Troubleshooting
- If google-services.json is required, upload it via Codemagic or include it in the repo securely.
- If a build script reports that `GRADLE_BIN` was not restored, check that the bootstrap step completed and wrote `GRADLE_BIN=<Gradle executable path>` to `$CM_ENV`. The executable path is created during bootstrap and is not expected to come from the build image.
