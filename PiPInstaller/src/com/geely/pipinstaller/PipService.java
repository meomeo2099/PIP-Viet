package com.geely.pipinstaller;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.os.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PipService extends Service {
    static final String ENABLE = "enable", DISABLE = "disable", REFRESH = "refresh", RECOVER = "recover", INSTALLED = "installed";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    static SharedPreferences state(Context c) { return c.getSharedPreferences("pip-control", MODE_PRIVATE); }
    static void launch(Context c, String action) {
        c.startForegroundService(new Intent(c, PipService.class).setAction(action));
    }
    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = state(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("operation", "Thiết lập cửa sổ PiP", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "operation").setSmallIcon(R.drawable.ic_pip)
                .setContentTitle("PIP Việt").setContentText("Đang kiểm tra màn hình")
                .setContentIntent(open).setOngoing(true).build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? RECOVER : intent.getAction();
        worker.execute(() -> {
            try {
                if (INSTALLED.equals(action)) {
                    if (intent.getIntExtra("session", -2) != prefs.getInt("session", -1) || !prefs.getBoolean("pending", false)) return;
                    if (intent.getIntExtra("status", PackageInstaller.STATUS_FAILURE) != PackageInstaller.STATUS_SUCCESS)
                        throw new IOException("Android không cho phép tự cài overlay. " + intent.getStringExtra("detail"));
                    prefs.edit().putInt("session", -1).commit();
                    activate(new PlatformBridge(this));
                } else if (RECOVER.equals(action)) {
                    recover("Thao tác trước bị gián đoạn hoặc quá thời gian chờ.");
                } else if (prefs.getBoolean("pending", false)) {
                    // A instalacao assincrona ja tem uma operacao em andamento.
                    return;
                } else if (ENABLE.equals(action)) {
                    begin();
                    PlatformBridge bridge = new PlatformBridge(this);
                    status("Đang kiểm tra tương thích…");
                    bridge.checkCompatibility();
                    if (bridge.installed()) {
                        bridge.checkInstalledOverlay();
                        if (bridge.enabled()) { complete(true, "PiP đã được bật."); return; }
                        activate(bridge);
                    } else {
                        status("Đang cài thành phần PiP…");
                        installOverlay();
                    }
                } else if (DISABLE.equals(action)) {
                    begin();
                    PlatformBridge bridge = new PlatformBridge(this);
                    if (!bridge.installed()) { complete(false, "Chưa cài thành phần PiP."); return; }
                    // Reversao permitida mesmo quando o hash da SystemUI mudou.
                    prefs.edit().putBoolean("rollback", true).commit();
                    bridge.setEnabled(false);
                    status("Đang tắt PiP…");
                    bridge.restartAndVerify(false);
                    complete(false, "Đã tắt PiP và khôi phục cấu hình ban đầu.");
                } else {
                    inspect();
                }
            } catch (Exception e) {
                android.util.Log.e("PiPControl", "Operation failed", e);
                if (prefs.getBoolean("pending", false) || prefs.getBoolean("rollback", false)) recover(message(e));
                else {
                    prefs.edit().putBoolean("busy", false).putBoolean("compatible", false)
                            .putString("message", message(e)).apply();
                }
            } finally {
                if (!prefs.getBoolean("pending", false)) {
                    stopForeground(true);
                    stopSelf(startId);
                }
            }
        });
        return START_NOT_STICKY;
    }

    private void inspect() throws Exception {
        prefs.edit().putBoolean("busy", true).apply();
        if (prefs.getBoolean("rollback", false)) { recover("Có thao tác khôi phục chưa hoàn tất."); return; }
        PlatformBridge bridge = new PlatformBridge(this);
        boolean enabled = bridge.enabled();
        prefs.edit().putBoolean("enabled", enabled).putBoolean("privileged", true).apply();
        try {
            bridge.checkCompatibility();
            if (bridge.installed()) bridge.checkInstalledOverlay();
            prefs.edit().putBoolean("compatible", true).putString("message", enabled
                    ? "PiP đang bật. Bạn có thể mở video và kiểm tra thao tác trên cửa sổ."
                    : "Màn hình đáp ứng kiểm tra cấu hình. Có thể bật và kiểm tra PiP.").apply();
        } catch (Exception e) {
            prefs.edit().putBoolean("compatible", false).putString("message", message(e)).apply();
        }
        prefs.edit().putBoolean("busy", false).apply();
    }

    private void begin() {
        if (!prefs.edit().putBoolean("pending", true).putBoolean("busy", true).commit())
            throw new IllegalStateException("Không lưu được trạng thái khôi phục.");
        getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 90000, recoveryIntent());
    }

    private PendingIntent recoveryIntent() {
        return PendingIntent.getBroadcast(this, 2, new Intent(this, RecoveryReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void installOverlay() throws Exception {
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(PlatformBridge.OVERLAY);
        int id = installer.createSession(params);
        prefs.edit().putInt("session", id).commit();
        try (PackageInstaller.Session session = installer.openSession(id);
             InputStream input = getAssets().open("pip-overlay-platform.apk");
             OutputStream output = session.openWrite("base.apk", 0, -1)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            session.fsync(output);
        }
        try (PackageInstaller.Session session = installer.openSession(id)) {
            Intent callback = new Intent(this, InstallReceiver.class).setAction("com.geely.pipinstaller.INSTALL_RESULT");
            PendingIntent result = PendingIntent.getBroadcast(this, id, callback, PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(result.getIntentSender());
        }
    }

    private void activate(PlatformBridge bridge) throws Exception {
        bridge.checkCompatibility();
        bridge.checkInstalledOverlay();
        // Gravado antes da mutacao para permitir recuperacao apos morte do processo/boot.
        if (!prefs.edit().putBoolean("rollback", true).commit()) throw new IOException("Không chuẩn bị được dữ liệu khôi phục.");
        status("Đang bật PiP. Giao diện hệ thống sẽ khởi động lại trong chốc lát…");
        bridge.setEnabled(true);
        bridge.restartAndVerify(true);
        complete(true, "Đã bật PiP. Hãy mở video và thử kéo, chạm, phóng to cửa sổ.");
    }

    private void complete(boolean enabled, String message) {
        prefs.edit().putBoolean("enabled", enabled).putBoolean("pending", false)
                .putBoolean("rollback", false).putBoolean("busy", false).putInt("session", -1)
                .putString("message", message).commit();
        getSystemService(AlarmManager.class).cancel(recoveryIntent());
    }

    private void recover(String reason) {
        if (!prefs.getBoolean("pending", false) && !prefs.getBoolean("rollback", false)) return;
        int session = prefs.getInt("session", -1);
        if (session >= 0) try { getPackageManager().getPackageInstaller().abandonSession(session); } catch (Exception ignored) { }
        boolean needsRollback = prefs.getBoolean("rollback", false);
        try {
            PlatformBridge bridge = new PlatformBridge(this);
            if (needsRollback) {
                status("Đang khôi phục cấu hình ban đầu…");
                bridge.setEnabled(false);
                bridge.restartAndVerify(false);
            }
            complete(bridge.enabled(), reason + (needsRollback ? " Đã tự tắt PiP để khôi phục." : " Overlay chưa được bật."));
        } catch (Exception failure) {
            android.util.Log.e("PiPControl", "Recovery failed", failure);
            prefs.edit().putBoolean("pending", false).putBoolean("busy", false).putBoolean("rollback", needsRollback)
                    .putBoolean("compatible", false).putInt("session", -1).putString("message",
                    reason + " Chưa xác nhận khôi phục: " + message(failure) + " Hãy chọn Tắt PiP hoặc khởi động lại màn hình.").commit();
            // Uma nova tentativa pode ser feita pelo usuario; o boot tambem verifica rollback.
            getSystemService(AlarmManager.class).cancel(recoveryIntent());
        }
    }

    private void status(String text) { prefs.edit().putString("message", text).apply(); }
    private static String message(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
