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

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemProperties;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.android.dialer.R;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class WifiCallUtils {

    private static final String TAG = WifiCallUtils.class.getSimpleName();
    private static final int DELAYED_TIME = 5 * 1000;
    private static WindowManager mWindowManager;
    private static TextView mTextView;
    private static boolean mViewRemoved = true;
    private static Timer mtimer;

    private static final String SYSTEM_PROPERTY_WIFI_CALL_READY = "sys.wificall.ready";
    private static final String SYSTEM_PROPERTY_WIFI_CALL_TURNON = "persist.sys.wificall.turnon";

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

    private static boolean cellularNetworkAvailable(Context context) {
        boolean available = false;

        TelephonyManager tm = (TelephonyManager) context.
                getSystemService(Context.TELEPHONY_SERVICE);
        List<CellInfo> cellInfoList = tm.getAllCellInfo();

        if (cellInfoList != null) {
            for (CellInfo cellinfo : cellInfoList) {
                if (cellinfo.isRegistered()) {
                    available = true;
                }
            }
        }

        return available;
    }

    public static void pupConnectWifiCallDialog(final Context context) {
        AlertDialog.Builder diaBuilder = new AlertDialog.Builder(context);
        diaBuilder.setMessage(com.android.dialer.R.string.alert_call_no_cellular_coverage);
        diaBuilder.setPositiveButton(com.android.internal.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.phone", "com.android.phone.WifiCallingSettings");
                context.startActivity(intent);
            }
        });
        diaBuilder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
            }
        });
        diaBuilder.create().show();
    }

    private static boolean isWifiNotConnected(final Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetworkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return (wifiNetworkInfo.isAvailable() && !wifiNetworkInfo.isConnected());
    }

    public static boolean isShowingPromptForWifiCall(final Context context) {
        boolean wifiCallTurnOn = SystemProperties.getBoolean(SYSTEM_PROPERTY_WIFI_CALL_TURNON, false);
        return wifiCallTurnOn && isWifiNotConnected(context) && !cellularNetworkAvailable(context);
    }

    public static void pupConnectWifiCallNotification(final Context context) {
        final NotificationManager notiManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent intent = new Intent();
        intent.setAction(android.provider.Settings.ACTION_WIFI_SETTINGS);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(context);
        builder.setOngoing(false);
        builder.setWhen(0);
        builder.setContentIntent(pendingIntent);
        builder.setAutoCancel(true);
        builder.setSmallIcon(R.drawable.wifi_calling_on_notification);
        builder.setContentTitle(
                context.getResources().getString(R.string.alert_user_connect_to_wifi_for_call));
        builder.setContentText(
                context.getResources().getString(R.string.alert_user_connect_to_wifi_for_call));
        notiManager.notify(1, builder.build());
        new Thread() {
            public void run() {
                try {
                    Thread.currentThread().sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                notiManager.cancel(1);
            }
        }.start();
    }
}
