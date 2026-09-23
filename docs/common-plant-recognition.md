# Nhận diện cây/quả và khung mục tiêu ổn định

## Hành vi

- EfficientNet-Lite0 ImageNet chạy trước PlantNet, trên hai vùng từ cùng ảnh chụp. Vùng thứ hai tăng chiều rộng/cao 10% (5% mỗi cạnh); phần vượt biên dùng màu đệm. Ảnh nhập từ thư viện dùng vùng trong 90% và toàn ảnh vì không có dữ liệu ngoài biên.
- Chấp nhận nhóm phổ thông khi cả hai vùng cùng nhãn, điểm ≥ 0,75 và chênh lệch top-1/top-2 ≥ 0,20. Nhãn nhóm không được chuyển thành tên khoa học hoặc tự ghi vào nhật ký loài.
- Khi không có nhóm phổ thông rõ ràng, PlantNet phải đồng ý trên hai vùng với ngưỡng 0,70 và margin 0,15. Hai dự đoán đồng ý vẫn có thể cùng sai; đây không phải bộ phát hiện ngoài phân phối hoàn chỉnh.
- Nhãn tổng quát rõ ràng ngoài cây/quả trả thông báo ngoài phạm vi. Nấm và cỏ khô trả chưa chắc chắn. Không hỗ trợ mọi cây/quả, ví dụ không có nhóm xoài trong mô hình bổ sung.
- Mô hình bổ sung 18.582.189 byte, dùng dependency MediaPipe đã có; không thêm quyền Internet. Checksum và nhãn được kiểm tra trước đóng gói và trong APK/AAB.

## Theo dõi

Ứng viên mới cần ba frame liên tiếp ≥ 0,50; mục tiêu đã xác nhận tiếp tục được nối ở ≥ 0,22. NMS IoU 0,50, ghép một-một theo IoU ≥ 0,25, EMA với hằng số 150 ms. Mục tiêu mất dấu được giữ tối đa 500 ms theo đồng hồ đơn điệu nhưng không được quét. Chỉ vẽ mục tiêu đang chọn, ưu tiên tâm khi chưa chọn; chạm để đổi hoặc tạo vùng thủ công.

## Kiểm chứng

```powershell
.\gradlew.bat :app:testDebugUnitTest :buildSrc:test :app:assembleDebug :app:assembleDebugAndroidTest
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
& $adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb shell am instrument -w -e class com.example.data.classifier.CommonPlantBenchmarkInstrumentedTest com.aistudio.gnzmon.wqrk.test/androidx.test.runner.AndroidJUnitRunner
& $adb pull /sdcard/Android/data/com.aistudio.gnzmon.wqrk/files/common-plant-benchmark.json app/build/reports/common-plant/
```

Thêm `-e enforceAccuracy true` để test thất bại nếu bộ acceptance chưa đạt recall chuối 90% hoặc precision kết quả chấp nhận 95%. Test mặc định chỉ kiểm tra pipeline chạy và xuất báo cáo, không có nghĩa độ chính xác đã đạt.

`tools/prepare_common_plant_benchmark.py` tái tạo 83 ảnh kiểm thử: Fruits-360 (72 ảnh), PlantVillage lá cà chua (8), scikit-image cảnh đồ vật (2), golden hoa PlantNet (1). Manifest lưu nguồn, revision, nhãn tác giả và SHA-256; ảnh chỉ nằm trong APK test. Calibration và acceptance dùng ảnh tách biệt. Fruits-360 có nhiều góc quay tương tự của cùng mẫu; bộ này không đại diện cho cảnh camera thực tế. Hoa chỉ có một golden fixture; chưa có bộ hoa hiệu chỉnh độc lập. Chưa có chuyên gia xác minh độc lập toàn bộ nhãn khoa học.

Giữ ngưỡng cố định trước khi chạy acceptance. Báo cáo riêng kết quả chấp nhận, từ chối và recall chuối; không thay nhãn hoặc loại ảnh khó để đạt chỉ tiêu. Kiểm thử rung 10 giây bằng chuỗi tọa độ tổng hợp không thay thế phép đo camera cảnh tĩnh thật.

## Kết quả phiên triển khai

- 122 kiểm thử JVM của ứng dụng và 5 kiểm thử buildSrc: đạt. Có kiểm thử kết quả trả về muộn sau khi đổi mục tiêu, mục tiêu tạm mất dấu không được quét, và tracking giữ ID/giảm rung ít nhất 50% trên chuỗi tổng hợp 10 giây.
- Sau chỉnh sửa giao diện cuối, chạy lại 43 kiểm thử giao diện/trạng thái/chọn mục tiêu: đạt. Đã xem ảnh Việt/Anh: dấu tiếng Việt đúng, một khung, không còn ID hoặc phần trăm trên thẻ chính, các nút có độ tương phản rõ.
- APK ứng dụng và APK kiểm thử đã build; kiểm tra checksum/đóng gói ba mô hình đạt. Mô hình bổ sung 18,6 MB, dưới giới hạn 100 MB.
- RMX2021, Android 10/API 29: benchmark 83 ảnh chạy xong trong 48,089 giây; kiểm thử chụp camera đạt 20 trường hợp trong 28,621 giây (trước/sau, dọc/ngang, tâm/bốn góc, thử lại sau lỗi capture). Hai vùng ảnh cùng lần chụp có kích thước đúng.
- Benchmark dùng CPU cho PlantNet để kết quả tái lập được; chưa đo riêng đường GPU của bộ phân loại kết hợp.
- Đã kiểm tra với chế độ máy bay bật, Wi-Fi tắt, dữ liệu di động tắt; hệ thống báo `Active default network: none`. Bluetooth vẫn bật vì ROM không cho shell tắt Bluetooth; ứng dụng không có quyền Internet. Đã khôi phục airplane=0, Wi-Fi=1, Bluetooth=1, mobile-data=0 như trước kiểm thử.
- Đối chiếu từng dòng nhật ký: 8 mục trước/sau, không đổi và không thêm mục do benchmark.
- APK cuối đã cài bằng `install -r` và khởi động thành công; kiểm tra nhật ký lại sau lần cài cuối vẫn giữ đủ 8 mục. APK 117.935.503 byte, SHA-256 `2d9dd06d7a598979ac0f18cacae2fef4eb758debb0582b4b408b68b5a9c4790f`, tại `app/build/outputs/apk/debug/app-debug.apk`.

| Bộ ảnh | Số ảnh | Kết quả chấp nhận | Đúng trong kết quả chấp nhận | Recall chuối | Từ chối |
| --- | ---: | ---: | ---: | ---: | ---: |
| Calibration | 41 | 18 | 14/18 = 77,8% | 10/20 = 50% | 56,1% |
| Acceptance | 42 | 19 | 14/19 = 73,7% | 8/20 = 40% | 54,8% |

**Chưa đạt nghiệm thu độ chính xác** (yêu cầu recall chuối ≥ 90%, precision ≥ 95%). Không điều chỉnh ngưỡng theo kết quả acceptance. Cả 8 ảnh chuối được chấp nhận đều đúng tên phổ thông; 12 ảnh chuối còn lại bị từ chối.

Năm lỗi được chấp nhận trong bộ acceptance: một ảnh cam thành `Cucurbita maxima`, hai ảnh xoài thành `Cucurbita pepo`/táo, sàn nhà thành `Lithops pseudotruncatella`, và golden hoa thành nhóm cardoon. Hai vùng đồng ý chưa đủ để loại bỏ dự đoán sai tự tin cao. Cần cải thiện mô hình/phát hiện ngoài phạm vi trước khi coi nhận diện phổ thông đạt yêu cầu.

Lệnh benchmark mặc định báo `OK (1 test)` chỉ có nghĩa đã chạy và xuất báo cáo; `numericalGatePassed` trong báo cáo là `false`. Chưa có cảnh mẫu đứng yên được xác nhận để nghiệm thu mức giảm rung 50% trên camera thật. Ảnh UI Việt/Anh là fixture để kiểm tra bố cục, không phải bằng chứng nhận đúng ảnh chuối.

Chứng cứ cục bộ: `app/build/reports/common-plant/` gồm `full-build.log`, `jvm-full-results.zip`, `common-plant-benchmark.json`, `camera-capture-smoke.json`, `journal-verification.json`, các trạng thái mạng và ảnh `vi.png`/`en.png`. Bản sao nhật ký nằm trong thư mục build và không đưa vào Git.
