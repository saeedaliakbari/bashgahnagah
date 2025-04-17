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
import android.os.Handler;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String WORK_TAG = "message_worker";
    private static final String CHANNEL_ID = "background_notification_channel";
    private static final int PERMISSION_REQUEST_CODE = 1;

    private int selectedSimId = 0;
    private int selectedHour = 0;
    private int selectedMinute = 3;

    private Handler handler = new Handler();
    private Runnable updateTimerRunnable;

    private boolean hasTriggeredWorker = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        selectedSimId = getSharedPreferences("prefs", MODE_PRIVATE).getInt("selectedSimId", 0);

        initViews();
        checkAndRequestPermissions();
        createNotificationChannel();
        startTimerUpdater();
        updateTimeButtonText();
    }

    private void initViews() {
        EditText fetchAddressEditText = findViewById(R.id.fetch);
        EditText postAddressEditText = findViewById(R.id.post);
        Button timePickerButton = findViewById(R.id.duration);
        Spinner simSpinner = findViewById(R.id.sims);
        Button registerJobButton = findViewById(R.id.registerJobButton);
        Button cancelJobButton = findViewById(R.id.cancelJobButton);

        timePickerButton.setOnClickListener(view -> showTimePickerDialog());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        setupSimSpinner(simSpinner);

        registerJobButton.setOnClickListener(v -> {
            String fetchUrl = fetchAddressEditText.getText().toString();
            String postUrl = postAddressEditText.getText().toString();

            if (validateInputs(fetchUrl, postUrl)) {
                registerBackgroundJob(fetchUrl, postUrl, selectedSimId);
            }
        });

        cancelJobButton.setOnClickListener(v -> {
            WorkManager.getInstance(this).cancelUniqueWork(WORK_TAG);
            Toast.makeText(this, "کار پس‌زمینه لغو شد", Toast.LENGTH_SHORT).show();
        });
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.SEND_SMS
                    },
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private void setupSimSpinner(Spinner simSpinner) {
        SubscriptionManager subscriptionManager = ContextCompat.getSystemService(this, SubscriptionManager.class);
        if (subscriptionManager == null) {
            Log.e(TAG, "SubscriptionManager is not available");
            return;
        }

        List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();
        List<String> simNames = new ArrayList<>();

        if (subscriptionInfos != null && !subscriptionInfos.isEmpty()) {
            for (SubscriptionInfo info : subscriptionInfos) {
                CharSequence displayName = info.getDisplayName();
                simNames.add(displayName != null ? displayName.toString() : "SIM " + info.getSubscriptionId());
            }
        } else {
            simNames.add("سیم‌کارت یافت نشد");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                simNames
        );

        simSpinner.setAdapter(adapter);
        simSpinner.setSelection(selectedSimId);
        simSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSimId = position;
                getSharedPreferences("prefs", MODE_PRIVATE)
                        .edit()
                        .putInt("selectedSimId", selectedSimId)
                        .apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedSimId = 0;
            }
        });
    }

    private void showTimePickerDialog() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    updateTimeButtonText();
                },
                selectedHour,
                selectedMinute,
                true
        );

        timePickerDialog.setTitle("انتخاب بازه زمانی");
        timePickerDialog.show();
    }

    private void updateTimeButtonText() {
        Button timeButton = findViewById(R.id.duration);
        timeButton.setSingleLine(false);
        timeButton.setMaxLines(3);
        timeButton.setTextSize(14);

        String timeText = String.format("%02d دقیقه", selectedMinute);

        long lastRegistered = getSharedPreferences("prefs", MODE_PRIVATE)
                .getLong("last_registered_time", -1);
        long intervalMinutes = getSharedPreferences("prefs", MODE_PRIVATE)
                .getLong("interval_minutes", -1);

        if (lastRegistered != -1 && intervalMinutes > 0) {
            long now = System.currentTimeMillis();
            long intervalMillis = intervalMinutes * 60 * 1000;
            long elapsed = now - lastRegistered;
            long remaining = intervalMillis - (elapsed % intervalMillis);

            long remainingSeconds = (remaining / 1000);
            long minutes = remainingSeconds / 60;
            long seconds = remainingSeconds % 60;

            timeText += String.format("\n(اجرای بعدی تا %02d:%02d دیگر)", minutes, seconds);

            if (remainingSeconds <= 0 && !hasTriggeredWorker) {
                hasTriggeredWorker = true;

                EditText fetchEditText = findViewById(R.id.fetch);
                EditText postEditText = findViewById(R.id.post);
                String fetch = fetchEditText.getText().toString();
                String post = postEditText.getText().toString();

                if (!fetch.isEmpty() && !post.isEmpty()) {
                    Data inputData = new Data.Builder()
                            .putString("fetch", fetch)
                            .putString("post", post)
                            .putInt("cardId", selectedSimId)
                            .build();

                    runMessageWorkerNow(inputData);
                }
            } else if (remainingSeconds > 0) {
                hasTriggeredWorker = false;
            }
        }

        timeButton.setText(timeText);
    }

    private void runMessageWorkerNow(Data inputData) {
        androidx.work.OneTimeWorkRequest oneTimeWorkRequest =
                new androidx.work.OneTimeWorkRequest.Builder(messageWorker.class)
                        .setInputData(inputData)
                        .build();

        WorkManager.getInstance(this).enqueue(oneTimeWorkRequest);
    }

    private boolean validateInputs(String fetchUrl, String postUrl) {
        if (fetchUrl.isEmpty() || postUrl.isEmpty()) {
            Toast.makeText(this, "لطفا آدرس‌ها را وارد کنید", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (selectedHour == 0 && selectedMinute < 1) {
            Toast.makeText(this, "حداقل بازه زمانی 1 دقیقه است", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void registerBackgroundJob(String fetchUrl, String postUrl, int simId) {
        Data inputData = new Data.Builder()
                .putString("fetch", fetchUrl)
                .putString("post", postUrl)
                .putInt("cardId", simId)
                .build();

        long repeatInterval = selectedHour * 60 + selectedMinute;
        repeatInterval = Math.max(repeatInterval, 1);

//        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
//                messageWorker.class,
//                repeatInterval,
//                TimeUnit.MINUTES
//        )
//                .setInputData(inputData)
//                .build();
//
//        WorkManager.getInstance(this)
//                .enqueueUniquePeriodicWork(
//                        WORK_TAG,
//                        ExistingPeriodicWorkPolicy.REPLACE,
//                        workRequest
//                );

        Toast.makeText(this,
                "کار پس‌زمینه با موفقیت ثبت شد\nبازه زمانی: هر " +
                        selectedHour + " ساعت و " + selectedMinute + " دقیقه",
                Toast.LENGTH_LONG).show();

        getSharedPreferences("prefs", MODE_PRIVATE)
                .edit()
                .putLong("last_registered_time", System.currentTimeMillis())
                .putLong("interval_minutes", selectedHour * 60L + selectedMinute)
                .apply();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "اعلان‌های پس‌زمینه",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("اعلان‌های مربوط به ارسال پیامک در پس‌زمینه");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Spinner simSpinner = findViewById(R.id.sims);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                setupSimSpinner(simSpinner);
            } else {
                Toast.makeText(this,
                        "برای استفاده از برنامه باید مجوزهای لازم را تایید کنید",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startTimerUpdater() {
        updateTimerRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeButtonText();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(updateTimerRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && updateTimerRunnable != null) {
            handler.removeCallbacks(updateTimerRunnable);
        }
    }
}
