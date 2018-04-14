package ru.yandex.subbota_job.notes;

import android.app.Notification;
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
import android.os.Parcel;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Releasable;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.metadata.CustomPropertyKey;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SyncService extends Service {
    //private static final int SyncAll = 1;
    private static final int OnFileChanged = 2;
    private static final int OnContinue = 3;
    private static final int Quit = 4;
    private static final String COMMAND = "COMMAND";
    private static final String SyncAllKey = "SyncAll";
    private Handler mHandler;

    public SyncService() {
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void onFileChanged(Context context, String path) {
        Log.d("SyncService", "OnFileChanged");
        Intent intent = new Intent(context, SyncService.class);
        intent.setData(Uri.fromFile(new File(path)));
        intent.putExtra(COMMAND, OnFileChanged);
        context.startService(intent);
    }

    public static void restart(Context context) {
        Log.d("SyncService", "OnContinue");
        Intent intent = new Intent(context, SyncService.class);
        intent.putExtra(COMMAND, OnContinue);
        context.startService(intent);
    }

    public static void syncAll(Context context) {
        Log.d("SyncService", "SyncAll");
        setSyncAll(context, true);
        restart(context);
    }

    private static boolean getSyncAll(Context context){
        return context.getSharedPreferences("ru.yandex.subbota_job.notes", Context.MODE_PRIVATE)
                .getBoolean(SyncAllKey, false);
    }
    private static void setSyncAll(Context context, boolean syncAll){
        context.getSharedPreferences("ru.yandex.subbota_job.notes", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(SyncAllKey, syncAll)
                .commit();
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
        int command = OnContinue;
        String path = null;
        if (intent != null) {
            command = intent.getIntExtra(COMMAND, OnContinue);
            Uri uri = intent.getData();
            if (uri!=null)
                path = uri.getPath();
        }
        mHandler.sendMessage(Message.obtain(mHandler, command, startId, 0, path));
        return START_STICKY;
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
                    mContext.stopSelf(msg.arg1);
                    return true;
                case Quit:
                    if (!mUpdatedFiles.isEmpty())
                        setSyncAll(mContext, true);
                    mClient.disconnect();
                    ((HandlerThread)Thread.currentThread()).quit();
                    return true;
                default:
                    mContext.stopSelf(msg.arg1);
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
            Log.d("SyncService", "trying connect...");
            ConnectionResult result = mClient.blockingConnect();
            if (result.isSuccess())
                return true;
            Log.d("SyncService", GoogleApiAvailability.getInstance().getErrorString(result.getErrorCode()));
            makeNotification(result.getResolution(), result.getErrorCode()
                    , mContext.getString(R.string.connection_failed_title)
                    , GoogleApiAvailability.getInstance().getErrorString(result.getErrorCode()));
            mResolvePending = true;
            return false;
        }
        private void onResolved() {
            if (!connect())
                return;

            Log.d("SyncService", "Connected");
            boolean isSyncAll = getSyncAll(mContext);
            try {
                beginSyncNotification();
                if (isSyncAll) {
                    if (!syncAll())
                        return;
                }
                for (Pair<String, Integer> p = mUpdatedFiles.peek(); p != null; p = mUpdatedFiles.peek()) {
                    if (!(isSyncAll || uploadFile(p.first)))
                        return;
                    mUpdatedFiles.remove();
                    mContext.stopSelf(p.second);
                    Log.d("SyncService", String.format("stopSelf(%d)", p.second));
                }
            }finally {
                endSyncNotifycation();
            }
        }

        long getModifiedDate(Metadata remoteFile){
            long remoteModifiedDate = remoteFile.getModifiedDate().getTime();
            String customPropValue = remoteFile.getCustomProperties().get(getCustomModifiedDate());
            if (!TextUtils.isEmpty(customPropValue))
                remoteModifiedDate = Long.valueOf(customPropValue);
            return remoteModifiedDate;
        }
        private boolean syncAll() {
            DriveApi.MetadataBufferResult bufferResult = null;
            Holder<DriveFolder> folder = null;
            try {
                Log.d("SyncService", "syncAll");
                File dir = NotesListAdapter.getOrAddDirectory(mContext);
                if (dir == null)
                    return false;
                Map<String, File> localFiles = new HashMap<String, File>();
                for (File f : dir.listFiles()) {
                    localFiles.put(f.getName(), f);
                }
                folder = getFolder();
                Query query = new Query.Builder()
                        .addFilter(Filters.eq(SearchableField.MIME_TYPE, mContext.getString(R.string.noteMimeType)))
                        .build();
                bufferResult = folder.get().queryChildren(mClient, query).await();
                checkStatus(bufferResult.getStatus());
                MetadataBuffer buf = bufferResult.getMetadataBuffer();
                for (int i = 0; i < buf.getCount(); ++i) {
                    Metadata remoteFile = buf.get(i);
                    File localFile = localFiles.get(remoteFile.getTitle());
                    if (localFile != null) {
                        long remoteModifiedDate = getModifiedDate(remoteFile) / 10000L;
                        long localModifiedDate = localFile.lastModified() / 10000L;
                        Log.d("SyncService", String.format("%s: remote %s local %s"
                                , remoteFile.getTitle()
                                , new Date(remoteModifiedDate*10000L).toString()
                                , new Date(localModifiedDate*10000L).toString()));
                        // С точностью до 10сек
                        if (remoteModifiedDate< localModifiedDate)
                            uploadFile(folder.get(), localFile, remoteFile.getDriveId().asDriveFile());
                        else if (remoteModifiedDate > localModifiedDate)
                            downloadFile(dir, remoteFile);
                        localFiles.remove(remoteFile.getTitle());
                    } else {
                        downloadFile(dir, remoteFile);
                    }
                }
                for (File localFile : localFiles.values())
                    uploadFile(localFile.getPath());
                setSyncAll(mContext, false);
                return true;
            }catch(MyError e){
                makeNotification(e.mStatus.getResolution(), e.mStatus.getStatusCode(), mContext.getString(R.string.operation_failed_title), e.mStatus.getStatusMessage());
                mResolvePending = true;
                mClient.disconnect();
                return false;
            }catch(IOException e){
                return true;
            }finally {
                if (bufferResult != null)
                    bufferResult.release();
                if (folder != null)
                    folder.release();
            }
        }

        private void downloadFile(File dir, Metadata remoteFile) throws IOException {
            Log.d("SyncService", "downloadFile "+remoteFile.getTitle());
            DriveApi.DriveContentsResult contentsResult = remoteFile.getDriveId().asDriveFile()
                    .open(mClient, DriveFile.MODE_READ_ONLY, null).await();
            checkStatus(contentsResult.getStatus());
            InputStream inputStream = contentsResult.getDriveContents().getInputStream();
            File dest = new File(dir, remoteFile.getTitle());
            OutputStream outputStream = new FileOutputStream(dest);
            copyContents(inputStream, outputStream);
            outputStream.close();
            dest.setLastModified(getModifiedDate(remoteFile));
        }

        private CustomPropertyKey getCustomModifiedDate()
        {
            return new CustomPropertyKey("NoteModifiedDate", 1);
        }
        private void uploadFile(DriveFolder folder, File localFile, DriveFile driveFile) throws IOException
        {
            Log.d("SyncService", "uploadFile "+localFile.getName());
            if (localFile.exists())
            {
                DriveApi.DriveContentsResult contentsResult = null;
                if (driveFile != null) {
                    contentsResult = driveFile.open(mClient, DriveFile.MODE_WRITE_ONLY, null).await();
                }else{
                    contentsResult = Drive.DriveApi.newDriveContents(mClient).await();
                }
                checkStatus(contentsResult.getStatus());
                copyContents(new FileInputStream(localFile), contentsResult.getDriveContents().getOutputStream());
                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                        .setTitle(localFile.getName())
                        .setMimeType(mContext.getString(R.string.noteMimeType))
                        .setCustomProperty(getCustomModifiedDate(), String.valueOf(localFile.lastModified()))
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
        }
        private boolean uploadFile(String path) {
            Holder<DriveFolder> folder = null;
            try {
                folder = getFolder();
                File f = new File(path);
                DriveFile driveFile = searchFile(folder.get(), f.getName());
                uploadFile(folder.get(), f, driveFile);
                return true;
            }catch(MyError e){
                makeNotification(e.mStatus.getResolution(), e.mStatus.getStatusCode(), mContext.getString(R.string.operation_failed_title), e.mStatus.getStatusMessage());
                mResolvePending = true;
                mClient.disconnect();
                return false;
            }catch(IOException e){
                return true;
            }finally {
                if (folder != null)
                    folder.release();
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
            if (buf.getCount() > 0)
                return buf.get(0).getDriveId().asDriveFile();
            return null;
        }

        private static class Holder<T> implements Releasable {
            private final Releasable mReleasable;
            private final T mObj;
            public Holder(Releasable releasable, T obj){
                mReleasable = releasable;
                mObj = obj;
            }

            public T get(){ return mObj; }
            @Override
            public void release() {
                if (mReleasable != null)
                    mReleasable.release();
            }
        }
        private Holder<DriveFolder> getFolder()
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
                        return new Holder(result, result.getMetadataBuffer().get(i).getDriveId().asDriveFolder());
            }
            {// create one
                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                        .setTitle(folderName)
                        .build();
                DriveFolder.DriveFolderResult result = Drive.DriveApi.getRootFolder(mClient).createFolder(mClient, changeSet).await();
                checkStatus(result.getStatus());
                return new Holder(null, result.getDriveFolder());
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
            builder.setCategory(Notification.CATEGORY_ERROR);
            builder.setContentTitle(notifyTitle);
            builder.setContentText(notifyText);
            builder.setContentIntent(pendingIntent);
            NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(0, builder.build());
        }
        private void beginSyncNotification()
        {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext);
            builder.setSmallIcon(R.drawable.ic_sync_24dp);
            builder.setCategory(Notification.CATEGORY_SERVICE);
            builder.setContentTitle(mContext.getString(R.string.sync_in_progress));
            builder.setContentText("");
            builder.setProgress(0, 0, true);
            NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(1, builder.build());
        }
        private void endSyncNotifycation()
        {
            NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(1);
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
