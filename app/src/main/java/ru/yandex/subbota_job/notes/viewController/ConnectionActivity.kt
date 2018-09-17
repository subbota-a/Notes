package ru.yandex.subbota_job.notes.viewController

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import ru.yandex.subbota_job.notes.executor.ImportService
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import android.support.design.widget.Snackbar
import com.google.android.gms.tasks.Task
import android.support.annotation.NonNull
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.auth.*


class ConnectionActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val client = GoogleSignIn.getClient(this, ImportService.getGoogleSignInOptions())
		startActivityForResult(client.signInIntent, RC_SIGN_IN)
	}
	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		when(requestCode) {
			RC_SIGN_IN -> {
				val task = GoogleSignIn.getSignedInAccountFromIntent(data)
				Log.d("ConnectionActivity", "Google sign in ${task.isSuccessful}")
				if (task.isSuccessful)
				{
					val account = task.getResult(ApiException::class.java)
					firebaseAuthWithGoogle(account)
				}else
					finish()
			}
		}
	}

	private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
		val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
		FirebaseAuth.getInstance().signInWithCredential(credential)
				.addOnCompleteListener(this, OnCompleteListener<AuthResult> { task ->
					if (task.isSuccessful) {
						// Sign in success, update UI with the signed-in user's information
						Log.d("ConnectionActivity", "signInWithCredential:success")
						val intent = Intent(this, NotesListActivity::class.java)
								.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
								.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
						startActivity(intent)
					} else {
						Log.d("ConnectionActivity", "signInWithCredential:fail")
						finish()
					}
				})
	}

	companion object {
		private const val RC_SIGN_IN = 10;
	}
}
