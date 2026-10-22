package com.geely.pipinstaller;

import java.io.IOException;
import java.util.Arrays;

public final class CompatibilityPolicyTest {
    private static final String[] BASE = {
        "com.android.systemui.Dependency",
        "com.android.systemui.statusbar.CommandQueue$CommandQueueStart",
        "com.android.systemui.keyguard.KeyguardViewMediator",
        "com.android.systemui.SystemBars"
    };
    private static void reject(String[] services) throws Exception {
        try { CompatibilityPolicy.checkComponents(services); }
        catch (IOException expected) { return; }
        throw new AssertionError("An incompatible ROM was accepted: " + Arrays.toString(services));
    }
    public static void main(String[] args) throws Exception {
        CompatibilityPolicy.checkComponents(BASE);
        String[] vendor = Arrays.copyOf(BASE, 5); vendor[4] = "com.vendor.CarStatusBar";
        reject(vendor); // Must not silently remove the car's status bar.
        String[] nativePip = Arrays.copyOf(BASE, 5); nativePip[4] = "com.android.systemui.pip.PipUI";
        reject(nativePip); // A ROM that already starts PipUI needs no fixed overlay.
        reject(new String[0]);
        reject(Arrays.copyOf(BASE, 3));
        String[] reordered = BASE.clone(); reordered[0] = BASE[1]; reordered[1] = BASE[0]; reject(reordered);
        String[] replacement = BASE.clone(); replacement[3] = "com.vendor.SystemBars"; reject(replacement);
        System.out.println("PASS: compatible profile, vendor services, native PiP, empty, missing, order and replacement");
    }
}
