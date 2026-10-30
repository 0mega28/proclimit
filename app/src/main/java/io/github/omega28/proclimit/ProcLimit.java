package io.github.omega28.proclimit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.util.Date;

/**
 * Calls the hidden IActivityManager.setProcessLimit / getProcessLimit via plain reflection.
 * Values: -1 = Standard, 0 = no background processes, 1..4 = at most N.
 */
final class ProcLimit {
    static final String PERMISSION = "android.permission.SET_PROCESS_LIMIT";
    static final int TARGET_LIMIT = 4;
    static final int STANDARD = -1;

    private static final String PREFS = "proclimit";
    private static final String KEY_BOOT_RESULT = "boot_result";

    private ProcLimit() {}

    static boolean hasPermission(Context context) {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    static String grantCommand(Context context) {
        return "adb shell pm grant " + context.getPackageName() + " " + PERMISSION;
    }

    static int getLimit() throws Exception {
        Object am = activityManager();
        Method m = Class.forName("android.app.IActivityManager").getMethod("getProcessLimit");
        return (Integer) invoke(m, am);
    }

    static void setLimit(int limit) throws Exception {
        Object am = activityManager();
        Method m = Class.forName("android.app.IActivityManager").getMethod("setProcessLimit", int.class);
        invoke(m, am, limit);
    }

    /** Sets the limit, reads it back, and returns a one-line human-readable result. */
    static String apply(int limit) {
        try {
            setLimit(limit);
            int now = getLimit();
            return (now == limit ? "OK" : "MISMATCH") + ": set " + describe(limit) + ", now " + describe(now);
        } catch (Throwable t) {
            return "FAILED: " + t;
        }
    }

    static String describe(int limit) {
        if (limit < 0) return "Standard (" + limit + ")";
        if (limit == 0) return "No background processes (0)";
        return "At most " + limit + " (" + limit + ")";
    }

    static void saveBootResult(Context context, String result) {
        String stamped = DateFormat.getDateTimeInstance().format(new Date()) + " — " + result;
        prefs(context).edit().putString(KEY_BOOT_RESULT, stamped).commit();
    }

    static String bootResult(Context context) {
        return prefs(context).getString(KEY_BOOT_RESULT, "No boot recorded yet");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Object activityManager() throws Exception {
        Method getService = Class.forName("android.app.ActivityManager").getMethod("getService");
        return getService.invoke(null);
    }

    /** Unwraps InvocationTargetException so the real cause (e.g. SecurityException) is reported. */
    private static Object invoke(Method m, Object target, Object... args) throws Exception {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }
}
