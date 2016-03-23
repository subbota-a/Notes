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
    public interface OnItemClickListener{
        void OnClick(RecyclerView view, int index);
    }
    private OnItemClickListener onItemClickListener;
    private GestureDetector mDetector;
    public void setOnItemClickListener(OnItemClickListener listener)
    {
        onItemClickListener = listener;
    }
    public RecyclerViewGestureDetector(Context context, final RecyclerView view)
    {
        view.addOnItemTouchListener(this);
        mDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (onItemClickListener == null)
                    return false;
                View itemView = view.findChildViewUnder(e.getX(), e.getY());
                if (itemView == null)
                    return false;
                int position = view.getChildAdapterPosition(itemView);
                if (position == RecyclerView.NO_POSITION)
                    return false;
                onItemClickListener.OnClick(view, position);
                return true;
            }
        }
        );
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
