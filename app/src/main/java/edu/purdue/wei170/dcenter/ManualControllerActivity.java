package edu.purdue.wei170.dcenter;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloSubscriptionCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.rx2.Rx2Apollo;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.common.error.DJIError;
import dji.sdk.useraccount.UserAccountManager;
import edu.purdue.wei170.dcenter.graphql.DroneSubscription;
import edu.purdue.wei170.dcenter.graphql.InsertFlightControlMsgMutation;
import edu.purdue.wei170.dcenter.graphql.type.FlightControlMsgs_insert_input;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subscribers.DisposableSubscriber;

public class ManualControllerActivity extends AppCompatActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback {

    private static final String TAG = ManualControllerActivity.class.getName();

    protected MApplication application;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private FlightController mFlightController;
    protected TextView mConnectStatusTextView;
    private Button mBtnLocate;
    private Button mBtnTakeOff;
    private Button mBtnLand;
    private Button mBtnGoHome;

    private TextView mTextDroneStatus;
    private TextView mDroneName;

    private OnScreenJoystick mScreenJoystickRight;
    private OnScreenJoystick mScreenJoystickLeft;

    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;

    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;

    private GoogleMap gMap;
    private Marker droneMarker = null;

    private Integer droneId;
    private double droneLocationLat, droneLocationLng, droneLocationAlt;
    private float droneDirection;
    private boolean isLanding;
    private boolean isGoingHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manualcontroller);
        application = (MApplication) getApplication();
        droneId = getIntent().getIntExtra("droneId", 0);

        // Subscribe the dronestatus data
        subDroneData();

        initUI();

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

         // Setup the google map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
//            updateTitleBar();
            loginAccount();
        }
    };

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(ManualControllerActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateTitleBar() {
        if(mConnectStatusTextView == null) return;
        boolean ret = false;
        BaseProduct product = DJIApplication.getProductInstance();
        if (product != null) {
            if(product.isConnected()) {
                //The product is connected
                mConnectStatusTextView.setText(DJIApplication.getProductInstance().getModel() + " Connected");
                ret = true;
            } else {
                if(product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft)product;
                    if(aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mConnectStatusTextView.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        if(!ret) {
            // The product or the remote controller are not connected.
            mConnectStatusTextView.setText("Disconnected");
        }
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initFlightController();
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        if (null != mSendVirtualStickDataTimer) {
            mSendVirtualStickDataTask.cancel();
            mSendVirtualStickDataTask = null;
            mSendVirtualStickDataTimer.cancel();
            mSendVirtualStickDataTimer.purge();
            mSendVirtualStickDataTimer = null;
        }
        disposables.dispose();
        super.onDestroy();
    }

    private void subDroneData() {
        // Get droneId from the previous intent
        DroneSubscription droneSub = DroneSubscription.builder().id(droneId).build();

        ApolloSubscriptionCall<DroneSubscription.Data> droneSubCall = application.apolloClient().subscribe(droneSub);
        disposables.add(Rx2Apollo.from(droneSubCall)
                .subscribeWith(new DisposableSubscriber<Response<DroneSubscription.Data>>() {
                    @Override
                    public void onNext(final Response<DroneSubscription.Data> dataResponse) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateUI(dataResponse.data().Drones_by_pk());
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable t) {

                    }

                    @Override
                    public void onComplete() {

                    }
                })
        );
    }

    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        showToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    private void initFlightController() {

        Aircraft aircraft = (Aircraft) DJIApplication.getProductInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            showToast("Disconnected");
            mFlightController = null;
            return;
        } else {
            mFlightController = aircraft.getFlightController();
            mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
        }
    }

    private void initUI() {

        mDroneName = (TextView) findViewById(R.id.drone_name_text_view);
        mBtnLocate = (Button) findViewById(R.id.btn_locate);
        mBtnTakeOff = (Button) findViewById(R.id.btn_take_off);
        mBtnLand = (Button) findViewById(R.id.btn_land);
        mBtnGoHome = (Button) findViewById(R.id.btn_go_home);

        mScreenJoystickRight = (OnScreenJoystick)findViewById(R.id.directionJoystickRight);
        mScreenJoystickLeft = (OnScreenJoystick)findViewById(R.id.directionJoystickLeft);
        mTextDroneStatus = (TextView) findViewById(R.id.text_drone_status);

        mBtnLocate.setOnClickListener(this);
        mBtnTakeOff.setOnClickListener(this);
        mBtnLand.setOnClickListener(this);
        mBtnGoHome.setOnClickListener(this);

        mTextDroneStatus.setSingleLine(false);

        mScreenJoystickRight.setJoystickListener(new OnScreenJoystickListener(){

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }

                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }

                float pitchJoyControlMaxSpeed = 10;
                float rollJoyControlMaxSpeed = 10;

                mPitch = (float)(pitchJoyControlMaxSpeed * pX);

                mRoll = (float)(rollJoyControlMaxSpeed * pY);

                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 100, 200);
                }

            }

        });

        mScreenJoystickLeft.setJoystickListener(new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }

                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }
                float verticalJoyControlMaxSpeed = 2;
                float yawJoyControlMaxSpeed = 30;

                mYaw = (float)(yawJoyControlMaxSpeed * pX);
                mThrottle = (float)(verticalJoyControlMaxSpeed * pY);

                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 100);
                }

            }
        });
    }

    @Override
    public void onClick(View v) {

        final List<FlightControlMsgs_insert_input> inputObjs = new ArrayList<FlightControlMsgs_insert_input>();

        switch (v.getId()) {
            case R.id.btn_locate:
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                break;

            case R.id.btn_take_off:
                inputObjs.add(FlightControlMsgs_insert_input.builder()
                        .drone_id(droneId)
                        .type("take_off")
                        .value("1")
                        .createdAt((new Timestamp(System.currentTimeMillis())).toString())
                        .build()
                );

                break;

            case R.id.btn_land:
                String landType = (isLanding ? "cancel_landing" : "landing");
                inputObjs.add(FlightControlMsgs_insert_input.builder()
                        .drone_id(droneId)
                        .type(landType)
                        .value("1")
                        .createdAt((new Timestamp(System.currentTimeMillis())).toString())
                        .build()
                );

                break;

            case R.id.btn_go_home:
                String goHomeType = (isGoingHome ? "cancel_go_home" : "go_home");
                inputObjs.add(FlightControlMsgs_insert_input.builder()
                        .drone_id(droneId)
                        .type(goHomeType)
                        .value("1")
                        .createdAt((new Timestamp(System.currentTimeMillis())).toString())
                        .build()
                );

                break;

            default:
                break;
        }

        InsertFlightControlMsgMutation insertMut = InsertFlightControlMsgMutation.builder()
                .objects(inputObjs)
                .build();
        ApolloCall<InsertFlightControlMsgMutation.Data> mutCall = application.apolloClient().mutate(insertMut);

        disposables.add(Rx2Apollo.from(mutCall).subscribe());
    }

    class SendVirtualStickDataTask extends TimerTask {

        @SuppressLint("DefaultLocale")
        @Override
        public void run() {

            // Check if the joysticks are back to the center
            if (mPitch == 0 && mRoll == 0 && mYaw == 0 && mThrottle == 0) {
                mSendVirtualStickDataTimer.cancel();
                mSendVirtualStickDataTimer.purge();
                mSendVirtualStickDataTimer = null;
            }

            if (droneId != null) {
                final List<FlightControlMsgs_insert_input> inputObjs = new ArrayList<FlightControlMsgs_insert_input>();
                inputObjs.add(FlightControlMsgs_insert_input.builder()
                        .drone_id(droneId)
                        .type("joystick")
                        .value(String.format("%f %f %f %f", mPitch, mRoll, mYaw, mThrottle))
                        .createdAt((new Timestamp(System.currentTimeMillis())).toString())
                        .build()
                );
                InsertFlightControlMsgMutation insertMut = InsertFlightControlMsgMutation.builder()
                        .objects(inputObjs)
                        .build();
                ApolloCall<InsertFlightControlMsgMutation.Data> mutCall = application.apolloClient().mutate(insertMut);

                disposables.add(Rx2Apollo.from(mutCall).subscribe());
            }
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }
    }

    private void setUpMap() {
        gMap.setOnMapClickListener(this); // add the listener for click for amap object
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);

        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                    droneMarker.setRotation(droneDirection); // Set the rotation
                }
            }
        });
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    private void cameraUpdate() {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);
    }

    @SuppressLint("DefaultLocale")
    private void updateUI(DroneSubscription.Drones_by_pk dataResponse) {
        mDroneName.setText(dataResponse.name());

        droneLocationLat = Double.parseDouble(Objects.requireNonNull(dataResponse.status()).locLat());
        droneLocationLng = Double.parseDouble(Objects.requireNonNull(dataResponse.status()).locLng());
        droneLocationAlt = Double.parseDouble(Objects.requireNonNull(dataResponse.status()).locAlt());
        droneDirection = Float.parseFloat(Objects.requireNonNull(dataResponse.status()).heading());

        DroneSubscription.Status status = dataResponse.status();

        // Update the buttons
        setGoHome(status.isGoingHome());
        setLanding(status.isLanding());

        if (gMap != null) { // When Google map is ready
            updateDroneLocation();
        }

        mTextDroneStatus.setText(
                String.format("Battery: %d%%\nAlt: %.1f\nvX: %s\nvY:%s\nvZ: %s",
                        status.battery(), droneLocationAlt, status.vX(), status.vY(), status.vZ()
                )
        );
    }

    private void setGoHome(boolean isGoingHome) {
        this.isGoingHome = isGoingHome;
        if (isGoingHome) {
            mBtnGoHome.setText("Cancel");
        } else {
            mBtnGoHome.setText("Go Home");
        }
    }

    private void setLanding(boolean isLanding) {
        this.isLanding = isLanding;
        if (isLanding) {
            mBtnLand.setText("Cancel");
        } else {
            mBtnLand.setText("Land");
        }
    }
}
