/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
 *
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.dialpad;

import android.app.DialogFragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.view.animation.AccelerateInterpolator;

import com.android.contacts.common.CallUtil;
import com.android.dialer.DialpadCling;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.dialpad.DialpadFragment.ErrorDialogFragment;
import com.android.dialer.widget.multiwaveview.GlowPadView;
import com.android.dialer.R;
import com.android.internal.telephony.MSimConstants;
import static com.android.internal.telephony.MSimConstants.SUBSCRIPTION_KEY;

/**
 * Fragment that displays a twelve-key phone dialpad.
 */
public class SmartDialpadFragment extends DialpadFragment {
    private static final String TAG = SmartDialpadFragment.class.getSimpleName();

    private GlowPadView mDialWidget;
    public GlowPadViewMethods glowPadViewMethods;
    private DialpadCling mDialpadCling;
    private int transferX = 0;
    private int transferY = 0;
    private boolean mDialButtonLongClicked = false;

    private static final int DIALBUTTON_LONGCLICK_DURATION = 0;
    private static final int DIALBUTTON_ACTIVE_DURATION = 500;
    private static final float DIALBUTTON_ACTIVE_DISTANCE = 15.0f;
    private static float mDownX;
    private static float mDownY;
    private static long mDownPressTime;

    private static final int SHOW_CLING_DURATION = 550;
    public static final int DISMISS_CLING_DURATION = 250;
    private MotionEvent mDownEvent;
    private MotionEvent mMoveEvent;
    private Handler handler = new Handler();
    private Runnable runnable = new Runnable() {
        public void run() {
            if (canShowDialWidget()) {
                transferCoordinates();
                if (mDownEvent != null) {
                    mDownEvent = MotionEvent.obtain(mDownEvent.getDownTime(),
                            mDownEvent.getDownTime(),
                            mDownEvent.getAction(),
                            mDownEvent.getX() + getTransferCoordinateX(),
                            mDownEvent.getY() + getTransferCoordinateY(),
                            mDownEvent.getMetaState());
                    mDialWidget.onTouchEvent(mDownEvent);
                }
                if (mMoveEvent != null) {
                    mMoveEvent = MotionEvent.obtain(mMoveEvent.getEventTime(),
                            mMoveEvent.getEventTime(),
                            mMoveEvent.getAction(),
                            mMoveEvent.getX() + getTransferCoordinateX(),
                            mMoveEvent.getY() + getTransferCoordinateY(),
                            mMoveEvent.getMetaState());
                    mDialWidget.onTouchEvent(mMoveEvent);
                }
                mDialWidget.resumeAnimations();
                getDialtactsActivity().updateFakeMenuButtonsVisibility(false);
                setDialButtonLongClicked(true);
                mDownEvent = null;
            }
        }
    };

    private OnTouchListener mTouchToDialButton = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction() & MotionEvent.ACTION_MASK;

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mDownEvent = MotionEvent.obtain(event.getDownTime(),
                            event.getDownTime(),
                            event.getAction(),
                            event.getX(),
                            event.getY(),
                            event.getMetaState());
                    handler.postDelayed(runnable, DIALBUTTON_LONGCLICK_DURATION);
                    mDownPressTime = event.getEventTime();
                    mDownX = event.getX();
                    mDownY = event.getY();
                    getDialtactsActivity().mViewPager.setPagingEnabled(false);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (getDialWidgetVisibility() == View.VISIBLE) {
                        MotionEvent ev = MotionEvent.obtain(event.getEventTime(),
                                event.getEventTime(),
                                event.getAction(),
                                event.getX() + getTransferCoordinateX(),
                                event.getY() + getTransferCoordinateY(),
                                event.getMetaState());
                        mDialWidget.onTouchEvent(ev);
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                    if (handler != null && runnable != null)
                        handler.removeCallbacks(runnable);
                    if (event.getEventTime() - mDownPressTime <= DIALBUTTON_ACTIVE_DURATION &&
                            Math.abs(event.getX() - mDownX) <= DIALBUTTON_ACTIVE_DISTANCE &&
                            Math.abs(event.getY() - mDownY) <= DIALBUTTON_ACTIVE_DISTANCE) {
                        dialButtonPressed();
                    } else if (getDialWidgetVisibility() == View.VISIBLE) {
                        MotionEvent ev = MotionEvent.obtain(event.getDownTime(),
                                event.getDownTime(),
                                event.getAction(),
                                event.getX() + getTransferCoordinateX(),
                                event.getY() + getTransferCoordinateY(),
                                event.getMetaState());
                        mDialWidget.onTouchEvent(ev);
                    }
                    final int currentPosition = getDialtactsActivity().mPageChangeListener
                            .getCurrentPosition();
                    getDialtactsActivity().updateFakeMenuButtonsVisibility(
                            currentPosition == DialtactsActivity.TAB_INDEX_DIALER
                                    && !getDialtactsActivity().mInSearchUi);
                    getDialtactsActivity().mViewPager.setPagingEnabled(true);
                    setDialWidgetVisibility(false);
                    return false;
                case MotionEvent.ACTION_CANCEL:
                    // After user lock screen this button would receive
                    // action_cancle, we need reset ui.
                    if (handler != null && runnable != null)
                        handler.removeCallbacks(runnable);
                    if (getDialWidgetVisibility() == View.VISIBLE) {
                        // Send action_cancle event to GlowPadView
                        MotionEvent ev = MotionEvent.obtain(event.getEventTime(),
                                event.getEventTime(), event.getAction(), event.getX()
                                        + getTransferCoordinateX(),
                                event.getY() + getTransferCoordinateY(),
                                event.getMetaState());
                        mDialWidget.onTouchEvent(ev);
                    }
                    // Refresh current ui
                    int position = getDialtactsActivity().mPageChangeListener.getCurrentPosition();
                    getDialtactsActivity().updateFakeMenuButtonsVisibility(
                            position == DialtactsActivity.TAB_INDEX_DIALER
                                    && !getDialtactsActivity().mInSearchUi);
                    getDialtactsActivity().mViewPager.setPagingEnabled(true);
                    setDialWidgetVisibility(false);
                    return false;
            }
            return false;
        }
    };

    private DialtactsActivity getDialtactsActivity() {
        return (DialtactsActivity) SmartDialpadFragment.this.getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View fragmentView = super.onCreateView(inflater, container, savedState);

        if (getResources().getBoolean(R.bool.config_show_onscreen_dial_button)) {
            mDialButton.setOnTouchListener(mTouchToDialButton);
        }

        mDialWidget = (GlowPadView) fragmentView.findViewById(R.id.dialDualWidget);
        if (mDialButton != null && mDialWidget != null) {
            glowPadViewMethods = new GlowPadViewMethods(mDialWidget);
            mDialWidget.setOnTriggerListener(glowPadViewMethods);
        }
        mDialpadCling = (DialpadCling) fragmentView.findViewById(R.id.dialpad_cling);
        final DialtactsActivity activity = (DialtactsActivity) getActivity();
        if (activity.canShowDialpadCling()) {
            showFirstRunDialpadCling();
        }
        return fragmentView;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.dialButton: {
                mHaptic.vibrate();
                return;
            }
        }
        super.onClick(view);
    }

    /**
     * @return true if the widget with the phone number digits is emergency number.
     */
    private boolean isEmergencyNumber() {
        final String number = mDigits.getText().toString();
        return PhoneNumberUtils.isEmergencyNumber(number);
    }

    class GlowPadViewMethods implements GlowPadView.OnTriggerListener {
        private final GlowPadView mGlowPadView;

        GlowPadViewMethods(GlowPadView glowPadView) {
            mGlowPadView = glowPadView;
        }

        public void onGrabbed(View v, int handle) {
        }

        public void onReleased(View v, int handle) {
            setDialWidgetVisibility(false);
        }

        public void onTrigger(View v, int target) {
            final int resId = mGlowPadView.getResourceIdForTarget(target);
            switch (resId) {
                case R.drawable.ic_dial_slot1:
                    dialWidgetSwitched(MSimConstants.SUB1);
                    break;

                case R.drawable.ic_dial_slot2:
                    dialWidgetSwitched(MSimConstants.SUB2);
                    break;
            }
        }

        public void onGrabbedStateChange(View v, int handle) {
        }

        public void onFinishFinalAnimation() {
        }
    }

    public int getDialWidgetVisibility() {
        return (mDialWidget == null) ? View.GONE : mDialWidget.getVisibility();
    }

    public void setDialWidgetVisibility(boolean visible) {
        if (visible) {
            mDialWidget.setVisibility(View.VISIBLE);
            mDialButton.setVisibility(View.GONE);
        } else {
            mDialWidget.setVisibility(View.GONE);
            mDialButton.setVisibility(View.VISIBLE);
        }
    }

    public boolean canShowDialWidget() {
        if (isDigitsEmpty())
            return false;

        if (phoneIsInUse())
            return false;

        if (isEmergencyNumber())
            return false;

        int phoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < phoneCount; i++) {
            if (!DialtactsActivity.isValidSimState(i))
                return false;
        }
        return true;
    }

    public void dialWidgetSwitched(int subscription) {
        if (isDigitsEmpty()) { // No number entered.
            handleDialButtonClickWithEmptyDigits();
        } else {
            final String number = mDigits.getText().toString();

            // "persist.radio.otaspdial" is a temporary hack needed for one
            // carrier's automated
            // test equipment.
            // TODO: clean it up.
            if (number != null
                    && !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
                    && number.matches(mProhibitedPhoneNumberRegexp)
                    && (SystemProperties.getInt("persist.radio.otaspdial", 0) != 1)) {
                Log.i(TAG, "The phone number is prohibited explicitly by a rule.");
                if (getActivity() != null) {
                    DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                            R.string.dialog_phone_call_prohibited_message);
                    dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
                }

                // Clear the digits just in case.
                mDigits.getText().clear();
            } else {
                final Intent intent = CallUtil.getCallIntent(number,
                        (getActivity() instanceof DialtactsActivity ?
                                ((DialtactsActivity) getActivity()).getCallOrigin() : null));
                intent.putExtra("dial_widget_switched", subscription);
                startActivity(intent);
                mClearDigitsOnStop = true;
                getActivity().finish();
            }
        }
    }

    public void transferCoordinates() {
        int[] locationButton = new int[2];
        int[] locationWidget = new int[2];

        if (mDialButton != null && mDialButton.getVisibility() == View.VISIBLE) {
            mDialButton.getLocationOnScreen(locationButton);
        }
        setDialWidgetVisibility(true);
        if (mDialWidget != null && mDialWidget.getVisibility() == View.VISIBLE) {
            mDialWidget.getLocationOnScreen(locationWidget);
        }
        transferX = locationButton[0] - locationWidget[0];
        transferY = locationButton[1] - locationWidget[1];
    }

    public int getTransferCoordinateX() {
        return transferX;
    }

    public int getTransferCoordinateY() {
        return transferY;
    }

    public void setDialButtonLongClicked(boolean longClicked) {
        mDialButtonLongClicked = longClicked;
    }

    public boolean getDialButtonLongClicked() {
        return mDialButtonLongClicked;
    }

    public DialpadCling getDialpadCling() {
        return mDialpadCling;
    }

    public void showFirstRunDialpadCling() {
        // Enable the clings only if they have not been dismissed before
        if (!getDialtactsActivity().mPrefs.getBoolean(DialpadCling.DIALPAD_CLING_DISMISSED_KEY,
                false)) {
            initCling(R.id.dialpad_cling, false, 0);
        } else {
            removeCling(R.id.dialpad_cling);
        }
    }

    private void removeCling(int id) {
        final View dialpadCling = getDialtactsActivity().findViewById(id);
        if (dialpadCling != null) {
            final ViewGroup parent = (ViewGroup) dialpadCling.getParent();
            parent.post(new Runnable() {
                public void run() {
                    parent.removeView(dialpadCling);
                }
            });
        }
    }

    private DialpadCling initCling(int clingId, boolean animate, int delay) {
        if (mDialpadCling != null) {
            mDialpadCling.init(getDialtactsActivity());
            mDialpadCling.setVisibility(View.VISIBLE);
            mDialpadCling.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            mDialpadCling.requestAccessibilityFocus();
            if (animate) {
                mDialpadCling.buildLayer();
                mDialpadCling.setAlpha(0f);
                mDialpadCling.animate()
                        .alpha(1f)
                        .setInterpolator(new AccelerateInterpolator())
                        .setDuration(SHOW_CLING_DURATION)
                        .setStartDelay(delay)
                        .start();
            } else {
                mDialpadCling.setAlpha(1f);
            }
            DialtactsActivity.mDialpadClingShowed = true;
        }
        return mDialpadCling;
    }
}
