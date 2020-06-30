package com.pulseid.behavioralpulseid;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RadioGroup radioModeGroup;
    private Button btnGo;
    private Button btnStop;
    private Button btnUsage;
    private Button btnDown;
    private TextView infoView;
    private TextView paramsView;
    public static TextView debugView;

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Context context = this.getApplicationContext();

        //Configure shared preferences
        pref = getSharedPreferences("pulseidpreferences", MODE_PRIVATE); // 0 - for private mode
        editor = pref.edit();

        if (pref.getString("params_text",null)==null)
            editor.putString("params_text",getString(R.string.app_collect_values));
        if (pref.getString("debug_text",null)==null)
            editor.putString("debug_text",getString(R.string.app_debugging));
        if (pref.getString("head_text",null)==null)
            editor.putString("head_text",getString(R.string.app_head_information));
        editor.commit();

        //Set configured parameters on user interface
        infoView = findViewById(R.id.infoView);
        paramsView = findViewById(R.id.collectedValuesView);
        debugView = findViewById(R.id.debugView);

        radioModeGroup = findViewById(R.id.radioGroup);
        btnGo = findViewById(R.id.btnGo);
        btnStop = findViewById(R.id.btnStop);
        btnUsage = findViewById(R.id.btnUsage);
        btnDown = findViewById(R.id.downloadButton);


        //Update ui texts
        if (pref.getString("params_text",null)!=paramsView.getText()){
            paramsView.setText(pref.getString("params_text", null));
        }

        debugView.setMovementMethod(new ScrollingMovementMethod());
        if (pref.getString("debug_text",null)!=debugView.getText()){
            debugView.setText(pref.getString("debug_text",null));
        }

        //Check which mode is actually running (or set "Model creation" by default)
        if (pref.getBoolean("test", false) && !pref.getBoolean("train", false)){
            radioModeGroup.check(R.id.radioButton2);
            editor.putString("head_text", getString(R.string.info_train_mode));
        }else if (!pref.getBoolean("test", false) && pref.getBoolean("train", false)) {
            radioModeGroup.check(R.id.radioButton);
            editor.putString("head_text", getString(R.string.info_test_mode));
        }else if (pref.getBoolean("test", false) && pref.getBoolean("train", false)){
            radioModeGroup.check(R.id.radioButton3);
            editor.putString("head_text", getString(R.string.info_dynamic_mode));
        }else{
            editor.putString("head_text", getString(R.string.app_head_information));
        }
        editor.commit();
        infoView.setText(pref.getString("head_text", null));

        //Checks for the usage access. If not allowed, launches the settings section to enable it
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 0, System.currentTimeMillis());
            if (stats.isEmpty()) {
                btnUsage.setVisibility(View.VISIBLE);
                infoView.setText(R.string.enable_usage_settings);
                btnDown.setVisibility(View.INVISIBLE);
                btnGo.setVisibility(View.INVISIBLE);
                btnStop.setVisibility(View.INVISIBLE);
            }
        }
        //Listener of usage button
        btnUsage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),1);
            }
        });
        //Listener of go button. When pressed, mode settings are configured and background service is initiated
        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int selectedId = radioModeGroup.getCheckedRadioButtonId();
                if (findViewById(selectedId).equals(findViewById(R.id.radioButton))) {//---Training mode
                    editor.putBoolean("train",true);
                    editor.putBoolean("test",false);
                    editor.putString("head_text", getString(R.string.info_train_mode));
                    stopService(context);
                    startService(context);
                }else if (findViewById(selectedId).equals(findViewById(R.id.radioButton2))){//---Evaluation mode
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setCancelable(true);
                    builder.setTitle("Select who you are");
                    builder.setMessage("For evaluation purposes, please select if you are the device owner or an impostor.");
                    builder.setNegativeButton("Owner",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        editor.putBoolean("eval-owner",true).commit();
                                        startEvaluation(context);
                                    }
                                }
                            });
                    builder.setPositiveButton("Impostor",
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    editor.putBoolean("eval-owner",false).commit();
                                    startEvaluation(context);
                                }
                        }
                    });
                    builder.setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    });

                    AlertDialog dialog = builder.create();
                    dialog.show();
                } else if (findViewById(selectedId).equals(findViewById(R.id.radioButton3))){ //---Dynamic mode
                    if (areSufficientInstances(context.getFilesDir().getPath().concat("/dataset.arff"), 80)) { //Poner un nuevo valor coherente
                        editor.putBoolean("train",true);
                        editor.putBoolean("test",true);
                        editor.putBoolean("eval-owner",true);
                        editor.putString("head_text", getString(R.string.info_dynamic_mode));
                        stopService(context);
                        startService(context);
                    }else{
                        editor.putString("head_text", getString(R.string.info_test_not_available));
                    }
                }
                editor.commit();
                infoView.setText(pref.getString("head_text", null));

            }
        });
        //Listener of stop button. When pressed, background service is stopped
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putString("debug_text", new SimpleDateFormat("dd MMM yyyy HH:mm").format(new Date(System.currentTimeMillis()))+" Service stopped"+"\n"+pref.getString("debug_text",null));
                editor.putBoolean("train",false);
                editor.putBoolean("test",false);
                editor.commit();
                stopService(context);
            }
        });
        //Listener of download button. When pressed, dataset files are copied to Downloads folder
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
                                            copyFile(new File(context.getFilesDir().getPath().concat("/dataset.arff")), new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()+"/"+File.separator,"dataset.arff"));
                                            copyFile(new File(context.getFilesDir().getPath().concat("/ownerdataset.arff")), new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()+"/"+File.separator,"ownerdataset.arff"));
                                            copyFile(new File(context.getFilesDir().getPath().concat("/impostordataset.arff")), new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()+"/"+File.separator,"impostordataset.arff"));

                                            Toast.makeText(context,"Files exported to 'Downloads'",Toast.LENGTH_LONG).show();
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

    //Starts the service configuring selected mode
    private void startEvaluation(Context context) {
        if (areSufficientInstances(context.getFilesDir().getPath().concat("/dataset.arff"), 150)) { //Poner un nuevo valor coherente
            editor.putBoolean("train", false);
            editor.putBoolean("test", true);
            editor.putString("head_text", getString(R.string.info_test_mode));
            File f = new File(context.getFilesDir().getPath().concat("/testset.arff"));
            //To assure that each time evaluation starts, there is no data of the owner/impostor for the other one
            if (f.delete())
                System.out.println("Testset deleted");
            stopService(context);
            startService(context);
        }else{
            editor.putString("head_text", getString(R.string.info_test_not_available));
        }
        editor.commit();
        infoView.setText(pref.getString("head_text", null));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        btnUsage.setVisibility(View.GONE);
        btnGo.setVisibility(View.VISIBLE);
        btnStop.setVisibility(View.VISIBLE);
        btnDown.setVisibility(View.VISIBLE);
        editor.putString("head_text", getString(R.string.app_head_information)).commit();
        infoView.setText(pref.getString("head_text",null));
    }
    //Starts background service
    private void startService(Context context) {
        Intent brIntent = new Intent();
        brIntent.setAction("startService");
        brIntent.setClass(context, BootOrScreenBR.class);
        context.sendBroadcast(brIntent);
    }
    //Stops background service
    private void stopService(Context context) {
        Intent brIntent = new Intent();
        brIntent.setAction("stopService");
        brIntent.setClass(context, BootOrScreenBR.class);
        context.sendBroadcast(brIntent);
    }

    //Method used to copy configuration files
    private void copyFile(File src, File dst) throws IOException {
        if (src.exists()) {
            FileInputStream inStream = new FileInputStream(src);
            FileOutputStream outStream = new FileOutputStream(dst);
            FileChannel inChannel = inStream.getChannel();
            FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
            inStream.close();
            outStream.close();
        }
    }
    //Method used to check number of instances before staring evaluation or dynamic mode
    public static boolean areSufficientInstances(String path, int number) {
        try {
            if (new File(path).exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(path));
                int lines = 0;
                while (reader.readLine() != null)
                    lines++;
                reader.close();
                if (lines >= number)
                    return true;
            }
        } catch (IOException e){
            e.printStackTrace();
        }
        return false;
    }

}


