# Báo cáo kiểm thử

## Bản 1.4.0 (21/11/2026)

- `./test-core.sh`: PASS toàn bộ, gồm `StatsSmokeTest` (28 assertion: phân loại tự động không nhầm "vận hành"/"bản vẽ"/"vấn đề", chia cột ngày/tuần/tháng/quý, đang tồn tại, phát sinh mới, đã xử lý, kỳ liền trước, thời gian xử lý trung bình, gộp thiết bị/hệ thống không phân biệt hoa thường và dấu, dữ liệu rỗng).
- Biên dịch toàn bộ mã nguồn bằng `javac` + `android.jar` API 34 với `R` giả: không lỗi (chỉ kiểm tra cú pháp/kiểu, không thay Gradle build/lint).
- Câu SQL thống kê chạy được trên schema v4 (kiểm bằng sqlite3).
- Icon: XML vector hợp lệ; đã dựng bản xem trước `icon-preview.png` từ cùng dữ liệu hình (kích cỡ lớn/nhỏ, mặt nạ tròn/bo góc, đơn sắc).
- **Chưa kiểm tra trên thiết bị:** bố cục thực tế trên màn hình nhỏ (tab Thống kê, biểu đồ Canvas, nút nổi, bảng trượt), chạm chọn cột biểu đồ, hộp chọn ngày, màn hình khởi động Android 12+, hiển thị icon trên launcher.

## Bản 1.3.0 (21/11/2026)

- `./test-core.sh`: PASS toàn bộ, gồm `MatchSuggesterSmokeTest` (14 assertion: gợi ý bất thường theo KKS/tên thiết bị không dấu/lỗi gõ/số hiệu, gợi ý bản vẽ theo số hoặc tiêu đề, không gợi ý khi không liên quan).
- Biên dịch toàn bộ mã nguồn bằng `javac` với `android.jar` API 34 và `R` giả sinh từ thư mục res: **không lỗi**. Đây chỉ là kiểm tra cú pháp/kiểu, **không thay cho** Gradle build (`assembleDebug`), lint, hay chạy trên thiết bị.
- Schema v4: cài mới và nâng cấp từ v3 cho cùng tập bảng/cột/chỉ mục (kiểm bằng sqlite3 trong Python); các câu SQL mới (lịch sử phiếu, danh sách tệp) chạy được trên schema này.
- Chưa kiểm tra trên thiết bị: giao diện hàng Truy cập nhanh, hộp chọn tệp, mở tệp bằng ứng dụng ngoài, restore backup cũ (v3) vào app v4.

Ngày thực hiện: 19/11/2026 (múi giờ Asia/Ho_Chi_Minh)

## Đã chạy

Lệnh: `./test-core.sh`

Kết quả:

```text
PASS: 10 core assertions
```

Các assertion đã chạy:

1. Tìm kiếm tiếng Việt không dấu (`Bơm cấp nước` ↔ `bom cap nuoc`).
2. Chuẩn hóa ký tự `đ` (`Động cơ` ↔ `dong co`).
3. Ca qua đêm bao phủ đúng thời điểm sau nửa đêm.
4. Ca cùng mã nhưng khác occurrence ID không bị gộp chung.
5. Công việc cùng occurrence ID được nhận diện cùng phiên.
6. Số phiên kế tiếp tăng đúng.
7. Rule engine sinh cảnh báo với dữ liệu thử.
8. Nhận diện khoảng ca chồng lấn và yêu cầu chọn thủ công.
9. Không tự gộp các phiên khi chưa có occurrence ID.
10. Cảnh báo hồ sơ đã được mở lại.

## Chưa thể chạy

- Android Gradle compile / lint / assembleDebug / assembleRelease.
- Android instrumentation/database migration tests.
- Test cài đặt/chạy trên emulator hoặc thiết bị thật.
- Test backup-export/import trên hai thiết bị, kiểm tra rollback và tệp đính kèm.
- Test provider API với API key/quota/mạng thực.
- Test xác minh chữ ký APK, ABI, kích thước APK.

Nguyên nhân: workspace hiện tại có JDK 21 nhưng không có Gradle, Android SDK, `android.jar`, ADB, AAPT/AAPT2, D8, apksigner hoặc zipalign. Do đó không tuyên bố APK đã build, cài hoặc chạy thành công.

## Kiểm tra cấu trúc SQLite (ngoài Android runtime)

- Trích xuất và thực thi 36 câu lệnh DDL từ `LocalDatabase.java` bằng SQLite 3 của Python: PASS.
- Xác minh unique constraint ngăn tạo 2 RepairSession cùng `(anomaly_id, shift_occurrence_id)`: PASS.
- Đây là kiểm tra SQL schema độc lập, không thay thế kiểm thử SQLite Android.

## Thử build release trong workspace

- Đã chạy `./build-release.sh`: script dừng với exit code 2 và thông báo `Missing Gradle 9.6+`. Không có APK output.

## Cập nhật 1.1.0 (20/11/2026)

- `./test-core.sh`: 10 assertion nghiệp vụ lõi + bộ `ScheduleSmokeTest` (nhập lịch ca từ JSON mẫu tháng 11/2026: 90 lần ca liền mạch, ca đêm qua ngày, giao ca 07:00/15:00/23:00, phát hiện sai tháng, trùng kíp, thiếu mã kíp).
- Toàn bộ mã nguồn `app/src/main` đã được biên dịch bằng `javac` với `android.jar` API 36 (không lỗi). Tài nguyên XML đã kiểm tra hợp lệ cú pháp và tham chiếu chéo.
- Migration SQLite v1→v2 và các câu truy vấn lịch ca đã kiểm tra bằng sqlite3.
- Chưa chạy trên thiết bị/emulator: cần build bằng GitHub Actions hoặc Android Studio và thử các màn hình (đặc biệt nhập file Excel thật từ bộ nhớ điện thoại và trợ lý phân tích).

## Cập nhật 1.2.0 (20/11/2026)

- `./test-core.sh`: CoreSmokeTest (10), ScheduleSmokeTest (đọc `docs/mau/Lich_Truc_Ca_Thang_11_2026.xlsx` bằng `XlsxReader`: 30 ngày × 3 ca, nhân sự 5 kíp, giờ ca theo cài đặt, CSV, bố cục dọc, phát hiện lỗi), AssistantSmokeTest (ý định câu hỏi, khoảng thời gian).
- 13 câu SQL của `AnalysisEngine` đã chạy trên schema thật bằng sqlite3.
- Toàn bộ `app/src/main` biên dịch sạch bằng `javac` + `android.jar` API 36; tài nguyên XML hợp lệ.
