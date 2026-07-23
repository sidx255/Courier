package ca.pkay.rcloneexplorer.Services;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.util.Calendar;
import java.util.TimeZone;

import ca.pkay.rcloneexplorer.BroadcastReceivers.TriggerReciever;
import ca.pkay.rcloneexplorer.Database.DatabaseHandler;
import ca.pkay.rcloneexplorer.Items.Trigger;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.guided.GuidedSchedule;
import ca.pkay.rcloneexplorer.notifications.AppErrorNotificationManager;
import ca.pkay.rcloneexplorer.util.PermissionManager;
import ca.pkay.rcloneexplorer.util.SyncLog;
import ca.pkay.rcloneexplorer.workmanager.SyncManager;

public class TriggerService extends Service {

    private DatabaseHandler dbHandler;
    private Context context;

    public static String TRIGGER_RECIEVE = "TRIGGER_RECIEVE";
    public static String TRIGGER_ID = "TRIGGER_ID";

    public static String CHANNEL_ID = "CHANNEL_ID";
    public static int SERVICE_NOTIFICATION_ID = 42;

    //Required for Servicecall
    public TriggerService() {}

    public TriggerService(Context c) {
        this.dbHandler = new DatabaseHandler(c);
        this.context = c;
    }

    public void queueTrigger(){
        for(Trigger t : dbHandler.getAllTrigger()){
            queueSingleTrigger(t);
        }
    }

    public void queueSingleTrigger(Trigger trigger){
        if(trigger.getType() == Trigger.TRIGGER_TYPE_SCHEDULE) {
            queueSingleScheduleTrigger(trigger);
        } else {
            queueSingleIntervalTrigger(trigger);
        }

    }

    // Sets a single exact wake alarm, cancelling any previous one for the same PendingIntent.
    // Shared by schedule and interval triggers so both fire reliably (including in Doze).
    @SuppressLint("ScheduleExactAlarm") // permission is enforced via PermissionManager below
    private void scheduleExactAlarm(long timeToTrigger, PendingIntent pi){
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);

        if(!(new PermissionManager(context)).grantedAlarms()){
            new AppErrorNotificationManager(context).showNotification();
            return;
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean allowWhileIdle = sharedPreferences.getBoolean(context.getString(R.string.shared_preferences_allow_sync_trigger_while_idle), false);

        if (allowWhileIdle) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeToTrigger, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, timeToTrigger, pi);
        }
    }

    private void queueSingleScheduleTrigger(Trigger trigger){
        Long nextRunTime = GuidedSchedule.INSTANCE.nextRunTime(
                trigger,
                System.currentTimeMillis(),
            TimeZone.getDefault()
        );
        if(nextRunTime != null){
            scheduleExactAlarm(nextRunTime, getIntent(trigger.getId()));
        } else {
            cancelTrigger(trigger.getId());
        }
    }

    private void queueSingleIntervalTrigger(Trigger trigger){
        if(trigger.isEnabled()){
            // One-shot exact alarm, re-armed after every fire (see handleReceivedTrigger) and on
            // boot / app-update / time-change. setInexactRepeating is batched away by Doze, so a
            // background interval backup would silently stop firing while the device is idle.
            long intervalMillis = (long) trigger.getTime() * 60 * 1000;
            long timeToTrigger = System.currentTimeMillis() + intervalMillis;
            scheduleExactAlarm(timeToTrigger, getIntent(trigger.getId()));
        } else {
            cancelTrigger(trigger.getId());
        }
    }

    public void cancelTrigger(long triggerID){
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getIntent(triggerID));
    }

    private void startTask(Trigger trigger){
        boolean skipBecauseOfWeekday;
        //account for monday beeing 1 and sunday beeing 0. Therefor we need to offset by 2
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)-2;

        //check for sundays. Calendar starts with sunday.
        if(day==-1){
            skipBecauseOfWeekday = !trigger.isEnabledAtDay(6);
        }else{
            skipBecauseOfWeekday = !trigger.isEnabledAtDay(day);
        }

        if(skipBecauseOfWeekday){
            SyncLog.info(context, trigger.getTitle(),
                    "Trigger fired but skipped: today is not an enabled weekday for this trigger.");
            return;
        }

        SyncManager sm = new SyncManager(this.context);
        sm.queue(trigger);
    }

    private PendingIntent getIntent(long triggerId){
        Intent i = new Intent(context, TriggerReciever.class);
        i.setAction(TRIGGER_RECIEVE);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(TRIGGER_ID, triggerId);

        // Todo: Beacause of the long to int cast, this may fail when the user has more than Integer.MAX tasks.
        return PendingIntent.getBroadcast(context, (int) triggerId, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public void handleReceivedTrigger(long id) {
        Trigger t = dbHandler.getTrigger(id);
        // this can happen if the trigger was scheduled, but then deleted.
        if (t == null) {
            return;
        }
        startTask(t);
        queueSingleTrigger(t);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotification();
        if (intent == null) {
            stopForeground(true);
            return Service.START_NOT_STICKY;
        }
        long id = intent.getLongExtra(TRIGGER_ID, -1);
        this.dbHandler = new DatabaseHandler(getBaseContext());
        this.context = getBaseContext();
        Trigger t = dbHandler.getTrigger(id);

        // this can happen if the trigger was scheduled, but then deleted.
        if(t == null) {
            stopForeground(true);
            return Service.START_NOT_STICKY;
        }

        startTask(t);
        queueSingleTrigger(t);
        stopForeground(true);
        return Service.START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotification(){
        createNotificationChannel();
        Notification notification;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle(getText(R.string.notification_triggerservice_title))
                    .setContentText(getText(R.string.notification_triggerservice_description))
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build();
        } else {
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                    .setContentTitle(getText(R.string.notification_triggerservice_title))
                    .setContentText(getText(R.string.notification_triggerservice_description))
                    .setSmallIcon(R.drawable.ic_launcher_foreground);
            notification = notificationBuilder.build();
        }
        startForeground(SERVICE_NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_triggerservice_title),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_triggerservice_description));

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

}
