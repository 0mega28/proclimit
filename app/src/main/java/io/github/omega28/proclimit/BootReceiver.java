package io.github.omega28.proclimit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "ProcLimit";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        String result = ProcLimit.hasPermission(context)
                ? ProcLimit.apply(ProcLimit.TARGET_LIMIT)
                : "SKIPPED: " + ProcLimit.PERMISSION + " not granted";
        Log.i(TAG, "boot: " + result);
        ProcLimit.saveBootResult(context, result);
    }
}
