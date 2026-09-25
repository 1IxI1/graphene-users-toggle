package local.usertap;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

public final class UserSwitchService extends AccessibilityService {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String USER_NAME = SYSTEM_UI + ":id/user_name";
    private static final String USER_ITEM = SYSTEM_UI + ":id/user_item";
    private static final String USER_SWITCH = SYSTEM_UI + ":id/multi_user_switch";
    private static final int IDLE = 0, SHADE = 1, MENU = 2;
    private static final long TIMEOUT = 5000;
    static UserSwitchService connected;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<View> zones = new ArrayList<>();
    private final TapSequence[] taps = new TapSequence[2];
    private WindowManager windowManager;
    private boolean registered;
    private int state = IDLE;
    private int lastEdge = -1;
    private String targetName;
    private long startedAt;
    private boolean stepQueued;

    private final Runnable step = () -> {
        stepQueued = false;
        try {
            advance();
        } catch (RuntimeException error) {
            finish("Switcher unavailable: " + error.getClass().getSimpleName(), true);
        }
    };
    private final Runnable timeout = () -> finish("Switcher timed out. Select a user manually if needed.", true);
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                cancel();
                removeZones();
            } else {
                refreshZones();
            }
        }
    };

    @Override protected void onServiceConnected() {
        connected = this;
        windowManager = getSystemService(WindowManager.class);
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        taps[0] = new TapSequence(slop, dp(30));
        taps[1] = new TapSequence(slop, dp(30));
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        registered = true;
        Config.status(this, "Service connected. Triple-tap a configured top screen edge.");
        refreshZones();
    }

    private boolean unlocked() {
        return getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    void refreshZones() {
        removeZones();
        if (windowManager == null || !unlocked() || state != IDLE
                || !Config.prefs(this).getBoolean("enabled", true)) return;
        for (int edge = 0; edge < 2; edge++) {
            final int side = edge;
            String destination = Config.target(this, side == 1);
            if (destination.isEmpty() || Config.reserved(destination)) continue;
            TextView zone = new TextView(this);
            zone.setGravity(Gravity.CENTER);
            zone.setTextSize(11);
            zone.setTextColor(Color.WHITE);
            zone.setContentDescription("Triple-tap to switch to " + destination);
            if (Config.prefs(this).getBoolean("markers", true)) {
                GradientDrawable background = new GradientDrawable();
                background.setColor(side == 0 ? 0x9925537A : 0x99653874);
                background.setCornerRadius(dp(10));
                zone.setBackground(background);
                zone.setText(destination);
            }
            zone.setOnTouchListener((view, event) -> onZoneTouch(side, view, event));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    dp(88), dp(36), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | (side == 0 ? Gravity.LEFT : Gravity.RIGHT);
            params.x = dp(8);
            int resource = getResources().getIdentifier("status_bar_height", "dimen", "android");
            int statusBarHeight = resource == 0 ? dp(48) : getResources().getDimensionPixelSize(resource);
            params.y = Math.max(dp(4), (statusBarHeight - dp(36)) / 2);
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            params.setFitInsetsTypes(0);
            params.setTitle("User Tap " + (side == 0 ? "left" : "right"));
            try {
                windowManager.addView(zone, params);
                zones.add(zone);
            } catch (RuntimeException error) {
                Config.status(this, "Could not display touch zones: " + error.getClass().getSimpleName());
            }
        }
    }

    private boolean onZoneTouch(int side, View view, MotionEvent event) {
        if (!unlocked()) return true;
        TapSequence sequence = taps[side];
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (lastEdge != side) taps[1 - side].reset();
            lastEdge = side;
            sequence.down(event.getRawX(), event.getRawY(), event.getEventTime());
        } else if (action == MotionEvent.ACTION_MOVE) {
            sequence.move(event.getRawX(), event.getRawY());
        } else if (action == MotionEvent.ACTION_UP) {
            int result = sequence.up(event.getRawX(), event.getRawY(), event.getEventTime());
            view.performClick();
            if (result == TapSequence.TRIPLE) switchFromEdge(side == 1);
            else if (result == TapSequence.DRAG_DOWN) performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
            sequence.reset();
        }
        return true;
    }

    void switchFromEdge(boolean right) {
        if (state != IDLE) return;
        if (!unlocked()) {
            Toast.makeText(this, "Unlock the current user first", Toast.LENGTH_SHORT).show();
            return;
        }
        targetName = Config.target(this, right);
        if (targetName.isEmpty() || Config.reserved(targetName)) {
            Toast.makeText(this, "Configure this edge in User Tap first", Toast.LENGTH_SHORT).show();
            return;
        }
        for (TapSequence sequence : taps) sequence.reset();
        startedAt = SystemClock.uptimeMillis();
        state = SHADE;
        removeZones();
        handler.postDelayed(timeout, TIMEOUT);
        Config.status(this, "Opening user switcher");
        if (!performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) {
            finish("Android could not open Quick Settings", true);
            return;
        }
        queueStep(80);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (state != IDLE && SYSTEM_UI.contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) {
            queueStep(40);
        }
    }

    private void queueStep(long delay) {
        if (!stepQueued && state != IDLE) {
            stepQueued = true;
            handler.postDelayed(step, delay);
        }
    }

    private void advance() {
        if (state == IDLE) return;
        if (!unlocked()) {
            cancel();
            removeZones();
            return;
        }
        // Traverse only SystemUI roots, and only after a user initiated this operation.
        // Never click coordinates, arbitrary text, PIN controls or generic confirmation buttons.
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && SYSTEM_UI.contentEquals(active.getPackageName() == null ? "" : active.getPackageName())) roots.add(active);
        for (AccessibilityWindowInfo window : getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && SYSTEM_UI.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())
                    && !roots.contains(root)) roots.add(root);
        }
        int buttons = 0;
        int users = 0;
        for (AccessibilityNodeInfo root : roots) {
            buttons += findById(root, USER_SWITCH).size();
            users += findById(root, USER_NAME).size();
        }
        String diagnostics = "SystemUI roots: " + roots.size() + ", switch buttons: " + buttons + ", user labels: " + users
                + ", flags: " + getServiceInfo().flags;
        if (!diagnostics.equals(Config.prefs(this).getString("diagnostics", ""))) {
            Config.prefs(this).edit().putString("diagnostics", diagnostics).apply();
        }
        List<AccessibilityNodeInfo> matches = new ArrayList<>();
        for (AccessibilityNodeInfo root : roots) {
            List<AccessibilityNodeInfo> labels = findById(root, USER_NAME);
            for (AccessibilityNodeInfo label : labels) {
                if (!label.isVisibleToUser() || label.getText() == null || !targetName.contentEquals(label.getText())) continue;
                AccessibilityNodeInfo item = label.getParent();
                for (int depth = 0; depth < 4 && item != null; depth++, item = item.getParent()) {
                    if (!USER_ITEM.equals(item.getViewIdResourceName())) continue;
                    if (!matches.contains(item)) matches.add(item);
                    break;
                }
            }
        }
        if (matches.size() > 1) {
            finish("More than one user has that name. Use unique user names.", true);
            return;
        }
        if (matches.size() == 1) {
            AccessibilityNodeInfo item = matches.get(0);
            if (!item.isEnabled() || !item.isClickable()) {
                finish("The requested user is already active or unavailable", true);
                return;
            }
            if (item.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                long elapsed = SystemClock.uptimeMillis() - startedAt;
                finish("User selected in " + elapsed + " ms (excluding the system transition)", false);
                return;
            }
        }
        if (state == SHADE) {
            for (AccessibilityNodeInfo root : roots) {
                for (AccessibilityNodeInfo button : findById(root, USER_SWITCH)) {
                    if (button.isVisibleToUser() && button.isEnabled() && button.isClickable()) {
                        // Set state first: a click can synchronously trigger another UI event.
                        state = MENU;
                        if (!button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) state = SHADE;
                        queueStep(80);
                        return;
                    }
                }
            }
        }
        // Short bounded retry covers dropped/coalesced accessibility events and animations.
        queueStep(100);
    }

    private void finish(String message, boolean showToast) {
        if (state == IDLE) return;
        cancel();
        Config.status(this, message);
        if (showToast) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        handler.postDelayed(this::refreshZones, 1200);
    }

    private List<AccessibilityNodeInfo> findById(AccessibilityNodeInfo root, String id) {
        // Virtual Compose nodes may expose IDs without implementing the framework's
        // findAccessibilityNodeInfosByViewId lookup. Walk their accessibility tree.
        List<AccessibilityNodeInfo> found = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
        pending.add(root);
        int count = 0;
        while (!pending.isEmpty() && count < 2000) {
            AccessibilityNodeInfo node = pending.removeFirst();
            count++;
            if (!SYSTEM_UI.contentEquals(node.getPackageName() == null ? "" : node.getPackageName())) continue;
            if (id.equals(node.getViewIdResourceName())) found.add(node);
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) pending.addLast(child);
            }
        }
        return found;
    }

    private void cancel() {
        state = IDLE;
        stepQueued = false;
        handler.removeCallbacks(step);
        handler.removeCallbacks(timeout);
    }

    private void removeZones() {
        if (windowManager != null) for (View view : zones) {
            try { windowManager.removeViewImmediate(view); }
            catch (IllegalArgumentException ignored) { }
        }
        zones.clear();
        for (TapSequence sequence : taps) if (sequence != null) sequence.reset();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        refreshZones();
    }

    @Override public void onInterrupt() {
        cancel();
        refreshZones();
    }

    @Override public void onDestroy() {
        connected = null;
        cancel();
        handler.removeCallbacksAndMessages(null);
        removeZones();
        if (registered) unregisterReceiver(screenReceiver);
        super.onDestroy();
    }
}
