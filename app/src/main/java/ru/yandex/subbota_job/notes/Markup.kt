package ru.yandex.subbota_job.notes

import android.graphics.Color
import android.graphics.Typeface
import android.os.Parcel
import android.os.Parcelable
import android.text.Layout
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.*
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.io.StringWriter
import java.util.*
import kotlin.math.max

class BoldStyleSpan: StyleSpan(Typeface.BOLD){}
class ItalicStyleSpan: StyleSpan(Typeface.ITALIC){}
class MarkedTextSpan: BackgroundColorSpan(Color.rgb(0xFF,0xF1, 0x76)){}
class RedTextSpan: ForegroundColorSpan(Color.rgb(0xD5,0x00,0x00)){}
/**
 * Markup supports the following tags
 *
 */
class Markup(val spannable: SpannableStringBuilder) {
	override fun toString() : String
	{
		return ToString(spannable).proceed()
	}
	fun toggleBold()
	{
		toggleStyle(BoldStyleSpan::class.java)
	}
	fun toggleItalic()
	{
		toggleStyle(ItalicStyleSpan::class.java)
	}
	fun toggleStrikethrough()
	{
		toggleStyle(StrikethroughSpan::class.java)
	}
	fun toggleUnderline()
	{
		toggleStyle(UnderlineSpan::class.java)
	}
	fun toggleMarker()
	{
		toggleStyle(MarkedTextSpan::class.java)
	}
	fun toggleRedText()
	{
		toggleStyle(RedTextSpan::class.java)
	}
	data class Range(val start:Int, val end:Int){
		val isEmpty:Boolean get() = start==end
		val isNull:Boolean get() = start>end
	}
	fun getSelection() : Range
	{
		var ss = Selection.getSelectionStart(spannable)
		if (ss<0)
			ss = 0
		var se = Selection.getSelectionEnd(spannable)
		if (se<0)
			se = spannable.length - 1
		return Range(ss,se)
	}
	fun changeFontSize(increase: Boolean)
	{
		val range = getSelection().also { if (it.isNull) return }
		val spans = spannable.getSpans(range.start, range.end, RelativeSizeSpan::class.java)
		var percent = spans.firstOrNull()?.sizeChange ?: 1f
		if (increase)
			percent += 0.1f
		else
			percent -= 0.1f
		replaceStyle(range.start, range.end, spans.asIterable(), if (percent != 0f) RelativeSizeSpan(percent) else null)
	}
	private fun removeCharacterStyleFromRange(ss:Int, se:Int, spans: Iterable<CharacterStyle>)
	{
		for(s in spans){
			val st = spannable.getSpanStart(s)
			val en = spannable.getSpanEnd(s)
			val flags = spannable.getSpanFlags(s)
			spannable.removeSpan(s)
			if (st<ss)
				spannable.setSpan(CharacterStyle.wrap(s), st, ss, flags)
			if (se<en)
				spannable.setSpan(CharacterStyle.wrap(s), se, en, flags)
		}
	}
	private fun spanFlags(ss:Int, se:Int):Int{
		return if (ss<se) Spanned.SPAN_POINT_POINT else Spanned.SPAN_MARK_POINT
	}
	private fun <T:CharacterStyle> toggleStyle(styleClass: Class<T>){
		val range = getSelection().also { if (it.isNull) return }
		val spans = spannable.getSpans(range.start, range.end, styleClass).asIterable()

		if (!spans.any())
			spannable.setSpan(styleClass.getConstructor().newInstance(), range.start, range.end, spanFlags(range.start, range.end))
		else
			removeCharacterStyleFromRange(range.start, range.end, spans)
	}
	private fun <T:CharacterStyle> replaceStyle(ss:Int, se:Int, spans: Iterable<T>, style : T?)
	{
		removeCharacterStyleFromRange(ss,se,spans)
		if (style!=null)
			spannable.setSpan(style, ss, se, spanFlags(ss,se))
	}
	private fun splitCharacterStyles(ss:Int, se:Int)
	{
		for(s in spannable.getSpans(ss,se,CharacterStyle::class.java)){
			val st = spannable.getSpanStart(s)
			val en = spannable.getSpanEnd(s)
			if (st<ss)
				spannable.setSpan(CharacterStyle.wrap(s), st, ss, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
			if (se<en)
				spannable.setSpan(CharacterStyle.wrap(s), se, en, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
			if (st<ss || se<en){
				spannable.removeSpan(s)
				spannable.setSpan(CharacterStyle.wrap(s), ss, se, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
			}
		}
	}
	fun<T:ParagraphStyle> replaceParagraphStyle(style : ()->T, type: Class<T>){
		val range = getSelection().also { if (it.isNull) return }
		var st = range.start
		while(st > 0 && spannable[st-1]!='\n')
			--st
		var en = st+1
		do {
			while (en < spannable.length && spannable[en-1] != '\n')
				++en
			val spans = spannable.getSpans(st, en, type)
			for (s in spans) {
				val start = spannable.getSpanStart(s)
				val end = spannable.getSpanEnd(s)
				val flags = spannable.getSpanFlags(s)
				val p = lazy{Parcel.obtain().also{(s as Parcelable).writeToParcel(it, 0)}}
				try {
					if (start < st) {
						p.value.setDataPosition(0)
						val newSpan = type.getConstructor(Parcel::class.java).newInstance(p.value)
						spannable.setSpan(newSpan, start, st, flags)
					}
					if (end > en) {
						p.value.setDataPosition(0)
						val newSpan = type.getConstructor(Parcel::class.java).newInstance(p.value)
						spannable.setSpan(newSpan, en, end, flags)
					}
					spannable.removeSpan(s)
				}finally {
					if (p.isInitialized())
						p.value.recycle()
				}
			}
			spannable.setSpan(style(), st, en, Spanned.SPAN_PARAGRAPH)
			st = en++
		}while (st < range.end)
	}

	private class ToString(private val spannable: SpannableStringBuilder){
		private val ser = Xml.newSerializer()
		private fun paragraphStyle()
		{
			val len = spannable.length
			var i = 0
			while (i < len) {
				val next = spannable.nextSpanTransition(i, len, ParagraphStyle::class.java)
				val spans = spannable.getSpans(i, next, ParagraphStyle::class.java).toMutableList()
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
					(spannable.getSpanFlags(it) and Spanned.SPAN_COMPOSING)==0 && spannable.getSpanStart(it)<=i && spannable.getSpanEnd(it)>=next
				}.toMutableList()
				Log.d(logTag, "${spans.size} spans ($i,$next)")
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

		fun logSpan(){
			val spans = spannable.getSpans(0, spannable.length, Object::class.java)
			for(x in spans){
				Log.d(logTag, "${x.toString()} -> ${String.format("0x%x",spannable.getSpanFlags(x))} -> (${spannable.getSpanStart(x)}, ${spannable.getSpanEnd(x)})")
			}

		}
		fun proceed(): String {
			if (spannable.isEmpty())
				return ""
			logSpan()
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
					is BoldStyleSpan -> handler("b", null)
					is ItalicStyleSpan -> handler("i", null)
					is StrikethroughSpan -> handler("s", null)
					is UnderlineSpan -> handler("u", null)
					is BackgroundColorSpan -> handler("span", mapOf(Pair("background-color", colorFormat(span.backgroundColor))))
					is ForegroundColorSpan -> handler("span", mapOf(Pair("color", colorFormat(span.foregroundColor))))
					is RelativeSizeSpan -> handler("span", mapOf(Pair("font-size-adjust", span.sizeChange.toString())))
					is AlignmentSpan.Standard -> handler("p", mapOf(Pair("align", when (span.alignment) {
						Layout.Alignment.ALIGN_NORMAL->"left"
						Layout.Alignment.ALIGN_CENTER->"center"
						Layout.Alignment.ALIGN_OPPOSITE->"right"
						else -> "left"
					})))
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
							"b" -> style = BoldStyleSpan()
							"i" -> style = ItalicStyleSpan()
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
											parseAlign(parser.getAttributeValue(i)).also{
												style = AlignmentSpan.Standard(it)
											}
											break@loop
										}
									}
							}
						}
						if (style != null) {
							val styleStack = map.get(tag) ?: Stack<StyleInfo>()
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
			return when (attributeValue){
				"left"-> Layout.Alignment.ALIGN_NORMAL
				"center"-> Layout.Alignment.ALIGN_CENTER
				"right"-> Layout.Alignment.ALIGN_OPPOSITE
				else -> Layout.Alignment.ALIGN_NORMAL
			}
		}

		private fun parseColor(color: String): Int?{
			if (color[0]!='#')
				return null
			return color.substring(1).toLongOrNull(16)?.toInt()
		}
		private fun colorFormat(color:Int):String = String.format("#%08x",color)

	}
}