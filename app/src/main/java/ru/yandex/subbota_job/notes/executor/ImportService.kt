package ru.yandex.subbota_job.notes.executor

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.Context
import android.os.Build
import android.support.v4.app.NotificationCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.drive.Drive
import ru.yandex.subbota_job.notes.R
import ru.yandex.subbota_job.notes.dataModel.DriveStorage
import ru.yandex.subbota_job.notes.dataModel.LocalDatabase
import ru.yandex.subbota_job.notes.viewController.ConnectionActivity
import ru.yandex.subbota_job.notes.viewController.NotesListActivity

private const val ACTION_FOO = "ru.yandex.subbota_job.notes.executor.action.IMPORT"

/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
class ImportService : IntentService("ImportService") {

	@SuppressLint("ApplySharedPref")
	override fun onHandleIntent(intent: Intent?) {
		createNotificationChannel()
		beginSyncNotification()
		try {
			val account = getAccount(this) ?: throw Exception("Требуется авторизация")
			if (getSharedPreferences(preferenceName, Context.MODE_PRIVATE).getString(importKey, null) == account.id)
				return;
			val externalStorage = DriveStorage(this, account)
			val dao = LocalDatabase.instance(this).noteEdition()
			externalStorage.read(){
				dao.insertNote(it)
			}
			getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().putString(importKey, account.id).commit()
		}catch(e:Exception){
			makeNotification("Импорт прошёл с ошибками", e.message)
		}finally {
			endSyncNotifycation()
		}
	}
	private fun createNotificationChannel() {
		// Create the NotificationChannel, but only on API 26+ because
		// the NotificationChannel class is new and not in the support library
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			var channel = NotificationChannel(ALARM_CHANNEL_ID, getString(R.string.alarm_channel_name), NotificationManager.IMPORTANCE_HIGH)
			channel.description = getString(R.string.alarm_channel_description)
			// Register the channel with the system; you can't change the importance
			// or other notification behaviors after this
			val notificationManager = getSystemService<NotificationManager>(NotificationManager::class.java!!)
			notificationManager!!.createNotificationChannel(channel)

			channel = NotificationChannel(PLAY_CHANNEL_ID, getString(R.string.running_channel_name), NotificationManager.IMPORTANCE_LOW)
			channel.description = getString(R.string.running_channel_description)
			notificationManager.createNotificationChannel(channel)
		}
	}

	private fun makeNotification(notifyTitle: String, notifyText: String?) {
		createNotificationChannel()
		val intent = Intent(this, NotesListActivity::class.java)
		intent.action = "ERROR"
		intent.putExtra("TITLE", notifyTitle)
		intent.putExtra("TEXT", notifyText)
		intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

		val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
		val builder = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
		builder.setSmallIcon(R.drawable.ic_warning_24dp)
		builder.setCategory(Notification.CATEGORY_ERROR)
		builder.setContentTitle(notifyTitle)
		builder.setContentText(notifyText)
		builder.setContentIntent(pendingIntent)
		val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		mNotificationManager.notify(0, builder.build())
	}

	private fun beginSyncNotification() {
		val builder = NotificationCompat.Builder(this, PLAY_CHANNEL_ID)
		builder.setSmallIcon(R.drawable.download)
		builder.setCategory(Notification.CATEGORY_SERVICE)
		builder.setContentTitle(getString(R.string.import_in_progress))
		builder.setContentText("")
		builder.setProgress(0, 0, true)
		//NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		//mNotificationManager.notify(1, builder.build());
		startForeground(1, builder.build())
	}

	private fun endSyncNotifycation() {
		stopForeground(true)
	}

	companion object {
		private const val preferenceName = "ru.yandex.subbota_job.notes.executor.ImportService"
		private const val importKey = "importKey"
		private val ALARM_CHANNEL_ID = "ru.yandex.subbota_job.alarm_channel_id"
		private val PLAY_CHANNEL_ID = "ru.yandex.subbota_job.play_channel_id"
		@JvmStatic
		fun startImport(context: Context, force:Boolean) {
			val account = getAccount(context) ?: throw Exception("You must be signed in before calling start import")
			if (force)
				context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().putString(importKey, null).commit()
			else if (context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).getString(importKey, null) == account.id)
				return;
			context.startService(Intent(context, ImportService::class.java))
		}
		fun getGoogleSignInOptions(): GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
				.requestScopes(Drive.SCOPE_FILE)
				.requestId()
				.requestIdToken("483166223054-s53j4s12ne4udmcu2qhtql0f0s6rb4rn.apps.googleusercontent.com")
				.build()
		fun getAccount(context: Context): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)?.let{
			if (it.id != null && it.grantedScopes.contains(Drive.SCOPE_FILE)) it else null
		}
	}
}
