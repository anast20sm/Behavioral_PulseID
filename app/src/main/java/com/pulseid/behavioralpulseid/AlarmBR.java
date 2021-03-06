package com.pulseid.behavioralpulseid;

import android.app.ActivityManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.RemoteException;
import android.provider.Settings;
import androidx.core.app.NotificationManagerCompat;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.MODE_PRIVATE;

public class AlarmBR extends BroadcastReceiver {
    private int appsLastInterval = 1;
    private SharedPreferences pref;

    //This method carries out collection of all parameters
    private void retrieveData(Context context) {
        int brighness = getBrightness(context);
        int orientation = getScreenOrientation(context);
        float lightSensor = BackgroundService.light;
        double[] memmory = getMemoryUsage(context); //They are availableMegs and percentAvailable respectively
        long[] networkStats = getNetworkStats(context); //They are txMB and rxMB respectively
        long bluetoothStats = getBluetoothStats();
        long unlocks = BootOrScreenBR.counter;
        long lockTime = BootOrScreenBR.screenOffTime;
        BootOrScreenBR.counter = 0;
        BootOrScreenBR.screenOffTime = 0;
        String[] pausedToResumed = getTopPkgChange(context); //They are firstPaused and lastResumed respectively
        String[] mostUsedLastDay = mostUsedAppLastDay(context);


        try {
            //Object mlDataHandler is created (which uses Weka library) and collected datas are sent to perform configured action
            MlDataHandler mlDataHandler = new MlDataHandler(context);
            if (!pref.getBoolean("train",false) && pref.getBoolean("test",false)){//---Evaluation mode
                double[] corr = mlDataHandler.test(brighness,orientation, lightSensor, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastInterval, mostUsedLastDay);//EXTRA
                updateConfidence(context, corr[0], corr[1]);
            }else if (pref.getBoolean("train",false) && !pref.getBoolean("test",false)){//---Training mode
                mlDataHandler.train(brighness, orientation, lightSensor, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastInterval, mostUsedLastDay);//EXTRA
                BackgroundService.builder.setContentText("Training model...");
                BackgroundService.builder.setContentTitle("Behavioral PulseID (Training)");
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.notify(1001, BackgroundService.builder.build());
            }else if (pref.getBoolean("train",false) && pref.getBoolean("test",false)){//---Dynamic mode
                double[] corr = mlDataHandler.test(brighness,orientation, lightSensor, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastInterval, mostUsedLastDay);//EXTRA
                updateConfidence(context, corr[0], corr[1]);
                if (corr[0]>85 && corr[1]<0.48) {
                    mlDataHandler.train(brighness, orientation, lightSensor, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastInterval, mostUsedLastDay);//EXTRA
                }
            }
            System.out.println("*----------------------*" + new SimpleDateFormat("MMM dd,yyyy HH:mm").format(new Date(System.currentTimeMillis())));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //This method carries out the update of the notification with percentage of correct predictions
    private void updateConfidence(Context context, double conf, double error) {
        if (MainActivity.areSufficientInstances(MlDataHandler.TESTPATH,40)) {
            if (conf < 50) {
                BackgroundService.builder.setColor(0xcc0000);//Rojo
            } else if (conf < 70) {
                BackgroundService.builder.setColor(0xffcc00);//Naranja
            } else if (conf < 90) {
                BackgroundService.builder.setColor(0xfff00);//Amarillo
            } else {
                BackgroundService.builder.setColor(0x00cc66);//Verde
            }
            DecimalFormat df = new DecimalFormat("##.##");
            BackgroundService.builder.setContentText("Validation: " + (int) conf + "% (t.e=" + df.format(error) +")");
        }else{
            BackgroundService.builder.setContentText("Collecting samples to evaluate.");
        }
        BackgroundService.builder.setContentTitle("Behavioral PulseID (Evaluating)");
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(1001, BackgroundService.builder.build());
    }

    private String[] getTopPkgChange(Context context) {
        String resumedPackage = "";
        String pausedPackage = "";
        /*This boolean is used to take only first paused package, so now what this method does
         * is retrieve first paused and last resumend packages during last minute*/
        boolean firstHunted = true;
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
        if (!resumedPackage.equals("")) {
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

    //Bluetooth statistics are generated. This method checks Bluetooth state and number of paired
    // and connected devices. They are concatenated in an unique int
    private static int getBluetoothStats() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        int enabled = 0;
        if (mBluetoothAdapter.isEnabled() || mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED)
            enabled = 1;
        int bondedDevices = mBluetoothAdapter.getBondedDevices().size();
        System.out.println("Bluetooth results is: "+ enabled + bondedDevices + BackgroundService.connectedDevices);
        if (enabled==1)
            return Integer.parseInt(""+ enabled + bondedDevices + BackgroundService.connectedDevices);
        else return 0;
    }

    private static String[] mostUsedAppLastDay(Context context) {
        long end = System.currentTimeMillis();
        long begin = end - (24 * 60 * 60 * 1000);
        String mostUsed = "";
        long mostTotalTime;
        String secondMostUsed = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end); //The documentation
            ArrayList<Long> al = new ArrayList<>();
            //All times are added to a list
            for (UsageStats model : stats) {
                al.add(model.getTotalTimeInForeground());
            }
            //Higher value is saved
            mostTotalTime = Collections.max(al);
            UsageStats mostToRemove = null;
            //The name of that higher value is saved
            for (UsageStats model : stats){
                if (model.getTotalTimeInForeground()==mostTotalTime)
                    mostToRemove=model;
            }
            mostUsed=mostToRemove.getPackageName();
            //Then this most used app is removed and the search is done again
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
        retrieveData(context);
    }
}
