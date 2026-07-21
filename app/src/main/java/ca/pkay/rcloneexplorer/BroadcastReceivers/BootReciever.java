package ca.pkay.rcloneexplorer.BroadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ca.pkay.rcloneexplorer.Services.TriggerService;

public class BootReciever extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        switch (intent.getAction()) {
            // Exact alarms are cleared by the system on reboot, app update, and clock/timezone
            // changes. Re-arm every trigger so scheduled backups survive all of these events.
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
                break;
            default:
                return;
        }

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                new TriggerService(appContext).queueTrigger();
            } finally {
                pendingResult.finish();
            }
        }).start();
    }
}
