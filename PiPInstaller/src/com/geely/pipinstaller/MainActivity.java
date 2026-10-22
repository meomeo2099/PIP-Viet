package com.geely.pipinstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public final class MainActivity extends Activity {
    private static final int BG = 0xff0c1424, CARD = 0xff152238, TEXT = 0xfff1f5fc,
            MUTED = 0xffb2c2d9, ACCENT = 0xff63d9c2;
    private final Handler handler = new Handler();
    private TextView badge, description;
    private Button enable, disable;
    private ProgressBar progress;
    private final Runnable render = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, 700); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout outer = column(24, 24);
        outer.setGravity(Gravity.CENTER);
        LinearLayout card = column(28, 28);
        card.setBackground(background(CARD, 24));
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(48), dp(960));
        outer.addView(card, new LinearLayout.LayoutParams(width, -2));

        TextView brand = text("THT  /  TIỆN ÍCH MÀN HÌNH", 13, ACCENT, true);
        brand.setLetterSpacing(.1f); card.addView(brand);
        TextView title = text("PIP Việt", 32, TEXT, true);
        title.setPadding(0, dp(10), 0, dp(6)); card.addView(title);
        card.addView(text("Điều khiển cửa sổ video nổi trên màn hình xe", 18, MUTED, false));

        LinearLayout stateCard = column(20, 18);
        stateCard.setBackground(background(0xff0e1a2c, 16));
        LinearLayout.LayoutParams statusLayout = new LinearLayout.LayoutParams(-1, -2);
        statusLayout.setMargins(0, dp(24), 0, dp(16)); card.addView(stateCard, statusLayout);
        badge = text("ĐANG KIỂM TRA", 15, ACCENT, true); stateCard.addView(badge);
        description = text("Đang đọc trạng thái màn hình…", 18, TEXT, false);
        description.setPadding(0, dp(10), 0, 0);
        description.setMinHeight(dp(64)); stateCard.addView(description);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        stateCard.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));

        String device = Build.MANUFACTURER + " · " + Build.MODEL + " · Android " + Build.VERSION.RELEASE;
        TextView deviceLabel = text(device, 14, MUTED, false);
        deviceLabel.setPadding(0, 0, 0, dp(16)); card.addView(deviceLabel);
        LinearLayout buttons = new LinearLayout(this);
        boolean narrow = getResources().getConfiguration().screenWidthDp < 600;
        buttons.setOrientation(narrow ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        enable = button("Bật PiP", ACCENT, 0xff092c2b);
        disable = button("Tắt PiP / Khôi phục", 0xff2a3c56, TEXT);
        LinearLayout.LayoutParams first = narrow ? new LinearLayout.LayoutParams(-1, dp(64))
                : new LinearLayout.LayoutParams(0, dp(64), 1);
        first.setMargins(0, 0, narrow ? 0 : dp(12), narrow ? dp(12) : 0);
        buttons.addView(enable, first);
        buttons.addView(disable, narrow ? new LinearLayout.LayoutParams(-1, dp(64))
                : new LinearLayout.LayoutParams(0, dp(64), 1));
        card.addView(buttons);
        TextView note = text("Thực hiện khi xe đang đỗ. Giao diện hệ thống sẽ khởi động lại trong chốc lát khi áp dụng.", 14, MUTED, false);
        note.setPadding(0, dp(18), 0, dp(10)); card.addView(note);
        Button help = button("Hướng dẫn sử dụng", 0xff1b2c45, MUTED);
        help.setTextSize(15); card.addView(help, new LinearLayout.LayoutParams(-1, dp(52)));
        help.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("Sử dụng PIP Việt")
                .setMessage("1. Đợi kiểm tra màn hình hoàn tất.\n\n2. Chọn Bật PiP, đợi giao diện hệ thống khởi động lại.\n\n3. Mở ứng dụng có hỗ trợ PiP và thử kéo, chạm, phóng to cửa sổ.\n\n4. Muốn trở về ban đầu, chọn Tắt PiP / Khôi phục trước khi gỡ ứng dụng.\n\nHiện hỗ trợ Android 9 với cấu hình SystemUI phù hợp. Màn hình cần chữ ký và quyền hệ thống tương thích. ROM có dịch vụ riêng cần kiểm tra và làm overlay riêng.")
                .setPositiveButton("Đã hiểu", null).show());
        TextView footer = text("PIP Việt 1.0.0  ·  THT", 12, MUTED, false);
        footer.setPadding(0, dp(16), 0, 0); card.addView(footer);
        scroll.addView(outer); setContentView(scroll);
        enable.setOnClickListener(view -> act(PipService.ENABLE));
        disable.setOnClickListener(view -> act(PipService.DISABLE));
        update();
    }

    private void act(String action) {
        enable.setEnabled(false); disable.setEnabled(false);
        PipService.launch(this, action);
    }
    @Override protected void onResume() {
        super.onResume();
        if (!PipService.state(this).getBoolean("pending", false)) PipService.launch(this, PipService.REFRESH);
        handler.post(render);
    }
    @Override protected void onPause() { handler.removeCallbacks(render); super.onPause(); }
    private void update() {
        SharedPreferences s = PipService.state(this);
        boolean busy = s.getBoolean("busy", true), enabled = s.getBoolean("enabled", false);
        boolean compatible = s.getBoolean("compatible", false), recovery = s.getBoolean("rollback", false);
        badge.setText(busy ? "ĐANG XỬ LÝ" : recovery ? "CẦN KHÔI PHỤC" : enabled ? "PIP ĐANG BẬT" : compatible ? "SẴN SÀNG BẬT PIP" : "CHƯA TƯƠNG THÍCH");
        badge.setTextColor(recovery ? 0xffffbd72 : enabled || compatible || busy ? ACCENT : 0xffffbd72);
        description.setText(s.getString("message", "Đang kiểm tra màn hình…"));
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        enable.setEnabled(!busy && compatible && !enabled && !recovery);
        disable.setEnabled(!busy && s.getBoolean("privileged", false) && (enabled || recovery));
        enable.setAlpha(enable.isEnabled() ? 1f : .4f); disable.setAlpha(disable.isEnabled() ? 1f : .4f);
    }
    private LinearLayout column(int horizontal, int vertical) {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(horizontal), dp(vertical), dp(horizontal), dp(vertical)); return view;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private Button button(String label, int background, int foreground) {
        Button view = new Button(this); view.setText(label); view.setTextSize(18); view.setAllCaps(false);
        view.setPadding(dp(12), dp(6), dp(12), dp(6));
        view.setTextColor(foreground); view.setBackground(background(background, 14)); return view;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
