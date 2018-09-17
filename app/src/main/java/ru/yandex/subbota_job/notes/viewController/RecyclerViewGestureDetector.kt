package ru.yandex.subbota_job.notes.viewController

import android.content.Context
import android.gesture.GestureOverlayView
import android.support.v4.view.GestureDetectorCompat
import android.support.v7.widget.RecyclerView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Created by subbota on 23.03.2016.
 */
class RecyclerViewGestureDetector(context: Context, view: RecyclerView, listener: GestureDetector.OnGestureListener?) : RecyclerView.OnItemTouchListener {
	private val mDetector: GestureDetector

	init {
		assert(listener != null)
		view.addOnItemTouchListener(this)
		mDetector = GestureDetector(context, listener)
	}

	override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
		return mDetector.onTouchEvent(e)
	}

	override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {

	}

	override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {

	}
}
