import android.text.Selection
import android.text.SpannableStringBuilder
import io.reactivex.*
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import io.reactivex.subjects.Subject
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Test
import org.reactivestreams.Subscription
import ru.yandex.subbota_job.notes.BoldStyleSpan
import ru.yandex.subbota_job.notes.Markup
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit

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
	fun impl1(source:Subject<String> ){
		val s0 = source.subscribe({print("s0: $it\n")})
		source.onNext("one")
		val s1 = source.subscribe({print("s1: $it\n")})
		source.onNext("two")
		s0.dispose()
		val s2 = source.subscribe({print("s2: $it\n")})
		source.onNext("three")
		s1.dispose()
		source.onNext("four")
		s2.dispose()
		source.onNext("five")
	}
	@Test
	fun testX(){
		print("PublishSubject\n")
		impl1(PublishSubject.create<String>())
		print("BehaviorSubject\n")
		impl1(BehaviorSubject.create<String>())
		print("ReplaySubject\n")
		impl1(ReplaySubject.createWithSize<String>(1))
	}
	@Test
	fun testCreate(){
		val s1 = Flowable.interval(500, 50, TimeUnit.MILLISECONDS, Schedulers.computation())
		val s2 = Flowable.interval(0, 70, TimeUnit.MILLISECONDS, Schedulers.computation())
		Flowable.combineLatest(s1, s2, BiFunction { t1:Long, t2:Long ->
			print("combineLatest: $t1, $t2\n")
			Pair(t1,t2)
			}).observeOn(Schedulers.computation())
			.subscribeOn(Schedulers.computation()).onBackpressureDrop()
			.subscribe(){
				print("subscribe $it\n")
				Thread.sleep(1000)
			}
		Thread.sleep(10000)
	}
	@Test
	fun testBackpresure(){
		Flowable.interval(50, TimeUnit.MILLISECONDS)
				.take(30)
				.subscribeOn(Schedulers.computation())
				.doOnNext(){
						print("${Date().time} post $it\n")
					}
				.onBackpressureLatest()
				.observeOn(Schedulers.io(), false, 1)
				.subscribe( {
						print("${Date().time} onNext $it\n")
						try {
							TimeUnit.MILLISECONDS.sleep(500)
						} catch (e: InterruptedException) {
							e.printStackTrace()
						}
				})
		Thread.sleep(20000)
	}
}