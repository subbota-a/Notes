package ru.yandex.subbota_job.notes;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;

public class ConnectionActivity extends AppCompatActivity {
    public static final String PENDING_INTENT = "PENDING_INTENT";
    public static final String CONNECTION_ERROR = "CONNECTION_ERROR";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(0);
        PendingIntent pendingIntent = (PendingIntent)intent.getParcelableExtra(PENDING_INTENT);
        int errCode = intent.getIntExtra(CONNECTION_ERROR, 0);
        if (pendingIntent != null)
            try {
                startIntentSenderForResult(pendingIntent.getIntentSender(), 1, (Intent)null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
                finish();
            }
        else {
            GoogleApiAvailability.getInstance().showErrorDialogFragment(this, errCode, 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1){
            if (resultCode == RESULT_OK)
                SyncService.Reconnect();
            finish();
        }
    }
}
