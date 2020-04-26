package com.pulseid.behavioralpulseid;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RadioGroup radioModeGroup;
    private Button btnGo;
    private Button btnStop;
    private Button btnUsage;
    private Button btnDown;
    TextView infoView;
    public static TextView paramsView;
    public static TextView debugView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Context context = this.getApplicationContext();

        infoView = findViewById(R.id.infoView);
        paramsView = findViewById(R.id.collectedValuesView);
        debugView = findViewById(R.id.debugView);

        radioModeGroup = findViewById(R.id.radioGroup);
        btnGo = findViewById(R.id.btnGo);
        btnStop = findViewById(R.id.btnStop);
        btnUsage = findViewById(R.id.btnUsage);
        btnDown = findViewById(R.id.downloadButton);

        //If params or debug information on background service is different than what is on the ui, update
        if (BackgroundService.uiParams!=paramsView.getText()){
            paramsView.setText(BackgroundService.uiParams);
        }
        debugView.setMovementMethod(new ScrollingMovementMethod());
        if (BackgroundService.debug!=debugView.getText()){
            debugView.setText(BackgroundService.debug);
        }

        //Check which mode is actually running (or "Model creation" by default)
        if (BackgroundService.test){
            radioModeGroup.check(R.id.radioButton2);
            infoView.setText(R.string.info_test_mode);
        }else if (!BackgroundService.test){
            radioModeGroup.check(R.id.radioButton);
            infoView.setText(R.string.info_train_mode);
        }else{
            infoView.setText(R.string.app_head_information);
        }

        //Checks for the usage access. If not allowed, launches the settings section to enable it
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 0, System.currentTimeMillis());
            if (stats.isEmpty()) {
                btnUsage.setVisibility(View.VISIBLE);
                infoView.setText(R.string.enable_usage_settings);
            }
        }

        btnUsage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),1);
            }
        });



        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int selectedId = radioModeGroup.getCheckedRadioButtonId();
                if (findViewById(selectedId).equals(findViewById(R.id.radioButton))) {
                    BackgroundService.test=false;
                    infoView.setText(R.string.info_train_mode);
                }else if (findViewById(selectedId).equals(findViewById(R.id.radioButton2))){
                    BackgroundService.test=true;
                    infoView.setText(R.string.info_test_mode);
                }
                stopService(context);
                startService(context);
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(context, "Service stopped", Toast.LENGTH_SHORT).show();
                stopService(context);
            }
        });

        btnDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setCancelable(true);
                builder.setTitle("Export profile data");
                builder.setMessage("Please confirm if you want to download the .arff files that contains your trained profile and your last 20 test instances. Locate them at Download folder.");
                builder.setPositiveButton("Confirm",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                            copyFile(new File(context.getFilesDir().getPath().concat("/dataset.arff")), new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()+"/dataset.arff"));
                                            copyFile(new File(context.getFilesDir().getPath().concat("/testset.arff")), new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()+"/testset.arff"));
                                        }else {
                                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                                        }
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        btnUsage.setVisibility(View.GONE);
        infoView.setText(R.string.app_head_information);
    }

    private void startService(Context context) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("startService");
        broadcastIntent.setClass(context, BootOrScreenBroadcastReceiver.class);
        context.sendBroadcast(broadcastIntent);
    }

    private void stopService(Context context) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("stopService");
        broadcastIntent.setClass(context, BootOrScreenBroadcastReceiver.class);
        context.sendBroadcast(broadcastIntent);
    }

    public void copyFile(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }

}


