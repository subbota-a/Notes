package ru.yandex.subbota_job.notes;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SyncService extends Service {
    private GoogleApiClient mClient;
    private BlockingQueue<Pair<String, Integer>> mUpdatedFiles = new LinkedBlockingQueue<Pair<String, Integer>>();
    private Thread mThread;

    public SyncService() {
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void OnFileChanged(Context context, String path) {
        Log.d("SyncService", "OnFileChanged");
        Intent intent = new Intent(context, SyncService.class);
        intent.setData(Uri.fromFile(new File(path)));
        context.startService(intent);
    }

    public static void SyncForce(Context context) {
        Intent intent = new Intent(context, SyncService.class);
        context.startService(intent);
    }

    public static void Reconnect() {
        Log.d("SyncService", "OnFileChanged");
    }

    class BackgroundWorker implements Runnable
    {
        @Override
        public void run() {
            try {
                for(;;) {
                    Pair<String, Integer> task = mUpdatedFiles.take();
                    if (TextUtils.isEmpty(task.first))
                        syncAll();
                    else
                        syncFile(task.first);
                    stopSelf(task.second);
                }
            }catch (InterruptedException e){
                return;
            }
        }

        private void syncFile(String path) {

        }

        private void syncAll() {

        }
    }
    private class ConnectionController implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
        @Override
        public void onConnected(Bundle bundle) {
            Log.d("SyncService", "onConnected");
            mThread = new Thread(new BackgroundWorker());
            mThread.start();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("SyncService", "onConnectionSuspended");
        }
        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d("SyncService", "onConnectionFailed: " + connectionResult.getErrorMessage());
            Intent intent = new Intent(SyncService.this, ConnectionActivity.class);
            intent.putExtra(ConnectionActivity.PENDING_INTENT, connectionResult.getResolution());
            intent.putExtra(ConnectionActivity.CONNECTION_ERROR, connectionResult.getErrorCode());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(SyncService.this, 0, intent, 0);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(SyncService.this);
            builder.setSmallIcon(R.drawable.ic_warning_24dp);
            builder.setContentTitle(getString(R.string.connection_failed_title));
            builder.setContentText(GoogleApiAvailability.getInstance().getErrorString(connectionResult.getErrorCode()));
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(0, builder.build());
            stopSelf();
        }
    }
    @Override
    public void onCreate() {
        Log.d("SyncService", "onCreate");
        ConnectionController connectionController = new ConnectionController();
        mClient = new GoogleApiClient.Builder(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(connectionController)
                .addOnConnectionFailedListener(connectionController)
                .build();
        mClient.connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("SyncService", "onStartCommand");
        if (intent == null)
            sync(startId);
        Uri uri = intent.getData();
        mUpdatedFiles.add(new Pair<String, Integer>(uri != null ? uri.getPath() : null, startId));
        return START_STICKY;
    }

    private void sync(int startId) {
        stopSelf(startId);
    }

    @Override
    public void onDestroy() {
        Log.d("SyncService", "onDestroy");
        mClient.disconnect();
        if (mThread != null)
            mThread.interrupt();
    }
}
