# 🎭 Emoji Mask AR for Android (100% Offline / On-Device)

Ứng dụng **Emoji Mask** dành cho hệ điều hành Android, được phát triển theo tiêu chuẩn ứng dụng mẫu iOS "Emoji Mask" (App Store ID: 6482981337). Ứng dụng mang lại trải nghiệm đeo mặt nạ Emoji thực tế ảo (AR) sống động, theo dõi biểu cảm khuôn mặt thời gian thực (Real-Time Facial Expression Tracking), chụp ảnh selfie và chia sẻ với bạn bè mà **hoàn toàn không cần Server** (100% Offline / On-Device).

---

## 🌟 Các Tính Năng Nổi Bật (Key Features)

### 1. Đeo Mặt Nạ Emoji Thực Tế Ảo (AR Cutout & 3D Mask Mapping)
- **Chuẩn App Mẫu iOS EmojiMask**:
  - **Chế độ Khoét Lỗ AR (AR Cutout Mode)**: Mặt nạ ôm trọn khuôn mặt 3D với sống mũi nổi khối và các **lỗ khoét tự nhiên cho Mắt và Miệng**. Nhờ đó, người dùng khi **lè lưỡi, há miệng cười to, nhăn mặt hay nháy mắt** đều để lộ đôi mắt thật, hàm răng và chiếc lưỡi thật qua mặt nạ y như app mẫu trên App Store!
  - **Nút chuyển chế độ tức thì**: Dễ dàng đổi qua lại giữa **🎭 Mặt Nạ AR Khoét Lỗ** và **🎨 Hoạt Hình 2D**.
- **Hỗ Trợ Nhiều Người Cùng Lúc (Multi-Face Tracking)**: Đeo mặt nạ đồng thời cho 2 hoặc nhiều người trong cùng một khung hình với bộ lọc chuyển động EMA riêng biệt cho từng người.
- Bộ sưu tập 18 mặt nạ Emoji đa dạng:
  - 😀 **Mỉm Cười (Grinning)**
  - 😂 **Cười Ra Nước Mắt (Laughing Tears)**: Tia nước mắt 3D tuôn trào sống động
  - 🐱 **Mặt Mèo (Cat Emoji)**: Có tai mèo 3D vàng hồng và ria mép hai bên má chuẩn app mẫu
  - 😍 **Mắt Trái Tim (Heart Eyes)**: Viền trái tim phát sáng co bóp nhịp nhàng quanh mắt
  - 😘 **Chu Môi Hôn (Blowing Kiss)**: Tự động bắn tim đỏ khi chu môi
  - 😉 **Nháy Mắt (Wink)**: Chớp mắt linh hoạt theo mắt thật
  - 😱 **Kinh Ngạc (Scream)**: Miệng há to, tay ôm má
  - 😎 **Kính Ngầu (Cool Guy)**: Kính râm đen bóng bẩy phản chiếu ánh sáng
  - 😭 **Khóc Ròng (Crying)**: 2 dòng nước mắt động tuôn trào
  - 😡 **Tức Giận (Angry)**: Khuôn mặt đổi sang sắc đỏ rực, lông mày xếch
  - 🤪 **Hài Hước (Zany)**: Mắt to mắt nhỏ, lưỡi thè tinh nghịch
  - 🤩 **Ngôi Sao (Star-Struck)**: Mắt sao vàng kim lấp lánh
  - 🥳 **Tiệc Tùng (Party)**: Nón sinh nhật rực rỡ
  - 🤖 **Người Máy (Robot)**: Kim loại be-ven, mắt pixel, ăng-ten phát sáng
  - 👽 **Người Ngoài Hành Tinh (Alien)**: Mắt đen to huyền bí
  - 🤡 **Chú Hề (Clown)**: Trang điểm mắt xanh, mũi đỏ quả anh đào
  - 😈 **Ác Quỷ (Devil)**: Sừng nhọn, màu tím ma mị
  - 😷 **Khẩu Trang (Face Mask)**: Đeo khẩu trang y tế chuẩn

### 2. Theo Dõi Biểu Cảm Khuôn Mặt Thời Gian Thực (Real-Time Expression Tracking)
Hệ thống sử dụng cơ chế xử lý hình học đa kênh On-Device (Google ML Kit Face Contours + Classifications) với thuật toán lọc EMA bất đối xứng để phát hiện tức thì tất cả các sắc thái cảm xúc:
- **😄 Mỉm cười (Smiling)**: Miệng cong lên mỉm cười tự nhiên, hai má tự động ửng hồng (`blushPaint`).
- **😂 Cười to / Há miệng (Laughing / Mouth Open)**: Nhận diện khoảng hở giữa môi trên và môi dưới theo thời gian thực; vẽ khoang miệng mở rộng, hàm răng trên trắng muốt và lưỡi hồng chuyển động linh hoạt.
- **😉 Nháy mắt & Chớp mắt (Winking & Blinking)**: 
  - Tính toán tỷ lệ co giãn mí mắt theo hình học (Eye Aspect Ratio - EAR) từ 16 điểm viền mắt (`LEFT_EYE`, `RIGHT_EYE`) kết hợp xác suất mở mắt.
  - Bộ lọc EMA bất đối xứng (`alphaEyeClose = 0.80`) giúp nhận diện cú nháy mắt siêu nhanh (dưới 150ms).
  - Mắt nháy chuyển mượt mà thành cung nháy mắt cong nghệ thuật emoji 😉 với nét vẽ tròn trịa; nháy mắt trái, nháy mắt phải hoặc nhắm cả hai mắt (😌) độc lập.
- **😘 Chu môi hôn (Kissing / Mouth Pucker)**:
  - Phân tích độ thu hẹp bề ngang của miệng so với khoảng cách giữa hai mắt kết hợp tỷ lệ khung hình môi.
  - Khuôn miệng Emoji lập tức chụm lại hình nụ hôn đáng yêu, viền môi hồng thắm và tự động bắn ra các trái tim đỏ bay lơ lửng (Floating Hearts 💕).
- **😠 Cau mày / Tức giận (Frowning / Brow Furrow)**:
  - Phân tích chuyển động của hai đầu lông mày (`LEFT_EYEBROW_TOP`, `RIGHT_EYEBROW_TOP`): khoảng cách hai đầu chân mày co lại và trễ xuống sống mũi tạo góc xếch chữ V sắc sảo.
  - Lông mày Emoji dốc thẳng vào trong tạo nét mặt tức giận, xuất hiện nếp nhăn cau mày ở giữa trán và trán ửng đỏ rực.
  - Khóe môi trễ xuống thành hình vòng cung mếu/cau có (☹️).
- **Thanh trạng thái biểu cảm trực quan**: Viên nang (pill) hiển thị cảm xúc hiện tại theo thời gian thực (*😄 Mỉm Cười*, *😂 Cười To*, *😉 Nháy Mắt Trái*, *😉 Nháy Mắt Phải*, *😘 Chu Môi Hôn*, *😠 Cau Mày*, *😌 Nhắm Mắt*, *😮 Há Miệng*, *🙂 Bình Thường*...).

### 3. Chụp Ảnh & Chia Sẻ (Photo Capture & Sharing)
- Nút chụp ảnh thiết kế chuẩn camera cao cấp với hiệu ứng chớp sáng màn hình và phản hồi rung xúc giác (Haptic Feedback).
- Tự động ghép (composite) mặt nạ Emoji sắc nét ở độ phân giải gốc.
- **Lưu Vào Máy (Save to Gallery)**: Lưu ảnh trực tiếp vào Thư viện ảnh thiết bị (`Pictures/EmojiMask`) qua Android MediaStore API (chuẩn Android 10, 11, 12, 13, 14, 15).
- **Chia Sẻ (Share)**: Mở trình chia sẻ của Android để gửi trực tiếp qua Zalo, Messenger, Facebook, Instagram, Telegram, v.v.

### 4. Đổi Ngôn Ngữ Tức Thì (Tiếng Việt 🇻🇳 & Tiếng Anh 🇬🇧)
- Nút chuyển đổi ngôn ngữ nhanh trên góc trên màn hình: **🇻🇳 VI** / **🇬🇧 EN**.
- Chuyển đổi giao diện ngay lập tức mà không cần khởi động lại máy hay đổi ngôn ngữ hệ thống.
- Lưu cài đặt tự động vào bộ nhớ máy (`SharedPreferences`).

### 5. 100% Offline / On-Device (Không Cần Server)
- Tích hợp công nghệ nhận diện AI On-Device (Google ML Kit Face Engine).
- Toàn bộ luồng ảnh chỉ xử lý trên RAM/GPU của điện thoại, bảo mật tuyệt đối 100%, không thu thập hay gửi bất kỳ dữ liệu nào ra bên ngoài.

---

## 🛠 Hướng Dẫn Chạy Bằng Android Studio Trên Máy Thật

### Bước 1: Mở Project trong Android Studio
1. Mở **Android Studio**.
2. Chọn **Open** (hoặc `File` -> `Open`).
3. Chọn thư mục dự án: `c:\Users\Admin\Downloads\EmojiMask-ADR`.
4. Chờ Android Studio đồng bộ Gradle (Gradle Sync) tự động.

### Bước 2: Bật Chế Độ Gỡ Lỗi USB (USB Debugging) trên Máy Thật
1. Trên điện thoại Android của bạn, vào **Cài đặt (Settings)** -> **Giới thiệu về điện thoại (About Phone)**.
2. Nhấn 7 lần liên tục vào **Số hiệu bản dựng (Build Number)** để kích hoạt *Tùy chọn nhà phát triển (Developer Options)*.
3. Quay lại **Cài đặt** -> **Tùy chọn nhà phát triển (Developer Options)** -> Bật **Gỡ lỗi USB (USB Debugging)**.
4. Cắm cáp USB nối điện thoại với máy tính.
5. Trên màn hình điện thoại, chọn **Cho phép gỡ lỗi USB từ máy tính này (Allow USB Debugging)**.

### Bước 3: Chạy Ứng Dụng (Run App)
1. Trong thanh công cụ trên cùng của Android Studio, ở danh sách thiết bị (Device Dropdown), chọn chiếc điện thoại thật của bạn vừa cắm vào.
2. Nhấn nút **Run** (Nút tam giác màu xanh lá ▶ hoặc phím tắt `Shift + F10`).
3. Ứng dụng sẽ tự động được build và cài đặt lên điện thoại của bạn.
4. Khi mở ứng dụng lần đầu, nhấn **Cấp Quyền Camera** để bắt đầu trải nghiệm!

---

## 📂 Cấu Trúc Dự Án (Project Architecture)

```
EmojiMask-ADR/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Khai báo quyền Camera, FileProvider, Theme
│   │   ├── java/com/randomvisual/emojimask/
│   │   │   ├── EmojiMaskApplication.kt  # Khởi tạo Locale ngôn ngữ
│   │   │   ├── MainActivity.kt          # Điều khiển CameraX, Flash, Flip, Shutter
│   │   │   ├── ml/
│   │   │   │   └── FaceExpressionTracker.kt # Xử lý phân tích biểu cảm, tính toán góc 3D, lọc nhiễu EMA
│   │   │   ├── model/
│   │   │   │   ├── EmojiMaskType.kt     # Danh sách 17 loại mặt nạ Emoji
│   │   │   │   └── FaceExpressionState.kt # Mô hình dữ liệu biểu cảm khuôn mặt
│   │   │   ├── ui/
│   │   │   │   ├── adapter/
│   │   │   │   │   └── EmojiMaskAdapter.kt # Carousel chọn mặt nạ mượt mà
│   │   │   │   └── dialog/
│   │   │   │       └── PhotoPreviewDialog.kt # Hộp thoại xem trước ảnh, Lưu & Chia sẻ
│   │   │   ├── util/
│   │   │   │   └── LanguageManager.kt   # Quản lý chuyển đổi ngôn ngữ Vi/En
│   │   │   └── view/
│   │   │       └── EmojiMaskOverlayView.kt # Vẽ mặt nạ Emoji AR 3D thời gian thực trên Canvas
│   │   └── res/
│   │       ├── drawable/                # Các icon vector sắc nét, nút chụp, viền kính mờ
│   │       ├── layout/                  # Giao diện chính, item carousel, dialog xem trước
│   │       ├── values/                  # Strings tiếng Anh, màu sắc AR, dark theme
│   │       └── values-vi/               # Strings tiếng Việt chuẩn chỉnh
│   └── build.gradle.kts                 # Cấu hình CameraX & Google ML Kit
├── build.gradle.kts
├── settings.gradle.kts
└── local.properties
```
