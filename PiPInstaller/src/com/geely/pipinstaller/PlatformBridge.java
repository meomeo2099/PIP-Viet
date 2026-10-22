package com.geely.pipinstaller;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import java.io.*;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class PlatformBridge {
    static final String OVERLAY = "com.geely.ex2.mica.pipoverlayprobe";
    static final String SYSTEM_UI = "com.android.systemui";
    static final String SYSTEM_HASH = "0C9AB49B1CCF83133B3CBCAB1CC1D980E97C6472FE9D06F65E2BEF07D0737332";
    static final String OVERLAY_HASH = BuildConfig.OVERLAY_SHA256;
    private final Context context;
    private final Class<?> overlayInterface;
    private final Object overlayManager;

    PlatformBridge(Context context) throws Exception {
        this.context = context;
        if (Build.VERSION.SDK_INT != 28) throw new IOException("Phiên bản này hỗ trợ Android 9. Cần cấu hình riêng cho Android khác.");
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(SYSTEM_UI, 0);
        if (android.os.Process.myUid() / 100000 != 0)
            throw new IOException("Hãy cài và mở ứng dụng bằng người dùng chính của màn hình.");
        if (info.uid != android.os.Process.myUid())
            throw new IOException("Chữ ký hoặc UID của ứng dụng chưa khớp với SystemUI của xe.");
        for (String permission : new String[]{"android.permission.INSTALL_PACKAGES",
                "android.permission.CHANGE_OVERLAY_PACKAGES", "android.permission.DUMP"}) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                throw new IOException("Thiếu quyền hệ thống: " + permission);
        }
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "overlay");
        if (binder == null) throw new IOException("Không tìm thấy dịch vụ quản lý overlay.");
        overlayInterface = Class.forName("android.content.om.IOverlayManager");
        overlayManager = Class.forName("android.content.om.IOverlayManager$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    void checkCompatibility() throws Exception {
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(SYSTEM_UI, 0);
        try (InputStream input = new FileInputStream(info.sourceDir)) {
            if (!SYSTEM_HASH.equals(sha256(input)))
                Compatibility.checkSystemUi(context, info);
        }
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
            throw new IOException("Màn hình không khai báo hỗ trợ cửa sổ PiP.");
        try (InputStream input = context.getAssets().open("pip-overlay-platform.apk")) {
            if (!OVERLAY_HASH.equals(sha256(input))) throw new IOException("Gói overlay đính kèm không vượt qua kiểm tra toàn vẹn.");
        }
    }

    boolean installed() {
        try { context.getPackageManager().getPackageInfo(OVERLAY, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    void checkInstalledOverlay() throws Exception {
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(OVERLAY, 0);
        try (InputStream input = new FileInputStream(info.sourceDir)) {
            if (!OVERLAY_HASH.equals(sha256(input)))
                throw new IOException("Đang có overlay khác chữ ký hoặc phiên bản. Hãy tắt và gỡ overlay cũ trước.");
        }
    }

    boolean enabled() throws Exception {
        Object info = overlayInterface.getMethod("getOverlayInfo", String.class, int.class)
                .invoke(overlayManager, OVERLAY, 0);
        return info != null && (Boolean) info.getClass().getMethod("isEnabled").invoke(info);
    }

    void setEnabled(boolean value) throws Exception {
        boolean accepted = (Boolean) overlayInterface.getMethod("setEnabled", String.class, boolean.class, int.class)
                .invoke(overlayManager, OVERLAY, value, 0);
        if (!accepted || enabled() != value) throw new IOException("Hệ thống chưa xác nhận thay đổi overlay.");
    }

    int systemUiPid() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (SYSTEM_UI.equals(process.processName) && process.uid == android.os.Process.myUid())
                return process.pid;
        }
        return -1;
    }

    void restartAndVerify(boolean pipExpected) throws Exception {
        int oldPid = systemUiPid();
        if (oldPid <= 0 || oldPid == android.os.Process.myPid())
            throw new IOException("Không xác định được tiến trình SystemUI để khởi động lại.");
        android.os.Process.killProcess(oldPid);
        int currentPid = -1;
        for (int attempt = 0; attempt < 10; attempt++) {
            Thread.sleep(700);
            currentPid = systemUiPid();
            if (currentPid > 0 && currentPid != oldPid) break;
        }
        if (currentPid <= 0 || currentPid == oldPid)
            throw new IOException("SystemUI chưa tự khởi động lại. Hãy tắt PiP rồi khởi động lại màn hình.");
        Thread.sleep(1200);
        String dump = dumpSystemUi();
        boolean loaded = dump.contains("dumping service: com.android.systemui.pip.PipUI")
                && dump.contains("PipManager");
        if (dump.trim().isEmpty() || !dump.contains("com.android.systemui/.SystemUIService"))
            throw new IOException("Không kiểm tra được trạng thái SystemUI.");
        if (loaded != pipExpected) throw new IOException("SystemUI chưa nạp cấu hình PiP như mong đợi.");
        Thread.sleep(1000);
        if (systemUiPid() != currentPid) throw new IOException("SystemUI bị khởi động lại trong lúc kiểm tra.");
    }

    private String dumpSystemUi() throws Exception {
        File output = new File(context.getCacheDir(), "systemui-check.txt");
        java.lang.Process process = new ProcessBuilder("/system/bin/dumpsys", "activity", "service",
                "com.android.systemui/.SystemUIService").redirectErrorStream(true).redirectOutput(output).start();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Hết thời gian chờ kiểm tra SystemUI.");
        }
        if (process.exitValue() != 0) throw new IOException("Hệ thống từ chối kiểm tra SystemUI.");
        try (InputStream input = new FileInputStream(output); ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1 && result.size() < 1024 * 1024) result.write(buffer, 0, count);
            return result.toString("UTF-8");
        }
    }

    static String sha256(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[32768]; int count;
        while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02X", value & 255));
        return result.toString();
    }
}
