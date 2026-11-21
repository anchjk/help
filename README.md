# EVNQT1 HELP DESK

Ứng dụng Android native Java quản lý cục bộ hai phân hệ: **Bất thường thiết bị** và **Hồ sơ bản vẽ / revision**.

> Trạng thái bàn giao hiện tại: đã tạo project source và chạy được bộ kiểm thử Java thuần cho các quy tắc nghiệp vụ lõi. **Chưa tạo APK** vì workspace hiện tại không có Android SDK, Gradle, Android Build Tools hoặc công cụ ký APK. Không dùng file APK giả hoặc APK từ project khác.

Đặc tả gốc người dùng cung cấp được giữ tại `docs/PRODUCT_SPECIFICATION.md`; trạng thái chi tiết theo từng nhóm nằm trong `docs/IMPLEMENTATION_STATUS.md`.

## 1. Quyết định kỹ thuật

- **Android native Java**, giao diện dựng bằng Android SDK platform views; không dùng Flutter/React Native/Compose/WebView.
- **SQLiteOpenHelper** cho dữ liệu quan hệ local; schema có version (`DB_VERSION=4`), foreign key, index, transaction ở các thao tác ghi chính.
- **AES-256-GCM + PBKDF2-HMAC-SHA256** để mã hóa file backup bằng mật khẩu người dùng.
- **Hoàn toàn offline**: ứng dụng không khai báo quyền INTERNET, không có chức năng AI; lịch ca đọc từ Excel/CSV bằng bộ đọc zip+XML tự viết, trợ lý phân tích chạy bằng SQL/luật cục bộ.
- Không có Firebase, backend, analytics, ads, push/local notification, notification channel, alarm hay foreground service.

Lý do: giảm runtime và dependency, không đóng gói browser/native cross-platform engine; các tác vụ quản lý vẫn hoạt động offline. Tài liệu Android hiện hành tại thời điểm tạo source công bố API 36 là target cần thiết cho app mới trên Google Play từ 31/08/2026; Android Gradle Plugin 9.4.1 là nhánh hiện hành và AGP 9.4 yêu cầu Gradle 9.6.0. Xem tài liệu chính thức: [target API](https://support.google.com/googleplay/android-developer/answer/11926878), [AGP API](https://developer.android.com/reference/tools/gradle-api), [AGP 9.4 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes), [AGP/Gradle compatibility](https://developer.android.com/build/releases/about-agp).

## 2. Toolchain và cấu hình

| Thành phần | Cấu hình project |
|---|---|
| Ngôn ngữ | Java 17 source/target |
| JDK build | JDK 17 trở lên; workspace được kiểm tra có JDK 21 |
| Android Gradle Plugin | 9.4.1 |
| Gradle | 9.6.0 trở lên theo AGP 9.4 |
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 29 (Android 10) |
| Database | SQLite platform |
| ABI | Không có native library do project tự đóng gói; APK chỉ chứa bytecode Java/resources |
| Dependency ngoài | Không khai báo thư viện runtime ngoài Android Gradle Plugin |

Workspace lúc tạo project: có OpenJDK 21.0.12; không tìm thấy `gradle`, `adb`, `aapt`, `aapt2`, `d8`, `apksigner`, `zipalign`, `android.jar` hoặc Android SDK trong các vị trí đã kiểm tra. Vì vậy không thể xác minh `compileSdk` thực tế bằng compile, không thể cài thử và không thể ký APK tại workspace này.

## 3. Build trên máy có Android SDK

1. Cài Android Studio bản stable hiện hành hoặc cài JDK 17+, Gradle 9.6+, Android SDK Platform 36 và Build Tools tương thích.
2. Mở thư mục `evnqt1-help-desk` bằng Android Studio, chờ Gradle Sync.
3. Chạy `gradle assembleDebug` để tạo APK debug.
4. Để tạo APK release có chữ ký riêng, tạo keystore của đơn vị/người dùng, sau đó bổ sung `signingConfig` trong `app/build.gradle`; không đưa keystore hoặc mật khẩu vào source control.
5. Chạy `gradle assembleRelease`. Kiểm tra APK bằng `apksigner verify --verbose --print-certs <apk>` và cài bằng `adb install -r <apk>`.

Không phát hành production bằng debug keystore. Cần tự đặt application signing key và bảo quản bản sao ngoại tuyến.

## 4. Chạy kiểm thử phần nghiệp vụ thuần Java

Không cần Android SDK cho bộ test này:

```bash
./test-core.sh
```

Hoặc chạy thủ công:

```bash
mkdir -p build/core-test
javac -d build/core-test \
  app/src/main/java/vn/evnqt1/helpdesk/core/ShiftOccurrence.java \
  app/src/main/java/vn/evnqt1/helpdesk/core/ShiftResolver.java \
  app/src/main/java/vn/evnqt1/helpdesk/core/SessionRules.java \
  app/src/main/java/vn/evnqt1/helpdesk/core/TextNormalizer.java \
  app/src/main/java/vn/evnqt1/helpdesk/core/AlertRuleEngine.java \
  app/src/test/java/vn/evnqt1/helpdesk/core/CoreSmokeTest.java
java -cp build/core-test vn.evnqt1.helpdesk.core.CoreSmokeTest
```

## 5. Chức năng đã có trong source

- Tổng quan số lượng bất thường và hồ sơ bản vẽ.
- Tạo và xem hồ sơ bất thường; thiết bị bằng tên mô tả, KKS tùy chọn; cập nhật trạng thái và lưu status history.
- **Thống kê** (tab riêng): đang tồn tại, phát sinh mới và đã xử lý theo khoảng ngày tùy chọn, xu hướng, loại/thiết bị/hệ thống hay xảy ra nhất; Cảnh báo ở biểu tượng chuông trên thanh trên.
- **Ghi công việc 3 trường** (tên thiết bị, KKS không bắt buộc, nội dung) + đính kèm ảnh/PDF/Word/Excel; sau khi xác nhận app gợi ý bất thường liên quan nhất (theo tên thiết bị + KKS) hoặc cho chọn hồ sơ khác; bản vẽ gợi ý theo số bản vẽ hoặc tên/tiêu đề. Tạo bất thường/bản vẽ cũng đính kèm được tệp.
- Ghi nhận nhiều WorkEntry; cùng `shiftOccurrenceId` được gộp vào một RepairSession / DrawingWorkSession; ca kế tiếp tạo phiên mới.
- Tạo hồ sơ bản vẽ theo số bản vẽ người dùng nhập; revision lưu thành bản ghi append-only và không tự tăng khi chỉ ghi chú công việc.
- Lịch ca A/B/C/D/E: **nhập từ file Excel (.xlsx) hoặc CSV** theo mẫu `docs/mau/Lich_Truc_Ca_Thang_11_2026.xlsx` (bảng Ngày/Thứ/Sáng/Chiều/Đêm + danh sách nhân sự các kíp; tự đối chiếu với lịch thật, cho sửa trước khi lưu), **cài giờ ca sáng/chiều/đêm** hoặc thêm thủ công; hỗ trợ ca qua đêm bằng start/end datetime và snapshot roster khi ghi công việc.
- Rule engine cảnh báo local: tồn tại lâu, không cập nhật, mức độ cao/nghiêm trọng, lặp lại và mở lại; log chỉ dành cho anomaly; không có Android notification.
- Tìm kiếm local có chuẩn hóa dấu tiếng Việt cơ bản.
- Báo cáo scope chung / anomaly / drawing, xuất CSV UTF-8 BOM, XLSX (OpenXML cơ bản) và PDF.
- Thùng rác xóa mềm 30 ngày; dọn dữ liệu con nhờ foreign key cascade; xóa tệp đính kèm của hồ sơ hết hạn khi không còn tham chiếu.
- Backup một file `.elbackup` mã hóa AES-256-GCM, kiểm tra manifest/checksum, xem trước dữ liệu và khôi phục thay thế có bản sao rollback database.
- **Trợ lý phân tích offline** (không mạng, không AI): 12 mẫu phân tích chọn sẵn (tổng quan, tồn đọng lâu nhất, bất thường mới, mức độ × trạng thái, thiết bị/KKS/hệ thống lặp lại, thời gian xử lý, khối lượng theo kíp, bản vẽ sửa nhiều nhất, ai trực lúc…, lịch trực của một người, số ca từng người, cảnh báo) và ô hỏi nhanh hiểu theo từ khóa tiếng Việt có/không dấu.

## 6. Hướng dẫn sử dụng nhanh

1. Mở **Khác → Lịch ca**: bấm **Tải file mẫu** để lấy file Excel mẫu, điền lịch tháng và nhân sự, bấm **Chọn file lịch ca**, kiểm tra màn hình xem lại (ô lỗi tô đỏ, chạm để sửa) rồi **Lưu lịch**. Dùng **Cài giờ ca** để đặt giờ ca sáng/chiều/đêm. Có thể thêm từng ca thủ công; ca qua đêm tự cộng một ngày nếu giờ kết thúc nhỏ hơn giờ bắt đầu.
2. Tạo **Bất thường**: tên thiết bị mô tả là bắt buộc; KKS để trống nếu chưa biết; có thể đính kèm tệp.
3. Tạo **Bản vẽ**: số bản vẽ là bắt buộc và được giữ nguyên văn bản người dùng nhập.
4. **Ghi công việc** (từ Tổng quan, tab Bất thường/Bản vẽ hoặc trang hồ sơ): nhập tên thiết bị + KKS (hoặc số/tiêu đề bản vẽ), nội dung, đính kèm tệp rồi **Xác nhận**; chọn hồ sơ theo gợi ý hoặc chọn hồ sơ khác. Thời gian là lúc xác nhận; ca được xác định khi có đúng một occurrence phù hợp.
5. Với bản vẽ, chỉ dùng **Ghi nhận lần chỉnh sửa / Revision** khi có thay đổi chính thức; nhập revision trước/sau và mô tả thay đổi.
6. Mở **Cảnh báo** để xem log nội bộ. Xem cảnh báo không đồng nghĩa xác nhận xử lý bất thường.
7. Từ **Khác → Báo cáo**, chọn phạm vi, khoảng ngày và định dạng. File xuất vào nơi người dùng chọn qua Android Storage Access Framework.
8. Từ **Khác → Sao lưu / Khôi phục**, đặt mật khẩu tối thiểu 10 ký tự; Khôi phục hiện hỗ trợ chế độ thay thế, chưa có chế độ merge.
9. **Khác → Trợ lý phân tích**: chọn mẫu phân tích (có ô tham số như khoảng thời gian, kíp, nhân sự) hoặc gõ câu hỏi ngắn; kết quả có thể mở hồ sơ liên quan hoặc sao chép.

## 7. Bảo mật và giới hạn hiện tại

- SQLite và tệp đính kèm đang nằm trong vùng lưu trữ riêng của ứng dụng (app sandbox); backup được mã hóa xác thực. **Database SQLite và tệp đính kèm chưa được mã hóa độc lập từng byte khi đang lưu trong sandbox**. Đây là thiếu sót so với yêu cầu mã hóa dữ liệu nhạy cảm ở mức ứng dụng; cần tích hợp SQLCipher hoặc cơ chế mã hóa dữ liệu/tệp đã được kiểm toán trước khi dùng với dữ liệu kỹ thuật mật.
- Restore thay thế có backup rollback; chưa triển khai merge theo UUID.
- Nhập lịch ca cần file đúng bố cục (hàng "Ngày" + các hàng Sáng/Chiều/Đêm, mục "Kíp A…E"); không đọc ảnh/PDF. Luôn kiểm tra màn hình xem lại trước khi lưu.
- Chưa có form sửa hồ sơ, xóa hàng loạt, import danh sách, lọc nâng cao, cấu hình bật/tắt từng loại cảnh báo hoặc xử lý xác nhận ca chồng lấn bằng hộp chọn.
- Tệp đính kèm lưu trong app-private storage, xem/mở được từ trang hồ sơ; liên kết tệp trước/sau theo từng revision và xem trước ảnh trong app còn thiếu.
- Trợ lý phân tích hiểu câu hỏi theo từ khóa nên chỉ trả lời tốt các dạng câu đã liệt kê; câu lạ sẽ chuyển thành tìm kiếm hồ sơ.
- Có chế độ sáng/tối theo hệ thống; chưa có cấu hình cỡ chữ riêng, export report giàu bảng/ảnh, hoặc UI dành riêng cho tablet.
- Bộ kiểm thử đã chạy chỉ bao phủ 7 assertion của logic thuần Java; chưa có Android unit/instrumentation/integration test cho database, backup, API và UI.

**Không dùng project này như bản production cho hồ sơ nhà máy nhạy cảm trước khi hoàn tất các giới hạn trên và kiểm thử/đánh giá an toàn thông tin.**

## 8. Kết quả build và đường dẫn APK

- `assembleDebug`: chưa chạy được vì không có Gradle/Android SDK.
- `assembleRelease`: chưa chạy được vì không có Gradle/Android SDK; chưa có keystore ký release.
- APK được tạo: **không có**.
- Đường dẫn APK: **không có file APK để cung cấp**.
- Kiểm thử core: `PASS: 7 core assertions` (xem `TEST_REPORT.md`).

## Build APK online

Xem [BUILD_ONLINE.md](BUILD_ONLINE.md) để build APK bằng GitHub Actions trên máy chủ GitHub. Workflow tạo APK debug để cài thử và APK release chưa ký; bản release cần ký bằng keystore riêng trước khi phát hành.
