# Kiểm chứng nhận diện ngoại tuyến

## Kiểm thử mã và giao diện

```powershell
.\gradlew.bat :app:testDebugUnitTest :buildSrc:test
.\gradlew.bat :app:assembleDebug :app:verifyDebugPlantNetApk :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:testDebugUnitTest --tests com.example.UncertainScreenshotTest '-Proborazzi.test.record=true'
```

`TargetSnapshotTest` kiểm tra ánh xạ preview → cảm biến → bitmap đầy đủ, gồm vùng cắt lệch gốc,
tỷ lệ ảnh khác nhau, preview phản chiếu, bốn góc xoay, giữ nguyên vùng khi tracker/ma trận thay đổi,
thiếu ma trận, ma trận suy biến và vùng ngoài ảnh. `ScanStateUiTest` kiểm tra lỗi capture giải phóng
lượt quét và có thể thử lại. Test classifier/repository kiểm tra top-3, điểm 34%/67%, ngưỡng xác nhận,
đầu ra không hợp lệ và việc chỉ lưu kết quả đủ ngưỡng.

Ảnh UI được xuất trong `app/build/reports/recognition/vi-34.png` và `en-67.png`. Hai ảnh người dùng
đính kèm chỉ là tham chiếu lỗi thông báo/bố cục. Tên ứng viên trong test UI là dữ liệu giả, không phải
nhãn loài của các ảnh đính kèm.

## Thiết bị và golden inference

Cần Android ARM64, camera thật để kiểm tra capture; emulator chỉ thay thế được phần không phụ
thuộc chất lượng camera. Không có thiết bị thì chỉ báo cáo build/kiểm thử JVM, không báo inference đã đạt.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
& $adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb shell am instrument -w -e class com.example.data.classifier.PlantNetGoldenInstrumentedTest com.aistudio.gnzmon.wqrk.test/androidx.test.runner.AndroidJUnitRunner
& $adb shell am instrument -w -e class com.example.NatureScopeDetectorInstrumentedTest com.aistudio.gnzmon.wqrk.test/androidx.test.runner.AndroidJUnitRunner
& $adb shell am instrument -w -e class com.example.CameraLifecycleStressTest com.aistudio.gnzmon.wqrk.test/androidx.test.runner.AndroidJUnitRunner
& $adb shell am instrument -w -e class com.example.CameraCaptureInstrumentedTest com.aistudio.gnzmon.wqrk.test/androidx.test.runner.AndroidJUnitRunner
```

Cài đè bằng `-r` giữ dữ liệu; không dùng `uninstall` hoặc `pm clear`. Sao lưu SQLite và WAL trước
kiểm thử, đối chiếu từng dòng có trước sau khi chạy, cho phép có mục mới do người dùng thêm.
Không khôi phục đè bản sao cũ. Đọc kết quả `OK (...)`/`FAILURES` của instrumentation: lệnh ADB
có thể trả mã thoát 0 ngay cả khi tiến trình test crash. Xem
[báo cáo RMX2021 ngày 22/09/2026](device-validation-RMX2021-2026-09-22.md).

Golden fixture được đóng gói sẵn, là ảnh vuông có tên chuẩn `Cirsium vulgare (Savi) Ten.`. Hai bài
kiểm thử tách biệt: tensor cố định so sánh toàn bộ 1.081 logits với CPU tham chiếu (sai số tuyệt đối
`0,005`); pipeline JPEG/Android kiểm tra đúng tên loài và vượt ngưỡng xác nhận đang dùng.
Pillow BILINEAR và Android Canvas tạo đầu vào khác nhau khi thu nhỏ ảnh nên không dùng cùng
logits tuyệt đối cho cả hai pipeline. Tensor `.f32`, checksum và logits tham chiếu có thể tái tạo
bằng `python tools/generate_plantnet_golden.py`; script kiểm tra checksum model/ảnh trước khi xuất.
Một ảnh không đại diện cho độ chính xác trên thực vật/quả.

`CameraCaptureInstrumentedTest` chụp 20 frame thật (trước/sau × dọc/ngang × giữa/bốn góc), kiểm tra
ảnh vuông, hướng ảnh, ID mục tiêu cố định và thử lại sau khi tạm thiếu use case chụp. Test không
phân loại, không lưu ảnh camera và không thêm mục nhật ký. Kết quả kích thước/thời gian nằm trong
`camera-capture-smoke.json` ở thư mục files của ứng dụng. `CameraLifecycleStressTest` kiểm tra
đổi camera, vào nền, mở lại và tạo lại Activity; chỉ yêu cầu cấp quyền nếu quyền chưa có.

Kiểm tra thủ công trên camera trước và sau, dọc/ngang:

1. Đặt mẫu ở giữa và sát từng cạnh, xác nhận ảnh nhỏ khi phân tích chứa đúng mẫu và không mất đầu/cuối.
2. Đổi hướng máy giữa các lần quét; kiểm tra phản chiếu camera trước và preview khác tỷ lệ ảnh chụp.
3. Bấm chụp rồi để tracker cập nhật; xác nhận lượt quét giữ vùng và ID ở thời điểm bấm. Nếu di chuyển
   vật thật trước khi màn trập chụp, ảnh có thể khác; cố định vùng không có nghĩa là đóng băng cảnh.
4. Gây lỗi camera, thử lại; không bị kẹt ở trạng thái đang chụp. Lỗi biến đổi tọa độ có kiểm thử JVM
   riêng vì không thể chủ động tạo trên mọi điện thoại.
5. Kiểm tra tiếng Việt/Anh, chế độ máy bay, nhật ký không chứa gợi ý chưa xác nhận.

## Bộ dữ liệu benchmark

Chuẩn bị thư mục `plant-benchmark` gồm `manifest.json` và ảnh gốc. Mỗi ảnh phải được xoay sẵn đúng
hướng: benchmark đọc bitmap, không tự áp EXIF orientation. Chọn các nhóm `plant`, `leaf`, `flower`,
`fruit`, `object`, gồm mẫu đủ sáng, thiếu sáng, nền phức tạp và mẫu sát biên. Giữ cố định bộ dữ liệu giữa
hai phiên bản; ghi nguồn, quyền sử dụng, checksum và người xác nhận nhãn trong hồ sơ dữ liệu đi kèm.

Manifest là mảng JSON. Ví dụ cấu trúc dưới đây cần có ảnh thật tương ứng; repository chỉ kèm một
golden fixture, chưa có bộ benchmark đầy đủ cho cả năm nhóm:

```json
[
  {
    "image": "flower-01.jpg",
    "category": "flower",
    "scientificName": "Cirsium vulgare (Savi) Ten.",
    "rect": [80, 40, 360, 560]
  },
  {
    "image": "object-01.jpg",
    "category": "object"
  }
]
```

- `image`: đường dẫn tương đối từ thư mục manifest.
- `category`: một trong năm nhóm ở trên.
- `scientificName`: tên chuẩn được kiểm chứng, bắt buộc với bốn nhóm thực vật; đối chiếu cách viết
  trong `app/src/main/assets/models/plantnet_labels.txt` nếu loài thuộc danh mục. Mẫu ngoài danh mục
  giữ nhãn thật và cần được phân tích riêng; không đổi nhãn để khớp dự đoán.
- `rect`: `[left, top, right, bottom]` theo pixel trong ảnh đã xoay đúng hướng, nằm hoàn toàn trong ảnh,
  rộng/cao ít nhất một pixel. Bỏ trường này để dùng toàn ảnh. Nên có nhiều vùng không vuông để
  đánh giá khác biệt giữa center-crop cũ và cách giữ trọn mẫu mới.
- Với `object`, không cần tên loài. Mọi kết quả xác nhận thực vật trên ảnh đồ vật đều tính là sai.

Không suy đoán nhãn khoa học từ hai ảnh chụp màn hình hoặc từ chính kết quả mô hình.

## Chạy so sánh và lấy báo cáo

Cài hai APK bằng lệnh ở trên, rồi chạy trong PowerShell từ thư mục repository:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$deviceBenchmark = '/sdcard/Android/data/com.aistudio.gnzmon.wqrk/files/plant-benchmark'
& $adb shell mkdir -p $deviceBenchmark
& $adb push ./plant-benchmark/. $deviceBenchmark
& $adb shell am instrument -w -e class com.example.data.classifier.PlantNetComparisonInstrumentedTest -e plantBenchmarkManifest "$deviceBenchmark/manifest.json" com.aistudio.gnzmon.wqrk.test/androidx.test.runner.AndroidJUnitRunner
& $adb pull /sdcard/Android/data/com.aistudio.gnzmon.wqrk/files/plantnet-comparison.json ./app/build/reports/plantnet-comparison.json
```

Bỏ đối số `-e plantBenchmarkManifest ...` để chạy thử với một golden fixture có sẵn; báo cáo khi đó
có `smokeOnly: true`, chỉ xác nhận công cụ chạy được. Đây không phải benchmark độ chính xác đầy đủ.

Mỗi ảnh chạy hai cách cắt `legacy` và `whole_specimen`, cùng mô hình, CPU, ngưỡng `0,70` và chênh lệch
`0,15`. Công cụ warm-up trước khi đo và ghi từng dự đoán cùng tổng hợp theo nhóm:

| Trường | Ý nghĩa |
| --- | --- |
| `count`, `accepted` | Số mẫu và số kết quả được xác nhận |
| `acceptedPrecision` | Số kết quả xác nhận đúng / số kết quả xác nhận; `null` nếu không có kết quả xác nhận |
| `uncertainRate` | Số mẫu không đủ ngưỡng / tổng số mẫu |
| `meanMs`, `p95Ms` | Thời gian cắt ảnh + tiền xử lý + suy luận |

Thời gian không bao gồm decode ảnh, màn trập camera hoặc UI. Benchmark này so sánh cách cắt cùng
một ảnh gốc, không mô phỏng đầy đủ khác biệt chất lượng giữa preview cũ và ảnh chụp mới. Đo thêm
thời gian từ bấm chụp đến kết quả trên máy thật; ghi thiết bị, Android, commit, kích thước bộ dữ liệu
và điều kiện nhiệt. Không coi điểm mô hình tăng là độ chính xác tăng, không coi kiểm thử JVM đạt
là suy luận trên ARM64 đã đạt, và không suy rộng từ một golden fixture sang toàn bộ thực vật/quả.
