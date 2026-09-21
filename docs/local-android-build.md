# Build Android offline trên Windows

## Yêu cầu

- JDK 17 cho Android build; unit test Robolectric dùng Java 21 qua Gradle toolchain.
- Android SDK Platform 36.1 và Build-Tools 36.0.0.
- Thiết bị hoặc emulator ARM64, Android API 26 trở lên.

Lần build đầu cần mạng để Gradle tải dependency. APK đã build không cần mạng để phát hiện, nhận diện
hoặc sử dụng nhật ký.

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:testDebugUnitTest :buildSrc:test
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:bundleDebug
```

`assembleDebug` và `bundleDebug` tự chạy cổng kiểm tra model. Có thể chạy trực tiếp:

```powershell
.\gradlew.bat :app:validateDebugSpeciesClassifier
.\gradlew.bat :app:verifyDebugDetectorModelApk :app:verifyDebugPlantNetApk
.\gradlew.bat :app:verifyDebugDetectorModelBundle :app:verifyDebugPlantNetBundle
```

Cổng PlantNet kiểm tra model TFLite 46.942.600 byte, checksum đã pin, contract
`[1,3,224,224] → [1,1081]`, label map 1.081 dòng, manifest và toàn bộ thông báo giấy phép. Archive
chỉ được chứa hai model: EfficientDet detector và PlantNet classifier.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Nếu có thiết bị, chạy golden inference và các test thiết bị:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Golden test dùng ảnh `images/1.jpg` từ repository PlantNet-300K, kiểm tra top-1
`Cirsium vulgare (Savi) Ten.` và các logits tham chiếu trong tolerance cho phép.

## Checklist chế độ máy bay

1. Bật chế độ máy bay rồi mở ứng dụng.
2. Chụp/chọn ảnh thực vật và xác nhận detector → crop → classifier → UI → Room hoàn tất.
3. Thử ảnh không rõ hoặc ngoài danh mục và xác nhận kết quả “Không xác định”.
4. Xác nhận lỗi model (nếu có) nói rõ model ngoại tuyến chưa sẵn sàng, không nhắc DNS/máy chủ.
5. Chạy 100 lượt, ghi p95 GPU và bộ nhớ; mục tiêu GPU ≤ 500 ms và không tăng bộ nhớ liên tục.
