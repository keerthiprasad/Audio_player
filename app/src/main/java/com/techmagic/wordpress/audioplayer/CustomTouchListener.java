package com.techmagic.wordpress.audioplayer;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by Keerthi Prasad on 9/1/2017.
 */

public class CustomTouchListener implements RecyclerView.OnItemTouchListener {

    /*Gesture detector to intercept the touch events*/
    GestureDetector gestureDetector;
    private onItemClickListener clickListener;

    public CustomTouchListener(Context context,onItemClickListener clickListener) {
        this.clickListener = clickListener;
        gestureDetector = new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        View child = rv.findChildViewUnder(e.getX(),e.getY());
        if (child != null && clickListener != null && gestureDetector.onTouchEvent(e)){
            clickListener.onClick(child,rv.getChildLayoutPosition(child));
        }
        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {

    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

    }
}
