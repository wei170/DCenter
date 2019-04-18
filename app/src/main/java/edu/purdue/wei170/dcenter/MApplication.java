package edu.purdue.wei170.dcenter;

import android.app.Application;
import android.content.Context;

import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.response.CustomTypeAdapter;
import com.apollographql.apollo.response.CustomTypeValue;
import com.apollographql.apollo.subscription.WebSocketSubscriptionTransport;
import com.secneo.sdk.Helper;

import org.jetbrains.annotations.NotNull;

import java.sql.Timestamp;
import java.text.ParseException;

import okhttp3.OkHttpClient;

public class MApplication extends Application {

    private static final String BASE_URL = "http://api.dcenter.academy/v1alpha1/graphql";
    private ApolloClient apolloClient;
    private DJIApplication djiApplication;

    public MApplication() {}

    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(MApplication.this);

        if (djiApplication == null) {
            djiApplication = new DJIApplication();
            djiApplication.setContext(this);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Set up the Apollo manager
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .build();
        apolloClient = ApolloClient.builder()
                .serverUrl(BASE_URL)
                .okHttpClient(okHttpClient)
                .subscriptionTransportFactory(new WebSocketSubscriptionTransport.Factory(BASE_URL, okHttpClient))
                .build();

        // Create the djiApplication to register
        djiApplication.onCreate();
    }

    public ApolloClient apolloClient() {
        return apolloClient;
    }
}
