package com.example.bashgahnagah;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.reflect.Type;
import java.util.List;

import android.telephony.SmsManager;

public class messageWorker extends Worker {

    private static final String TAG = "messageWorker";
    private static final String AUTH_TOKEN = "Bearer e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // Replace with your actual token
    public messageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Perform background task here
        Log.d(TAG, "Background job is running...");
        try {
            URL url = new URL("https://bashgahagah.ir/sms/fetch.php");

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

                // Step 2: Deserialize JSON response into a list of Message objects
                Gson gson = new Gson();
                Type listType = new TypeToken<List<Message>>() {}.getType();
                List<Message> messages = gson.fromJson(jsonResponse.toString(), listType);
                SmsManager smsManager = SmsManager.getDefault();
                URL postUrl = new URL("https://bashgahagah.ir/sms/fetch.php");
                for (Message message : messages) {
                    smsManager.sendTextMessage(message.phone, null, message.message, null, null);
                    HttpURLConnection connection = (HttpURLConnection) postUrl.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    connection.setRequestProperty("Authorization", AUTH_TOKEN);
                    connection.setDoOutput(true);
                    // Step 2: Prepare form data
                    String formData = "id=" + message.id;
                    OutputStream outputStream = connection.getOutputStream();
                    outputStream.write(formData.getBytes());
                    outputStream.flush();
                    outputStream.close();
                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Response Code: " + responseCode);
                }
            } else {
                Log.e(TAG, "Failed to fetch messages. Response code: " + responseCode);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return Result.failure();
        }

        Log.d(TAG, "Background job completed!");
        return Result.success();
    }
}
    public class Message{
        public int id;
        public String phone;
        public String message;
    }
