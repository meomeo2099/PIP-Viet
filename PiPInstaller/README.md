# Ứng dụng PIP Việt

Xem [hướng dẫn tiếng Việt](../README.md) để cài đặt, kiểm tra tương thích và build bằng `THT_SIGN_*`.

- Mã nguồn ứng dụng: `src/com/geely/pipinstaller/`.
- `scripts/build.sh` tại thư mục gốc biên dịch lại cả overlay và ứng dụng.
- `build.ps1` gọi bộ build này thông qua WSL trên Windows.
- `assets/pip-overlay-platform.apk` là bản gốc lưu đối chiếu; không được nhúng vào build mới.
- Mã kích hoạt/tắt, callback cài đặt và khôi phục gốc được giữ lại; các thông báo được Việt hoá.

Tài liệu gốc: [docs/upstream/PiPInstaller.pt-BR.md](../docs/upstream/PiPInstaller.pt-BR.md).
