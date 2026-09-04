Codemagic Android APK build

Overview
This project uses Gradle (Kotlin DSL) to build a native Android APK. Codemagic can run the provided codemagic.yaml workflow to assemble Debug/Release APKs.

Local development
- Create a local .env (ignored by git) or set environment variables in the shell before running Gradle.
- Debug builds are automatically signed with Gradle's default debug certificate (no additional setup needed).

Codemagic setup
1. In Codemagic, ensure the environment variables are configured in Project settings.
2. Verify the codemagic.yaml workflow is set to build the APK.

Running the build
- The repo includes codemagic.yaml which runs:
  gradle :app:assembleDebug
  gradle :app:assembleRelease
- Artifacts are saved from app/build/outputs/apk/**/*.apk

Verification
- After a successful build, download the APK artifact from Codemagic build artifacts and install on a device/emulator to verify.

Troubleshooting
- If google-services.json is required, upload it via Codemagic or include it in the repo securely.
