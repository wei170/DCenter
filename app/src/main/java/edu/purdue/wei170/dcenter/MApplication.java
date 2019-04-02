package edu.purdue.wei170.dcenter;

import android.app.Application;
import android.content.Context;

import com.apollographql.apollo.ApolloClient;
import com.secneo.sdk.Helper;

import okhttp3.OkHttpClient;

public class MApplication extends Application {
    private static final String BASE_URL = "https://dcenter-guocheng.herokuapp.com/v1alpha1/graphql";
    private ApolloClient apolloClient;

    public MApplication() {}

    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(MApplication.this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .build();
        apolloClient = ApolloClient.builder()
                .serverUrl(BASE_URL)
                .okHttpClient(okHttpClient)
                .build();
    }

    public ApolloClient apolloClient() {
        return apolloClient;
    }
}
