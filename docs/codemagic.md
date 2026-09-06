Codemagic Android APK build

Overview
This project uses Gradle (Kotlin DSL) to build a native Android APK. Codemagic can run the provided codemagic.yaml workflow to assemble Debug/Release APKs. The workflow downloads Gradle 9.3.1 at runtime so builds do not depend on the Gradle version preinstalled on the Codemagic build image.

Local development
- Create a local .env (ignored by git) or set environment variables in the shell before running Gradle.
- Debug builds are automatically signed with Gradle's default debug certificate (no additional setup needed).

Codemagic setup
1. In Codemagic, ensure the environment variables are configured in Project settings.
2. Verify the codemagic.yaml workflow is set to build the APK.

Running the build
- The repo includes codemagic.yaml. Its bootstrap step downloads Gradle 9.3.1, creates the Gradle executable path in `GRADLE_BIN`, and persists that value through Codemagic's `$CM_ENV` environment file for later scripts.
- The Debug and optional Release build scripts restore `GRADLE_BIN` from `$CM_ENV`, validate that it points to an executable, and run:
  "$GRADLE_BIN" :app:assembleDebug --no-daemon --stacktrace
  "$GRADLE_BIN" :app:assembleRelease --no-daemon --stacktrace
- Artifacts are saved from app/build/outputs/apk/**/*.apk

Verification
- After a successful build, download the APK artifact from Codemagic build artifacts and install on a device/emulator to verify.

Troubleshooting
- If google-services.json is required, upload it via Codemagic or include it in the repo securely.
- If a build script reports that `GRADLE_BIN` was not restored, check that the bootstrap step completed and wrote `GRADLE_BIN=<Gradle executable path>` to `$CM_ENV`. The executable path is created during bootstrap and is not expected to come from the build image.
