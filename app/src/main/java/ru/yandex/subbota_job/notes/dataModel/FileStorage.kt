package ru.yandex.subbota_job.notes.dataModel

import android.content.Context
import ru.yandex.subbota_job.notes.UtfFile
import java.io.File
import java.io.IOException
import java.util.*

class FileStorage(val context: Context) {
	private val NotesDirectory = "notes"
	private fun getOrAddDirectory(): File? {
		val dir = File(context.filesDir, NotesDirectory)
		dir.mkdirs()
		return if (dir.isDirectory) dir else null
	}
	fun read(): List<Note>?
	{
		val file = getOrAddDirectory() ?: return null
		val files = file.listFiles()
		Arrays.sort(files) { lhs, rhs ->
			// make recent first
			val dif = rhs.lastModified() - lhs.lastModified()
			if (dif < 0) -1 else if (dif > 0) 1 else 0
		}
		val ret = ArrayList<Note>()
		for (f in files) {
			try {
				val content = UtfFile.ReadAll(f.path)
				val item = Note()
				val pair = UtfFile.Split(content)
				item.title = pair[0] ?: ""
				item.body = pair[1] ?: ""
				item.modified = f.lastModified()
				ret.add(item)
			} catch (e: IOException) {
			}
		}
		return ret
	}
	fun clear()
	{
		val file = getOrAddDirectory() ?: return
		file.deleteRecursively()
	}
}