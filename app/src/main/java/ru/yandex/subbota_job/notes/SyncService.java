package ru.yandex.subbota_job.notes;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SyncService extends Service {
    private static final int SyncAll = 1;
    private static final int OnFileChanged = 2;
    private static final int OnContinue = 3;
    private static final int Quit = 4;
    private static final String COMMAND = "COMMAND";
    private Handler mHandler;

    public SyncService() {
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void onFileChanged(Context context, String path) {
        Log.d("SyncService", "onFileChanged");
        Intent intent = new Intent(context, SyncService.class);
        intent.setData(Uri.fromFile(new File(path)));
        intent.putExtra(COMMAND, OnFileChanged);
        context.startService(intent);
    }

    public static void restart(Context context) {
        Log.d("SyncService", "onFileChanged");
        Intent intent = new Intent(context, SyncService.class);
        intent.putExtra(COMMAND, OnContinue);
        context.startService(intent);
    }




    @Override
    public void onCreate() {
        Log.d("SyncService", "onCreate");
        HandlerThread mThread = new HandlerThread("SyncService.Thread");
        mThread.start();
        mHandler = new Handler(mThread.getLooper(), new Impl(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("SyncService", "onStartCommand");
        int command = SyncAll;
        String path = null;
        if (intent != null) {
            command = intent.getIntExtra(COMMAND, SyncAll);
            Uri uri = intent.getData();
            if (uri!=null)
                path = uri.getPath();
        }
        mHandler.sendMessage(Message.obtain(mHandler, command, startId, 0, path));
        return START_STICKY;
    }

    private void sync(int startId) {
        stopSelf(startId);
    }

    @Override
    public void onDestroy() {
        Log.d("SyncService", "onDestroy");
        mHandler.sendEmptyMessage(Quit);
    }

    static class Impl implements Handler.Callback{
        private Service mContext;
        private GoogleApiClient mClient;
        private boolean mResolvePending = false;
        private BlockingQueue<Pair<String, Integer>> mUpdatedFiles = new LinkedBlockingQueue<Pair<String, Integer>>();
        public Impl(Service context)
        {
            mContext = context;
            mClient = new GoogleApiClient.Builder(mContext)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .build();
        }
        @Override
        public boolean handleMessage(Message msg) {
            switch(msg.what){
                case OnFileChanged:
                    addFile(new Pair<String, Integer>((String)msg.obj, msg.arg1));
                    return true;
                case OnContinue:
                    onResolved();
                    return true;
                case Quit:
                    mClient.disconnect();
                    ((HandlerThread)Thread.currentThread()).quit();
                    return true;
            }
            return false;
        }
        private void addFile(Pair<String, Integer> obj) {
            mUpdatedFiles.add(obj);
            if (mResolvePending)
                return;
            onResolved();
        }

        private boolean connect() {
            if (mClient.isConnected())
                return true;
            mResolvePending = false;
            ConnectionResult result = mClient.blockingConnect();
            if (result.isSuccess())
                return true;
            makeNotification(result.getResolution(), result.getErrorCode()
                    , mContext.getString(R.string.connection_failed_title)
                    , GoogleApiAvailability.getInstance().getErrorString(result.getErrorCode()));
            mResolvePending = true;
            return false;
        }
        private void onResolved() {
            if (!connect())
                return;
            for(Pair<String, Integer> p = mUpdatedFiles.peek(); p!=null; p = mUpdatedFiles.peek())
            {
                if (!updateFile(p.first))
                    return;
                mUpdatedFiles.remove();
                mContext.stopSelf(p.second);
            }
        }
        private boolean updateFile(String path) {
            try {
                DriveFolder folder = getFolder();
                File f = new File(path);
                DriveFile driveFile = searchFile(folder, f.getName());
                if (f.exists())
                {
                    DriveApi.DriveContentsResult contentsResult = null;
                    if (driveFile != null) {
                        contentsResult = driveFile.open(mClient, DriveFile.MODE_WRITE_ONLY, null).await();
                    }else{
                        contentsResult = Drive.DriveApi.newDriveContents(mClient).await();
                    }
                    checkStatus(contentsResult.getStatus());
                    copyContents(new FileInputStream(f), contentsResult.getDriveContents().getOutputStream());
                    MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                            .setTitle(f.getName())
                            .setMimeType(mContext.getString(R.string.noteMimeType))
                            .build();
                    if (driveFile != null){
                        checkStatus(contentsResult.getDriveContents().commit(mClient, null).await());
                    }else {
                        DriveFolder.DriveFileResult t = folder.createFile(mClient, changeSet, contentsResult.getDriveContents()).await();
                        checkStatus(t.getStatus());
                    }
                }else if (driveFile != null) {
                    driveFile.delete(mClient).await();
                }
                return true;
            }catch(MyError e){
                makeNotification(e.mStatus.getResolution(), e.mStatus.getStatusCode(), mContext.getString(R.string.operation_failed_title), e.mStatus.getStatusMessage());
                mResolvePending = true;
                mClient.disconnect();
                return false;
            }catch(IOException e){
                return true;
            }
        }

        private void copyContents(InputStream inputStream, OutputStream outputStream) throws IOException {
            int ch;
            while((ch=inputStream.read())!=-1)
                outputStream.write(ch);
            inputStream.close();
        }

        private DriveFile searchFile(DriveFolder folder, String name) {
            Query query = new Query.Builder().addFilter(Filters.eq(SearchableField.TITLE, name))
                    .addFilter(Filters.eq(SearchableField.MIME_TYPE, mContext.getString(R.string.noteMimeType)))
                    .build();
            DriveApi.MetadataBufferResult bufferResult = folder.queryChildren(mClient, query).await();
            checkStatus(bufferResult.getStatus());
            MetadataBuffer buf = bufferResult.getMetadataBuffer();
            if (buf.getCount()>0)
                return bufferResult.getMetadataBuffer().get(0).getDriveId().asDriveFile();
            return null;
        }

        private DriveFolder getFolder()
        {
            String folderName = mContext.getString(R.string.google_drive_folder);
            {// search one
                Query query = new Query.Builder()
                        .addFilter(Filters.eq(SearchableField.TITLE, folderName))
                        .addFilter(Filters.eq(SearchableField.TRASHED, false))
                        .build();
                DriveApi.MetadataBufferResult result = Drive.DriveApi.getRootFolder(mClient).queryChildren(mClient, query).await();
                checkStatus(result.getStatus());
                MetadataBuffer buf = result.getMetadataBuffer();
                for(int i=0; i<buf.getCount(); ++i)
                    if (buf.get(i).isFolder())
                        return result.getMetadataBuffer().get(i).getDriveId().asDriveFolder();
            }
            {// create one
                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                        .setTitle(folderName)
                        .build();
                DriveFolder.DriveFolderResult result = Drive.DriveApi.getRootFolder(mClient).createFolder(mClient, changeSet).await();
                checkStatus(result.getStatus());
                return result.getDriveFolder();
            }
        }

        private void makeNotification(PendingIntent resolution, int errCode, String notifyTitle, String notifyText)
        {
            Intent intent = new Intent(mContext, ConnectionActivity.class);
            intent.putExtra(ConnectionActivity.PENDING_INTENT, resolution);
            intent.putExtra(ConnectionActivity.CONNECTION_ERROR, errCode);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext);
            builder.setSmallIcon(R.drawable.ic_warning_24dp);
            builder.setContentTitle(notifyTitle);
            builder.setContentText(notifyText);
            builder.setContentIntent(pendingIntent);
            NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(0, builder.build());
        }
        static class MyError extends RuntimeException
        {
            public com.google.android.gms.common.api.Status mStatus;
            public MyError(com.google.android.gms.common.api.Status status){
                super();
                mStatus = status;
            }
        }
        private void checkStatus(com.google.android.gms.common.api.Status status) throws MyError{
            if (!status.isSuccess())
                throw new MyError(status);
        }
    }
}
