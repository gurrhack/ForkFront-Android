package com.tbd.forkfront;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

public class Movable {

	private final Activity mContext;
	private final View mRoot;
	private final View mMovable;
	private LinearLayout.LayoutParams mPaddingTop;
	private LinearLayout.LayoutParams mPaddingBottom;
	private Integer mPointerId;
	private float mPointerX;
	private float mPointerY;
	private boolean mIsPanning;
	private float mLastPosition;
	private int mOrientation;

	public Movable(Activity context, View root) {
		mContext = context;
		mRoot = root;
		mMovable = mRoot.findViewById(R.id.movable);

		mRoot.setOnTouchListener(mTouchListener);

		mPaddingTop = (LinearLayout.LayoutParams)mRoot.findViewById(R.id.paddingTop).getLayoutParams();
		mPaddingBottom = (LinearLayout.LayoutParams)mRoot.findViewById(R.id.paddingBottom).getLayoutParams();

		setOrientation(context.getResources().getConfiguration().orientation);
	}

	// ____________________________________________________________________________________
	public void setOrientation(int orientation)
	{
		mOrientation = orientation;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		float weight;
		if(mOrientation == Configuration.ORIENTATION_PORTRAIT)
			weight = prefs.getFloat("qWeightPort", .5f);
		else
			weight = prefs.getFloat("qWeightLand", .5f);

		mPaddingTop.weight = weight;
		mPaddingBottom.weight = 1.f - weight;

		mRoot.requestLayout();

		float spaceLeft = mRoot.getMeasuredHeight() - mMovable.getMeasuredHeight();
		if(spaceLeft > 0) {
			mLastPosition = spaceLeft * weight;
		}

		// Cancel dragging if active
		mPointerId = null;
	}

	// ____________________________________________________________________________________
	private int getActionIndex(MotionEvent event)
	{
		return (event.getAction() & MotionEvent.ACTION_POINTER_ID_MASK) >> MotionEvent.ACTION_POINTER_ID_SHIFT;
	}

	// ____________________________________________________________________________________
	private int getAction(MotionEvent event)
	{
		return event.getAction() & MotionEvent.ACTION_MASK;
	}

	View.OnTouchListener mTouchListener = new View.OnTouchListener() {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch(getAction(event)) {
				case MotionEvent.ACTION_DOWN:
					if(mPointerId == null) {
						int idx = getActionIndex(event);
						mPointerId = event.getPointerId(idx);
						mPointerX = event.getX(idx);
						mPointerY = event.getY(idx);
						mIsPanning = false;
					}
					break;

				case MotionEvent.ACTION_MOVE:
					if(mPointerId != null) {
						int idx = event.findPointerIndex(mPointerId);

						float posX = event.getX(idx);
						float posY = event.getY(idx);

						float dx = posX - mPointerX;
						float dy = posY - mPointerY;

						float spaceLeft = mRoot.getMeasuredHeight() - mMovable.getMeasuredHeight();
						if(spaceLeft > 0) {
							if(mIsPanning) {
								mLastPosition = Math.max(Math.min(mLastPosition + dy, spaceLeft), 0);

								float weight = mLastPosition / spaceLeft;
								mPaddingTop.weight = weight;
								mPaddingBottom.weight = 1.f - weight;

								mRoot.requestLayout();

								mPointerX = posX;
								mPointerY = posY;
							} else {
								float th = ViewConfiguration.get(mContext).getScaledTouchSlop();

								if(Math.abs(dx) > th || Math.abs(dy) > th) {
									mIsPanning = true;
									mLastPosition = spaceLeft * mPaddingTop.weight;
								}
							}
						}
					}
					break;

				case MotionEvent.ACTION_UP:
					if(mPointerId != null) {
						int idx = getActionIndex(event);
						int pointerId = event.getPointerId(idx);
						if(mPointerId == pointerId) {
							mPointerId = null;

							SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
							if(mOrientation == Configuration.ORIENTATION_PORTRAIT)
								edit.putFloat("qWeightPort", mPaddingTop.weight);
							else
								edit.putFloat("qWeightLand", mPaddingTop.weight);
							edit.commit();
						}
					}
					break;
			}
			return false;
		}
	};
}
