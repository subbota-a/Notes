import android.text.Selection
import android.text.SpannableStringBuilder
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Test
import ru.yandex.subbota_job.notes.BoldStyleSpan
import ru.yandex.subbota_job.notes.Markup

class TestMarkup {
	@Test
	fun toggleBold(){
		val s = SpannableStringBuilder("012345678")
		Selection.setSelection(s, 0, 8)
		val m = Markup(s)
		m.toggleBold()
		val spans = s.getSpans(0, s.length, BoldStyleSpan::class.java)
		assertThat(spans.size, `is`(1))
		val span = spans[0]
		assertThat(s.getSpanStart(span), `is`(0))
		assertThat(s.getSpanEnd(span), `is`(8))
	}

}