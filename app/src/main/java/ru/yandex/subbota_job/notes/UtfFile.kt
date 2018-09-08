package ru.yandex.subbota_job.notes

import android.text.TextUtils

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
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

	fun Join(title: String, content: String): String {
		val sb = StringBuilder()
		if (!TextUtils.isEmpty(title))
			sb.append(beginTag).append(title).append(endTag)
		sb.append(content)
		return sb.toString()
	}

	@Throws(IOException::class)
	fun ReadAll(path: String): String {
		val inputStream = FileInputStream(path)
		try {
			val f = File(path)
			val data = ByteArray(f.length().toInt())
			val count = inputStream.read(data)
			val start: Int
			val encoding: Charset?
			if (count >= 3 && data[0] == 0xef.toByte() && data[1] == 0xbb.toByte() && data[2] == 0xbf.toByte()) {
				encoding = StandardCharsets.UTF_8
				start = 3
			} else if (count >= 2 && data[0] == 0xfe.toByte() && data[1] == 0xff.toByte()) {
				encoding = StandardCharsets.UTF_16BE
				start = 2
			} else if (count >= 2 && data[0] == 0xff.toByte() && data[1] == 0xfe.toByte()) {
				encoding = StandardCharsets.UTF_16LE
				start = 2
			} else {
				encoding = Charset.forName("windows-1251")
				start = 0
			}
			return String(data, start, count - start, encoding)
		} finally {
			inputStream.close()
		}
	}

	fun getLine(text: String): String {
		var n = text.indexOf('\n')
		var r = text.indexOf('\r')
		if (n == -1 && r == -1)
			return text
		if (n == -1)
			n = Integer.MAX_VALUE
		if (r == -1)
			r = Integer.MAX_VALUE
		return text.substring(0, Math.min(n, r))
	}

	@Throws(IOException::class)
	fun Write(path: String, data: String) {
		val outputStream = FileOutputStream(path)
		outputStream.write(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()))
		try {
			outputStream.write(data.toByteArray(charset("UTF-8")))
		} finally {
			outputStream.close()
		}
	}
}
