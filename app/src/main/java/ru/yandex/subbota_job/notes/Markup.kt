package ru.yandex.subbota_job.notes

import android.graphics.Typeface
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.*
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.StringReader
import java.io.StringWriter
import java.util.*

/**
 * Markup supports the following tags
 *
 */
class Markup {
	private class StyleInfo(val style:Any?, val startPos: Int){
		var endPos = 0
		var count = 0
	}
	companion object {
		val logTag = "Markup"
		fun fromString(rawText: String?): SpannableStringBuilder
		{
			val spannable = SpannableStringBuilder()
			if (rawText.isNullOrEmpty())
				return spannable
			if (!rawText!!.startsWith("<?xml")) {
				spannable.append(rawText)
				return spannable
			}
			val parser = Xml.newPullParser()
			parser.setInput(StringReader(rawText))
			var eventType = parser.eventType
			val map = HashMap<String, StyleInfo>()
			val list = ArrayList<StyleInfo>()
			while(eventType != XmlPullParser.END_DOCUMENT){
				when (eventType){
					XmlPullParser.TEXT -> {
						spannable.append(parser.text)
					}
					XmlPullParser.START_TAG ->{
						var style : Any? = null
						val tag = parser.name
						when(tag){
							"b" -> style = StyleSpan(Typeface.BOLD)
							"i" -> style = StyleSpan(Typeface.ITALIC)
							"s" -> style = StrikethroughSpan()
							"u" -> style = UnderlineSpan()
							"span" -> {
								for(i in 0 until parser.attributeCount)
									when (parser.getAttributeName(i)){
										"color" -> {
											parseColor(parser.getAttributeValue(i))?.also{
												style = ForegroundColorSpan(it)
											}
										}
										"background-color" -> {
											parseColor(parser.getAttributeValue(i))?.also{
												style = BackgroundColorSpan(it)
											}
										}
										"font-size-adjust" -> {
											parser.getAttributeValue(i).toFloatOrNull()?.also{
												style = RelativeSizeSpan(it)
											}
										}
									}
							}
						}
						if (style != null) {
							var styleInfo = map.get(tag)
							if (styleInfo == null) {
								styleInfo = StyleInfo(style, spannable.length)
								map.put(tag, styleInfo)
							}
							styleInfo!!.count++
						}
					}
					XmlPullParser.END_TAG ->{
						val tag = parser.name
						val styleInfo = map.get(tag)
						if (styleInfo!=null) {
							styleInfo.endPos = spannable.length
							if (--styleInfo.count == 0) {
								list.add(styleInfo)
								map.remove(tag)
							}
						}
					}
				}
				eventType = parser.next()
			}
			for(info in list)
				if (info.endPos > info.startPos)
					spannable.setSpan(info.style, info.startPos, info.endPos, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
			return  spannable
		}

		private fun parseColor(color: String): Int?{
			if (color[0]!='#')
				return null
			return color.substring(1).toLongOrNull(16)?.toInt()
		}
		private fun colorFormat(color:Int):String = String.format("#%08x",color)

		private fun serializeSpan(spans: Array<CharacterStyle>, handler: (tagName:String, attr: Map<String,String>?)->Unit )
		{
			for(span in spans) {
				when (span) {
					is StyleSpan -> when(span.style){
						Typeface.BOLD -> handler("b", null)
						Typeface.ITALIC -> handler("i", null)
					}
					is StrikethroughSpan -> handler("s", null)
					is UnderlineSpan -> handler("u", null)
					is BackgroundColorSpan -> handler("span", mapOf(Pair("background-color", colorFormat(span.backgroundColor))))
					is ForegroundColorSpan -> handler("span", mapOf(Pair("color", colorFormat(span.foregroundColor))))
					is RelativeSizeSpan -> handler("span", mapOf(Pair("font-size-adjust", span.sizeChange.toString())))
				}
			}
		}
		fun toString(spannable: SpannableStringBuilder) : String
		{
			val len = spannable.length
			if (len==0)
				return ""
			val wrt = StringWriter()
			val ser = Xml.newSerializer()
			ser.setOutput(wrt)
			var i = 0
			ser.startDocument("utf-8", true)
			ser.startTag("", "body")
			while(i<len){
				val next = spannable.nextSpanTransition(i, len, CharacterStyle::class.java)
				val spans = spannable.getSpans(i, next, CharacterStyle::class.java)
				serializeSpan(spans){ tagName, attr ->
					ser.startTag("", tagName)
					attr?.forEach() {
						ser.attribute("", it.key, it.value)
					}
				}
				ser.text(spannable.substring(i, next))
				spans.reverse() // close tag in backward direction
				serializeSpan(spans){ tagName, _ ->
					ser.endTag("", tagName)
				}
				i=next
			}
			ser.endTag("", "body")
			ser.endDocument()
			ser.flush()
			return wrt.toString()
		}
	}
}