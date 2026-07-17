package ca.pkay.rcloneexplorer.BroadcastReceivers;

import static ca.pkay.rcloneexplorer.workmanager.SyncWorker.EXTRA_TASK_ID;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ca.pkay.rcloneexplorer.workmanager.SyncManager;
import ca.pkay.rcloneexplorer.workmanager.SyncOperation;
import ca.pkay.rcloneexplorer.workmanager.SyncWorker;

/**
 * This class requires a receiver declaration in the manifest
 */
public class SyncRestartAction extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SyncManager sm = new SyncManager(context);
        long taskId = intent.getLongExtra(EXTRA_TASK_ID, -1);
        String operationName = intent.getStringExtra(SyncWorker.TASK_OPERATION);
        if (operationName == null) {
            sm.queue(taskId);
            return;
        }
        try {
            sm.queue(taskId, SyncOperation.valueOf(operationName));
        } catch (IllegalArgumentException ignored) {
            sm.queue(taskId);
        }
    }
}
