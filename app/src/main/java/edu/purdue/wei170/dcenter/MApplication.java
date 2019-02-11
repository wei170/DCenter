package edu.purdue.wei170.dcenter;

import android.app.Application;
import android.content.Context;

import com.secneo.sdk.Helper;

public class MApplication extends Application {

    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext((paramContext));
        Helper.install(MApplication.this);
    }
}
