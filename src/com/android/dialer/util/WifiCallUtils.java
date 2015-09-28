/*
* Copyright (c) 2015, The Linux Foundation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*     * Redistributions of source code must retain the above copyright
*      notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*      with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*      contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.android.dialer.util;

import android.content.Context;
import android.content.Intent;


import android.os.SystemProperties;
import android.view.WindowManager;
import android.widget.TextView;
import android.telephony.ServiceState;
import android.util.Log;

import com.android.dialer.R;

import java.util.Timer;
import java.util.TimerTask;

public class WifiCallUtils {

    private static final String TAG = WifiCallUtils.class.getSimpleName();
    private static final int DELAYED_TIME = 5 * 1000;
    private static WindowManager mWindowManager;
    private static TextView mTextView;
    private static boolean mViewRemoved = true;
    private static Timer mtimer;

    public static final String SYSTEM_PROPERTY_WIFI_CALL_READY = "persist.sys.wificall.ready";

    public static void addWifiCallReadyMarqueeMessage(Context context) {
        if (mViewRemoved && SystemProperties.getBoolean(SYSTEM_PROPERTY_WIFI_CALL_READY, false)) {
            mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if(mTextView == null){
                mTextView = new TextView(context);
                Log.d(TAG, "mTextView is null, new mTextView = " + mTextView);
                mTextView.setText(
                        com.android.dialer.R.string.alert_call_over_wifi);
                mTextView.setSingleLine(true);
                mTextView.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                mTextView.setMarqueeRepeatLimit(-1);
                mTextView.setFocusableInTouchMode(true);
            } else {
                Log.d(TAG, "mTextView is not null, mTextView = " + mTextView);
            }

            WindowManager.LayoutParams windowParam = new WindowManager.LayoutParams();
            windowParam.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            windowParam.format= 1;
            windowParam.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowParam.alpha = 1.0f;
            windowParam.x = 0;
            windowParam.y = -500;
            windowParam.height = WindowManager.LayoutParams.WRAP_CONTENT;
            windowParam.width = (mWindowManager.getDefaultDisplay().getWidth()
                    < mWindowManager.getDefaultDisplay().getHeight()
                    ? mWindowManager.getDefaultDisplay().getWidth()
                    : mWindowManager.getDefaultDisplay().getHeight()) - 64;
            mWindowManager.addView(mTextView, windowParam);
            mViewRemoved = false;
            Log.d(TAG, "addWifiCallReadyMarqueeMessage, mWindowManager:" + mWindowManager
                    + " addView, mTextView:" + mTextView);
            Log.d(TAG, "addWifiCallReadyMarqueeMessage, mViewRemoved = " + mViewRemoved);

            scheduleRemoveWifiCallReadyMarqueeMessageTimer();
        }
    }

    public static void removeWifiCallReadyMarqueeMessage() {
        if (!mViewRemoved) {
            cancelRemoveWifiCallReadyMarqueeMessageTimer();
            mWindowManager.removeView(mTextView);
            mViewRemoved = true;
            Log.d(TAG, "removeWifiCallReadyMarqueeMessage, mWindowManager:" + mWindowManager
                    + " removeView, mTextView:" + mTextView);
            Log.d(TAG, "removeWifiCallReadyMarqueeMessage, mViewRemoved = " + mViewRemoved);
        }
    }

    private static void scheduleRemoveWifiCallReadyMarqueeMessageTimer() {
        // Schedule a timer, 5s later, remove the message
        mtimer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "timerTask is running");
                removeWifiCallReadyMarqueeMessage();
            }
        };
        mtimer.schedule(timerTask, DELAYED_TIME);
        Log.d(TAG, "schedule timerTask");
    }

    private static void cancelRemoveWifiCallReadyMarqueeMessageTimer() {
        Log.d(TAG, "cancelRemoveWifiCallReadyMarqueeMessageTimer");
        mtimer.cancel();
        mtimer.purge();
    }
}
