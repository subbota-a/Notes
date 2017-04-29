package ru.yandex.subbota_job.notes;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Scroller;

@SuppressLint("AppCompatCustomView")
public class FlingableEditText extends EditText implements GestureDetector.OnGestureListener{
    Scroller mScroller;
    GestureDetector mGestureDetector;
    public FlingableEditText(Context context) {
        super(context);
        init(null, 0);
    }

    public FlingableEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public FlingableEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        mGestureDetector = new GestureDetector(getContext(), this);
        mScroller = new Scroller(getContext());
        setScroller(mScroller);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        if (!mScroller.isFinished())
            mScroller.abortAnimation();
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        float maxY = getLayout().getHeight()-(getHeight()-getPaddingTop()-getPaddingBottom());
        if (maxY < 0)
            maxY = 0;
        mScroller.fling(getScrollX(), getScrollY(), -(int)velocityX, -(int)velocityY,
                0, getScrollX(), 0, (int)maxY);
        invalidate();
        return true;
    }
}
