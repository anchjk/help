# Trạng thái triển khai so với đặc tả

| Nhóm | Trạng thái | Ghi chú |
|---|---|---|
| Native app shell / điều hướng | Có source | UI native platform, không framework UI ngoài |
| SQLite schema hai phân hệ | Có source | 16 bảng nghiệp vụ và cài đặt; schema v4 |
| Thống kê bất thường theo khoảng ngày + biểu đồ | Có source + test JVM | `StatsCalculator` (core) được kiểm thử; giao diện/biểu đồ Canvas chưa test trên thiết bị |
| Ghi công việc 3 trường + gợi ý gán hồ sơ | Có source + test JVM | Gợi ý theo tên thiết bị/KKS (bất thường) và số/tiêu đề (bản vẽ); chưa test trên thiết bị |
| Tệp đính kèm (ảnh/PDF/Word/Excel) | Có source | Đính kèm khi tạo hồ sơ và ghi công việc; mở bằng app ngoài qua AttachmentProvider; chưa test trên thiết bị |
| Tạo/xem bất thường, status history | Một phần | Chưa có sửa hồ sơ, bulk delete, full filter/search fuzzy |
| Tạo/xem bản vẽ, revision | Một phần | Có create, work, append-only revision; thiếu liên kết tệp trước/sau, sửa hồ sơ |
| Work session theo occurrence ID | Có logic | Chưa có lựa chọn occurrence thủ công khi chồng lấn |
| Shift roster | Có source | Nhập theo tháng từ Excel/CSV hoặc thủ công; thiếu editor tháng đầy đủ |
| Alert log nội bộ | Có logic | Thiếu bật/tắt từng loại rule và filters UI |
| Reports | Một phần | Có CSV/XLSX/PDF basic; thiếu report profile/attachments/advanced filters |
| Trash 30 ngày | Một phần | Soft delete + restore + expiry cleanup; thiếu audit UX / test trên Android |
| Backup mã hóa | Một phần | Single backup, preview, replace + DB/file rollback; thiếu merge và cross-device test |
| Nhập lịch ca Excel/CSV | Có source + test JVM | Đọc .xlsx/.csv offline, đối chiếu thứ với lịch thật, xem lại/sửa trước khi lưu, cài giờ ca |
| Trợ lý phân tích offline | Có source + test JVM (ý định/khoảng thời gian), SQL kiểm bằng sqlite3 | 12 mẫu phân tích + hỏi nhanh theo từ khóa; không AI/mạng |
| AI (Gemini/OpenAI/chat) | Đã gỡ ở 1.2.0 | Không còn quyền INTERNET; DB v3 xóa hội thoại/cấu hình AI cũ |
| Database/file encryption | Chưa đạt | App sandbox + encrypted backup; thiếu app-level DB/file encryption (SQLCipher or equivalent) |
| Dark mode / typography config | Một phần | Có chế độ sáng/tối theo hệ thống; chưa có cấu hình cỡ chữ |
| Automated Android tests | Chưa chạy | SDK/toolchain không có trong workspace |
| APK release / device test | Chưa thực hiện | Thiếu Gradle/Android SDK/build tools/keystore |

## Không tuyên bố đã đạt

Source này là bản nền chức năng có thể tiếp tục phát triển, không phải production release hoàn chỉnh theo toàn bộ đặc tả. Không được dùng với hồ sơ kỹ thuật nhạy cảm cho đến khi xử lý các mục bảo mật và nghiệp vụ còn thiếu, rồi chạy đầy đủ build/test trên Android SDK và thiết bị mục tiêu.
