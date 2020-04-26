package com.pulseid.behavioralpulseid;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class BackgroundService extends Service implements SensorEventListener {

    public static float light;
    public static float pressure;
    public static float temp;
    public static float hum;
    private BootOrScreenBroadcastReceiver mBootReceiver = null;
    public static NotificationCompat.Builder builder = null;
    public static boolean stoppingAlarm = false; //Esta variable se utiliza en BootOrScreenBroadcastReceiver (está aquí para tener persistencia)
    public static String lastAppInForeground = "";
    public static boolean test; //Boolean variable to select application mode from the UI
    public static String uiParams = "Current collected parameters (last 1m):\n" +
            " - Brighness:\n" +
            " - Sensors:\n" +
            " - Memmory:\n" +
            " - NetworkStats:\n" +
            " - BluetoothStats:\n" +
            " - LockedTime:\n" +
            " - Unlocks:\n" +
            " - App changes:\n" +
            " - Number of apps:\n" +
            " - Apps most used last day:"; //
    public static String debug = "";
    public static boolean stopService = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerSensorsListener(this);
        createNotificationChannel(); //Necessary for Android>8

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        builder = new NotificationCompat.Builder(this, "NOTIFICATION_CHANNEL")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Behavioral PulseID")
                .setContentText("Se está entrenando el algoritmo")
                .setTimeoutAfter(-1)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent)
                .setTimeoutAfter(-1);
        startForeground(1001, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "SERVICE STARTED", Toast.LENGTH_LONG).show();
        mBootReceiver = new BootOrScreenBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mBootReceiver, filter);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBootReceiver != null) {
            unregisterReceiver(mBootReceiver);
            System.out.println("BootAndScreenReceiver unregistered");
        }
        //From Android 8, Android does not allow to have a persistent service,
        // so we need to restart it each time it is closed by the OS
        if (!stopService) {
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction("startService");
            broadcastIntent.setClass(this, BootOrScreenBroadcastReceiver.class);
            this.sendBroadcast(broadcastIntent);
        }
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "NOTIFICATION_CHANNEL";
            String description = "Description";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("NOTIFICATION_CHANNEL", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void registerSensorsListener(Context context) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor ambTmp = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        Sensor light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        Sensor pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        Sensor humidity = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);

        sensorManager.registerListener(this, ambTmp, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, humidity, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (this) {
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                light = event.values[0];
            } else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
                pressure = event.values[0];
            } else if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                temp = event.values[0];
            } else if (event.sensor.getType() == Sensor.TYPE_RELATIVE_HUMIDITY) {
                hum = event.values[0];
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
