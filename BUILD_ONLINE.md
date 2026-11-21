# Build APK online bằng GitHub Actions

## Cách nhanh nhất

1. Đăng nhập GitHub và tạo repository mới, ví dụ `evnqt1-help-desk`.
2. Giải nén `EVNQT1_HELP_DESK_source.zip` trên máy tính.
3. Upload toàn bộ nội dung thư mục project lên repository (bao gồm thư mục ẩn `.github/workflows`). Có thể dùng GitHub web: **Add file → Upload files**.
4. Mở tab **Actions** của repository. Nếu GitHub yêu cầu, chọn **I understand my workflows, go ahead and enable them**.
5. Chọn workflow **Android APK build** → **Run workflow** → **Run workflow**.
6. Chờ workflow chạy xong. Nếu job màu xanh, mở lần chạy đó, kéo xuống **Artifacts**, tải `EVNQT1-HELP-DESK-APKs.zip`.
7. Giải nén artifact. `app-debug.apk` là APK debug có chữ ký debug để cài thử. `app-release-unsigned.apk` là bản release chưa ký, không nên cài trực tiếp.

## Lưu ý về release signing

Workflow hiện build debug APK có thể cài thử và release APK chưa ký. Muốn phát hành/cài bản release ổn định, cần ký bằng keystore riêng và giữ bí mật keystore/password trong GitHub Actions Secrets. Không commit keystore hoặc mật khẩu vào repository. Giữ bản sao keystore an toàn vì mất keystore có thể khiến không cập nhật được app đã cài bằng chữ ký đó.

## Nếu workflow lỗi

Mở lần chạy trong **Actions**, chọn job **Build EVNQT1 HELP DESK APK**, mở bước màu đỏ và tải log. Các lỗi thường gặp:

- AGP/Gradle không tương thích: kiểm tra `build.gradle`, `gradle-wrapper.properties` nếu bổ sung wrapper, và phiên bản Gradle trong workflow.
- Thiếu SDK package: kiểm tra bước `Install Android SDK packages`.
- Lỗi Java compile/manifest/resource: xem log của `Build debug APK`.
- Test lõi lỗi: xem bước `Run core tests`.

Không coi build thành công nếu workflow chưa hoàn tất xanh và artifact APK chưa xuất hiện.
