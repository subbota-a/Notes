package ru.yandex.subbota_job.notes

import android.text.TextUtils
import java.io.*
import java.lang.StringBuilder
import java.nio.ByteBuffer

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object UtfFile {
	internal val beginTag = "<title>"
	internal val endTag = "</title>"
	fun Split(fileContent: String): Array<String?> {
		val ret = arrayOfNulls<String>(2)
		val endTagPos = fileContent.   indexOf(endTag)
		if (fileContent.startsWith(beginTag) && endTagPos >= 0) {
			ret[0] = fileContent.substring(beginTag.length, endTagPos)
			ret[1] = fileContent.substring(endTagPos + endTag.length)
		} else
			ret[1] = fileContent
		return ret
	}

	fun readFromStream(stm: InputStream):String{
		val buf = ArrayList<Byte>()
		do{
			val ch:Int = stm.read()
			if (ch >=0)
				buf.add(ch.toByte())
		}while (ch >=0)
		return readFromByteArray(buf.toByteArray())
	}

	fun readFromByteArray(data: ByteArray): String {
		val start: Int
		val encoding: Charset?
		if (data.size >= 3 && data[0] == 0xef.toByte() && data[1] == 0xbb.toByte() && data[2] == 0xbf.toByte()) {
			encoding = StandardCharsets.UTF_8
			start = 3
		} else if (data.size >= 2 && data[0] == 0xfe.toByte() && data[1] == 0xff.toByte()) {
			encoding = StandardCharsets.UTF_16BE
			start = 2
		} else if (data.size >= 2 && data[0] == 0xff.toByte() && data[1] == 0xfe.toByte()) {
			encoding = StandardCharsets.UTF_16LE
			start = 2
		} else {
			encoding = Charset.forName("windows-1251")
			start = 0
		}
		return String(data, start, data.size - start, encoding)
	}


	@Throws(IOException::class)
	fun ReadAll(path: String): String {
		val inputStream = FileInputStream(path)
		try {
			return readFromStream(inputStream)
		} finally {
			inputStream.close()
		}
	}
}
