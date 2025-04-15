package com.example.bashgahnagah;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    public int simId = 0;
    int hour=0;
    int minute=3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EditText addressEditText = findViewById(R.id.fetch);
        EditText postEditText = findViewById(R.id.post);
        Button pickTimeButton = findViewById(R.id.duration);
        pickTimeButton.setOnClickListener(view -> showTimePickerDialog());
        Context context = getApplicationContext();
        Spinner simSpinner = findViewById(R.id.sims);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {Manifest.permission.READ_PHONE_STATE,Manifest.permission.SEND_SMS};
            requestPermissions(permissions, PERMISSION_REQUEST_CODE);
            return;
        }
        List<SubscriptionInfo> subscriptionInfos = Objects.requireNonNull(ContextCompat.getSystemService(context, SubscriptionManager.class)).getActiveSubscriptionInfoList();
        List<String> simNames = new ArrayList<>();
        if (subscriptionInfos!=null){
            for (SubscriptionInfo info : subscriptionInfos) {
                simNames.add(info.getDisplayName().toString()) ;
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, simNames);
        simSpinner.setAdapter(adapter);
        simSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                simId = position+1;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                simId =0;
            }
        });
        Data inputData = new Data.Builder()
                .putString("fetch", addressEditText.getText().toString())
                .putString("post", postEditText.getText().toString())
                .putInt("cardId",simId)
                .build();
        Button registerJobButton = findViewById(R.id.registerJobButton);
        registerJobButton.setOnClickListener(v -> {
            // Register a one-time background job
            createNotificationChannel();
            PeriodicWorkRequest periodicWorkRequest = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                periodicWorkRequest = new PeriodicWorkRequest.Builder(
                        messageWorker.class,
                        hour*60+minute, TimeUnit.MINUTES
                ).setInputData(inputData).build();

                WorkManager.getInstance(getApplicationContext()).cancelAllWorkByTag("messageWorker");
                WorkManager.getInstance(getApplicationContext()).enqueue(periodicWorkRequest);
            }
        });
    }
    private void showTimePickerDialog() {
        // Get current time
        Calendar calendar = Calendar.getInstance();
        // Create time picker dialog
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (TimePicker view, int selectedHour, int selectedMinute) -> {
                    hour = selectedHour;
                    minute = selectedMinute;
                },
                hour,
                minute,
                true // 24-hour format
        );

        timePickerDialog.show();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Background Notifications";
            String description = "Notifications triggered by background jobs";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("background_notification_channel", name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}