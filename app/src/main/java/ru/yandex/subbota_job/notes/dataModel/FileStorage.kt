package ru.yandex.subbota_job.notes.dataModel

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import ru.yandex.subbota_job.notes.UtfFile
import java.io.File
import java.io.IOException
import java.util.*

class FileStorageFactory: ImportFactory{
	override fun create(context: Context): Import {
		return FileStorage(context)
	}

	override fun addAuthOptions(options: GoogleSignInOptions.Builder): GoogleSignInOptions.Builder {
		return options
	}
}

class FileStorage(val context: Context) : Import{
	private val NotesDirectory = "notes"
	private fun getOrAddDirectory(): File? {
		val dir = File(context.filesDir, NotesDirectory)
		dir.mkdirs()
		return if (dir.isDirectory) dir else null
	}
	override fun read(handler: (Note)->Unit)
	{
		val file = getOrAddDirectory() ?: return
		val files = file.listFiles()
		Arrays.sort(files) { lhs, rhs ->
			// make recent first
			val dif = rhs.lastModified() - lhs.lastModified()
			if (dif < 0) -1 else if (dif > 0) 1 else 0
		}
		for (f in files) {
			try {
				val content = UtfFile.ReadAll(f.path)
				val item = Note()
				val pair = UtfFile.Split(content)
				item.title = pair[0] ?: ""
				item.body = pair[1] ?: ""
				item.modified = f.lastModified()
				item.position = item.modified
				handler(item)
			} catch (e: IOException) {
			}
		}
	}
}