/*
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

package com.android.dialer.calllog;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.test.NeededForTesting;
import com.android.dialer.PhoneCallDetailsViews;
import com.android.dialer.R;

/**
 * Simple value object containing the various views within a call log entry.
 */
public final class CallLogListItemViews {
    /** The quick contact badge for the contact. */
    public final QuickContactBadge quickContactView;
    /** The primary action view of the entry. */
    public final View primaryActionView;
    /** The sub1 call button on the entry. */
    public final ImageView callButtonSub1;
    /** The sub1 icon to mark the call action. */
    public final ImageView callIconSub1;
    /** The sub2 call button on the entry. */
    public final ImageView callButtonSub2;
    /** The sub2 icon to mark the call action. */
    public final ImageView callIconSub2;

    /** The divider between the primary and the sub1 call button. */
    public final View dividerView_sub1;
    /** The divider between the sub2 call button and the sub2 call button. */
    public final View dividerView;

    /** The layout for the sub1 call button and the sub1 icon. */
    public final View layoutSub1;
    /** The layout for the sub2 call button and the sub2 icon. */
    public final View layoutSub2;

    /** The details of the phone call. */
    public final PhoneCallDetailsViews phoneCallDetailsViews;
    /** The text of the header of a section. */
    public final TextView listHeaderTextView;
    /** The divider to be shown below items. */
    public final View bottomDivider;

    private CallLogListItemViews(QuickContactBadge quickContactView, View primaryActionView,
            ImageView callButtonSub1, ImageView callIconSub1, ImageView callButtonSub2,
            ImageView callIconSub2, View dividerView_sub1, View dividerView,
            PhoneCallDetailsViews phoneCallDetailsViews,
            TextView listHeaderTextView, View bottomDivider,
            View layoutSub1, View layoutSub2) {
        this.quickContactView = quickContactView;
        this.primaryActionView = primaryActionView;
        this.callButtonSub1 = callButtonSub1;
        this.callIconSub1 = callIconSub1;

        this.callButtonSub2 = callButtonSub2;
        this.callIconSub2 = callIconSub2;

        this.dividerView_sub1 = dividerView_sub1;
        this.dividerView = dividerView;
        this.phoneCallDetailsViews = phoneCallDetailsViews;
        this.listHeaderTextView = listHeaderTextView;
        this.bottomDivider = bottomDivider;

        this.layoutSub1 = layoutSub1;
        this.layoutSub2 = layoutSub2;
    }

    public static CallLogListItemViews fromView(View view) {
        return new CallLogListItemViews(
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo),
                view.findViewById(R.id.primary_action_view),
                (ImageView) view.findViewById(R.id.call_button_sub1),
                (ImageView) view.findViewById(R.id.call_icon_sub1),
                (ImageView) view.findViewById(R.id.call_button_sub2),
                (ImageView) view.findViewById(R.id.call_icon_sub2),
                view.findViewById(R.id.divider_sub1),
                view.findViewById(R.id.divider),
                PhoneCallDetailsViews.fromView(view),
                (TextView) view.findViewById(R.id.call_log_header),
                view.findViewById(R.id.call_log_divider),
                view.findViewById(R.id.layout_sub1),
                view.findViewById(R.id.layout_sub2)
                );
    }

    @NeededForTesting
    public static CallLogListItemViews createForTest(Context context) {
        return new CallLogListItemViews(
                new QuickContactBadge(context),
                new View(context),
                new ImageView(context),
                new ImageView(context),
                new ImageView(context),
                new ImageView(context),
                new View(context),
                new View(context),
                PhoneCallDetailsViews.createForTest(context),
                new TextView(context),
                new View(context),
                new View(context),
                new View(context));
    }
}
