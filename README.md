# Lọc Ánh Sáng Đêm (Night Light Filter)

Ứng dụng Android phủ một lớp màu hổ phách lên màn hình để giảm ánh sáng xanh, kèm thanh chỉnh độ tối.

## Tính năng

- Bật/tắt bộ lọc bằng một công tắc, chỉnh **độ ấm màu** và **độ tối** theo thời gian thực
- 3 cài đặt nhanh: Nhẹ, Ban đêm, Rất ấm
- **Hẹn giờ tự động** (hỗ trợ khoảng qua nửa đêm, ví dụ 21:00 → 06:00)
- **Ô Cài đặt nhanh**: bật/tắt ngay từ bảng kéo xuống, không cần mở app (Android 13+ có nút thêm ô trong app; bản cũ hơn thì kéo xuống, bấm biểu tượng bút chì và kéo ô "Lọc ánh sáng" lên trên)
- Thông báo cố định có nút **Tắt** nhanh
- Tự bật lại sau khi khởi động máy hoặc cập nhật ứng dụng
- Cảm ứng đi xuyên qua lớp lọc, không ảnh hưởng thao tác trên các ứng dụng khác
- Không thu thập dữ liệu, không cần internet, không có thư viện bên thứ ba

Yêu cầu: Android 8.0 (API 26) trở lên.

## Cách lấy file APK

### Cách 1: GitHub Actions (không cần cài gì)

1. Tạo repository mới trên GitHub, đẩy toàn bộ thư mục này lên nhánh `main`.
2. Vào tab **Actions** → workflow **Build APK** sẽ tự chạy (hoặc bấm **Run workflow**).
3. Khi chạy xong (khoảng 3–5 phút), mở lần chạy đó và tải **NightLightFilter-debug-apk** ở phần *Artifacts*.
4. Giải nén, chép `app-debug.apk` vào điện thoại và cài đặt (cho phép "Cài đặt ứng dụng không rõ nguồn" nếu được hỏi).

### Cách 2: Android Studio

1. Cài [Android Studio](https://developer.android.com/studio), chọn **Open** và trỏ vào thư mục này.
2. Đợi Gradle đồng bộ xong, chọn **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

### Cách 3: Dòng lệnh

Cần JDK 17+ và Android SDK (platform 34), đặt biến `ANDROID_HOME`:

```bash
./gradlew assembleDebug
```

## Sử dụng

1. Mở ứng dụng, bật công tắc **Bật bộ lọc**.
2. Lần đầu, ứng dụng dẫn bạn tới màn hình cấp quyền **Hiển thị trên các ứng dụng khác**. Bật quyền rồi quay lại, bộ lọc sẽ tự bật.
3. Kéo hai thanh trượt để chỉnh theo ý thích. Thay đổi áp dụng ngay.

Nếu bộ lọc hay bị tắt trên Xiaomi, Oppo, Vivo, Samsung…, hãy cho phép ứng dụng tự khởi động / chạy nền và tắt tối ưu pin cho ứng dụng.

## Cấu trúc mã nguồn

| File | Vai trò |
|---|---|
| `MainActivity.kt` | Màn hình cài đặt |
| `OverlayService.kt` | Dịch vụ nền giữ lớp phủ, kiểm tra hẹn giờ, thông báo |
| `FilterView.kt` | View vẽ lớp màu hổ phách và lớp tối |
| `FilterPrefs.kt` | Lưu cài đặt |
| `NightLightTileService.kt` | Ô bật/tắt trong bảng Cài đặt nhanh |
| `BootReceiver.kt` | Bật lại bộ lọc sau khi khởi động máy |

Bản APK `debug` được ký bằng khóa debug nên cài trực tiếp được. Muốn phát hành lên CH Play, hãy tạo keystore và build bản `release`.
