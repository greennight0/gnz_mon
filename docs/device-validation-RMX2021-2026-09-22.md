# Kiểm thử RMX2021 — 22/09/2026

## Phạm vi

Thiết bị RMX2021, Android 10/API 29, ABI ARM64. Cài đè APK debug và APK instrumentation bằng
`adb install -r`; không gỡ ứng dụng, không xóa dữ liệu. Nhật ký được sao lưu trước khi kiểm thử.
Mã nền: commit `2797e0f`, cộng các thay đổi đang có trong workspace.

Giữ PlantNet 1.081 nhãn, ngưỡng `0,70`, chênh lệch `0,15`, xử lý trên thiết bị. Hai ảnh chụp màn hình
chỉ phục vụ hồi quy giao diện; không dùng làm nhãn loài chuẩn.

## Lỗi phát hiện và sửa

- Cập nhật `ImageCapture.targetRotation` theo display ngay khi bấm chụp. Activity xử lý thay đổi
  hướng màn hình mà không tạo lại use case, nên không thể chỉ dựa vào hướng lúc khởi tạo camera.
- Golden test cũ so sánh logits từ ảnh resize bằng Pillow với ảnh resize bằng Android Canvas.
  Cùng tensor Android, host và điện thoại chỉ lệch tối đa khoảng `0,0001722`; lỗi trước đó nằm ở
  đầu vào tham chiếu khác nhau. Golden mới so toàn bộ 1.081 logits từ tensor cố định, sai số tuyệt
  đối `0,005`; bài riêng kiểm tra JPEG qua pipeline Android và tên loài được xác nhận. Không hạ
  ngưỡng nhận diện để làm test đạt.
- Bản đầu phiên vẫn crash `SIGBUS/BUS_ADRERR` trong `libmediapipe_tasks_vision_jni.so` khi chạy
  vòng đời camera. Golden (2 bài) và detector (2 bài) đạt riêng, không đủ chứng minh crash đã hết.
- MediaPipe 0.10.32 nạp asset qua một tệp cache được ghi đè mỗi lần, rồi ánh xạ tệp vào bộ nhớ.
  Tạo graph mới khi graph cũ còn tồn tại có thể làm graph cũ đọc vùng tệp vừa bị truncate. Điều này
  phù hợp với SIGBUS quan sát được. Đổi sang `setModelAssetBuffer` với nội dung riêng cho từng graph
  loại bỏ đường ghi đè cache. Chỉ riêng thay đổi này đã làm bài vòng đời trước đó lỗi chạy đạt
  (`17,351 s`). Tham khảo mã nguồn phiên bản đã dùng:
  [cache asset](https://github.com/google-ai-edge/mediapipe/blob/v0.10.32/mediapipe/util/android/asset_manager_util.cc),
  [ánh xạ model](https://github.com/google-ai-edge/mediapipe/blob/v0.10.32/mediapipe/tasks/cc/core/external_file_handler.cc).
- Kiểm tra bytecode AAR đang dùng xác nhận `MPImage.close()` gọi `Bitmap.recycle()`. Đã bỏ việc
  coi bitmap truyền vào `BitmapImageBuilder` là buffer tái sử dụng, đọc kích thước trước khi đóng,
  và giải phóng cả nhánh lỗi trước khi chuyển quyền sở hữu. Đóng detector vẫn đồng bộ với inference
  và chỉ thực hiện một lần; detector tạo xong sau khi coroutine bị hủy cũng được đóng.
- Bài chụp ban đầu timeout vì continuation của Compose test tiếp tục trên luồng nền và CameraX
  từ chối `getSurfaceProvider`. Phần khởi tạo/gắn camera nay dùng `Dispatchers.Main.immediate`
  một cách tường minh; tải detector vẫn chạy nền. Nhánh lỗi trước khi giao detector cho analyzer
  đóng tài nguyên, và test báo lỗi khởi tạo ngay thay vì chỉ chờ timeout.

## Bằng chứng

Log và JSON nằm trong `app/build/reports/device-validation/run-20260922/`. Log lỗi ban đầu được
giữ lại riêng (`lifecycle.txt`, `crash-lifecycle.txt`, `logcat-lifecycle.txt`) để không nhầm với kết quả
của bản đã sửa. Tệp backup nhật ký chỉ giữ cục bộ trong thư mục build; không đưa nội dung vào tài liệu.

## Kết quả bản cuối

| Kiểm chứng | Kết quả | Thời gian |
| --- | --- | --- |
| Unit test trên mã cuối | 106/106 đạt, 0 lỗi, 0 bỏ qua | Gradle tổng 4 phút 8 giây |
| Đóng gói PlantNet và detector | Cả hai tác vụ verify đạt | Trong lượt Gradle trên |
| Golden: tensor cố định và JPEG Android | 2/2 đạt, chạy riêng | 10,563 s |
| Detector: khởi tạo và manifest | 2/2 đạt, chạy riêng | 8,446 s |
| Camera: đổi trước/sau, vào nền, mở lại, recreate | 1/1 đạt, chạy riêng | 15,202 s |
| Camera: 20 lượt chụp và thử lại sau lỗi | 1/1 đạt, chạy riêng | 44,500 s |
| Toàn bộ instrumentation | 8/8 đạt, gồm cả bài chọn mục tiêu khi tracker đổi | 43,110 s |
| Benchmark ảnh chuẩn | 1/1 đạt, chạy riêng sau suite | 9,328 s |

Thời gian instrumentation ở bảng là thời gian phía máy tính, gồm khởi động runner và ADB;
JUnit báo `35,988 s` cho toàn suite. Mã thoát ADB không được dùng riêng để kết luận đạt:
đã kiểm tra `OK (8 tests)` và kết quả từng nhóm. Không ghi nhận crash trong các lượt đạt này;
đây là xác nhận trên thiết bị và kịch bản đã thử, không phải bảo đảm cho mọi thiết bị.

Lượt chụp riêng có 20 ảnh, `762–1.934 ms`, trung bình `1.126,05 ms`. Lượt chụp trong toàn suite
cũng đủ 20 ảnh: `778–1.870 ms`, trung bình `1.011,05 ms`, p95 `1.830 ms`. Số đo là từ yêu cầu chụp
đến nhận snapshot trong test, có cả thời gian chờ của test; không gồm phân loại loài. Camera sau
trả ảnh `2448×3264`/`3264×2448`, camera trước `1944×2592`/`2592×1944`. Test kiểm tra ảnh cắt
vuông, đúng hướng/ID và detector xử lý nhiều frame, không lưu ảnh camera hay mục nhật ký.

Benchmark CPU dùng duy nhất ảnh chuẩn `Cirsium vulgare (Savi) Ten.`, `smokeOnly: true`:

| Cách xử lý | Xác nhận đúng | Tỷ lệ chưa xác định | Cắt + tiền xử lý + suy luận |
| --- | --- | --- | --- |
| `legacy` | 1/1 | 0/1 | 331,165 ms |
| `whole_specimen` | 1/1 | 0/1 | 294,153 ms |

Với một mẫu, mean và p95 trùng nhau. Không suy ra cải thiện độ chính xác hoặc tốc độ tổng thể
từ các số này. Ảnh chuẩn vốn vuông nên không đánh giá được lợi ích giữ trọn một mẫu dài sát biên.

Đối chiếu SQLite cùng WAL trước/sau: **1/1 mục có trước giữ nguyên toàn bộ cột**, tổng sau vẫn 1,
không có mục mới. Chỉ đọc bản sao để so sánh theo ID; không ghi khôi phục hay xóa dữ liệu trên máy.

## APK và hồ sơ đo

Checksum SHA-256 của tệp trên máy tính đã được đối chiếu với `base.apk` thực sự cài trên điện thoại;
cả ứng dụng và test APK đều khớp:

- `app/build/outputs/apk/debug/app-debug.apk`:
  `08413e5e9a4037ddcefed43b13f56e7984ebf0b222fa08e94f89812e7c1d3f1f`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`:
  `3a3b4fa64746298211ff5dddb41727b423a2850e51665ac5ac6cf639387eec49`

Fingerprint thiết bị: `realme/RMX2021/RMX2021:10/QP1A.190711.020/1651798546:user/release-keys`.

Trong thư mục bằng chứng nêu trên:

- `verification-final.txt`, `unit-tests-handoff.json`: Gradle hoàn tất và số unit test.
- `golden-handoff`, `detector-handoff`, `lifecycle-handoff`, `suite-handoff`, `benchmark-handoff`:
  mỗi tên có `.txt` và `.json`, giữ kết quả và thời gian từng lượt.
- `capture-main-dispatcher.txt/.json`, `camera-capture-isolated.json`: lượt chụp riêng.
- `camera-capture-smoke.json`: đủ 20 lượt chụp trong suite cuối.
- `plantnet-comparison.json`, `plantnet-android-golden.json`: kết quả mô hình trên điện thoại.
- `apk-checksums.json`, `device.json`, `journal-comparison.json`, `logcat-handoff.txt`: đối chiếu
  APK, thông tin thiết bị, kiểm tra nhật ký và log đã lấy về máy tính.

Các tệp build là bằng chứng cục bộ, không được Git theo dõi và có thể mất khi chạy clean.

## Cần mẫu thực trước camera

Kiểm thử tự động xác nhận luồng chụp, kích thước, hướng ảnh, xử lý lỗi và vòng đời; không xác nhận
đúng vị trí của một mẫu thực vật cụ thể. Người dùng cần đặt cây/lá/hoa/quả đủ sáng trước camera
trước/sau, ở giữa và sát từng cạnh, rồi kiểm tra ảnh mẫu có lấy trọn đối tượng. Thử di chuyển tracker
sau khi bấm chụp để đối chiếu vùng đã khóa; chuyển động thật trước lúc màn trập vẫn có thể đổi nội dung ảnh.

Benchmark một ảnh chuẩn chỉ chứng minh luồng suy luận hoạt động. Chưa có bộ ảnh có nhãn đủ năm nhóm
cây/lá/hoa/quả/đồ vật nên chưa thể kết luận độ chính xác tổng thể tăng. Xem
[hướng dẫn benchmark và kiểm thử](recognition-validation.md) để chuẩn bị manifest, nhãn chuẩn và vùng chọn.
