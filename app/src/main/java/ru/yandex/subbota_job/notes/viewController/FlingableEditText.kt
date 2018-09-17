package ru.yandex.subbota_job.notes.viewController

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Scroller

@SuppressLint("AppCompatCustomView")
class FlingableEditText : EditText, GestureDetector.OnGestureListener {
	var onSelectionChanged: ((selStart: Int, selEnd: Int)->Unit)? = null
	override fun onSelectionChanged(selStart: Int, selEnd: Int) {
		super.onSelectionChanged(selStart, selEnd)
		onSelectionChanged?.invoke(selStart, selEnd)
	}
	var onScrollChanged : (()->Unit)? = null
	override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
		super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
		onScrollChanged?.invoke()
	}

	internal lateinit var mScroller: Scroller
	internal lateinit var mGestureDetector: GestureDetector

	constructor(context: Context) : super(context) {
		init(null, 0)
	}

	constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
		init(attrs, 0)
	}

	constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
		init(attrs, defStyle)
	}

	private fun init(attrs: AttributeSet?, defStyle: Int) {
		mGestureDetector = GestureDetector(context, this)
		mScroller = Scroller(context)
		setScroller(mScroller)
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		mGestureDetector.onTouchEvent(event)
		return super.onTouchEvent(event)
	}

	override fun onDown(e: MotionEvent): Boolean {
		if (!mScroller.isFinished)
			mScroller.abortAnimation()
		return true
	}

	override fun onShowPress(e: MotionEvent) {}

	override fun onSingleTapUp(e: MotionEvent): Boolean {
		return false
	}

	override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
		return false
	}

	override fun onLongPress(e: MotionEvent) {}

	override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
		var maxY = (layout.height - (height - paddingTop - paddingBottom)).toFloat()
		if (maxY < 0)
			maxY = 0f
		mScroller.fling(scrollX, scrollY, -velocityX.toInt(), -velocityY.toInt(),
				0, scrollX, 0, maxY.toInt())
		invalidate()
		return true
	}
}
