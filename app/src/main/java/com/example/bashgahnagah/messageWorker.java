package com.example.bashgahnagah;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.bashgahnagah.interfaces.Message;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

import android.telephony.SmsManager;

public class messageWorker extends Worker {

    private static final String CHANNEL_ID = "background_notification_channel";
    private static final String TAG = "messageWorker";
    private static final String AUTH_TOKEN = "Bearer e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // Replace with your actual token

    public messageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Perform background task here
        Log.d(TAG, "Background job is running... fetching @ "+getInputData().getString("fetch")+ "sending @"+getInputData().getString("post"));
        try {
            URL url = new URL(getInputData().getString("fetch"));

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", AUTH_TOKEN);
            connection.connect();

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder jsonResponse = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    jsonResponse.append(line);
                }
                reader.close();

                Gson gson = new Gson();
                Type listType = new TypeToken<List<Message>>() {
                }.getType();
                List<Message> messages = gson.fromJson(jsonResponse.toString(), listType);
                URL postUrl = new URL(getInputData().getString("post"));
                Log.d(TAG, "messages: " + messages.toString());
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
return Result.failure();
                }
//                SubscriptionInfo simInfo = getApplicationContext().getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList().get(getInputData().getInt("cardId",1));
                List<SubscriptionInfo> activeSubs = getApplicationContext()
                        .getSystemService(SubscriptionManager.class)
                        .getActiveSubscriptionInfoList();

                int cardId = getInputData().getInt("cardId", 1);
                SubscriptionInfo simInfo = null;

                if (activeSubs != null && activeSubs.size() > cardId) {
                    simInfo = activeSubs.get(cardId);
                } else {
                    // مدیریت حالت خطا - مثلاً استفاده از سیم‌کارت پیش‌فرض
                    if (activeSubs != null && !activeSubs.isEmpty()) {
                        simInfo = activeSubs.get(0); // اولین سیم‌کارت
                    }
                }
                for (Message message : messages) {
                    SmsManager.getSmsManagerForSubscriptionId(simInfo.getSubscriptionId()).sendTextMessage(message.phone, null, message.message, null, null);
                    connection = (HttpURLConnection) postUrl.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    connection.setRequestProperty("Authorization", AUTH_TOKEN);
                    connection.setDoOutput(true);
                    String formData = "id=" + message.id;
                    byte[] postData = formData.getBytes(StandardCharsets.UTF_8);
                    OutputStream postOutputStream = connection.getOutputStream();
                    postOutputStream.write(postData);
                    postOutputStream.flush();
                    postOutputStream.close();
                    responseCode = connection.getResponseCode();
                    Log.d(TAG, "Response Code: " + responseCode);
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle(message.id+"id")
                            .setContentText("ارسال پیامک با موفقیت انجام شد")
                            .setPriority(NotificationCompat.PRIORITY_HIGH);
                    NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                    if (notificationManager != null) {
                        notificationManager.notify(1, builder.build());
                    }
                }





            } else {
                Log.e(TAG, "Failed to fetch messages. Response code: " + responseCode);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("failure")
                        .setContentText("Failed to fetch messages. Response code: " + responseCode)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

                NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    notificationManager.notify(1, builder.build());
                }
            }
        } catch (Exception e) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("failure")
                    .setContentText(e.toString())
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(1, builder.build());
            }
        }
        Log.d(TAG, "Background job completed!");
        return Result.success();
    }

}
