/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.dialer.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.interactions.TouchPointManager;
import com.android.dialer.R;
import com.android.dialer.widget.EmptyContentView;
import com.android.incallui.CallCardFragment;
import com.android.incallui.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.codeaurora.presenceserv.IPresenceService;
import org.codeaurora.presenceserv.IPresenceServiceCB;

/**
 * General purpose utility methods for the Dialer.
 */
public class DialerUtils {

    private static final String TAG = "DialerUtils";

    /**
     * Attempts to start an activity and displays a toast with the default error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent to start the activity with.
     */
    public static void startActivityWithErrorToast(Context context, Intent intent) {
        startActivityWithErrorToast(context, intent, R.string.activity_not_available);
    }

    /**
     * Attempts to start an activity and displays a toast with a provided error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent to start the activity with.
     * @param msgId Resource ID of the string to display in an error message if the activity is
     *              not found.
     */
    public static void startActivityWithErrorToast(Context context, Intent intent, int msgId) {
        try {
            if ((IntentUtil.CALL_ACTION.equals(intent.getAction())
                            && context instanceof Activity)) {
                // All dialer-initiated calls should pass the touch point to the InCallUI
                Point touchPoint = TouchPointManager.getInstance().getPoint();
                if (touchPoint.x != 0 || touchPoint.y != 0) {
                    Bundle extras = new Bundle();
                    extras.putParcelable(TouchPointManager.TOUCH_POINT, touchPoint);
                    intent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
                }
                final TelecomManager tm =
                        (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                tm.placeCall(intent.getData(), intent.getExtras());
            } else {
                context.startActivity(intent);
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, msgId, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Returns the component name to use in order to send an SMS using the default SMS application,
     * or null if none exists.
     */
    public static ComponentName getSmsComponent(Context context) {
        String smsPackage = Telephony.Sms.getDefaultSmsPackage(context);
        if (smsPackage != null) {
            final PackageManager packageManager = context.getPackageManager();
            final Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.fromParts(ContactsUtils.SCHEME_SMSTO, "", null));
            final List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, 0);
            for (ResolveInfo resolveInfo : resolveInfos) {
                if (smsPackage.equals(resolveInfo.activityInfo.packageName)) {
                    return new ComponentName(smsPackage, resolveInfo.activityInfo.name);
                }
            }
        }
        return null;
    }

    /**
     * Closes an {@link AutoCloseable}, silently ignoring any checked exceptions. Does nothing if
     * null.
     *
     * @param closeable to close.
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Joins a list of {@link CharSequence} into a single {@link CharSequence} seperated by a
     * localized delimiter such as ", ".
     *
     * @param resources Resources used to get list delimiter.
     * @param list List of char sequences to join.
     * @return Joined char sequences.
     */
    public static CharSequence join(Resources resources, Iterable<CharSequence> list) {
        StringBuilder sb = new StringBuilder();
        final BidiFormatter formatter = BidiFormatter.getInstance();
        final CharSequence separator = resources.getString(R.string.list_delimeter);

        Iterator<CharSequence> itr = list.iterator();
        boolean firstTime = true;
        while (itr.hasNext()) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(separator);
            }
            // Unicode wrap the elements of the list to respect RTL for individual strings.
            sb.append(formatter.unicodeWrap(
                    itr.next().toString(), TextDirectionHeuristics.FIRSTSTRONG_LTR));
        }

        // Unicode wrap the joined value, to respect locale's RTL ordering for the whole list.
        return formatter.unicodeWrap(sb.toString());
    }

    /**
     * @return True if the application is currently in RTL mode.
     */
    public static boolean isRtl() {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) ==
            View.LAYOUT_DIRECTION_RTL;
    }

    public static void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    public static void hideInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private static volatile IPresenceService mService;
    private static boolean mIsBound;

    private static ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "PresenceService connected");
            mService = IPresenceService.Stub.asInterface(service);
            try {
                mService.registerCallback(mCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "PresenceService registerCallback error " + e);
            }
        }
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "PresenceService disconnected");
            mService = null;
        }
    };

    private static IPresenceServiceCB mCallback = new IPresenceServiceCB.Stub() {

        public void setIMSEnabledCB() {
            Log.d(TAG, "PresenceService setIMSEnabled callback");
        }

    };

    public static void bindService(Context context) {
        if (!callFromDialtactsActivity(context)) {
            return;
        }
        Log.d(TAG, "PresenceService BindService ");
        Intent intent = new Intent(IPresenceService.class.getName());
        intent.setClassName("com.qualcomm.qti.presenceserv",
                            "com.qualcomm.qti.presenceserv.PresenceService");
        mIsBound = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public static void unbindService(Context context) {
        if (!callFromDialtactsActivity(context)) {
            return;
        }
        Log.d(TAG, "PresenceService unbindService");
        if (mService != null) {
            try {
                mService.unregisterCallback(mCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "PresenceService unregister error " + e);
            }
        }
        if (mIsBound) {
            Log.d(TAG, "PresenceService unbind");
            context.unbindService(mConnection);
            mIsBound = false;
        }
    }

    public static boolean isBound() {
        return mIsBound;
    }

    public static boolean startAvailabilityFetch(String number){
        Log.d(TAG, "startAvailabilityFetch   number " + number);
        if (mService != null) {
            try {
                boolean vt = false;
                vt = mService.invokeAvailabilityFetch(number);
                return vt;
            } catch (Exception e) {
                Log.d(TAG, "getVTCapOfContact ERROR " + e);
            } finally {
                return false;
            }
        }
        return false;
    }

    public static boolean getVTCapability(String number) {
        Log.d(TAG, "getVTCapability   number " + number);
        if (null != mService) {
            try {
                boolean vt = false;
                vt = mService.hasVTCapability(number);
                Log.d(TAG,
                    "getVTCapability success number " + number + " " + vt);
                return vt;
            } catch (Exception e) {
                Log.d(TAG, "getVTCapability ERROR " + e);
            } finally {
                return false;
            }
        }
        return false;
    }

    private static boolean callFromDialtactsActivity(Context context) {
        String contextString = context.toString();
        String Caller = contextString.substring(
            contextString.lastIndexOf(".") + 1, contextString.indexOf("@"));
        if (Caller.equals("DialtactsActivity")) {
            return true;
        }
        return false;
    }
}
