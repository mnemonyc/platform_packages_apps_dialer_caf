// Copyright 2013 Google Inc. All Rights Reserved.

package com.android.dialer;

import android.app.Application;

import com.android.contacts.common.extensions.ExtensionsFactory;
import com.android.recorder.CallRecorderService;

public class DialerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ExtensionsFactory.init(getApplicationContext());

        if (CallRecorderService.getInstance() != null) {
            CallRecorderService.getInstance().init(getApplicationContext());
        }
    }
}
