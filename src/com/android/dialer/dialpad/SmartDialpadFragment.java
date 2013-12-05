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

import android.accounts.Account;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.ToneGenerator;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.ContactListItemView.PhotoPosition;
import com.android.contacts.common.model.account.SimAccountType;
import com.android.contacts.common.MoreContactUtils;
import com.android.dialer.DialpadCling;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.dialpad.HanziToPinyin.Token;
import com.android.dialer.widget.multiwaveview.GlowPadView;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.MSimConstants;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * Fragment that displays a twelve-key phone dialpad.
 */
public class SmartDialpadFragment extends DialpadFragment implements View.OnClickListener,
        TextWatcher, CallLogQueryHandler.Listener, CallLogAdapter.CallFetcher {
    private static final String TAG = SmartDialpadFragment.class.getSimpleName();

    private static final String[] CONTACTS_SUMMARY_FILTER_NUMBER_PROJECTION = new String[] {
            ("_id"),
            ("normalized_number"),
            ("display_name"),
            ("photo_id"),
            ("lookup"),
            ("data_id"),
            (RawContacts.ACCOUNT_TYPE),
            (RawContacts.ACCOUNT_NAME),
    };
    private static final int AIRPLANE_MODE_ON_VALUE = 1;
    private static final int AIRPLANE_MODE_OFF_VALUE = 0;
    private static final String WITHOUT_SIM_FLAG = "no_sim";
    private static final int QUERY_CONTACT_ID = 0;
    private static final int QUERY_NUMBER = 1;
    private static final int QUERY_DISPLAY_NAME = 2;
    private static final int QUERY_PHOTO_ID = 3;
    private static final int QUERY_LOOKUP_KEY = 4;
    private static final int QUERY_DATA_ID = 5;
    private static final int QUERY_ACCOUNT_TYPE = 6;
    private static final int QUERY_ACCOUNT_NAME = 7;
    private static final Uri CONTENT_SMART_DIALER_FILTER_URI =
            Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "smart_dialer_filter");
    private Handler mHandler = new Handler();
    private Animation showAction;
    private Animation hideAction;
    private ContactItemListAdapter mAdapter;
    private CallLogAdapter mCallLogAdapter;
    private Cursor mCursor;
    private View mListoutside;
    private View mCountButton;
    private Button mCancel;
    private View mAddContact;
    private TextView mAddContactText;
    private TextView mCountView;

    private View mListTextView;
    private ListView mList;

    private View mCallLogListTextView;
    private ListView mCallLogListView;

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

    private BroadcastReceiver mAirplaneStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            setQueryFilter();
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
    public void afterTextChanged(Editable input) {
        super.afterTextChanged(input);
        if (mSmartDialEnabled) {
            return;
        }

        setCallLogQueryFilter();
        if (isDigitsEmpty()) {
            mAddContact.setVisibility(View.INVISIBLE);
        }
        setQueryFilter();
    }

    private CallLogQueryHandler mCallLogQueryHandler;

    private final ContentObserver mContactsObserver = new CustomContentObserver();

    private class CustomContentObserver extends ContentObserver {
        public CustomContentObserver() {
            super(mHandler);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (!phoneIsInUse()) {
                setCallLogQueryFilter();
                setQueryFilter();
            }
        }
    }

    public static final int CALLLOG_ITEM_CLICKED = 1;

    // Handle the click events for CallLog items in Dialpad.
    private Handler mCallLogClickHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CALLLOG_ITEM_CLICKED:
                    setDigitsPhoneByString(msg.obj.toString());
                    mDigits.setSelection(mDigits.length());
                    break;
                default:
                    Log.e(TAG, "Unkown message, message.what " + msg.what);
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        getActivity().registerReceiver(mAirplaneStateReceiver, filter);
        mCallLogQueryHandler = new CallLogQueryHandler(getActivity().getContentResolver(), this);
        getActivity().getContentResolver().registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, mContactsObserver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mAirplaneStateReceiver);
        getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View fragmentView = super.onCreateView(inflater, container, savedState);

        mList = (ListView) fragmentView.findViewById(R.id.listview);
        mList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onListItemClick(mList, view, position, id);
            }
        });
        FrameLayout headerContainer = new FrameLayout(inflater.getContext());
        View headerView = inflater.inflate(R.layout.smartdialerheader, null, false);
        headerContainer.addView(headerView);
        mList.addHeaderView(headerContainer, null, false);

        mListTextView = fragmentView.findViewById(R.id.textview_contacts);

        mCallLogListTextView = fragmentView.findViewById(R.id.textview_callLog);

        mCallLogListView = (ListView)fragmentView.findViewById(R.id.callloglistview);

        mListoutside = fragmentView.findViewById(R.id.listoutside);
        mCountButton = fragmentView.findViewById(R.id.filterbutton);
        mCountButton.setOnClickListener(this);
        mCountView = (TextView) fragmentView.findViewById(R.id.filter_number);
        mCancel = (Button) fragmentView.findViewById(R.id.cancel_btn);
        mCancel.setOnClickListener(this);
        mAddContact = fragmentView.findViewById(R.id.add_contact);
        mAddContact.setOnClickListener(this);
        mAddContactText = (TextView) fragmentView.findViewById(R.id.add_contact_text);
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
    public void onResume() {
        super.onResume();

        // Invisible as default,visible only when there is no match for
        // searching user input numbers
        mAddContact.setVisibility(View.INVISIBLE);
        if (!phoneIsInUse()) {
            setupListView();
            setCallLogQueryFilter();
            setQueryFilter();
            hideDialPadShowList(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (getActivity().isFinishing())
            return;

        if (mCallLogAdapter != null) {
            mCallLogAdapter.changeCursor(null, null);
            setCallLogListViewHeight(mCallLogListView);
        }

        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
        mAddContact.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.cancel_btn: {
                hideDialPadShowList(false);
                listScrollTop();
                return;
            }
            case R.id.filterbutton: {
                if (mDialpad.getVisibility() == View.VISIBLE) {
                    hideDialPadShowList(true);
                }
                return;
            }
            case R.id.add_contact: {
                final CharSequence digits = mDigits.getText();
                startActivity(getAddToContactIntent(digits));
                return;
            }
            case R.id.dialButton: {
                mHaptic.vibrate();
                return;
            }
        }
        super.onClick(view);
    }

    protected void showDialpadChooser(boolean enabled) {
        super.showDialpadChooser(enabled);

        if (enabled && mListoutside != null) {
            mListoutside.setVisibility(View.GONE);
        }
    }

    protected void showDialConference(boolean enabled) {
        super.showDialConference(enabled);

        if (!enabled) {
            if (mListoutside != null) {
                mListoutside.setVisibility(View.VISIBLE);
            }
        }
    }

    final static class ContactListItemCache {
        public CharArrayBuffer nameBuffer = new CharArrayBuffer(128);
        public CharArrayBuffer dataBuffer = new CharArrayBuffer(128);
        public CharArrayBuffer highlightedTextBuffer = new CharArrayBuffer(128);
        public CharArrayBuffer phoneticNameBuffer = new CharArrayBuffer(128);
    }

    private void setQueryFilter() {
        listScrollTop();
        if (mAdapter != null) {
            String filterString = getTextFilter();
            if (TextUtils.isEmpty(filterString)) {
                mAdapter.changeCursor(null);
            } else {
                Filter filter = mAdapter.getFilter();
                filter.filter(getTextFilter());
            }
        }
    }

    private void setCallLogQueryFilter() {
        if (mCallLogAdapter != null) {
            String filterString = getTextFilter();
            if (TextUtils.isEmpty(filterString)) {
                mCallLogAdapter.changeCursor(null, null);
                setCallLogListViewHeight(mCallLogListView);
            } else {
                mCallLogQueryHandler.fetchCalls(filterString);
            }
        }
    }

    private void hideDialPadShowList(boolean isHide) {
        if (isHide) {
            mDialpad.startAnimation(hideAction);
            mDialpad.setVisibility(View.GONE);
            mCountButton.setVisibility(View.GONE);
            mDialButtonContainer.setVisibility(View.GONE);
            if (mCallLogAdapter != null && mCallLogAdapter.getCount() > 0 ) {
                mCallLogListTextView.setVisibility(View.VISIBLE);
            } else {
                mCallLogListTextView.setVisibility(View.GONE);
            }
            if (mCursor != null && !mCursor.isClosed() && mCursor.getCount() > 0) {
                mListTextView.setVisibility(View.VISIBLE);
            } else {
                mListTextView.setVisibility(View.GONE);
            }

            mCancel.setVisibility(View.VISIBLE);
            mCancel.setText(android.R.string.cancel);
        } else {
            if (!dialpadChooserVisible()) {
                mDialButtonContainer.setVisibility(View.VISIBLE);
            }
            if (!mDialpad.isShown()) {
                mDialpad.startAnimation(showAction);
                mDialpad.setVisibility(View.VISIBLE);
            }
            if ((mCursor != null && !mCursor.isClosed() && mCursor.getCount() > 0)
                    || (mCallLogAdapter.getCount() > 0)) {
                mCountButton.setVisibility(View.VISIBLE);
                mCountView.setText(mCursor.getCount() + mCallLogAdapter.getCount() + "");
                mCountView.invalidate();

                mCallLogListTextView.setVisibility(View.GONE);
                mListTextView.setVisibility(View.GONE);
            }
            mCancel.setVisibility(View.GONE);
            listScrollTop();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        showAction = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        showAction.setDuration(100);
        hideAction = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f);
        hideAction.setDuration(100);
        setupListView();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (null != mAddContactText) {
            mAddContactText.setText(R.string.non_phone_add_to_contacts);
        }
        if (mCancel instanceof TextView) {
            ((TextView) mCancel).setText(android.R.string.cancel);
        }
    }

    private void setDigitsPhoneByString(String phone) {
        playTone(ToneGenerator.TONE_DTMF_0);
        mDigits.setText(phone);
        hideDialPadShowList(false);
        listScrollTop();
    }

    public void onListItemClick(ListView l, View v, int position, long id) {
        //because of position include headview, so list item position should minus head view count
        final Cursor cursor = (Cursor) mAdapter.getItem(position - l.getHeaderViewsCount());
        String phone;
        phone = cursor.getString(QUERY_NUMBER);

        setDigitsPhoneByString(phone);
        mDigits.setSelection(mDigits.length());

    }

    private void listScrollTop() {
        if (mCallLogListView != null) {
            mCallLogListView.post(new Runnable() {
                public void run() {
                    if (isResumed() && mCallLogListView != null) {
                        mCallLogListView.setSelection(0);
                    }
                }
            });
        }

        if (mList != null) {
            mList.post(new Runnable() {
                public void run() {
                    if (isResumed() && mList != null) {
                        mList.setSelection(0);
                    }
                }
            });
        }
    }

    private void setupListView() {
        if (getActivity() == null) {
            return;
        }
        setupCallLogListView();
        final ListView list = mList;
        mAdapter = new ContactItemListAdapter(getActivity());
        mList.setAdapter(mAdapter);
        list.setOnCreateContextMenuListener(this);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (mDialpad.getVisibility() == View.VISIBLE
                        && AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL == scrollState) {
                    hideDialPadShowList(true);
                } else if (mDialpad.getVisibility() == View.VISIBLE
                        && AbsListView.OnScrollListener.SCROLL_STATE_IDLE == scrollState) {
                    list.setSelection(0);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                    int visibleItemCount, int totalItemCount) {
            }
        });
        list.setSaveEnabled(false);
    }

    private void setupCallLogListView() {
        final ListView list = mCallLogListView;
        String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());
        mCallLogAdapter = new CallLogAdapter(getActivity(), this,
                new ContactInfoHelper(getActivity(), currentCountryIso), true,
                mCallLogClickHandler);
        mCallLogListView.setAdapter(mCallLogAdapter);
        list.setOnCreateContextMenuListener(this);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (mDialpad.getVisibility() == View.VISIBLE
                        && AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL == scrollState) {
                    hideDialPadShowList(true);
                } else if (mDialpad.getVisibility() == View.VISIBLE
                        && AbsListView.OnScrollListener.SCROLL_STATE_IDLE == scrollState) {
                    list.setSelection(0);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                    int visibleItemCount, int totalItemCount) {
            }
        });
        list.setSaveEnabled(false);
    }

    private String getTextFilter() {
        if (mDigits != null) {
            // filter useless space
            return mDigits.getText().toString().replaceAll("[^0123456789+]", "");
        }
        return null;
    }

    private Cursor doFilter(String filter) {
        final ContentResolver resolver = getActivity().getContentResolver();
        Builder builder = CONTENT_SMART_DIALER_FILTER_URI.buildUpon();
        builder.appendQueryParameter("filter", filter);
        // Do not show contacts in SIM card when airmode is on
        boolean isAirMode = Settings.System.getInt(
                getActivity().getContentResolver(),Settings.System.AIRPLANE_MODE_ON,
                        AIRPLANE_MODE_OFF_VALUE) == AIRPLANE_MODE_ON_VALUE;
        if (isAirMode) {
            builder.appendQueryParameter(
                   RawContacts.ACCOUNT_TYPE, SimAccountType.ACCOUNT_TYPE)
                           .appendQueryParameter(WITHOUT_SIM_FLAG, "true");
        } else {
            // Do not show contacts in disabled SIM card
            String disabledSimFilter = MoreContactUtils.getDisabledSimFilter();
            if (!TextUtils.isEmpty(disabledSimFilter)) {
                builder.appendQueryParameter(RawContacts.ACCOUNT_NAME, disabledSimFilter);
                builder.appendQueryParameter(WITHOUT_SIM_FLAG, "true");
            }
        }
        mCursor = resolver.query(builder.build(), CONTACTS_SUMMARY_FILTER_NUMBER_PROJECTION, null,
                null, null);
        // Bring on "Add to contacts" in UI thread when there is no match in
        // result for user input numbers
        if (mCursor.getCount() == 0) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mAddContact != null) {
                        mAddContact.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
        return mCursor;
    }

    private final class ContactItemListAdapter extends CursorAdapter {
        private CharSequence mUnknownNameText;
        private Cursor mSuggestionsCursor;
        private int mSuggestionsCursorCount;
        private ContactPhotoManager mContactPhotoManager = ContactPhotoManager
                .getInstance(mContext);

        private int[] getStartEnd(String s, int start, int end) {
            int[] offset = new int[2];

            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (!(c >= '0' && c <= '9' || c == '+' || c >= 'a' && c <= 'z')) {
                    if (i <= start) {
                        start++;
                        end++;
                    } else if (i > start && i <= end) {
                        end++;
                    }
                }
            }
            if (start > s.length()) {
                start = s.length();
            }
            if (end > s.length()) {
                end = s.length();
            }
            offset[0] = start;
            offset[1] = end;
            return offset;
        }

        private String getNumberFormChar(char c) {
            if (c >= 'a' && c <= 'c') {
                return "2";
            } else if (c >= 'd' && c <= 'f') {
                return "3";
            } else if (c >= 'g' && c <= 'i') {
                return "4";
            } else if (c >= 'j' && c <= 'l') {
                return "5";
            } else if (c >= 'm' && c <= 'o') {
                return "6";
            } else if (c >= 'p' && c <= 's') {
                return "7";
            } else if (c >= 't' && c <= 'v') {
                return "8";
            } else if (c >= 'w' && c <= 'z') {
                return "9";
            } else if ('0' <= c && c <= '9') {
                return "" + c;
            } else {
                return "";
            }
        }

        private String getNameNumber(String name) {
            String number = "";
            String nameLow = name.toLowerCase();
            for (int i = 0; i < nameLow.length(); i++) {
                char c = nameLow.charAt(i);
                number = number + getNumberFormChar(c);
            }
            return number;
        }

        public String getFullPinYin(String source) {
            if (!Arrays.asList(Collator.getAvailableLocales()).contains(Locale.CHINA)) {
                return source;
            }
            ArrayList<Token> tokens = HanziToPinyin.getInstance().get(source);
            if (tokens == null || tokens.size() == 0) {
                return source;
            }
            StringBuffer result = new StringBuffer();
            for (Token token : tokens) {
                if (token.type == Token.PINYIN) {
                    result.append(token.target);
                } else {
                    result.append(token.source);
                }
            }
            return result.toString();
        }

        private void setTextViewSearchByNumber(char[] charName, int size, Cursor cursor,
                TextView nameView, TextView dataView) {
            String strNameView = String.copyValueOf(charName, 0, size);
            String strDataViewPhone;
            strDataViewPhone = cursor.getString(QUERY_NUMBER);
            String nameNumcopy = null;
            int i, j;

            String inputNum = getTextFilter();
            String phoneNum = strDataViewPhone != null ? strDataViewPhone.replaceAll(
                    "[^0123456789+]", "") : null;

            if (phoneNum != null && inputNum != null && phoneNum.contains(inputNum)) {
                int start, end;
                start = phoneNum.indexOf(inputNum);
                end = start + inputNum.length();
                int[] offset = getStartEnd(strDataViewPhone, start, end);
                SpannableStringBuilder style = new SpannableStringBuilder(strDataViewPhone);
                style.setSpan(new BackgroundColorSpan(0xFF33B5E5), offset[0], offset[1],
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                dataView.setText(style);
            } else {
                dataView.setText(strDataViewPhone);
            }

            String nameNum = getNameNumber(strNameView);
            if (nameNum == null || nameNum.trim().length() == 0) {
                String strNameViewcopy = getFullPinYin(strNameView);
                nameNumcopy = getNameNumber(strNameViewcopy);
            }
            if (inputNum.startsWith("1") && !nameNum.startsWith("1")) {
                inputNum = inputNum.replaceFirst("^1+", "");
            }
            if (nameNum != null && inputNum != null && nameNum.contains(inputNum)) {
                int start, end;
                start = nameNum.indexOf(inputNum);
                end = start + inputNum.length();
                int[] offset = getStartEnd(strNameView.toLowerCase(), start, end);
                SpannableStringBuilder style = new SpannableStringBuilder(strNameView);
                style.setSpan(new BackgroundColorSpan(0xFF33B5E5), offset[0], offset[1],
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                nameView.setText(style);
            } else if (nameNumcopy != null && inputNum != null && nameNumcopy.contains(inputNum)) {
                int start = 0;
                int end = 0;
                int lengthsave = 0;
                String strNameViewword;
                String strNameViewwordcopy;
                String nameNumwordcopy;
                String inputNumcopy;
                for (i = 0; i < size; i++) {
                    strNameViewword = String.copyValueOf(charName, i, 1);
                    strNameViewwordcopy = getFullPinYin(strNameViewword);
                    nameNumwordcopy = getNameNumber(strNameViewwordcopy);
                    if (nameNumwordcopy != null && inputNum != null
                            && inputNum.contains(nameNumwordcopy) &&
                            (nameNumwordcopy.length() > lengthsave)
                            && (inputNum.indexOf(nameNumwordcopy) == 0)) {
                        lengthsave = nameNumwordcopy.length();
                        start = i;
                        end = i + 1;
                    }
                }
                if (start == 0 && end == 0) {
                    for (i = 0; i < size; i++) {
                        strNameViewword = String.copyValueOf(charName, i, 1);
                        strNameViewwordcopy = getFullPinYin(strNameViewword);
                        nameNumwordcopy = getNameNumber(strNameViewwordcopy);
                        if (nameNumwordcopy != null && inputNum != null
                                && nameNumwordcopy.contains(inputNum) &&
                                (nameNumwordcopy.indexOf(inputNum) == 0)) {
                            start = i;
                            end = i + 1;
                            break;
                        }
                    }
                } else {
                    inputNumcopy = inputNum.substring(lengthsave);
                    for (j = start + 1; j <= size; j++) {
                        strNameViewword = String.copyValueOf(charName, j, 1);
                        strNameViewwordcopy = getFullPinYin(strNameViewword);
                        nameNumwordcopy = getNameNumber(strNameViewwordcopy);
                        if (nameNumwordcopy != null && inputNumcopy != null
                                && inputNumcopy.contains(nameNumwordcopy)) {
                            inputNumcopy = inputNumcopy.substring(nameNumwordcopy.length());
                            end++;
                        } else if (nameNumwordcopy != null && inputNumcopy != null
                                && nameNumwordcopy.contains(inputNumcopy) &&
                                (nameNumwordcopy.indexOf(inputNumcopy) == 0)
                                && (inputNumcopy.length() != 0)) {
                            end++;
                            break;
                        } else {
                            break;
                        }
                    }
                }
                if (end > size) {
                    end = size;
                }
                SpannableStringBuilder style = new SpannableStringBuilder(strNameView);
                style.setSpan(new BackgroundColorSpan(0xFF33B5E5), start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                nameView.setText(style);
            } else {
                nameView.setText(strNameView);// here haven't highlight name
            }
        }

        public ContactItemListAdapter(Context context) {
            super(context, null, false);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (!mDataValid) {
                throw new IllegalStateException(
                        "this should only be called when the cursor is valid");
            }

            if (!mCursor.moveToPosition(position)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }

            View v;
            if (convertView == null || convertView.getTag() == null) {
                v = newView(mContext, mCursor, parent);
            } else {
                v = convertView;
            }
            bindView(v, mContext, mCursor);
            return v;
        }

        public void bindView(View itemView, Context context, Cursor cursor) {
            final ContactListItemView view = (ContactListItemView) itemView;
            final ContactListItemCache cache = (ContactListItemCache) view.getTag();

            cursor.copyStringToBuffer(QUERY_DISPLAY_NAME, cache.nameBuffer);

            TextView nameView = view.getNameTextView();
            TextView dataView = view.getDataView();
            int size = cache.nameBuffer.sizeCopied;
            if (size != 0) {
                setTextViewSearchByNumber(cache.nameBuffer.data, size, cursor, nameView, dataView);
            } else {
                nameView.setText(mUnknownNameText);
            }

            final long contactId = cursor.getLong(QUERY_CONTACT_ID);
            final String lookupKey = cursor.getString(QUERY_LOOKUP_KEY);

            String numType = "";
            final long dataId = cursor.getInt(QUERY_DATA_ID);

            Cursor dataCursor = context.getContentResolver().query(
                    ContentUris.withAppendedId(Data.CONTENT_URI, dataId), new String[] {
                            Phone.TYPE, Phone.LABEL
                    }, null, null, null);

            if (dataCursor != null && dataCursor.getCount()>0 && dataCursor.moveToFirst()){
                int type = dataCursor.getInt(dataCursor.getColumnIndex(Phone.TYPE));
                if (type != Phone.TYPE_CUSTOM){
                    numType = getResources().getString(Phone.getTypeLabelResource(type));
                } else {
                    numType = dataCursor.getString(dataCursor.getColumnIndex(Phone.LABEL));
                }
                dataCursor.close();
            }

            if (!TextUtils.isEmpty(numType)){
                view.setLabel("(" + numType + ")");
            }

            // get the location of that phone number
            /* if (!TextUtils.isEmpty(cursor.getString(QUERY_NUMBER))) {
                String locationStr = "";
                CallerInfo ci = new CallerInfo();
                ci.updateGeoDescription(mContext, cursor.getString(QUERY_NUMBER));
                locationStr = ci.geoDescription;
                if (TextUtils.isEmpty(locationStr)) {
                    view.setLocation(getString(R.string.call_log_empty_gecode));
                } else {
                    view.setLocation(locationStr);
                }
            }*/

            long photoId = 0;
            if (!cursor.isNull(QUERY_PHOTO_ID)) {
                photoId = cursor.getLong(QUERY_PHOTO_ID);
            }

            Account account = null;
            if (!cursor.isNull(QUERY_ACCOUNT_TYPE) && !cursor.isNull(QUERY_ACCOUNT_NAME)) {
                final String accountType = cursor.getString(QUERY_ACCOUNT_TYPE);
                final String accountName = cursor.getString(QUERY_ACCOUNT_NAME);
                account = new Account(accountName, accountType);
            }

            QuickContactBadge photo = view.getQuickContact();
            photo.assignContactFromPhone(cursor.getString(QUERY_NUMBER), true);
            mContactPhotoManager.loadThumbnail(photo, photoId, account, true);
            view.setPresence(null);

        }

        @Override
        public View newView(Context arg0, Cursor arg1, ViewGroup arg2) {
            final ContactListItemView view = new ContactListItemView(getActivity(), null);
            view.setPhotoPosition(PhotoPosition.LEFT);
            view.setTag(new ContactListItemCache());
            view.setQuickContactEnabled(true);
            view.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            return view;
        }

        /**
         * Run the query on a helper thread. Beware that this code does not run
         * on the main UI thread!
         */
        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            return doFilter(constraint.toString());
        }

        public void changeCursor(Cursor cursor) {
            // If the view doesnot created, do nothing.
            if (SmartDialpadFragment.this.getView() == null) {
                return;
            }
            if (isDigitsEmpty()) {
                mCountButton.setVisibility(View.GONE);

                mListTextView.setVisibility(View.GONE);
                mList.setVisibility(View.GONE);

                mCallLogListTextView.setVisibility(View.GONE);
                mCallLogListView.setVisibility(View.GONE);

                hideDialPadShowList(false);
            } else if ((cursor != null && cursor.moveToFirst())
                    || mCallLogAdapter.getCount() > 0) {
                mCountButton.setVisibility(View.VISIBLE);
                mList.setVisibility(View.VISIBLE);
                mCallLogListView.setVisibility(View.VISIBLE);
            } else {
                mCountButton.setVisibility(View.GONE);
                mListTextView.setVisibility(View.GONE);
                mList.setVisibility(View.GONE);
                mCallLogListTextView.setVisibility(View.GONE);
                mCallLogListView.setVisibility(View.GONE);
                hideDialPadShowList(false);
                mAddContact.setVisibility(View.VISIBLE);
            }
            if ((mDialpad.isShown() && !isDigitsEmpty() && cursor != null && cursor.getCount() > 0)
                    || (mDialpad.isShown() && mCallLogAdapter.getCount() > 0)) {
                mCountButton.setVisibility(View.VISIBLE);
                final int contactCount = cursor.getCount();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isDigitsEmpty()) {
                            mCountButton.setVisibility(View.GONE);
                        } else {
                            mCountView.setText(contactCount + mCallLogAdapter.getCount() + "");
                            mCountView.invalidate();
                        }
                    }
                }, 100);// wait 100ms for mCallLogAdapter
            } else {
                mCountButton.setVisibility(View.GONE);
            }

            setCallLogListViewHeight(mCallLogListView);
            super.changeCursor(cursor);
        }
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
        if (!MSimTelephonyManager.getDefault().isMultiSimEnabled())
            return false;

        int phoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < phoneCount; i++) {
            if (!MoreContactUtils.isMultiSimEnable(i)) {
                return false;
            }
        }
        if (MoreContactUtils.getButtonStyle())
            return false;
        return true;
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
            mDialpadCling.setVisibility(View.GONE);
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

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        // do nothing here
    }

    @Override
    public void onCallsFetched(Cursor combinedCursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        mCallLogAdapter.changeCursor(combinedCursor, getTextFilter());
        setCallLogListViewHeight(mCallLogListView);
    }

    @Override
    public void fetchCalls() {
        // do nothing here
    }

    private static final int MAX_ITEM_COUNT = 2;

    private void setCallLogListViewHeight(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) {
            return;
        }

        int count = listAdapter.getCount();
        if (mCursor != null && !mCursor.isClosed() && mCursor.getCount() > 0) {
            if (count > 1) {
                View listItem = listAdapter.getView(0, null, listView);
                listItem.measure(0, 0);
                int listItemHeight = listItem.getMeasuredHeight();
                ViewGroup.LayoutParams params = listView.getLayoutParams();
                params.height = listItemHeight * count + listView.getDividerHeight() * (count - 1);
                listView.setLayoutParams(params);
            } else if (count == 1) {
                View listItem = listAdapter.getView(0, null, listView);
                listItem.measure(0, 0);
                int listItemHeight = listItem.getMeasuredHeight();
                ViewGroup.LayoutParams params = listView.getLayoutParams();
                params.height = listItemHeight * count + listView.getDividerHeight();
                listView.setLayoutParams(params);
            } else {
                ViewGroup.LayoutParams params = listView.getLayoutParams();
                params.height = 0;
                listView.setLayoutParams(params);
            }
        } else {
            ViewGroup.LayoutParams params = listView.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            listView.setLayoutParams(params);
        }
        if (!mDialpad.isShown()) {
            if (count <= 0) {
                mCallLogListTextView.setVisibility(View.GONE);
            } else {
                mCallLogListTextView.setVisibility(View.VISIBLE);
            }
            if (mCursor != null && !mCursor.isClosed() && mCursor.getCount() > 0) {
                mListTextView.setVisibility(View.VISIBLE);
            } else {
                mListTextView.setVisibility(View.GONE);
            }
        }
    }
}
