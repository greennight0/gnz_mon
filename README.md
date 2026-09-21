# GNZ MON

GNZ MON là ứng dụng Android phát hiện và nhận diện thực vật hoàn toàn trên thiết bị. Ảnh camera
không được gửi tới máy chủ và ứng dụng không cần API key hay kết nối Internet khi nhận diện.

## Pipeline ngoại tuyến

1. CameraX cung cấp preview, ảnh chụp và frame phân tích.
2. EfficientDet-Lite0 INT8 tìm vùng cây, lá, hoa hoặc quả; tracker giữ ID mục tiêu ngắn hạn.
3. Ứng dụng crop vùng được chọn, center-crop thành hình vuông và resize về `224×224`.
4. PlantNet-300K ResNet18 chạy bằng LiteRT với tensor Float32 NCHW `[1,3,224,224]`.
5. 1.081 logits được softmax và lấy top-2. Kết quả chỉ được nhận khi top-1 ≥ `0,70` và
   margin top-1/top-2 ≥ `0,15`; trường hợp còn lại hiển thị “Không xác định”.
6. Tên khoa học và kết quả được lưu cục bộ bằng Room.

LiteRT ưu tiên GPU và tự động khởi tạo lại bằng CPU nếu GPU không khả dụng. PlantNet-300K là
closed-set classifier; ngưỡng từ chối giúp giảm đoán sai nhưng không bảo đảm loại bỏ mọi ảnh ngoài tập.

## Model và giấy phép

- Detector: `nature_scope_efficientdet_lite0_int8.tflite`.
- Classifier: `plantnet.tflite`, 46.942.600 byte, 1.081 loài.
- SHA-256 classifier: `6f59f046c6a86593713aca76a3ab7bb55b520265eb66f5a77a114e450b1ccbf5`.
- Label map, manifest nguồn và thông báo Apache-2.0/BSD-2-Clause được đóng gói trong APK.

Model PlantNet được pin từ
[LiteRT-Models](https://github.com/john-rocky/LiteRT-Models/tree/main/plantnet); benchmark và mapping
loài bắt nguồn từ [PlantNet-300K](https://github.com/plantnet/PlantNet-300K).

## Build và kiểm tra

Yêu cầu Android API 26+, kiến trúc ARM64. Xem [hướng dẫn build](docs/local-android-build.md).

```powershell
.\gradlew.bat :app:testDebugUnitTest :buildSrc:test
.\gradlew.bat :app:assembleDebug :app:verifyDebugPlantNetApk
```

Build thất bại nếu model/checksum/tensor contract/1.081 label không đúng, thiếu license hoặc archive
không chứa đúng một detector và một classifier. APK debug nằm tại
`app/build/outputs/apk/debug/app-debug.apk`.

## Quyền riêng tư và giới hạn

- Manifest chỉ yêu cầu camera; không có quyền Internet hoặc trạng thái mạng.
- Không có Gemini, backend, Firebase AI, Retrofit hay OkHttp trong runtime.
- SNS chỉ chuyển sang ứng dụng ngoài và không tham gia pipeline nhận diện.
- Tên Anh/Việt và mô tả chưa có dữ liệu dùng tên khoa học cùng thông báo ngoại tuyến, không tự suy đoán.
- Không dùng kết quả làm căn cứ duy nhất để ăn, chạm hoặc xử lý độc tính của thực vật.
