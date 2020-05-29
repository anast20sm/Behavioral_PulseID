package com.pulseid.behavioralpulseid;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;

import static android.content.Context.MODE_PRIVATE;

public class BootOrScreenBroadcastReceiver extends BroadcastReceiver {
    public static long startTimer;
    public static long counter = 0;
    public static long screenOffTime = 0;
    private static AlarmManager alarmManager;
    private static PendingIntent pendingIntent;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    @Override
    public void onReceive(Context context, Intent intent) {
        pref = context.getSharedPreferences("pulseidpreferences", MODE_PRIVATE);
        editor = pref.edit();

        Intent backgroundService = new Intent(context, BackgroundService.class);
        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //From Android 8, Android does not allow to have a persistent background services,
                // so we need to use foreground service (that uses a persistent notification)
                context.startForegroundService(backgroundService);
                startAlarm(context);
            } else {
                context.startService(backgroundService);
                startAlarm(context);
            }
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            stopAlarm();
            startTimer = System.currentTimeMillis();
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            startAlarm(context);
            long endTimer = System.currentTimeMillis();
            screenOffTime += (endTimer - startTimer);
            counter++;
        } else if (intent.getAction().equals("startService")) {
            editor.putBoolean("stop_service", false).commit();
            startAlarm(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(backgroundService);
            } else {
                context.startService(backgroundService);
            }
        } else if (intent.getAction().equals("stopService")) {
            MainActivity.debugView.setText(pref.getString("debug_text",null));
            cancelAlarm();
            context.stopService(backgroundService);
            editor.putBoolean("stop_service", true).commit();
        }

    }

    private static void stopAlarm() {
        BackgroundService.stoppingAlarm = true; //Indicate that alarm will be cancelled (on BackgroundService to ensure persistance between calls to this class)
        AsyncTask asyncTask = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                try {
                    Thread.sleep(60000); //Start asynchron counter to 1 minute (def interval)
                    if (BackgroundService.stoppingAlarm) { //If after this time boolean changed, it means device has been unlocked so there is no need to cancel AlarmManager
                        if (pendingIntent != null) {
                            alarmManager.cancel(pendingIntent);
                            pendingIntent.cancel();
                            alarmManager = null;
                            BackgroundService.stoppingAlarm = false;
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        asyncTask.execute();
    }
    private void cancelAlarm() {
        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            alarmManager = null;
            System.out.println("Alarm forced to stop");
        }
    }

    private void startAlarm(Context context) {
        if (BackgroundService.stoppingAlarm) { //If device was locked on last minute, cancel the removal of the alarm
            BackgroundService.stoppingAlarm = false;
        } else if (alarmManager == null) {
            alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent alarmIntent = new Intent(context, AlarmBroadcastReceiver.class);
            pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, 0);
            alarmManager.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis(), 1000 /*ms*/ * 60 /*s*/, pendingIntent);
        }

    }
}
