package io.github.omega28.proclimit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val result = if (ProcLimit.hasPermission(context)) {
            ProcLimit.apply(ProcLimit.TARGET_LIMIT)
        } else {
            ProcLimit.Result(false, "Permission ${ProcLimit.PERMISSION} not granted")
        }
        Log.i(TAG, "boot: ${if (result.ok) "OK" else "FAILED"}: ${result.message}")
        val time = ProcLimit.saveBootRecord(context, result)
        StatusNotifier.postBootResult(context, result, time)

        val toast = if (result.ok) "ProcLimit: ${ProcLimit.describe(ProcLimit.TARGET_LIMIT)}"
        else "ProcLimit couldn't set the limit: ${result.message}"
        Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val TAG = "ProcLimit"
    }
}
