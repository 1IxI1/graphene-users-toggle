package local.usertap;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView status;
    private final EditText[] targets = new EditText[2];
    private LinearLayout body;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(16), dp(24), dp(24));
        scroll.addView(body);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        text("User Tap", 30);
        text("Triple-tap a top screen edge to switch users", 17);
        status = text("", 14);
        text("Settings apply only to the current Android user. Enter exact, unique names from the system user switcher. Leave an edge blank to disable it.", 14);
        for (int i = 0; i < targets.length; i++) {
            text(i == 0 ? "Left edge target" : "Right edge target", 16);
            targets[i] = new EditText(this);
            targets[i].setSingleLine(true);
            targets[i].setHint("User name");
            targets[i].setText(Config.target(this, i == 1));
            body.addView(targets[i]);
        }
        button("Save targets", view -> save());
        toggle("Enable touch zones", "enabled", true);
        toggle("Show touch zones", "markers", true);
        button("Open accessibility settings", view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button("Test left edge", view -> test(false));
        button("Test right edge", view -> test(true));
        button("Refresh status", view -> updateStatus());
        text("Tap the same top corner three times. Single and double taps inside a zone are consumed. Swiping down from a zone opens notifications. You can hide the colored markers.", 14);
        text("Install, configure, and enable the accessibility service separately in each Android user. The setting survives restarts; the service reconnects after unlocking that user. No ADB or Shizuku is needed during normal use.", 14);
        text("Accessibility is a broad system capability. This app inspects the SystemUI switcher only after your request. No network, analytics, screen recording, or credential entry. Switching does not end the previous user's session.", 14);
        updateStatus();
    }

    private boolean save() {
        String[] values = new String[2];
        for (int i = 0; i < 2; i++) {
            values[i] = targets[i].getText().toString().trim();
            if (Config.reserved(values[i])) {
                Toast.makeText(this, "Enter a user name, not a switcher action", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        SharedPreferences.Editor editor = Config.prefs(this).edit();
        editor.putString("target_left", values[0]);
        editor.putString("target_right", values[1]);
        editor.apply();
        if (UserSwitchService.connected != null) UserSwitchService.connected.refreshZones();
        updateStatus();
        return true;
    }

    private void test(boolean right) {
        if (!save()) return;
        if (UserSwitchService.connected == null) {
            Toast.makeText(this, "Enable the User Tap accessibility service first", Toast.LENGTH_LONG).show();
        } else {
            UserSwitchService.connected.switchFromEdge(right);
        }
    }

    private void toggle(String title, String key, boolean defaultValue) {
        Switch control = new Switch(this);
        control.setText(title);
        control.setPadding(0, dp(14), 0, dp(14));
        control.setChecked(Config.prefs(this).getBoolean(key, defaultValue));
        control.setOnCheckedChangeListener((button, checked) -> {
            Config.prefs(this).edit().putBoolean(key, checked).apply();
            if (UserSwitchService.connected != null) UserSwitchService.connected.refreshZones();
        });
        body.addView(control);
    }

    private void updateStatus() {
        if (status != null) status.setText((UserSwitchService.connected == null ? "Service disconnected" : "Service connected")
                + "\n" + Config.prefs(this).getString("status", "Enable User Tap in accessibility settings.")
                + "\n" + Config.prefs(this).getString("diagnostics", ""));
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, dp(10), 0, dp(10));
        body.addView(view);
        return view;
    }

    private void button(String title, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        body.addView(button);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
