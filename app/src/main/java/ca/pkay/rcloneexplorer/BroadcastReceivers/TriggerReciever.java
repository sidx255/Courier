package ca.pkay.rcloneexplorer.BroadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ca.pkay.rcloneexplorer.Services.TriggerService;
import ca.pkay.rcloneexplorer.util.FLog;

public class TriggerReciever extends BroadcastReceiver {

    private static final String TAG = "TriggerReciever";

    @Override
    public void onReceive(Context context, Intent intent) {
        FLog.e(TAG, "Recieved Intent");

        if (intent == null || !TriggerService.TRIGGER_RECIEVE.equals(intent.getAction())) {
            return;
        }
        long id = intent.getLongExtra(TriggerService.TRIGGER_ID, -1);
        FLog.e(TAG, "Start Trigger: " + id);
        if (id == -1) {
            return;
        }

        // Dispatch the trigger without a dataSync foreground service: Android 15 caps
        // dataSync FGS runtime per day and refuses further starts once exhausted, which
        // would crash the dispatch. Enqueuing the sync + rescheduling is quick work, so
        // run it directly off the main thread via goAsync().
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                new TriggerService(appContext).handleReceivedTrigger(id);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

}
