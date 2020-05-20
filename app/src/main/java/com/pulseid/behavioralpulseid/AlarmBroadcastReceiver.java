package com.pulseid.behavioralpulseid;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.MODE_PRIVATE;

public class AlarmBroadcastReceiver extends BroadcastReceiver {
    private int appsLastInterval = 1;
    SharedPreferences pref;
    SharedPreferences.Editor editor;

    public void retrieveData(Context context) {
        int brighness = getBrightness(context);
        int orientation = getScreenOrientation(context);
        float[] sensors = getSensorValues(); //They are light,pressure, temperature and humidity respectively
        double[] memmory = getMemoryUsage(context); //They are availableMegs and percentAvailable respectively
        long[] networkStats = getNetworkStats(context); //They are txMB and rxMB respectively
        long bluetoothStats = getBluetoothStats(); //The sum of both tx and rx
        System.out.println("Bluetooth results is: "+bluetoothStats);
        //long unlocks = BootOrScreenBroadcastReceiver.counter;
        long locks = BootOrScreenBroadcastReceiver.counter;//EXTRA
        //long lockTime = BootOrScreenBroadcastReceiver.screenOffTime;
        long unlockTime;
        if (!BootOrScreenBroadcastReceiver.screenLocked){
            unlockTime = BootOrScreenBroadcastReceiver.screenOnTime + (System.currentTimeMillis() - BootOrScreenBroadcastReceiver.startTimer);//EXTRA
            BootOrScreenBroadcastReceiver.startTimer = System.currentTimeMillis();//EXTRA
        } else {
            unlockTime = BootOrScreenBroadcastReceiver.screenOnTime;//EXTRA
        }
        if (unlockTime>80000)//This is to avoid too large value after a reboot (due startTimer is not initialized)
            unlockTime=60000;
        System.out.println("ScreenOnTime: "+BootOrScreenBroadcastReceiver.screenOnTime+"\n" +
                "UnlockTime: "+unlockTime+"\n" +
                "Locks: "+locks);
        BootOrScreenBroadcastReceiver.screenOnTime = 0;//EXTRA

        BootOrScreenBroadcastReceiver.counter = 0;
        BootOrScreenBroadcastReceiver.screenOffTime = 0;
        String[] pausedToResumed = getTopPkgChange(context); //They are firstPaused and lastResumed respectively
        String[] mostUsedLastDay = mostUsedAppLastDay(context);


        try {
            //Creamos el objeto mlDataCollector (que usa weka) y le enviamos los datos que queremos registrar o probar contra el perfil
            MlDataCollector mlDataCollector = new MlDataCollector(context);
            if (!pref.getBoolean("train",false) && pref.getBoolean("test",false)){
                //double[] corr = mlDataCollector.test(brighness,orientation, sensors, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastInterval, mostUsedLastDay);
                double[] corr = mlDataCollector.test(brighness,orientation, sensors, memmory, networkStats, bluetoothStats, unlockTime, locks, pausedToResumed, appsLastInterval, mostUsedLastDay);//EXTRA
                updateConfidence(context, corr[0], corr[1]);
                //Toast.makeText(context, "Test", Toast.LENGTH_SHORT).show();
            }else if (pref.getBoolean("train",false) && !pref.getBoolean("test",false)){
                //mlDataCollector.train(brighness, orientation, sensors, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastInterval, mostUsedLastDay);
                mlDataCollector.train(brighness, orientation, sensors, memmory, networkStats, bluetoothStats, unlockTime, locks, pausedToResumed, appsLastInterval, mostUsedLastDay);//EXTRA
                BackgroundService.builder.setContentText("Entrenando el modelo...");
                BackgroundService.builder.setContentTitle("Behavioral PulseID (Training)");
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.notify(1001, BackgroundService.builder.build());
                //Toast.makeText(context, "Train", Toast.LENGTH_SHORT).show();
            }else if (pref.getBoolean("train",false) && pref.getBoolean("test",false)){
                //double[] corr = mlDataCollector.test(brighness,orientation, sensors, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastInterval, mostUsedLastDay);
                double[] corr = mlDataCollector.test(brighness,orientation, sensors, memmory, networkStats, bluetoothStats, unlockTime, locks, pausedToResumed, appsLastInterval, mostUsedLastDay);//EXTRA
                updateConfidence(context, corr[0], corr[1]);
                if (corr[0]>85 && corr[1]<0.4) {
                    //mlDataCollector.train(brighness, orientation, sensors, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastInterval, mostUsedLastDay);
                    mlDataCollector.train(brighness, orientation, sensors, memmory, networkStats, bluetoothStats, unlockTime, locks, pausedToResumed, appsLastInterval, mostUsedLastDay);//EXTRA
                }
            }
            System.out.println("*----------------------*" + new SimpleDateFormat("MMM dd,yyyy HH:mm").format(new Date(System.currentTimeMillis())));
            //System.out.println(mlDataCollector.readArff(MlDataCollector.ARFFPATH).toString());//A partir de 425 instances ya no lo imprime bien, mejor mirar el archivo directamente
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateConfidence(Context context, double conf, double error) {
        if (MainActivity.areSufficientInstances(MlDataCollector.TESTPATH,40)) {
            if (conf < 50) {
                BackgroundService.builder.setColor(0xcc0000);//Rojo
            } else if (conf < 70) {
                BackgroundService.builder.setColor(0xffcc00);//Naranja
            } else if (conf < 90) {
                BackgroundService.builder.setColor(0xfff00);//Amarillo
            } else {
                BackgroundService.builder.setColor(0x00cc66);//Verde
            }
            BackgroundService.builder.setContentText("El nivel de confianza es " + (int) conf + " con t.error=" + error);
        }else{
            BackgroundService.builder.setContentText("Recogiendo datos para evaluar.");
        }
        BackgroundService.builder.setContentTitle("Behavioral PulseID (Evaluating)");
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(1001, BackgroundService.builder.build());
    }

    private static float[] getSensorValues() {
        return new float[]{BackgroundService.light, BackgroundService.pressure, BackgroundService.temp, BackgroundService.hum};
    }

    private String[] getTopPkgChange(Context context) {
        String resumedPackage = "";
        String pausedPackage = "";
        /*This boolean is used to take only first paused package, so now what this method does
         * is retrieve first paused and last resumend packages during last minute*/
        Boolean firstHunted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            //Object to manage the usage stats of the device
            UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            //Window time = last minute
            final long INTERVAL = 60000;
            final long end = System.currentTimeMillis();
            final long begin = end - INTERVAL;
            //Request the events on that interval
            final UsageEvents usageEvents = mUsageStatsManager.queryEvents(begin, end);
            //While there are more events on the list of last minute..
            while (usageEvents.hasNextEvent()) {
                UsageEvents.Event event = new UsageEvents.Event();
                usageEvents.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED && firstHunted) {
                    pausedPackage = event.getPackageName();
                    firstHunted = false;
                } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    appsLastInterval++;
                    resumedPackage = event.getPackageName();
                }
            }
        }
        if (resumedPackage != "") {
            BackgroundService.lastAppInForeground = resumedPackage;
        }
        return new String[]{pausedPackage, resumedPackage};
    }

    private static int getBrightness(Context context) {
        return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1); // from 0 to 255
    }

    private static double[] getMemoryUsage(Context context) {
        //Returns the available memmory
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        double availableMegs = (mi.availMem / 1024);
        double percentAvail = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            percentAvail = mi.availMem / (double) mi.totalMem * 100.0;
        }
        return new double[]{availableMegs, percentAvail};
    }

    private static long[] getNetworkStats(Context context) {
        //Returns an array of longs that represents the network data used during last interval
        long rxValues = 0;
        long txValues = 0;
        //This method is only useful between versions M(6.0) and P(9). From 9 network stats will not be available
        //Gets network data from last minute only
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            NetworkStatsManager networkStatsManager = (NetworkStatsManager) context.getApplicationContext().getSystemService(Context.NETWORK_STATS_SERVICE);
            NetworkStats.Bucket bucket = null;
            NetworkStats.Bucket bucketD = null;
            try {
                bucket = networkStatsManager.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, "", System.currentTimeMillis() - 60000, System.currentTimeMillis());
                bucketD = networkStatsManager.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, context.getSystemService(Context.TELEPHONY_SERVICE).toString(), System.currentTimeMillis() - 60000, System.currentTimeMillis());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            rxValues = (bucket.getRxBytes() + bucketD.getRxBytes()) / 1024;/// (1024 * 1024);
            txValues = (bucket.getTxBytes() + bucketD.getTxBytes()) / 1024;/// (1024 * 1024);
        }
        return new long[]{rxValues, txValues};
    }

    private static int getBluetoothStats() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        int enabled = 0;
        if (mBluetoothAdapter.isEnabled())
            enabled = 1;
        if (mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED)
            enabled = 1;
        int devices = mBluetoothAdapter.getBondedDevices().size();
        return Integer.parseInt(""+enabled+devices);
    }

    private static String[] mostUsedAppLastDay(Context context) {
        long end = System.currentTimeMillis();
        long begin = end - (24 * 60 * 60 * 1000);
        String mostUsed = "";
        long mostTotalTime = 0;
        String secondMostUsed = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end); //The documentation
            ArrayList<Long> al = new ArrayList<>();
            //Añadimos todos los tiempos en una lista
            for (UsageStats model : stats) {
                al.add(model.getTotalTimeInForeground());
            }
            //Obtenemos el mayor
            mostTotalTime = Collections.max(al);
            UsageStats mostToRemove = null;
            //Obtenemos el nombre de éste
            for (UsageStats model : stats){
                if (model.getTotalTimeInForeground()==mostTotalTime)
                    mostToRemove=model;
            }
            mostUsed=mostToRemove.getPackageName();
            //Lo borramos de la lista y volvemos a hacer lo mismo
            al.remove(stats.indexOf(mostToRemove));
            mostTotalTime = Collections.max(al);
            for (UsageStats model : stats){
                if (model.getTotalTimeInForeground()==mostTotalTime)
                    secondMostUsed=model.getPackageName();
            }
        }
        return new String[]{mostUsed, secondMostUsed};
    }
    private int getScreenOrientation(Context context) {
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        //THIS IS TRIGGERED EVERY MINUTE AS CONFIGURED ON ALARMMANAGER
        //This method has to collect unlock counter, sensor values, pausedToResumedPackage, brightness,
        //memory usage, usagestats and networkstats
        pref = context.getSharedPreferences("pulseidpreferences", MODE_PRIVATE); // 0 - for private mode
        editor = pref.edit();
        retrieveData(context);
    }
}
