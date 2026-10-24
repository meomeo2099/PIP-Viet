# PIP Việt

Ứng dụng tiếng Việt để bật/tắt bản sửa điều khiển cửa sổ PiP trên màn hình Android của xe. Giao diện tối, nút lớn, tự hiển thị hãng, model và phiên bản Android; không gắn tên hiển thị với một dòng xe cụ thể.

Dựa trên [magnolima/geely-ex2-pip-overlay](https://github.com/magnolima/geely-ex2-pip-overlay), mốc `1c2bda31038cab0e4f9974a0425976598065639c`. Mã nguồn gốc được nhập nguyên bản trong commit riêng; tài liệu gốc nằm tại [docs/upstream](docs/upstream). Ghi nhận tác giả và nguồn gốc được giữ lại.

## Sử dụng

1. Cài `PIP-Viet-1.0.0.apk`, mở **PIP Việt** khi xe đang đỗ.
2. Đợi kiểm tra tương thích. Chọn **Bật PiP** khi nút được cho phép.
3. Đợi giao diện hệ thống khởi động lại, mở ứng dụng có hỗ trợ PiP, kiểm tra kéo/chạm/phóng to và đóng cửa sổ.
4. Chọn **Tắt PiP / Khôi phục** để trả về cấu hình ban đầu. Thực hiện bước này trước khi gỡ ứng dụng.

Ứng dụng không bổ sung PiP cho ứng dụng video vốn không hỗ trợ PiP. Thông báo “Đã bật” xác nhận kiểm tra kỹ thuật của SystemUI, không thay thế thử thao tác trên xe.

## Phạm vi tương thích

Không khóa theo tên hãng hoặc model. Tuy nhiên, đây là bản sửa SystemUI Android 9, **không thể bảo đảm mọi xe hoặc mọi Android** với một overlay cố định.

| Trường hợp | Xử lý |
| --- | --- |
| SystemUI gốc đã biết SHA | Giữ kiểm tra gốc |
| Android 9, SystemUI khác SHA | Đọc tài nguyên gốc không qua overlay; yêu cầu đúng bốn dịch vụ nền và có lớp `PipUI`, `pip.phone.PipManager` |
| ROM có dịch vụ riêng của hãng, thứ tự khác hoặc thiếu dịch vụ | Chặn áp dụng; cần phân tích và tạo overlay riêng để giữ các dịch vụ của xe |
| ROM đã khai báo PipUI | Không áp dụng bản sửa thừa |
| Android khác 9 | Báo chưa hỗ trợ; cần triển khai và kiểm tra riêng |
| Khác chứng chỉ/UID/quyền hệ thống | ROM có thể từ chối cài đặt hoặc ứng dụng sẽ báo thiếu quyền |

Ứng dụng giữ `android:sharedUserId="android.uid.systemui"` theo mã gốc; không đổi sang `android.uid.system`. `THT_SIGN_*` phải là khóa được ROM chấp nhận cho UID và các quyền này. Cùng tên secret không tự tạo quyền hệ thống.

## Những phần giữ nguyên

- Luồng cài overlay, bật/tắt, kiểm tra SystemUI, đánh dấu khôi phục và hẹn khôi phục sau 90 giây.
- Các receiver nhận kết quả cài đặt và khôi phục khi khởi động.
- Gói ứng dụng `com.geely.pipinstaller`, gói overlay `com.geely.ex2.mica.pipoverlayprobe`, cùng mảng tài nguyên overlay gốc.
- Bản APK overlay gốc còn trong repo làm tư liệu đối chiếu; **bản build PIP Việt không nhúng APK ký sẵn này**.

Thay đổi giới hạn ở giao diện, câu chữ, kiểm tra tương thích và quy trình biên dịch/ký. Cả overlay và ứng dụng đều được biên dịch lại bằng cùng `THT_SIGN_*`; mã SHA của overlay nhúng được sinh khi build, không bỏ kiểm tra toàn vẹn. Vì vậy không thể mô tả toàn bộ mã nguồn là “không sửa một dòng”; phần xử lý thao tác gốc được giữ lại.

## Build bằng GitHub Actions

Thêm năm **repository secrets** cho chính repo PIP-Viet:

- `THT_SIGN_KEYSTORE_BASE64`
- `THT_SIGN_STORE_PASSWORD`
- `THT_SIGN_KEY_ALIAS`
- `THT_SIGN_KEY_PASSWORD`
- `THT_SIGN_CERT_SHA256`

Secrets của repo khác không tự xuất hiện trong repo này. Không dán giá trị khóa vào source, issue hoặc nhật ký.

Vào **Actions → PIP Viet Android → Run workflow**, chọn nhánh cần build và bật `signed_apk`. Tải artifact `PIP-Viet-1.0.0` sau khi hai job thành công. Nếu thiếu secret hoặc chứng chỉ không khớp, build dừng; không tự dùng khóa khác.

CI của push/PR chỉ biên dịch bằng khóa tạm để kiểm tra, không phát hành APK dùng khóa tạm. Không tự tạo GitHub Release.

## Build trên máy

Cần JDK 17, Python 3, Android SDK Platform 35 và Build Tools 35.0.0. Trên Linux hoặc WSL:

```bash
export ANDROID_SDK_ROOT=/duong/dan/android-sdk
bash scripts/check.sh
bash scripts/build.sh --check
# Nạp năm biến THT_SIGN_* vào môi trường rồi chạy:
bash scripts/build.sh
```

Trên Windows có WSL đã cài các công cụ trong WSL, có thể chạy `PiPInstaller/build.ps1`; `-Check` chỉ kiểm tra. Trong WSL cần đặt `ANDROID_SDK_ROOT` tới SDK Linux.

Kết quả ký nằm tại `PiPInstaller/dist/`, gồm ứng dụng, overlay và `SHA256SUMS`. Chỉ ứng dụng cần cài trực tiếp; overlay đã được nhúng trong ứng dụng.

## Bản overlay cũ và bộ ADB gốc

Nếu xe đã có overlay từ bản gốc, hãy tắt overlay và gỡ gói overlay cũ trước khi cài bản ký bằng khóa khác. Tránh gỡ ứng dụng khi overlay còn bật.

`Manage-PipOverlay.ps1`, `Connect-PipOverlay.ps1`, hai file `.bat`, `guard.sh`, `rollback.sh` là bộ ADB gốc được giữ lại; bộ này vẫn giới hạn đúng SHA/Android/overlay đã xác nhận ban đầu. Không dùng bộ đó để cài bản overlay PIP Việt mới bằng cách đổi tên APK. Ưu tiên sử dụng ứng dụng PIP Việt đã build.

## Kiểm tra

`scripts/check.sh` kiểm thử chặn ROM có dịch vụ riêng, thiếu/thay/thứ tự dịch vụ khác và ROM đã có PipUI. Kiểm tra đối chiếu cũng bảo đảm mã máy trạng thái của `PipService` chỉ đổi chuỗi văn bản; hai receiver và danh sách tài nguyên được giữ nguyên.

Biên dịch và kiểm thử trên máy/CI không chứng minh hoạt động trên xe. Cần thử bật, kéo/chạm cửa sổ, tắt/khôi phục và khởi động lại trên từng ROM trước khi xác nhận hỗ trợ.

## Công tắc từ THT Dashboard — API1

Có giao tiếp cho Dashboard Car: bật để PIP Việt tự kiểm tra/cài/bật thành phần PiP, tắt để khôi phục bằng luồng gốc. Chỉ nhận Dashboard đúng package/chữ ký; không bỏ kiểm tương thích hoặc quyền SystemUI. Cần cập nhật cả PIP Việt và Dashboard. [Giao thức và kiểm chứng](docs/DASHBOARD-API.md).
