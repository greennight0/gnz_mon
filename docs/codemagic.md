Codemagic Android APK build

Overview
This project uses Gradle (Kotlin DSL) to build a native Android APK. Codemagic can run the provided codemagic.yaml workflow to assemble Debug/Release APKs.

Required secrets / signing
- Keystore (my-upload-key.jks): DO NOT commit to git. Upload it in Codemagic UI under Project settings -> Android signing.
- STORE_PASSWORD: keystore password (set as Codemagic secret)
- KEY_PASSWORD: key password (set as Codemagic secret)
- KEYSTORE_PATH: path to keystore on build machine. When using Codemagic signing store, set this to the path Codemagic provides or update gradle signing config to read from the signing store.
- KEY_ALIAS: alias used in keystore (default in this project: "upload").

Local development
- Create a local .env (ignored by git) or set environment variables in the shell before running Gradle.
- Example (Windows PowerShell): $env:STORE_PASSWORD='...'; $env:KEY_PASSWORD='...'; $env:KEYSTORE_PATH='./my-upload-key.jks'
- For debug builds the project uses debug.keystore; no action needed.

Codemagic setup
1. In Codemagic, go to the App settings -> Signing -> Android signing.
   - Upload the keystore file and set the alias and passwords.
   - Codemagic provides a path to the keystore on the build VM or you can reference the signing by its stored name in codemagic.yaml.
2. In Codemagic UI -> Environment variables, add STORE_PASSWORD and KEY_PASSWORD as secure variables, and (if needed) KEYSTORE_PATH.
3. Ensure codemagic.yaml workflow reference (android_signing) matches the uploaded signing configuration.

Running the build
- The repo includes codemagic.yaml which runs:
  gradle :app:assembleDebug
  gradle :app:assembleRelease
- Artifacts are saved from app/build/outputs/apk/**/*.apk

Verification
- After a successful build, download the APK artifact from Codemagic build artifacts and install on a device/emulator to verify.

Security
- Never commit keystore files or passwords. Use Codemagic secrets or environment variables to store credentials.

Troubleshooting
- If Gradle cannot find the keystore, verify KEYSTORE_PATH or use Codemagic's Android signing feature so Gradle sees the file at build time.
- If google-services.json is required, upload it via Codemagic or include it in the repo securely.
