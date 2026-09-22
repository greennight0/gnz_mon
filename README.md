# GNZ MON

GNZ MON là ứng dụng Android phát hiện và nhận diện thực vật hoàn toàn trên thiết bị. Ảnh camera
không được gửi tới máy chủ và ứng dụng không cần API key hay kết nối Internet khi nhận diện.

## Pipeline ngoại tuyến

1. CameraX cung cấp preview, ảnh chụp và frame phân tích. Quét mục tiêu dùng `ImageCapture`
   ưu tiên chất lượng, không lấy ảnh từ preview.
2. EfficientDet-Lite0 INT8 tìm vùng cây, lá, hoa hoặc quả; tracker lọc khung trùng, chờ ba frame
   ổn định, làm mượt và chỉ hiển thị một mục tiêu chính. Mất dấu tạm thời thì giữ khung 500 ms nhưng khóa quét.
3. Khi bấm chụp, vùng chọn được cố định trong tọa độ cảm biến. Ứng dụng đổi vùng này sang bitmap
   đầy đủ của ảnh chụp, xoay ảnh và vùng chọn cùng nhau, mở rộng thành hình vuông và resize về
   `224×224`. Vùng vuông được dịch vào trong ảnh hoặc bù biên khi cần, không cắt mất đầu/cuối mẫu.
4. EfficientNet-Lite0 nhận diện tên phổ thông trên vùng mục tiêu và vùng mở rộng 10% từ cùng ảnh.
   Cả hai vùng phải cùng nhãn, điểm ≥ `0,75`, chênh lệch top-1/top-2 ≥ `0,20`.
5. Khi chưa có nhóm rõ ràng, PlantNet-300K ResNet18 chạy bằng LiteRT trên cả hai vùng, với tensor
   Float32 NCHW `[1,3,224,224]`. Hai vùng phải cùng loài, top-1 ≥ `0,70` và margin ≥ `0,15`.
   Trường hợp còn lại hiển thị chưa chắc chắn hoặc ngoài phạm vi cây/quả. Điểm mô hình
   không phải xác suất đúng đã kiểm chứng và không hiện trên thẻ kết quả chính.
6. Chỉ kết quả loài được xác nhận mới lưu cục bộ bằng Room. Tên phổ thông và gợi ý chưa chắc chắn không được lưu
   nhật ký hoặc gắn lên khung như một loài đã xác định. Nếu chụp hoặc đổi tọa độ thất bại,
   ứng dụng giải phóng lượt quét và yêu cầu chụp lại.

LiteRT ưu tiên GPU và tự động khởi tạo lại bằng CPU nếu GPU không khả dụng. PlantNet-300K là
closed-set classifier; ngưỡng từ chối giúp giảm đoán sai nhưng không bảo đảm loại bỏ mọi ảnh ngoài tập.

## Model và giấy phép

- Detector: `nature_scope_efficientdet_lite0_int8.tflite`.
- Classifier: `plantnet.tflite`, 46.942.600 byte, 1.081 loài.
- Classifier phổ thông: `common_plant.tflite`, 18.582.189 byte; danh mục Việt/Anh chỉ ánh xạ những nhãn cây/quả có trong mô hình ImageNet.
- SHA-256 classifier: `6f59f046c6a86593713aca76a3ab7bb55b520265eb66f5a77a114e450b1ccbf5`.
- Label map, manifest nguồn và thông báo Apache-2.0/BSD-2-Clause được đóng gói trong APK.

Model PlantNet được pin từ
[LiteRT-Models](https://github.com/john-rocky/LiteRT-Models/tree/main/plantnet); benchmark và mapping
loài bắt nguồn từ [PlantNet-300K](https://github.com/plantnet/PlantNet-300K).

## Build và kiểm tra

Yêu cầu Android API 26+, kiến trúc ARM64. Xem [hướng dẫn build](docs/local-android-build.md).
Quy trình kiểm thử camera và benchmark có nhãn nằm trong
[hướng dẫn đánh giá nhận diện](docs/recognition-validation.md).

```powershell
.\gradlew.bat :app:testDebugUnitTest :buildSrc:test
.\gradlew.bat :app:assembleDebug :app:verifyDebugPlantNetApk
```

Build thất bại nếu model/checksum/tensor contract/1.081 label không đúng, thiếu license hoặc archive
không chứa đúng một detector và hai classifier. APK debug nằm tại
`app/build/outputs/apk/debug/app-debug.apk`.

## Quyền riêng tư và giới hạn

- Manifest chỉ yêu cầu camera; không có quyền Internet hoặc trạng thái mạng.
- Không có Gemini, backend, Firebase AI, Retrofit hay OkHttp trong runtime.
- SNS chỉ chuyển sang ứng dụng ngoài và không tham gia pipeline nhận diện.
- Tên Anh/Việt và mô tả chưa có dữ liệu dùng tên khoa học cùng thông báo ngoại tuyến, không tự suy đoán.
- Phạm vi hiện tại gồm các nhóm cây/quả phổ thông được ánh xạ và bộ 1.081 loài PlantNet; chưa xác định loài động vật hoặc nấm.
- Kiểm thử hình học/giao diện không chứng minh độ chính xác nhận diện thực tế tăng. Cần so sánh
  trên cùng bộ ảnh có nhãn và đo trên thiết bị trước khi đưa ra kết luận về chất lượng.
- Không dùng kết quả làm căn cứ duy nhất để ăn, chạm hoặc xử lý độc tính của thực vật.
