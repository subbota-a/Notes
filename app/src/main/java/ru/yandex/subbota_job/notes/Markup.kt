package ru.yandex.subbota_job.notes

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.*
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.io.StringWriter
import java.util.*

/**
 * Markup supports the following tags
 *
 */
class Markup {
	private class ToString(val spannable: SpannableStringBuilder){
		private val ser = Xml.newSerializer()
		private fun paragraphStyle()
		{
			val len = spannable.length
			var i = 0
			while (i < len) {
				val next = spannable.nextSpanTransition(i, len, ParagraphStyle::class.java)
				val spans = spannable.getSpans(i, next, ParagraphStyle::class.java)
				serializeSpan(spans) { tagName, attr ->
					ser.startTag("", tagName)
					attr?.forEach() {
						ser.attribute("", it.key, it.value)
					}
				}
				characterStyle(i, next)
				spans.reverse() // close tag in backward direction
				serializeSpan(spans) { tagName, _ ->
					ser.endTag("", tagName)
				}
				i = next
			}

		}
		private fun characterStyle(start: Int, end: Int) {
			var i = start
			while (i < end) {
				val next = spannable.nextSpanTransition(i, end, CharacterStyle::class.java)
				val spans = spannable.getSpans(i, next, CharacterStyle::class.java).filter{
					spannable.getSpanStart(it)<=i && spannable.getSpanEnd(it)>=next
				}.toMutableList()
				Log.d(logTag, "${spans.size} spans ($i,$next)")
				for(x in spans){
					Log.d(logTag, "${x.toString()} -> ${String.format("0x%x",spannable.getSpanFlags(x))} -> (${spannable.getSpanStart(x)}, ${spannable.getSpanEnd(x)})")
				}
				serializeSpan(spans) { tagName, attr ->
					ser.startTag("", tagName)
					attr?.forEach() {
						ser.attribute("", it.key, it.value)
					}
				}
				ser.text(spannable.substring(i, next))
				spans.reverse() // close tag in backward direction
				serializeSpan(spans) { tagName, _ ->
					ser.endTag("", tagName)
				}
				i = next
			}
		}

		fun proceed(): String {
			if (spannable.isEmpty())
				return ""
			val wrt = StringWriter()
			ser.setOutput(wrt)
			ser.startDocument("utf-8", true)
			ser.startTag("", "body")
			paragraphStyle()
			ser.endTag("", "body")
			ser.endDocument()
			ser.flush()
			Log.d(logTag, wrt.toString())
			return wrt.toString()
		}
		private fun serializeSpan(spans: Iterable<Any>, handler: (tagName:String, attr: Map<String,String>?)->Unit )
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
					is AlignmentSpan.Standard -> handler("p", mapOf(Pair("align", when (span.alignment) {
						Layout.Alignment.ALIGN_NORMAL->"left"
						Layout.Alignment.ALIGN_CENTER->"center"
						Layout.Alignment.ALIGN_OPPOSITE->"right"})))
					else -> Log.d(logTag, "Unknown span ${span.toString()}")
				}
			}
		}
	}
	companion object {
		private class StyleInfo(val style:Any?, val startPos: Int){
			var endPos = 0
		}
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
			val map = HashMap<String, Stack<StyleInfo>>()
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
								loop@ for(i in 0 until parser.attributeCount)
									when (parser.getAttributeName(i)){
										"color" -> {
											parseColor(parser.getAttributeValue(i))?.also{
												style = ForegroundColorSpan(it)
											}
											break@loop
										}
										"background-color" -> {
											parseColor(parser.getAttributeValue(i))?.also{
												style = BackgroundColorSpan(it)
											}
											break@loop
										}
										"font-size-adjust" -> {
											parser.getAttributeValue(i).toFloatOrNull()?.also{
												style = RelativeSizeSpan(it)
											}
											break@loop
										}
									}
							}
							"p" -> {
								loop@ for(i in 0 until parser.attributeCount)
									when (parser.getAttributeName(i)){
										"align" -> {
											parseAlign(parser.getAttributeValue(i))?.also{
												style = AlignmentSpan.Standard(it)
											}
											break@loop
										}
									}
							}
						}
						if (style != null) {
							var styleStack = map.get(tag) ?: Stack<StyleInfo>()
							styleStack.push(StyleInfo(style, spannable.length))
							map.put(tag, styleStack)
						}
					}
					XmlPullParser.END_TAG ->{
						val tag = parser.name
						val styleStack = map.get(tag)
						if (styleStack != null && styleStack.isNotEmpty()) {
							val styleInfo = styleStack.pop()
							styleInfo.endPos = spannable.length
							list.add(styleInfo)
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

		private fun parseAlign(attributeValue: String): Layout.Alignment {
			when (attributeValue){
				"left"-> return Layout.Alignment.ALIGN_NORMAL
				"center"-> return Layout.Alignment.ALIGN_CENTER
				"right"-> return Layout.Alignment.ALIGN_OPPOSITE
				else -> return Layout.Alignment.ALIGN_NORMAL
			}
		}

		private fun parseColor(color: String): Int?{
			if (color[0]!='#')
				return null
			return color.substring(1).toLongOrNull(16)?.toInt()
		}
		private fun colorFormat(color:Int):String = String.format("#%08x",color)

		fun toString(spannable: SpannableStringBuilder) : String
		{
			return ToString(spannable).proceed()
		}
	}
}