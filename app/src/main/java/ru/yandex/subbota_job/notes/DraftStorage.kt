package ru.yandex.subbota_job.notes

import android.content.Context
import android.text.TextUtils

/**
 * Created by subbota on 27.09.2016.
 */

internal class DraftStorage(private val mContext: Context) {
	private val keyPath = this.javaClass.toString() + "path"
	private val keyContent = this.javaClass.toString() + "content"
	private val preferenceName = this.javaClass.toString()
	val draftPath: String
		get() = mContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).getString(keyPath, "")
	val draftContent: String
		get() = mContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).getString(keyContent, "")

	fun saveDraft(path: String, content: String) {
		mContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit()
				.putString(keyPath, path).putString(keyContent, content).apply()
	}

	fun clearDraft() {
		mContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().clear().apply()
	}
}
