# GNZ MON

GNZ MON là ứng dụng Android dùng camera để phát hiện, theo dõi các đối tượng tự nhiên theo
thời gian thực và định danh loài từ ảnh do người dùng chủ động chụp. Giao diện được xây dựng
bằng Jetpack Compose; nhật ký các loài đã nhận diện được lưu cục bộ bằng Room.

> **Phân biệt hai tầng AI:** Google ML Kit chạy **trên thiết bị** để tạo bounding box và
> tracking ID trên luồng camera. Gemini **không xử lý video trực tiếp**; API chỉ được gọi khi
> người dùng yêu cầu phân tích một ảnh chụp (hoặc ảnh được chọn), nhằm trả về thông tin định
> danh và phân loại sinh học.

## Kiến trúc duy nhất đang được triển khai

```text
Camera / ảnh thư viện
       │
       ├─ CameraX Preview ───────────────────────────► PreviewView + Compose overlay
       │
       ├─ CameraX ImageAnalysis (KEEP_ONLY_LATEST, YUV_420_888)
       │        └─ ML Kit Object Detection (STREAM_MODE, on-device)
       │             └─ bounding box + tracking ID
       │                  └─ fallback IoU/khoảng cách tâm + One Euro Filter
       │                       └─ khung theo dõi được ánh xạ lên PreviewView
       │
       └─ Người dùng chọn mục tiêu và chụp/chọn ảnh
                └─ crop vùng bounding box (nếu có)
                     └─ resize ≤ 1024 px, JPEG 85, Base64
                          └─ Gemini 2.5 Flash qua HTTPS
                               └─ JSON phân loại sinh học
                                    └─ UI chi tiết + nhật ký Room
```

Đây là pipeline thực tế duy nhất của dự án. Ứng dụng hiện **không triển khai** YOLOv8,
SSD MobileNet, BYTETracker, DeepSORT, Kalman Filter hoặc Re-ID. Không nên dùng các tên đó để
mô tả GNZ MON nếu chưa bổ sung implementation và dependency tương ứng.

## Luồng xử lý

1. Sau khi được cấp quyền camera, `CameraPreviewView` bind ba CameraX use case vào lifecycle:
   `Preview`, `ImageCapture` và `ImageAnalysis`.
2. `Preview` hiển thị camera trong `PreviewView`. `ImageAnalysis` chỉ giữ frame mới nhất để
   tránh tích tụ hàng đợi; một executor riêng chuyển frame YUV sang `ObjectDetectorAnalyzer`.
3. Analyzer tạo `InputImage` có rotation metadata và gọi ML Kit. Kết quả được chuẩn hóa về
   `[0, 1]`, làm ổn định track rồi biến đổi từ hệ tọa độ ảnh sang hệ tọa độ preview trước khi
   Compose vẽ bounding box.
4. Người dùng chọn một track rồi nhấn nút chụp. Ứng dụng ưu tiên bitmap hiện tại của
   `PreviewView` để phản hồi nhanh; nếu không có thì dùng `ImageCapture`, chuyển `ImageProxy`
   thành bitmap và xoay ảnh đúng hướng. Ảnh từ system photo picker cũng có thể đi vào cùng
   luồng phân tích.
5. `MainViewModel` crop ảnh theo bounding box mục tiêu (nếu có), rồi gửi ảnh đến repository.
   `GeminiVisionClient` mới là nơi thực hiện yêu cầu nhận diện loài qua mạng.
6. Kết quả được gắn lại vào track, hiển thị trên giao diện và lưu trong nhật ký Room. Nếu API
   key thiếu hoặc lời gọi Gemini thất bại, repository hiện dùng mẫu từ knowledge base offline
   thay cho kết quả Gemini.

## Thuật toán tracking thực tế

`ObjectDetectorAnalyzer` cấu hình Google ML Kit Object Detection bằng `STREAM_MODE`, bật đa
đối tượng và classification. ML Kit thực hiện detection/tracking thời gian thực trên thiết bị
và cung cấp `trackingId` khi có thể.

Để duy trì track khi ML Kit không trả ID hoặc ID không dùng được trong frame hiện tại:

- Ưu tiên ID của ML Kit nếu ID đó chưa được gán cho đối tượng khác trong cùng frame.
- Nếu cần fallback, so khớp detection với lịch sử chưa được gán bằng Intersection over Union
  (IoU) và khoảng cách bình phương giữa hai tâm. Track được nhận lại khi IoU đạt ít nhất
  `0.25`, hoặc khoảng cách tâm chuẩn hóa bình phương không quá `0.015`.
- Nếu không có ứng viên phù hợp, cấp synthetic ID trong dải bắt đầu từ `200`.
- Bounding box được làm mượt độc lập trên bốn cạnh bằng adaptive One Euro Filter. Chế độ
  `STATIONARY`/`MOBILE` được chọn từ lịch sử 10 mẫu vận tốc, có hysteresis và debounce 300 ms;
  sampling rate được chặn trong khoảng 10–60 Hz.
- Track không xuất hiện quá 1.500 ms bị xóa khỏi lịch sử. Đây là cơ chế hết hạn lịch sử, không
  phải Kalman prediction hay nhận dạng lại theo ngoại hình.

## AI nhận diện loài bằng Gemini

Gemini chỉ được gọi cho **ảnh tĩnh sau thao tác phân tích**, không được gọi trên từng frame của
`ImageAnalysis`:

- Ảnh đầu vào được giảm kích thước theo tỉ lệ sao cho cạnh lớn nhất tối đa `1024 px`.
- Ảnh được nén JPEG chất lượng `85`, mã hóa Base64 và gửi dưới dạng `inlineData`.
- Endpoint hiện dùng model `gemini-2.5-flash` với timeout kết nối/đọc/ghi 30 giây.
- Prompt yêu cầu chỉ trả về một JSON object gồm tên Anh/Việt, tên khoa học, nhóm sinh vật,
  kingdom, family, order, mô tả, môi trường sống, phân bố, vai trò sinh thái, thông tin thú vị,
  tình trạng bảo tồn, cảnh báo an toàn và điểm tin cậy.

Kết quả từ mô hình có thể sai hoặc không chắc chắn. Không dùng nhận diện của ứng dụng thay thế
chuyên gia khi quyết định ăn, chạm, chăm sóc sinh vật, xử lý độc tính hoặc đánh giá bảo tồn.

## Công nghệ và phụ thuộc chính

Các thành phần được khai báo và sử dụng trong ứng dụng gồm:

| Thành phần | Vai trò |
| --- | --- |
| Kotlin, Android Gradle Plugin, KSP, Secrets Gradle Plugin | Ngôn ngữ, build, sinh mã Room và đưa biến cấu hình vào `BuildConfig` |
| Jetpack Compose + Material 3 | Giao diện khai báo và overlay camera |
| CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) | Preview, phân tích frame và chụp ảnh |
| Google ML Kit Object Detection `17.0.2` | Detection, classification thô và tracking on-device |
| Room `2.7.0` | Lưu nhật ký loài trên thiết bị |
| OkHttp `4.10.0` | Gửi HTTP request trực tiếp tới Gemini API |
| Kotlin Coroutines | Xử lý bất đồng bộ và state |
| Accompanist Permissions | Yêu cầu quyền camera trong Compose |
| Robolectric, Roborazzi, JUnit, Compose UI Test | Unit/UI/screenshot testing |

Phiên bản đầy đủ và danh sách dependency có hiệu lực nằm trong `gradle/libs.versions.toml` và
`app/build.gradle.kts`. Version catalog còn chứa một số alias hoặc dependency hỗ trợ khác;
bảng trên chỉ mô tả các thành phần tham gia trực tiếp vào pipeline và chức năng hiện tại.

## Cấu hình Gemini API key

Dự án dùng Secrets Gradle Plugin, đọc file `.env` ở root và sinh trường
`BuildConfig.GEMINI_API_KEY`. Không commit API key thật vào Git.

1. Sao chép file mẫu:

   ```bash
   cp .env.example .env
   ```

2. Điền key của riêng bạn trong `.env` (giá trị dưới đây chỉ là ví dụ):

   ```properties
   GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
   ```

3. Build ứng dụng:

   ```bash
   gradle assembleDebug
   ```

Ứng dụng cũng cho phép truyền `customApiKey` trong runtime; giá trị không rỗng này được ưu
tiên hơn `BuildConfig.GEMINI_API_KEY`. Placeholder `MY_GEMINI_API_KEY` được xem như chưa cấu
hình. Lưu ý: key được đóng gói trong ứng dụng client không thể được coi là bí mật tuyệt đối;
với bản phát hành thực tế nên giới hạn key/quota và cân nhắc proxy backend có kiểm soát.

## Quyền camera

Manifest khai báo:

- `android.permission.CAMERA` để preview, phân tích và chụp ảnh.
- `android.permission.INTERNET` để gọi Gemini API.
- Camera và autofocus là feature không bắt buộc ở mức manifest (`required="false"`).

Ứng dụng yêu cầu quyền camera ở runtime và chỉ tạo `CameraPreviewView` khi quyền đã được cấp.
Nếu từ chối, người dùng vẫn ở màn hình giải thích/yêu cầu quyền; hãy cấp quyền trong hộp thoại
hệ thống hoặc trong **Settings > Apps > GNZ MON > Permissions > Camera**. Photo Picker của hệ
thống được dùng để chọn ảnh nên ứng dụng không yêu cầu quyền đọc toàn bộ thư viện ảnh.

## Giới hạn và quyền riêng tư

- **On-device:** frame liên tục dùng cho ML Kit detection/tracking được xử lý trên thiết bị;
  pipeline này không tự gửi video camera lên Gemini.
- **Gửi ra ngoài thiết bị:** khi người dùng chủ động phân tích ảnh và Gemini được cấu hình,
  ảnh đã crop/resize/nén cùng prompt được gửi qua HTTPS tới Google Gemini API. Không chụp hoặc
  gửi dữ liệu nhạy cảm, người, giấy tờ hay vị trí riêng tư mà không có sự đồng ý phù hợp.
- **Lưu cục bộ:** kết quả nhận diện được lưu vào cơ sở dữ liệu Room của nhật ký. Việc xóa mục
  hoặc xóa nhật ký được thực hiện trong ứng dụng; cấu hình Android hiện cũng cho phép backup,
  vì vậy dữ liệu có thể tham gia cơ chế sao lưu của hệ điều hành theo các rule trong dự án.
- **Không phải nhận diện chắc chắn:** ML Kit chỉ tạo vùng/nhãn tổng quát; Gemini mới suy luận
  loài từ ảnh tĩnh và có thể hallucinate. Kết quả fallback offline cũng chỉ là dữ liệu mẫu,
  không chứng minh loài trong ảnh đã được nhận diện.
- **Điều kiện vận hành:** chất lượng phụ thuộc ánh sáng, độ nét, góc chụp, che khuất, kích thước
  đối tượng, kết nối mạng, quota API và độ ổn định ID của ML Kit. Track fallback chỉ dùng hình
  học ngắn hạn và hết hạn sau 1,5 giây, không có Re-ID qua lần xuất hiện dài.
