package com.geely.pipinstaller;

import java.io.IOException;
import java.util.Arrays;

/** Fixed RROs may only replace arrays whose existing services they preserve. */
final class CompatibilityPolicy {
    private static final String[] BASE = {
        "com.android.systemui.Dependency",
        "com.android.systemui.statusbar.CommandQueue$CommandQueueStart",
        "com.android.systemui.keyguard.KeyguardViewMediator",
        "com.android.systemui.SystemBars"
    };
    static void checkComponents(String[] components) throws IOException {
        if (Arrays.asList(components).contains("com.android.systemui.pip.PipUI"))
            throw new IOException("ROM đã khai báo PipUI. Không cần áp dụng overlay này.");
        if (!Arrays.equals(BASE, components))
            throw new IOException("ROM có danh sách dịch vụ riêng. Cần overlay dành cho ROM này để giữ đủ thanh hệ thống.");
    }
}
