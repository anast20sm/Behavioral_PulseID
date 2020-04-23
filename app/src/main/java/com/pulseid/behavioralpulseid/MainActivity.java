package com.pulseid.behavioralpulseid;

import androidx.appcompat.app.AppCompatActivity;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RadioGroup radioSexGroup;
    private Button btnGo;
    private Button btnStop;
    private Button btnUsage;
    TextView infoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Context context = this.getApplicationContext();

        infoView = findViewById(R.id.infoView);
        radioSexGroup = findViewById(R.id.radioGroup);
        btnGo = findViewById(R.id.btnGo);
        btnStop = findViewById(R.id.btnStop);
        btnUsage = findViewById(R.id.btnUsage);

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
                int selectedId = radioSexGroup.getCheckedRadioButtonId();
                if (findViewById(selectedId).equals(findViewById(R.id.radioButton))) {
                    BackgroundService.mode=true;
                    infoView.setText(R.string.info_train_mode);
                }else if (findViewById(selectedId).equals(findViewById(R.id.radioButton2))){
                    BackgroundService.mode=false;
                    infoView.setText(R.string.info_test_mode);
                }
                //When the app is initialized, the service is started (I need to decide how to manage launching the service)
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction("startService");
                broadcastIntent.setClass(context, BootOrScreenBroadcastReceiver.class);
                context.sendBroadcast(broadcastIntent);
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(context, "Service should die now", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        btnUsage.setVisibility(View.GONE);
        infoView.setText(R.string.app_head_information);
    }

}


