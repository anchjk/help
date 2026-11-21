# Kiến trúc tổng quát

## Nguyên tắc

- Hai aggregate độc lập: Anomaly và Drawing.
- Anomaly có trạng thái, StatusHistory, RepairSession và Alert.
- Drawing có DrawingWorkSession và DrawingRevision; không có Alert/status anomaly.
- WorkEntry là bản ghi thao tác riêng; session chỉ gom theo `shift_occurrence_id` cụ thể.
- Shift occurrence lưu thời điểm bắt đầu/kết thúc đầy đủ, cho phép qua đêm; roster snapshot được chép vào session/entry.
- File tách khỏi SQLite; bảng `attachments` giữ path, mime, hash, owner và caption.
- Không có chức năng AI và không có quyền mạng (từ 1.2.0). Lịch ca đọc từ Excel/CSV (`core/XlsxReader`, `CsvReader`, `ScheduleSheetParser`); trợ lý phân tích offline gồm `core/AssistantIntent` (hiểu câu hỏi theo từ khóa) và `data/AnalysisEngine` (SQL tổng hợp, chỉ đọc).

## Luồng công việc bất thường

1. Người dùng nhập work_at và nội dung.
2. Repository tìm occurrence duy nhất chứa thời điểm `start_at <= work_at < end_at`.
3. Nếu tìm thấy đúng một occurrence, tìm RepairSession theo `(anomaly_id, shift_occurrence_id)`; nếu chưa có, tạo session số kế tiếp.
4. Nếu không có occurrence hoặc nhiều occurrence chồng nhau, work entry được lưu ở trạng thái chưa xác định ca (UI hiện chưa có hộp thoại chọn occurrence thủ công).
5. Tạo WorkEntry mới trong mọi trường hợp; không ghi đè các entry trước.
6. Chỉ cập nhật trạng thái anomaly theo kết quả được người dùng chọn; status history ghi lại thay đổi.

## Luồng bản vẽ

1. `drawing_number` do người dùng nhập, giữ nguyên văn bản và là trường nghiệp vụ chính.
2. WorkEntry bản vẽ được lưu thành DrawingWorkEntry; cùng shift occurrence gom vào DrawingWorkSession.
3. Ghi chú/kiểm tra công việc không tăng revision.
4. Chỉ dialog “Ghi nhận lần chỉnh sửa / Revision” tạo DrawingRevision append-only và cập nhật `current_revision` snapshot.
5. Drawing không được đưa vào AlertRuleEngine.

## Cảnh báo

- Chạy đồng bộ cục bộ lúc khởi động và khi mở menu Cảnh báo.
- Dedup key `anomalyId|alertType`; điều kiện hết hiệu lực chuyển record active sang expired và giữ lịch sử.
- Không đăng ký receiver, alarm, notification channel, foreground service hay quyền thông báo Android.

## Backup

- Checkpoint SQLite, tạo snapshot DB và đính kèm thư mục attachments.
- Có manifest chứa schema version, thời điểm, tùy chọn chat và SHA-256 database.
- Đóng gói ZIP rồi mã hóa AES-256-GCM với khóa dẫn xuất PBKDF2-HMAC-SHA256 (210.000 vòng), salt 16 byte và IV 12 byte.
- Import xác thực GCM, giải nén chống path traversal, kiểm tra checksum; restore thay thế giữ bản sao DB rollback.
- Merge theo UUID chưa được triển khai.
