package ru.yandex.subbota_job.notes.dataModel

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Releasable
import com.google.android.gms.drive.Drive
import com.google.android.gms.drive.DriveFile
import com.google.android.gms.drive.DriveFolder
import com.google.android.gms.drive.metadata.CustomPropertyKey
import com.google.android.gms.drive.query.Filters
import com.google.android.gms.drive.query.Query
import com.google.android.gms.drive.query.SearchableField
import com.google.android.gms.tasks.Tasks
import ru.yandex.subbota_job.notes.UtfFile
import java.lang.Exception

interface Import{
	fun read(handler: (Note)->Unit)
}
interface ImportFactory{
	fun classKey() = "importClass"

	fun create(context: Context): Import
	fun addAuthOptions(options: GoogleSignInOptions.Builder): GoogleSignInOptions.Builder
}
class DriveStorageFactory: ImportFactory{
	override fun create(context: Context): Import {
		return DriveStorage(context)
	}

	override fun addAuthOptions(options: GoogleSignInOptions.Builder): GoogleSignInOptions.Builder {
		return options.requestScopes(Drive.SCOPE_FILE)
	}
}

class DriveStorage(val context: Context):Import {
	val account: GoogleSignInAccount = GoogleSignIn.getLastSignedInAccount(context)?.let{
		if (it.id != null && it.grantedScopes.contains(Drive.SCOPE_FILE)) it else null} ?: throw Exception("Требуется авторизация")
	val resourceClient = Drive.getDriveResourceClient(context, account)
	val customModifiedDate = CustomPropertyKey("NoteModifiedDate", 1)
	private class Holder<T>(private val mReleasable: Releasable?, private val mObj: T) : Releasable {
		fun get(): T {
			return mObj
		}
		override fun release() {
			mReleasable?.release()
		}
	}
	private fun getNotesFolder() : Holder<DriveFolder>?{
		val rootFolder = Tasks.await(resourceClient.rootFolder)!!
		val query = Query.Builder()
				.addFilter(Filters.eq(SearchableField.TITLE, "NotesBackup"))
				.addFilter(Filters.eq(SearchableField.TRASHED, false))
				.build()
		val result = Tasks.await(resourceClient.queryChildren(rootFolder,query))!!
		for(metadata in result)
			if (metadata.isFolder)
				return Holder(result, metadata.driveId.asDriveFolder())
		result.release()
		return null
	}
	override fun read(handler: (Note)->Unit)
	{
		val notesFolder = getNotesFolder() ?: return
		try {
			val query = Query.Builder()
					.addFilter(Filters.eq(SearchableField.MIME_TYPE, "text/plain"))
					.build()
			val buf = Tasks.await(resourceClient.queryChildren(notesFolder.get(), query))
			try {
				for (remoteFile in buf) {
					handler(downloadFile(remoteFile))
				}
			}finally {
				buf?.release()
			}
		} finally {
			notesFolder.release()
		}
	}
	private fun getModifiedDate(remoteFile: com.google.android.gms.drive.Metadata): Long {
		var remoteModifiedDate = remoteFile.modifiedDate.time
		val customPropValue = remoteFile.customProperties[customModifiedDate]
		if (!customPropValue.isNullOrEmpty())
			remoteModifiedDate = java.lang.Long.valueOf(customPropValue)
		return remoteModifiedDate
	}
	private fun downloadFile(remoteFile: com.google.android.gms.drive.Metadata):Note {
		Log.d("ImportService", "downloadFile " + remoteFile.title)
		val contentsResult = Tasks.await(resourceClient.openFile(remoteFile.driveId.asDriveFile(), DriveFile.MODE_READ_ONLY))!!
		val inputStream = contentsResult.inputStream
		try{
			val content =UtfFile.readFromStream(inputStream)
			val pair = UtfFile.Split(content)
			return Note().apply {
				title = pair[0] ?: ""
				body = pair[1] ?: ""
				makeTitle()
				modified = getModifiedDate(remoteFile)
				position = modified
			}
		}finally {
			inputStream.close()
			resourceClient.discardContents(contentsResult)
		}
	}
}