package io.github.omega28.proclimit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Applies the limit on LOCKED_BOOT_COMPLETED (early, before unlock) and again on BOOT_COMPLETED (after unlock,
 * possibly minutes later). The first success of a boot is recorded and notified; the toast waits for
 * BOOT_COMPLETED since nothing can be seen before unlock.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val locked = when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> true
            Intent.ACTION_BOOT_COMPLETED -> false
            else -> return
        }

        val result = if (ProcLimit.hasPermission(context)) {
            ProcLimit.apply(ProcLimit.TARGET_LIMIT)
        } else {
            ProcLimit.Result(false, "Permission ${ProcLimit.PERMISSION} not granted")
        }
        Log.i(TAG, "${intent.action}: ${if (result.ok) "OK" else "FAILED"}: ${result.message}")

        val earlier = ProcLimit.bootRecord(context)?.takeIf { it.ok && it.time >= ProcLimit.bootTime() }
        if (earlier == null || !result.ok) {
            val time = ProcLimit.saveBootRecord(context, result)
            StatusNotifier.postBootResult(context, result, time)
        }

        if (!locked) {
            val toast = if (result.ok) "ProcLimit: ${ProcLimit.describe(ProcLimit.TARGET_LIMIT)}"
            else "ProcLimit couldn't set the limit: ${result.message}"
            Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val TAG = "ProcLimit"
    }
}
