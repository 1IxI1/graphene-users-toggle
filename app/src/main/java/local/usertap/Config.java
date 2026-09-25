package local.usertap;

import android.content.Context;
import android.content.SharedPreferences;

final class Config {
    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static String target(Context context, boolean right) {
        return prefs(context).getString(right ? "target_right" : "target_left", "");
    }

    static boolean reserved(String name) {
        // These are actions in the system dialog, not user destinations.
        return name.equalsIgnoreCase("Add user") || name.equalsIgnoreCase("Add guest")
                || name.equalsIgnoreCase("Guest") || name.equalsIgnoreCase("End session")
                || name.equalsIgnoreCase("Manage users");
    }

    static void status(Context context, String value) {
        prefs(context).edit().putString("status", value).apply();
    }
}
