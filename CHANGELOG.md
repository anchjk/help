# Changelog

## 1.4.0 (21/11/2026)

**Thống kê bất thường (tab mới "Thống kê", offline)**
- Chọn khoảng thời gian: 7 ngày, 30 ngày, tháng này, tháng trước, quý này, năm nay, hoặc **Từ ngày – Đến ngày** tùy chọn.
- Bốn chỉ số chính: **đang tồn tại** (chưa xử lý/đang xử lý), **phát sinh mới** trong kỳ, **đã xử lý** trong kỳ, **tồn đọng tăng/giảm**; kèm so sánh với kỳ liền trước cùng độ dài, thời gian xử lý trung bình, tuổi trung bình của bất thường tồn đọng.
- Biểu đồ cột phát sinh mới / đã xử lý theo ngày (≤ 31 ngày), tuần (≤ 140 ngày), tháng hoặc quý; chạm vào cột để xem số liệu. Biểu đồ vòng tồn đọng theo mức độ.
- Danh sách cụ thể: bất thường đang tồn tại, phát sinh mới trong kỳ, **đã xử lý trong kỳ là những bất thường nào** (kèm thời gian xử lý); chạm để mở hồ sơ.
- **Hay xảy ra nhất** theo Loại / Thiết bị / Hệ thống (xếp hạng, số lượng, %); chạm một dòng để xem các bất thường trong nhóm. "Loại" tự động phân theo từ khóa trong tiêu đề/mô tả (rò rỉ, rung/ồn, nhiệt độ, áp suất/lưu lượng, điện/tín hiệu, van/điều khiển, cơ khí, tắc nghẽn, dầu bôi trơn, gỉ sét, cháy/khói, sai lệch thông số) — chỉ mang tính tham khảo.
- Tổng quan có thẻ "7 ngày qua" đi thẳng tới màn thống kê; bộ lọc danh sách bất thường có thêm "Đang tồn tại".

**Giao diện tối ưu cho điện thoại**
- Thanh dưới: Tổng quan · Bất thường · Bản vẽ · **Thống kê** · Khác. **Cảnh báo** chuyển lên **biểu tượng chuông có số đếm** ở thanh trên (và có thêm trong mục Khác).
- **Nút nổi "+"** ở góc phải dưới (một tay với tới): ghi công việc bất thường/bản vẽ, tạo bất thường, tạo hồ sơ bản vẽ; hiện dạng bảng trượt từ đáy.
- Thẻ chỉ số Tổng quan dạng ô màu có biểu tượng; thẻ hồ sơ có dải màu theo mức độ, hiện "tồn tại N ngày" và "cập nhật … trước".
- Nút/ô nhập cao tối thiểu 48dp; cột nội dung tối đa 720dp, căn giữa khi xoay ngang/máy tính bảng; màn hình khởi động Android 12+ cùng tông với icon.

**Icon app mới**: lục giác neon xanh–tím, tia sét vàng cam, chấm đỏ cảnh báo trên nền xanh đen mạch điện; có bản icon đơn sắc cho Android 13+. Xem `icon-preview.png`.

- Kiểm thử mới: `StatsSmokeTest` (28 assertion).

## 1.3.0 (21/11/2026)

**Giao diện**
- Tổng quan → **Truy cập nhanh**: các thẻ lối tắt luôn cao bằng nhau; nhãn chiếm cố định 2 dòng nên biểu tượng và chữ thẳng hàng, không còn thẻ cao thẻ thấp.

**Ghi công việc chỉ còn 3 trường + tệp đính kèm**
- Bất thường: nhập **Tên thiết bị**, **Mã KKS** (không bắt buộc), **Nội dung công việc**, đính kèm tệp (ảnh, PDF, Word, Excel) rồi bấm **Xác nhận**.
- Sau khi xác nhận, app **gợi ý bất thường liên quan nhất** theo tên thiết bị + KKS (KKS trùng là tín hiệu mạnh nhất; tên thiết bị so không phân biệt dấu, chịu lỗi gõ, số hiệu "số 1/số 2" phải khớp; hồ sơ chưa xử lý được ưu tiên). Người dùng chọn gán theo gợi ý hoặc **Chọn bất thường khác** để xem toàn bộ danh sách (có ô tìm kiếm không dấu). Không có gợi ý phù hợp → vào thẳng danh sách; chưa có hồ sơ nào → có nút tạo mới từ nội dung vừa nhập (giữ nguyên tệp đã chọn).
- Bản vẽ: nhập **Số bản vẽ** hoặc **Tên/tiêu đề bản vẽ**, nội dung, tệp đính kèm; gợi ý theo số bản vẽ (bỏ khoảng trắng/gạch/dấu chấm, chịu lệch 1 ký tự) hoặc tên/tiêu đề.
- Mở "Ghi công việc" từ trang chi tiết một hồ sơ thì ghi thẳng vào hồ sơ đó (đã điền sẵn thiết bị/KKS hoặc số/tiêu đề bản vẽ), không hỏi gán.
- Thời gian ghi là lúc bấm Xác nhận; ca trực và nhân sự tự gán theo lịch ca. Trạng thái bất thường đổi bằng nút **Đổi trạng thái** ở trang chi tiết.

**Tệp đính kèm**
- Thêm chức năng đính kèm tệp khi **tạo bất thường**, **tạo hồ sơ bản vẽ** và khi **ghi công việc** (bất thường và bản vẽ); chọn nhiều tệp một lần, bỏ chọn trước khi lưu.
- Trang chi tiết hiển thị **Tệp đính kèm** của hồ sơ và tệp của từng phiếu ngay trong lịch sử công việc; chạm để mở bằng ứng dụng xem tệp trên máy (qua `AttachmentProvider`, chỉ đọc, không export), xóa được từng tệp.
- Lưu tên tệp gốc; chỉ nhận ảnh, PDF, Word (.doc/.docx), Excel (.xls/.xlsx/.xlsm), tối đa 100 MB/tệp. Sửa lỗi nút "Đính kèm tệp" cũ không lưu được khi chọn nhiều tệp.
- Database lên **v4** (có migration, không mất dữ liệu): phiếu công việc lưu thêm tên thiết bị/KKS (hoặc tên/số bản vẽ) đã nhập; tệp gắn được vào từng phiếu.
- Kiểm thử mới: `MatchSuggesterSmokeTest` (14 assertion).

## 1.2.0 (20/11/2026)

**Gỡ hoàn toàn AI, chạy offline 100%**
- Xóa chức năng AI, trợ lý chat AI, nhập API key và nhận diện ảnh. Ứng dụng không còn khai báo quyền INTERNET.
- Database lên v3: xóa lịch sử hội thoại và cấu hình AI cũ; tự dọn khóa API/khóa Keystore còn sót trên máy đã cài bản cũ.

**Nhập lịch trực từ file Excel/CSV (không cần AI)**
- Đọc trực tiếp file .xlsx (hoặc .csv) theo mẫu: hàng Ngày/Thứ/Sáng/Chiều/Đêm, danh sách nhân sự theo kíp, dòng "(Từ dd/MM/yyyy đến …)". Nhận diện theo nhãn nên không phụ thuộc vị trí ô; hiểu cả bố cục xoay dọc, CSV (, ; Tab) và nhân sự ghi cùng dòng "Kíp A: …".
- Màn hình xem lại: đối chiếu thứ với lịch thật, báo ô thiếu/trùng kíp, chạm ô để sửa, sửa nhân sự và giờ ca trước khi lưu.
- Nút **Tải file mẫu** (file mẫu Lịch trực tháng 11/2026 của bạn được đóng sẵn trong app).
- **Cài giờ ca**: đặt giờ ca sáng/chiều/đêm (kiểm tra các ca nối liền nhau), áp dụng cho lần nhập sau và có thể cập nhật luôn lịch tháng đang xem.

**Trợ lý phân tích offline**
- Trang **Trợ lý phân tích** với 12 mẫu chọn sẵn có ô tham số (khoảng thời gian, kíp, nhân sự, nhóm theo…): tổng quan, tồn đọng lâu nhất, bất thường mới, mức độ × trạng thái, thiết bị/KKS/hệ thống lặp lại, thời gian xử lý, khối lượng công việc theo kíp, bản vẽ sửa nhiều nhất, ai trực lúc…, lịch trực của một người, số ca từng người, cảnh báo.
- Ô hỏi nhanh hiểu câu hỏi tiếng Việt (có/không dấu) theo từ khóa; câu lạ chuyển thành tìm kiếm hồ sơ. Kết quả mở được hồ sơ liên quan và sao chép được.
- Kiểm thử: `AssistantSmokeTest`, `ScheduleSmokeTest` dùng chính file Excel mẫu (90 ca liền mạch), SQL phân tích kiểm bằng sqlite3 trên schema thật.

## 1.1.1 (20/11/2026)

- **Kết nối AI chỉ bằng API key**: dán key (hoặc bấm "Dán từ bộ nhớ tạm") → app tự nhận diện nhà cung cấp (Gemini, OpenAI, OpenRouter, Groq, xAI; key lạ thử Gemini rồi OpenAI), kiểm tra key, tải danh sách model, tự chọn model phù hợp (ưu tiên bản ổn định, đọc được ảnh) và lưu. Có thể đổi model trong danh sách nếu muốn. Không còn ô nhà cung cấp/model/base URL phải nhập tay.
- Lưu key không còn bị chặn nếu Android Keystore lỗi trên một số máy/ROM (dự phòng bằng vùng dữ liệu riêng của app).
- Thêm `ModelRankerTest` (nhận diện key, loại model embedding/tts/image, chọn model mặc định).

## 1.1.0 (20/11/2026)

**Giao diện**
- Thiết kế lại toàn bộ giao diện: header gradient, thẻ bo góc, chip trạng thái/mức độ có màu, thanh điều hướng dưới có biểu tượng và huy hiệu số cảnh báo chưa xem, tab Tổng quan có thẻ "Đang trực" (kíp, ca, giờ còn lại, nhân sự).
- Hỗ trợ chế độ sáng/tối theo hệ thống; xử lý edge-to-edge (Android 15+), bàn phím và nút/cử chỉ Back (predictive back, Android 13+).
- Trang chi tiết hồ sơ (bất thường/bản vẽ) thay cho hộp thoại, có timeline lịch sử công việc và revision.
- Biểu mẫu mới: chọn ngày giờ bằng hộp chọn hệ thống (không gõ tay dd/MM/yyyy HH:mm), chọn giá trị bằng danh sách, lỗi nhập hiện ngay mà không mất dữ liệu đã gõ; tìm kiếm/lọc tức thì ngay trên danh sách.
- Icon app mới (adaptive icon + biểu tượng một màu cho Android 13+), file nguồn tại `docs/branding/`.
- Mục Giới thiệu: thêm thông báo bản quyền ANQT1.

**Lịch ca bằng ảnh (AI)**
- Chọn ảnh bảng lịch ca của tháng → AI đọc tháng/năm, mã kíp Sáng/Chiều/Đêm từng ngày, giờ đi ca và nhân sự các kíp → màn hình xem lại → lưu.
- Tự đối chiếu "thứ" in trên ảnh với lịch thật để phát hiện đọc sai hoặc chọn sai tháng; cảnh báo ô thiếu/trùng kíp; chạm ô để sửa, sửa giờ ca và nhân sự trước khi lưu.
- Lưu theo tháng: cập nhật tại chỗ các ca trùng (giữ liên kết phiên công việc), thay thế ca chồng lấn, có thể xóa lịch cả tháng.
- Ảnh chỉ gửi tới nhà cung cấp AI sau khi người dùng xác nhận, được thu nhỏ trong bộ nhớ và không lưu lại.

**Tối ưu**
- Danh sách phân trang (40 mục/lần), tổng quan chỉ tải 4 hồ sơ gần nhất, dọn dẹp thùng rác/làm mới cảnh báo chạy nền khi mở app.
- Khóa API Gemini gửi bằng header thay vì tham số URL.
- Database nâng lên v2 (thêm cột `slot`, `source` cho lịch ca) với migration giữ nguyên dữ liệu và backup cũ.
- Bỏ lint khỏi `assembleRelease` để build nhanh hơn.

**Kiểm thử**
- Thêm `ScheduleSmokeTest` dùng dữ liệu lịch tháng 11/2026 (`docs/sample_schedule_response.json`): 30 ngày × 3 ca, giao ca liền mạch, ca đêm qua ngày, phát hiện sai tháng/trùng kíp.

## 1.0.0-source (19/11/2026)

- Khởi tạo Android native Java project với minSdk 29, compileSdk/targetSdk 36.
- Thêm SQLite schema cho bất thường, bản vẽ, work sessions, revision, lịch ca, cảnh báo, audit và chat.
- Thêm quy tắc gộp session theo shift occurrence, ca qua đêm, tìm kiếm tiếng Việt và rule engine cảnh báo local.
- Thêm UI thao tác cơ bản, báo cáo CSV/XLSX/PDF, backup mã hóa, API adapter Gemini/OpenAI/compatible và màn hình chat xác nhận context.
- Chạy 7 assertion logic thuần Java; Android build chưa chạy do thiếu toolchain.
