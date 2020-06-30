package com.pulseid.behavioralpulseid;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
    private BootOrScreenBR mBootReceiver = null;
    //Public variables stored on service to ensure persistence
    public static NotificationCompat.Builder builder = null;
    public static boolean stoppingAlarm = false;
    public static String lastAppInForeground = "";
    public static int connectedDevices = 0;

    private SharedPreferences pref;

    //BroadcastReceiver that monitors Bluetooth information on real-time
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                connectedDevices++;
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                connectedDevices--;
            }
            System.out.println("Devices connected: "+connectedDevices);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        pref = getApplicationContext().getSharedPreferences("pulseidpreferences", MODE_PRIVATE); // 0 - for private mode

        registerSensorsListener(this);
        //Creation of notification channel and persistent notification that must be shown while service is running
        createNotificationChannel(); //Necessary for Android>8
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        builder = new NotificationCompat.Builder(this, "NOTIFICATION_CHANNEL")
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle("Behavioral PulseID")
                .setContentText("Initiating service...")
                .setTimeoutAfter(-1)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent);
        startForeground(1001, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Configuration of filters. They are BroadcastReceivers. One for persistence of the
        // application and other for Bluetooth monitoring
        Toast.makeText(this, "SERVICE STARTED", Toast.LENGTH_SHORT).show();
        mBootReceiver = new BootOrScreenBR();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mBootReceiver, filter);

        IntentFilter bluetoothFilter = new IntentFilter();
        bluetoothFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        bluetoothFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        bluetoothFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mReceiver, bluetoothFilter);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        //When service is killed, unregisters both filters and starts the service again
        super.onDestroy();
        if (mBootReceiver != null) {
            unregisterReceiver(mBootReceiver);
        }
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        //From Android 8, Android does not allow to have a persistent service,
        // so we need to restart it each time it is closed by the OS
        if (!pref.getBoolean("stop_service",false)) {
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction("startService");
            broadcastIntent.setClass(this, BootOrScreenBR.class);
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
        Sensor light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        /*Sensor ambTmp = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        Sensor pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        Sensor humidity = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);*/

        sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
        /*sensorManager.registerListener(this, ambTmp, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, humidity, SensorManager.SENSOR_DELAY_NORMAL);*/
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (this) {
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                light = event.values[0];
            }/* else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
                pressure = event.values[0];
            } else if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                temp = event.values[0];
            } else if (event.sensor.getType() == Sensor.TYPE_RELATIVE_HUMIDITY) {
                hum = event.values[0];
            }*/
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
