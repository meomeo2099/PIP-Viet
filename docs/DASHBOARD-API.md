# Công tắc Dashboard → PIP Việt (API1)

Theo yêu cầu24/10/2026: Dashboard dùng PIP Việt để bật thì tự cài/kích hoạt thành phần PiP, tắt thì khôi phục. Cần cập nhật cả hai ứng dụng; bản PIP Việt trước API1 chỉ mở giao diện thủ công.

## Quyền sở hữu thao tác

Dashboard kết nối `.DashboardApiService` bằng Messenger, không đổi UID/signer hoặc nhập overlay vào Dashboard. PIP Việt vẫn chạy bằng UID SystemUI và dùng nguyên PipService/PlatformBridge/InstallReceiver/RecoveryReceiver đã có: kiểm Android9, UID/quyền, hash/profile SystemUI, hash/signature overlay, cài PackageInstaller nếu thiếu, journal trước bật/tắt, restart/readback, watchdog90giây và phục hồi sau boot. Không thay khóa hoặc cơ chế an toàn này.

Service mới được export nhưng xác thực UID do Android đặt trong Message.sendingUid trước đọc payload. Chỉ package `vn.tht.ex2.car`/`vn.tht.ex2.car.platformtest` thuộc UID đó, có chữ ký khớp PIP Việt hoặc SHA-256 chứng thư THT hiện hữu `C8A2E9BCCF597C2FB6DC66BEE293FC13F2FC47EC77BC6B2B0D52C11F51192AB8`. Đổi signer phải cập nhật allowlist bằng chứng thư công khai; không nhận package/UID do extras tự khai. Các thành phần cài/khôi phục cũ vẫn private.

## Giao thức

Manifest application metadata: `vn.tht.pip.API_VERSION=1`. Component: `com.geely.pipinstaller.DashboardApiService`.

- what1: STATUS; `refresh=true` yêu cầu đọc lại (nếu không pending), gồm trạng thái busy cũ bị kẹt trước khi private service chạy. Poll tiếp dùng refresh=false để không tạo vòng refresh.
- what2: ENABLE; what3: DISABLE. Request ID8–80ký tự `[A-Za-z0-9_-]`, replyTo bắt buộc.
- Busy/pending chặn thao tác mới; rollback chưa xử lý chặn bật lại nhưng vẫn cho tắt/khôi phục. Đánh dấu busy bền vững trước gọi service gốc; không tự bật trong onBind/boot.
- Phản hồi gồm api1/request/accepted/busy/pending/rollback/enabled/compatible/privileged/message. Khi idle, đọc `PlatformBridge.enabled()` thực và đặt verified=true; lỗi đọc trả accepted=false. Accepted chỉ là nhận yêu cầu, không phải đã bật. Không trả các cờ trạng thái cho caller bị từ chối.
- Dashboard kiểm trước API metadata, UID/chữ ký helper khớp SystemUI. Kiểm UID phản hồi và request ID, chỉ idle+verified mới coi trạng thái đã xác nhận. Poll750ms, timeout120giây; hủy kết nối không hủy journal thao tác đang chạy. Khi trở lại màn Dashboard, chỉ hỏi STATUS, không tự gửi ENABLE lần nữa.

## Kiểm chứng

`scripts/check.sh` kiểm compatibility và core upstream hash không đổi, caller/request policy, metadata API và các component cũ private. `scripts/test-dashboard-api.sh` chạy Robolectric Android9 với reader PlatformBridge giả để kiểm caller, certificate, busy/rollback, trạng thái thật, recovery queue và service action; không cài overlay/restart SystemUI thật. `scripts/build.sh --check` biên dịch/ký/xác minh hai APK bằng khóa kiểm tra rồi xóa, không xuất bản cài xe. CI có thêm bài kiểm tra native này.

Chưa nghiệm thu IPC/UID/PackageInstaller/khôi phục trên IHU thật. Không phát hành APK mới hoặc đổi version/signer/overlay asset ở thay đổi này.
