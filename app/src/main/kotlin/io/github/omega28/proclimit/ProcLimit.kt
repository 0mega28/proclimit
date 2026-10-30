package io.github.omega28.proclimit

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.text.format.DateFormat
import java.lang.reflect.InvocationTargetException
import java.util.Date

/**
 * Calls the hidden IActivityManager.setProcessLimit / getProcessLimit via plain reflection.
 * Values: -1 = Standard, 0 = no background processes, 1..4 = at most N.
 */
object ProcLimit {
    const val PERMISSION = "android.permission.SET_PROCESS_LIMIT"
    const val TARGET_LIMIT = 4
    const val STANDARD = -1

    private const val PREFS = "proclimit"
    private const val KEY_BOOT_OK = "boot_ok"
    private const val KEY_BOOT_MESSAGE = "boot_message"
    private const val KEY_BOOT_TIME = "boot_time"

    data class Result(val ok: Boolean, val message: String)

    data class BootRecord(val ok: Boolean, val message: String, val time: Long)

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun grantCommand(context: Context): String = "adb shell pm grant ${context.packageName} $PERMISSION"

    fun getLimit(): Int = invoke("getProcessLimit") as Int

    fun setLimit(limit: Int) {
        invoke("setProcessLimit", limit)
    }

    /** Sets the limit and reads it back. */
    fun apply(limit: Int): Result = try {
        setLimit(limit)
        val now = getLimit()
        if (now == limit) Result(true, "Set to ${describe(limit).lowercase()}")
        else Result(false, "Asked for ${describe(limit).lowercase()}, but it's ${describe(now).lowercase()}")
    } catch (t: Throwable) {
        Result(false, t.toString())
    }

    fun describe(limit: Int): String = when {
        limit < 0 -> "Standard limit"
        limit == 0 -> "No background processes"
        limit == 1 -> "At most 1 process"
        else -> "At most $limit processes"
    }

    /** Stores the boot result and returns its timestamp. */
    fun saveBootRecord(context: Context, result: Result): Long {
        val time = System.currentTimeMillis()
        prefs(context).edit()
            .putBoolean(KEY_BOOT_OK, result.ok)
            .putString(KEY_BOOT_MESSAGE, result.message)
            .putLong(KEY_BOOT_TIME, time)
            .commit()
        return time
    }

    fun bootRecord(context: Context): BootRecord? {
        val prefs = prefs(context)
        val message = prefs.getString(KEY_BOOT_MESSAGE, null) ?: return null
        return BootRecord(prefs.getBoolean(KEY_BOOT_OK, false), message, prefs.getLong(KEY_BOOT_TIME, 0))
    }

    fun formatTime(context: Context, time: Long): String {
        val date = Date(time)
        return "${DateFormat.getMediumDateFormat(context).format(date)}, ${DateFormat.getTimeFormat(context).format(date)}"
    }

    /** Wall-clock time of the current boot. */
    fun bootTime(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    // Device-protected storage: readable at LOCKED_BOOT_COMPLETED, before the user unlocks.
    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reflective call on ActivityManager.getService(); unwraps InvocationTargetException to report the real cause. */
    private fun invoke(name: String, vararg args: Int): Any? {
        val service = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
        val types = Array<Class<*>>(args.size) { Int::class.javaPrimitiveType!! }
        val method = Class.forName("android.app.IActivityManager").getMethod(name, *types)
        try {
            return method.invoke(service, *args.toTypedArray())
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        }
    }
}
