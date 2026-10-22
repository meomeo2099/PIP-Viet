package com.geely.pipinstaller;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import dalvik.system.DexFile;
import java.io.IOException;
import java.util.Enumeration;

/** Checks the original APK, without active RROs masking its service array. */
final class Compatibility {
    static void checkSystemUi(Context context, ApplicationInfo info) throws Exception {
        AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
        try {
            java.lang.reflect.Method add = AssetManager.class.getMethod("addAssetPath", String.class);
            if ((Integer) add.invoke(assets, info.sourceDir) == 0)
                throw new IOException("Không đọc được tài nguyên SystemUI gốc.");
            if (info.splitSourceDirs != null) for (String split : info.splitSourceDirs)
                if ((Integer) add.invoke(assets, split) == 0)
                    throw new IOException("Không đọc được tài nguyên SystemUI bổ sung.");
            Resources resources = new Resources(assets, context.getResources().getDisplayMetrics(),
                    context.getResources().getConfiguration());
            int id = resources.getIdentifier("config_systemUIServiceComponents", "array", PlatformBridge.SYSTEM_UI);
            if (id == 0) throw new IOException("ROM này không có cấu hình PiP phù hợp. Chưa thay đổi hệ thống.");
            String[] components = resources.getStringArray(id);
            CompatibilityPolicy.checkComponents(components);
        } finally { assets.close(); }
        boolean pipUi = false, pipManager = false;
        String[] paths = info.splitSourceDirs == null ? new String[]{info.sourceDir}
                : combine(info.sourceDir, info.splitSourceDirs);
        for (String path : paths) {
            DexFile dex = new DexFile(path);
            try {
                Enumeration<String> classes = dex.entries();
                while (classes.hasMoreElements()) {
                    String name = classes.nextElement();
                    pipUi |= "com.android.systemui.pip.PipUI".equals(name);
                    pipManager |= "com.android.systemui.pip.phone.PipManager".equals(name);
                }
            } finally { dex.close(); }
        }
        if (!pipUi || !pipManager)
            throw new IOException("SystemUI thiếu bộ điều khiển PiP tương thích. Chưa thay đổi hệ thống.");
    }

    private static String[] combine(String base, String[] splits) {
        String[] paths = new String[splits.length + 1]; paths[0] = base;
        System.arraycopy(splits, 0, paths, 1, splits.length); return paths;
    }
}
