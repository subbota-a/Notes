package ru.yandex.subbota_job.notes;

import android.content.Context;
import android.gesture.GestureOverlayView;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by subbota on 23.03.2016.
 */
public class RecyclerViewGestureDetector implements RecyclerView.OnItemTouchListener {
    private GestureDetector mDetector;
    public RecyclerViewGestureDetector(Context context, RecyclerView view, GestureDetector.OnGestureListener listener)
    {
        assert listener != null;
        view.addOnItemTouchListener(this);
        mDetector = new GestureDetector(context, listener);
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        return mDetector.onTouchEvent(e);
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {

    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

    }
}
