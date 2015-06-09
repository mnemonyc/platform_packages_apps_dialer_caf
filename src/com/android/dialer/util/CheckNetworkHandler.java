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


import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.telephony.SubscriptionManager;

import com.android.ims.ImsConfig;
import com.android.ims.ImsConfigListener;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.dialer.R;

public class CheckNetworkHandler extends Handler {

    private static final String TAG = CheckNetworkHandler.class.getSimpleName();
    public static final int CHECK_NETWORK_STATUS = 1;
    public static final int REMOVE_TEXT_VIEW = 2;
    public static final int DELAYED_TIME = 5 * 1000;
    private ImsConfig mImsConfig;
    private Context mContext;
    private TextView mTextView;
    private int mMessageId;

    private ImsConfigListener imsConfigListener = new ImsConfigListener.Stub() {
        public void onGetVideoQuality(int status, int quality) {
            //TODO not required as of now
        }

        public void onSetVideoQuality(int status) {
            //TODO not required as of now
        }

        public void onGetFeatureResponse(int feature, int network, int value, int status) {
            //TODO not required as of now
        }

        public void onSetFeatureResponse(int feature, int network, int value, int status) {
            //TODO not required as of now
        }

        public void onGetPacketCount(int status, long packetCount) {
            //TODO not required as of now
        }

        public void onGetPacketErrorCount(int status, long packetErrorCount) {
            //TODO not required as of now
        }

        public void onGetWifiCallingPreference(int status, int wifiCallingStatus,
                int wifiCallingPreference) {
            if (hasRequestFailed(status)) {
                Log.e(TAG, "onGetWifiCallingPreference: failed. errorCode = " + status);
            }else if(wifiCallingStatus == ImsConfig.WifiCallingValueConstants.OFF){
                DialerUtils.pupConnectWifiCallDialog(mContext,
                        mMessageId);
            }else if(wifiCallingStatus == ImsConfig.WifiCallingValueConstants.ON){
                popMakeWifiCallText();
                CheckNetworkHandler.this.sendEmptyMessageDelayed(REMOVE_TEXT_VIEW, DELAYED_TIME);
            }
            Log.d(TAG, "onGetWifiCallingPreference: status = " + wifiCallingStatus +
                    " preference = " + wifiCallingPreference);
        }

        public void onSetWifiCallingPreference(int status) {
            //TODO not required as of now
        }
    };


    @Override
    public void handleMessage(Message msg) {
        Log.i(TAG, "handleMessage");
        if(mContext == null && msg.obj == null){
            Log.i(TAG, "mContext is null");
            return;
        } else if(mContext == null){
            mContext = (Context) msg.obj;
        }
        mMessageId = msg.arg1;
        if(mImsConfig == null){
            try {
                ImsManager imsManager = ImsManager.getInstance(mContext,
                        SubscriptionManager.getDefaultVoiceSubId());
                mImsConfig = imsManager.getConfigInterface();
            } catch (ImsException e) {
                mImsConfig = null;
                Log.e(TAG, "ImsService is not running");
            }
        }
        switch (msg.what) {
        case CHECK_NETWORK_STATUS:
            checkWifiCallStatus(msg);
            break;

        case REMOVE_TEXT_VIEW:
            if(mTextView != null){
                WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
                wm.removeView(mTextView);
            }
            break;
        default:
            Log.i(TAG, "unknow handleMessage with msg.what : " + msg.what);
            break;
        }
    }

    private void checkWifiCallStatus(Message msg) {
        try {
            Log.i(TAG, "checkwificallstatus");
            if (mImsConfig != null) {
                mImsConfig.getWifiCallingPreference(imsConfigListener);
            } else {
                Log.e(TAG, "getWifiCallingPreference failed. mImsConfig is null");
            }
        } catch (ImsException e) {
            Log.e(TAG, "getWifiCallingPreference failed. Exception = " + e);
        }
    }

    private boolean hasRequestFailed(int result) {
        return (result != ImsConfig.OperationStatusConstants.SUCCESS);
    }

    private void popMakeWifiCallText() {
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        if(mTextView == null){
            mTextView = new TextView(mContext);
            mTextView.setText(
                    com.android.dialer.R.string.alert_call_over_wifi);
            mTextView.setSingleLine(true);
            mTextView.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            mTextView.setMarqueeRepeatLimit(-1);
            mTextView.setFocusableInTouchMode(true);
        }
        WindowManager.LayoutParams windowParam = new WindowManager.LayoutParams();
        windowParam.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        windowParam.format= 1;
        windowParam.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowParam.alpha = 1.0f;
        windowParam.x = 0;
        windowParam.y = -500;
        windowParam.height = WindowManager.LayoutParams.WRAP_CONTENT;
        windowParam.width = (wm.getDefaultDisplay().getWidth() < wm.getDefaultDisplay()
                .getHeight() ? wm.getDefaultDisplay().getWidth()
                        : wm.getDefaultDisplay().getHeight()) - 64;
        wm.addView(mTextView, windowParam);
    }
}
