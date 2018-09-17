package ru.yandex.subbota_job.notes

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SyncService : Service() {
	//private var mHandler: Handler? = null
	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	/*
	override fun onCreate() {
		Log.d("SyncService", "onCreate")
		val mThread = HandlerThread("SyncService.Thread")
		mThread.start()
		mHandler = Handler(mThread.looper, Impl(this))
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Log.d("SyncService", "onStartCommand")
		var command = OnContinue
		var path: String? = null
		if (intent != null) {
			command = intent.getIntExtra(COMMAND, OnContinue)
			val uri = intent.data
			if (uri != null)
				path = uri.path
		}
		mHandler!!.sendMessage(Message.obtain(mHandler, command, startId, 0, path))
		return Service.START_STICKY
	}

	override fun onDestroy() {
		Log.d("SyncService", "onDestroy")
		mHandler!!.sendEmptyMessage(Quit)
	}

	internal class Impl(private val mContext: Service) : Handler.Callback {
		private val mClient: GoogleApiClient
		private var mResolvePending = false
		private val mUpdatedFiles = LinkedBlockingQueue<Pair<String, Int>>()

		private val customModifiedDate: CustomPropertyKey
			get() = CustomPropertyKey("NoteModifiedDate", 1)
		private// search one
		// create one
		val folder: Holder<DriveFolder>
			get() {
				val folderName = mContext.getString(R.string.google_drive_folder)
				run {
					val query = Query.Builder()
							.addFilter(Filters.eq(SearchableField.TITLE, folderName))
							.addFilter(Filters.eq(SearchableField.TRASHED, false))
							.build()
					val result = Drive.DriveApi.getRootFolder(mClient)!!.queryChildren(mClient, query).await()
					checkStatus(result.status)
					val buf = result.metadataBuffer
					for (i in 0 until buf.count)
						if (buf.get(i).isFolder)
							return Holder(result, result.metadataBuffer.get(i).driveId.asDriveFolder())
				}
				run {
					val changeSet = MetadataChangeSet.Builder()
							.setTitle(folderName)
							.build()
					val result = Drive.DriveApi.getRootFolder(mClient)!!.createFolder(mClient, changeSet).await()
					checkStatus(result.status)
					return Holder(null, result.driveFolder)
				}
			}

		init {
			mClient = GoogleApiClient.Builder(mContext)
					.addApi(Drive.API)
					.addScope(Drive.SCOPE_FILE)
					.build()
		}

		override fun handleMessage(msg: Message): Boolean {
			when (msg.what) {
				OnFileChanged -> {
					addFile(Pair(msg.obj as String, msg.arg1))
					return true
				}
				OnContinue -> {
					onResolved()
					mContext.stopSelf(msg.arg1)
					return true
				}
				Quit -> {
					if (!mUpdatedFiles.isEmpty())
						setSyncAll(mContext, true)
					mClient.disconnect()
					(Thread.currentThread() as HandlerThread).quit()
					return true
				}
				else -> mContext.stopSelf(msg.arg1)
			}
			return false
		}

		private fun addFile(obj: Pair<String, Int>) {
			mUpdatedFiles.add(obj)
			if (mResolvePending)
				return
			onResolved()
		}

		private fun connect(): Boolean {
			if (mClient.isConnected)
				return true
			mResolvePending = false
			Log.d("SyncService", "trying connect...")
			val result = mClient.blockingConnect()
			if (result.isSuccess)
				return true
			Log.d("SyncService", GoogleApiAvailability.getInstance().getErrorString(result.errorCode))
			makeNotification(result.resolution, result.errorCode, mContext.getString(R.string.connection_failed_title), GoogleApiAvailability.getInstance().getErrorString(result.errorCode))
			mResolvePending = true
			return false
		}

		private fun onResolved() {
			if (!connect())
				return

			Log.d("SyncService", "Connected")
			val isSyncAll = getSyncAll(mContext)
			try {
				beginSyncNotification()
				if (isSyncAll) {
					if (!syncAll())
						return
				}
				var p: Pair<String, Int>? = mUpdatedFiles.peek()
				while (p != null) {
					if (!(isSyncAll || uploadFile(p.first)))
						return
					mUpdatedFiles.remove()
					mContext.stopSelf(p.second)
					Log.d("SyncService", String.format("stopSelf(%d)", p.second))
					p = mUpdatedFiles.peek()
				}
			} finally {
				endSyncNotifycation()
			}
		}

		fun getModifiedDate(remoteFile: Metadata): Long {
			var remoteModifiedDate = remoteFile.modifiedDate.time
			val customPropValue = remoteFile.customProperties[customModifiedDate]
			if (!TextUtils.isEmpty(customPropValue))
				remoteModifiedDate = java.lang.Long.valueOf(customPropValue)
			return remoteModifiedDate
		}

		private fun syncAll(): Boolean {
			var bufferResult: DriveApi.MetadataBufferResult? = null
			var folder: Holder<DriveFolder>? = null
			try {
				Log.d("SyncService", "syncAll")
				val dir = getOrAddDirectory(mContext) ?: return false
				val localFiles = HashMap<String, File>()
				for (f in dir.listFiles()) {
					localFiles[f.name] = f
				}
				folder = this.folder
				val query = Query.Builder()
						.addFilter(Filters.eq(SearchableField.MIME_TYPE, mContext.getString(R.string.noteMimeType)))
						.build()
				bufferResult = folder!!.get().queryChildren(mClient, query).await()
				checkStatus(bufferResult.status)
				val buf = bufferResult.metadataBuffer
				for (i in 0 until buf.count) {
					val remoteFile = buf.get(i)
					val localFile = localFiles[remoteFile.title]
					if (localFile != null) {
						val remoteModifiedDate = getModifiedDate(remoteFile) / 10000L
						val localModifiedDate = localFile.modified() / 10000L
						Log.d("SyncService", String.format("%s: remote %s local %s", remoteFile.title, Date(remoteModifiedDate * 10000L).toString(), Date(localModifiedDate * 10000L).toString()))
						// С точностью до 10сек
						if (remoteModifiedDate < localModifiedDate)
							uploadFile(folder.get(), localFile, remoteFile.driveId.asDriveFile())
						else if (remoteModifiedDate > localModifiedDate)
							downloadFile(dir, remoteFile)
						localFiles.remove(remoteFile.title)
					} else {
						downloadFile(dir, remoteFile)
					}
				}
				for (localFile in localFiles.values)
					uploadFile(localFile.path)
				setSyncAll(mContext, false)
				return true
			} catch (e: MyError) {
				makeNotification(e.mStatus.resolution, e.mStatus.statusCode, mContext.getString(R.string.operation_failed_title), e.mStatus.statusMessage)
				mResolvePending = true
				mClient.disconnect()
				return false
			} catch (e: IOException) {
				return true
			} finally {
				bufferResult?.release()
				folder?.release()
			}
		}

		@Throws(IOException::class)
		private fun downloadFile(dir: File, remoteFile: Metadata) {
			Log.d("SyncService", "downloadFile " + remoteFile.title)
			val contentsResult = remoteFile.driveId.asDriveFile()
					.open(mClient, DriveFile.MODE_READ_ONLY, null).await()
			checkStatus(contentsResult.status)
			val inputStream = contentsResult.driveContents.inputStream
			val dest = File(dir, remoteFile.title)
			val outputStream = FileOutputStream(dest)
			copyContents(inputStream, outputStream)
			outputStream.close()
			dest.setModified(getModifiedDate(remoteFile))
		}

		@Throws(IOException::class)
		private fun uploadFile(folder: DriveFolder, localFile: File, driveFile: DriveFile?) {
			Log.d("SyncService", "uploadFile " + localFile.name)
			if (localFile.exists()) {
				var contentsResult: DriveApi.DriveContentsResult? = null
				if (driveFile != null) {
					contentsResult = driveFile.open(mClient, DriveFile.MODE_WRITE_ONLY, null).await()
				} else {
					contentsResult = Drive.DriveApi.newDriveContents(mClient).await()
				}
				checkStatus(contentsResult.status)
				copyContents(FileInputStream(localFile), contentsResult.driveContents.outputStream)
				val changeSet = MetadataChangeSet.Builder()
						.setTitle(localFile.name)
						.setMimeType(mContext.getString(R.string.noteMimeType))
						.setCustomProperty(customModifiedDate, localFile.modified().toString())
						.build()
				if (driveFile != null) {
					checkStatus(contentsResult.driveContents.commit(mClient, null).await())
				} else {
					val t = folder.createFile(mClient, changeSet, contentsResult.driveContents).await()
					checkStatus(t.status)
				}
			} else driveFile?.delete(mClient)?.await()
		}

		private fun uploadFile(path: String): Boolean {
			var folder: Holder<DriveFolder>? = null
			try {
				folder = this.folder
				val f = File(path)
				val driveFile = searchFile(folder!!.get(), f.name)
				uploadFile(folder.get(), f, driveFile)
				return true
			} catch (e: MyError) {
				makeNotification(e.mStatus.resolution, e.mStatus.statusCode, mContext.getString(R.string.operation_failed_title), e.mStatus.statusMessage)
				mResolvePending = true
				mClient.disconnect()
				return false
			} catch (e: IOException) {
				return true
			} finally {
				folder?.release()
			}
		}

		@Throws(IOException::class)
		private fun copyContents(inputStream: InputStream, outputStream: OutputStream) {
			var ch: Int = inputStream.read()
			while (ch  != -1) {
				outputStream.write(ch)
				ch = inputStream.read()
			}
			inputStream.close()
		}

		private fun searchFile(folder: DriveFolder, name: String): DriveFile? {
			val query = Query.Builder().addFilter(Filters.eq(SearchableField.TITLE, name))
					.addFilter(Filters.eq(SearchableField.MIME_TYPE, mContext.getString(R.string.noteMimeType)))
					.build()
			val bufferResult = folder.queryChildren(mClient, query).await()
			checkStatus(bufferResult.status)
			val buf = bufferResult.metadataBuffer
			return if (buf.count > 0) buf.get(0).driveId.asDriveFile() else null
		}

		private class Holder<T>(private val mReleasable: Releasable?, private val mObj: T) : Releasable {

			fun get(): T {
				return mObj
			}

			override fun release() {
				mReleasable?.release()
			}
		}

		private fun createNotificationChannel() {
			// Create the NotificationChannel, but only on API 26+ because
			// the NotificationChannel class is new and not in the support library
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				var channel = NotificationChannel(ALARM_CHANNEL_ID, mContext.getString(R.string.alarm_channel_name), NotificationManager.IMPORTANCE_HIGH)
				channel.description = mContext.getString(R.string.alarm_channel_description)
				// Register the channel with the system; you can't change the importance
				// or other notification behaviors after this
				val notificationManager = mContext.getSystemService<NotificationManager>(NotificationManager::class.java!!)
				notificationManager!!.createNotificationChannel(channel)

				channel = NotificationChannel(ACTION_CHANNEL_ID, mContext.getString(R.string.running_channel_name), NotificationManager.IMPORTANCE_LOW)
				channel.description = mContext.getString(R.string.running_channel_description)
				notificationManager.createNotificationChannel(channel)
			}
		}

		private fun makeNotification(resolution: PendingIntent?, errCode: Int, notifyTitle: String, notifyText: String?) {
			createNotificationChannel()
			val intent = Intent(mContext, ConnectionActivity::class.java)
			intent.putExtra(ConnectionActivity.PENDING_INTENT, resolution)
			intent.putExtra(ConnectionActivity.CONNECTION_ERROR, errCode)
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

			val pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0)
			val builder = NotificationCompat.Builder(mContext, ALARM_CHANNEL_ID)
			builder.setSmallIcon(R.drawable.ic_warning_24dp)
			builder.setCategory(Notification.CATEGORY_ERROR)
			builder.setContentTitle(notifyTitle)
			builder.setContentText(notifyText)
			builder.setContentIntent(pendingIntent)
			val mNotificationManager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			mNotificationManager.notify(0, builder.build())
		}

		private fun beginSyncNotification() {
			val builder = NotificationCompat.Builder(mContext, ACTION_CHANNEL_ID)
			builder.setSmallIcon(R.drawable.ic_sync_24dp)
			builder.setCategory(Notification.CATEGORY_SERVICE)
			builder.setContentTitle(mContext.getString(R.string.sync_in_progress))
			builder.setContentText("")
			builder.setProgress(0, 0, true)
			//NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
			//mNotificationManager.notify(1, builder.build());
			mContext.startForeground(1, builder.build())
		}

		private fun endSyncNotifycation() {
			//NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
			//mNotificationManager.cancel(1);
			mContext.stopForeground(true)
		}

		internal class MyError(var mStatus: com.google.android.gms.common.api.Status) : RuntimeException()

		@Throws(MyError::class)
		private fun checkStatus(status: com.google.android.gms.common.api.Status) {
			if (!status.isSuccess)
				throw MyError(status)
		}

		companion object {
			private val ALARM_CHANNEL_ID = "ru.yandex.subbota_job.alarm_channel_id"
			private val ACTION_CHANNEL_ID = "ru.yandex.subbota_job.action_channel_id"
		}
	}

	companion object {
		//private static final int SyncAll = 1;
		private val OnFileChanged = 2
		private val OnContinue = 3
		private val Quit = 4
		private val COMMAND = "COMMAND"
		private val SyncAllKey = "SyncAll"

		fun isSyncAvailable(context: Context): Boolean {
			val client = GoogleApiAvailability.getInstance()
			return client.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
		}

		fun showAvailableError(context: AppCompatActivity, req: Int) {
			val client = GoogleApiAvailability.getInstance()
			val dlg = client.getErrorDialog(context, client.isGooglePlayServicesAvailable(context), req)
			SupportErrorDialogFragment.newInstance(dlg).show(context.supportFragmentManager, "")
		}

		fun onFileChanged(context: Context, path: String) {
			Log.d("SyncService", "OnFileChanged")
			if (!isSyncAvailable(context))
				return
			val intent = Intent(context, SyncService::class.java)
			intent.data = Uri.fromFile(File(path))
			intent.putExtra(COMMAND, OnFileChanged)
			context.startService(intent)
		}

		fun restart(context: Context) {
			Log.d("SyncService", "OnContinue")
			if (!isSyncAvailable(context))
				return
			val intent = Intent(context, SyncService::class.java)
			intent.putExtra(COMMAND, OnContinue)
			context.startService(intent)
		}

		fun syncAll(context: Context) {
			Log.d("SyncService", "SyncAll")
			if (!isSyncAvailable(context))
				return
			setSyncAll(context, true)
			restart(context)
		}

		private fun getSyncAll(context: Context): Boolean {
			return context.getSharedPreferences("ru.yandex.subbota_job.notes", Context.MODE_PRIVATE)
					.getBoolean(SyncAllKey, false)
		}

		private fun setSyncAll(context: Context, syncAll: Boolean) {
			context.getSharedPreferences("ru.yandex.subbota_job.notes", Context.MODE_PRIVATE)
					.edit()
					.putBoolean(SyncAllKey, syncAll)
					.commit()
		}
	}
	*/
}
