package ru.yandex.subbota_job.notes

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import android.util.Log

import com.google.android.gms.common.GoogleApiAvailability

class ConnectionActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val intent = this.intent
		if (intent == null) {
			Log.d("ConnectionActivity", "intent==null")
			finish()
			return
		}
		val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		mNotificationManager.cancel(0)
		val pendingIntent = intent.getParcelableExtra<PendingIntent>(PENDING_INTENT)
		val errCode = intent.getIntExtra(CONNECTION_ERROR, 0)
		if (pendingIntent != null)
			try {
				Log.d("ConnectionActivity", "startIntentSenderForResult")
				startIntentSenderForResult(pendingIntent.intentSender, 1, null as Intent?, 0, 0, 0)
			} catch (e: IntentSender.SendIntentException) {
				e.printStackTrace()
				finish()
			}
		else {
			GoogleApiAvailability.getInstance().showErrorDialogFragment(this, errCode, 0)
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
		super.onActivityResult(requestCode, resultCode, data)
		Log.d("ConnectionActivity", String.format("onActivityResult: requestCode=%d, resultCode=%d", requestCode, resultCode))
		if (requestCode == 1) {
			if (resultCode == AppCompatActivity.RESULT_OK)
				SyncService.restart(this)
			finish()
		}
	}

	companion object {
		val PENDING_INTENT = "PENDING_INTENT"
		val CONNECTION_ERROR = "CONNECTION_ERROR"
	}
}
