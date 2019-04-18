package edu.purdue.wei170.dcenter;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloMutationCall;
import com.apollographql.apollo.ApolloSubscriptionCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.rx2.Rx2Apollo;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.GoHomeExecutionState;
import dji.common.flightcontroller.LandingGearState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import edu.purdue.wei170.dcenter.graphql.DronesQuery;
import edu.purdue.wei170.dcenter.graphql.FlightControlMsgsSubscription;
import edu.purdue.wei170.dcenter.graphql.InsertDroneStatusMutation;
import edu.purdue.wei170.dcenter.graphql.InsertDronesMutation;
import edu.purdue.wei170.dcenter.graphql.UpdateDroneStatusMutation;
import edu.purdue.wei170.dcenter.graphql.UpdateFlightMsgMutation;
import edu.purdue.wei170.dcenter.graphql.type.DroneStatus_bool_exp;
import edu.purdue.wei170.dcenter.graphql.type.DroneStatus_insert_input;
import edu.purdue.wei170.dcenter.graphql.type.DroneStatus_set_input;
import edu.purdue.wei170.dcenter.graphql.type.Drones_bool_exp;
import edu.purdue.wei170.dcenter.graphql.type.Drones_insert_input;
import edu.purdue.wei170.dcenter.graphql.type.FlightControlMsgs_bool_exp;
import edu.purdue.wei170.dcenter.graphql.type.FlightControlMsgs_order_by;
import edu.purdue.wei170.dcenter.graphql.type.FlightControlMsgs_set_input;
import edu.purdue.wei170.dcenter.graphql.type.Integer_comparison_exp;
import edu.purdue.wei170.dcenter.graphql.type.Order_by;
import edu.purdue.wei170.dcenter.graphql.type.Text_comparison_exp;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.DisposableSubscriber;

public class DroneReceptorActivity extends AppCompatActivity {

    private MApplication application;
    private TextView msgLogger;

    private FlightController mFlightController;

    private final CompositeDisposable disposables = new CompositeDisposable();

    private String droneSerialNumber;
    private Integer droneId;
    private Integer droneStatusId;
    private double droneLocationLat, droneLocationLng, droneLocationAlt;

    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;

    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dronereceptor);
        application = (MApplication) getApplication();

        initUi();

    }

    @Override
    protected void onResume(){
        super.onResume();
        initFlightController();
    }

    @Override
    protected void onDestroy() {
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

    public void showMsg(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                msgLogger.append(msg + "\n");
            }
        });
    }

    private void initUi() {
        msgLogger = findViewById(R.id.msg_log_text);
        msgLogger.setSingleLine(false);
        msgLogger.setMovementMethod(new ScrollingMovementMethod());
        showMsg("Started !");
        initFlightController();
    }

    private void initFlightController() {

        BaseProduct product = DJIApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
                mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
                mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
                mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
                mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);

                // Get the Aircraft serialNumber
                mFlightController.getSerialNumber(new CommonCallbacks.CompletionCallbackWith<String>() {
                    @Override
                    public void onSuccess(String s) {
                        droneSerialNumber = s;
                        msgLogger.append("Drone Serial Number Received: " + droneSerialNumber + "\n");
                        fetchDroneInfo();
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        msgLogger.append(djiError.toString());
                        Log.e("DJI", djiError.toString());
                    }
                });

                mFlightController.setStateCallback(new FlightControllerState.Callback() {

                    @Override
                    public void onUpdate(@NonNull FlightControllerState djiFlightControllerCurrentState) {
                        observeFlightControllerStatus(djiFlightControllerCurrentState);
                    }
                });

                // Always enable virtual joystick
                mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null){
                            showMsg(djiError.getDescription());
                        } else {
                            showMsg("Enable Virtual Stick Success");
                        }
                    }
                });

                // Setup the timer for the joystick controll
                mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                mSendVirtualStickDataTimer = new Timer();
                mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 10);

            }
        }
    }

    // Update the drone location based on states from MCU.
    private void fetchDroneInfo() {
        // Make sure we get the droneSerialNumber
        if (droneSerialNumber == null) return;

        Drones_bool_exp exp = Drones_bool_exp.builder()
                .serialNumber(Text_comparison_exp.builder()
                        ._eq(droneSerialNumber)
                        .build())
                .build();
        DronesQuery dronesQuery = DronesQuery.builder().where(exp).build();
        ApolloCall<DronesQuery.Data> dronesQueryCall = application.apolloClient().query(dronesQuery);

        disposables.add(Rx2Apollo.from(dronesQueryCall).subscribeWith(
                new DisposableObserver<Response<DronesQuery.Data>>() {
                    @Override
                    public void onNext(Response<DronesQuery.Data> dataResponse) {
                        if (dataResponse.data() != null) {
                            List<DronesQuery.Drone> drones = dataResponse.data().Drones();

                            if (drones.isEmpty()) { // the drone is not registered
                                registerDrone();
                            } else {
                                // get the Droneid and Statusid
                                droneId = drones.get(0).id();
                                droneStatusId = drones.get(0).status().id();
                                subFlightControlMsg();
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        msgLogger.append(e.getMessage() + "\n");
                        Log.e("Apollo", e.getMessage());
                    }

                    @Override
                    public void onComplete() {
                    }
                }
        ));
    }

    private void registerDrone() {
        // Create the mutation objects
        final List<Drones_insert_input> insertDroneObjs = new ArrayList<Drones_insert_input>();
        insertDroneObjs.add(Drones_insert_input.builder()
                .serialNumber(droneSerialNumber)
                .build()
        );

        // Register the drone with the DroneStatus
        InsertDronesMutation insertDronesMut = InsertDronesMutation.builder()
                .objects(insertDroneObjs)
                .build();
        ApolloCall<InsertDronesMutation.Data> insertDronesMutCall = application.apolloClient().mutate(insertDronesMut);

        disposables.add(Rx2Apollo.from(insertDronesMutCall)
                .subscribeWith(new DisposableObserver<Response<InsertDronesMutation.Data>>() {
                    @Override
                    public void onNext(Response<InsertDronesMutation.Data> dataResponse) {
                        List<InsertDronesMutation.Returning> rets = dataResponse.data().insert_Drones().returning();
                        if (!rets.isEmpty()) {
                            // Get the Drone id
                            droneId = rets.get(0).id();
                            subFlightControlMsg();
                            showMsg(String.format("The drone %s is registered", droneSerialNumber));
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        showMsg(e.getMessage());
                    }

                    @Override
                    public void onComplete() { }
                })
        );

        final List<DroneStatus_insert_input> insertDSObjs = new ArrayList<DroneStatus_insert_input>();
        insertDSObjs.add(DroneStatus_insert_input.builder()
                .drone_serialNumber(droneSerialNumber)
                .build()
        );

        // Register the drone with the DroneStatus
        InsertDroneStatusMutation insertDSMut = InsertDroneStatusMutation.builder()
                .objects(insertDSObjs)
                .build();
        ApolloCall<InsertDroneStatusMutation.Data> insertDSMutCall = application.apolloClient().mutate(insertDSMut);

        disposables.add(Rx2Apollo.from(insertDSMutCall)
                .subscribeWith(new DisposableObserver<Response<InsertDroneStatusMutation.Data>>() {
                    @SuppressLint("DefaultLocale")
                    @Override
                    public void onNext(Response<InsertDroneStatusMutation.Data> dataResponse) {
                        // Get the DroneStatus
                        droneStatusId = dataResponse.data().insert_DroneStatus().returning().get(0).id();

                        showMsg(String.format("Register the DroneStatus for drone %s with id %d", droneSerialNumber, droneStatusId));
                    }

                    @Override
                    public void onError(Throwable e) {
                        showMsg(e.getMessage());
                    }

                    @Override
                    public void onComplete() {

                    }
                })
        );
    }

    private void observeFlightControllerStatus(FlightControllerState djiFlightControllerCurrentState) {
        // Update the location
        droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
        droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
        droneLocationAlt = djiFlightControllerCurrentState.getAircraftLocation().getAltitude();

        // Build the Apollo mutation call
        if (droneStatusId != null) {
            UpdateDroneStatusMutation updateDSMut = UpdateDroneStatusMutation.builder()
                    .where(DroneStatus_bool_exp.builder()
                            .id(Integer_comparison_exp.builder()._eq(droneStatusId).build())
                            .build())
                    ._set(DroneStatus_set_input.builder()
                            .locLat(String.valueOf(droneLocationLat))
                            .locLng(String.valueOf(droneLocationLng))
                            .locAlt(String.valueOf(droneLocationAlt))
                            .vX(String.valueOf(djiFlightControllerCurrentState.getVelocityX()))
                            .vY(String.valueOf(djiFlightControllerCurrentState.getVelocityY()))
                            .vZ(String.valueOf(djiFlightControllerCurrentState.getVelocityZ()))
                            .heading(String.valueOf(mFlightController.getCompass() != null
                                    ? mFlightController.getCompass().getHeading()
                                    : "0.0"))
                            .isGoingHome(djiFlightControllerCurrentState.isGoingHome())
                            .isLanding(djiFlightControllerCurrentState.getGoHomeExecutionState().equals(GoHomeExecutionState.GO_DOWN_TO_GROUND))
                            .build())
                    .build();
            ApolloCall<UpdateDroneStatusMutation.Data> updateDSMutCall = application.apolloClient().mutate(updateDSMut);

            // Listen to the Apollo Mutation call
            disposables.add(Rx2Apollo.from(updateDSMutCall).subscribe());
        }
    }

    private void subFlightControlMsg() {
        FlightControlMsgsSubscription sub = FlightControlMsgsSubscription.builder()
                .limit(1)
                .order_by(
                        Arrays.asList(
                                FlightControlMsgs_order_by.builder().createdAt(Order_by.DESC).build()
                        )
                )
                .where(FlightControlMsgs_bool_exp.builder()
                        .drone_id(Integer_comparison_exp.builder()
                                ._eq(droneId)
                                .build())
                        ._and(
                                Arrays.asList(
                                        FlightControlMsgs_bool_exp.builder()
                                                .createdAt(Text_comparison_exp.builder()
                                                        ._gte((new Timestamp(System.currentTimeMillis())).toString())
                                                        .build())
                                                .build()
                                        )
                        )
                        .build())
                .build();
        ApolloSubscriptionCall<FlightControlMsgsSubscription.Data> subCall = application.apolloClient().subscribe(sub);

        // Listen to the Apollo Subscription call
        disposables.add(Rx2Apollo.from(subCall)
                .subscribeOn(Schedulers.io())
                .subscribeWith(
                new DisposableSubscriber<Response<FlightControlMsgsSubscription.Data>>() {
                    @Override
                    public void onNext(final Response<FlightControlMsgsSubscription.Data> dataResponse) {
                        final List<FlightControlMsgsSubscription.FlightControlMsg> msgs = dataResponse.data().FlightControlMsgs();
                        if (!msgs.isEmpty()) { // if get any control update
                            handleMsgUpdate(msgs);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {

                    }

                    @Override
                    public void onComplete() {

                    }
                }
        ));
    }

    private void updateMsgRecTime(Integer msgId) {
        UpdateFlightMsgMutation mut = UpdateFlightMsgMutation.builder()
                .where(FlightControlMsgs_bool_exp.builder()
                        .id(Integer_comparison_exp.builder()
                                ._eq(msgId)
                                .build())
                        .build())
                ._set(FlightControlMsgs_set_input.builder()
                        .receivedAt((new Timestamp(System.currentTimeMillis())).toString())
                        .build())
                .build();
        ApolloMutationCall<UpdateFlightMsgMutation.Data> mutCall = application.apolloClient().mutate(mut);

        disposables.add(Rx2Apollo.from(mutCall).subscribe());
    }

    private void handleMsgUpdate(List<FlightControlMsgsSubscription.FlightControlMsg> msgs) {
        // Check the msg type
        for (FlightControlMsgsSubscription.FlightControlMsg msg : msgs) {
            // Update the receive time
            final Integer msgId = msg.id();
            updateMsgRecTime(msgId);

            switch (msg.type()) {
                case "take_off":
                    if (mFlightController != null) {
                        mFlightController.startTakeoff(
                                new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if (djiError != null) {
                                            showMsg(djiError.getDescription());
                                        } else {
                                            showMsg("Take off Success");
                                        }
                                    }
                                }
                        );
                    }

                    break;

                case "go_home":
                    if (mFlightController != null) {
                        mFlightController.startGoHome(
                                new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if (djiError != null) {
                                            showMsg(djiError.getDescription());
                                        } else {
                                            showMsg("Start Going Home");
                                        }
                                    }
                                }
                        );
                    }

                    break;

                case "cancel_go_home":
                    if (mFlightController != null) {
                        mFlightController.cancelGoHome(
                                new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if (djiError != null) {
                                            showMsg(djiError.getDescription());
                                        } else {
                                            showMsg("Cancel Going Home");
                                        }
                                    }
                                }
                        );
                    }

                    break;

                case "landing":
                    if (mFlightController != null) {
                        mFlightController.startLanding(
                                new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if (djiError != null) {
                                            showMsg(djiError.getDescription());
                                        } else {
                                            showMsg("Start landing");
                                        }
                                    }
                                }
                        );
                    }

                    break;

                case "cancel_landing":
                    if (mFlightController != null) {
                        mFlightController.cancelLanding(
                                new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        if (djiError != null) {
                                            showMsg(djiError.getDescription());
                                        } else {
                                            showMsg("Cancel Landing");
                                        }
                                    }
                                }
                        );
                    }

                    break;

                case "joystick":
                    String[] ctrldata = msg.value().split(" ");
                    mPitch      = Float.valueOf(ctrldata[0]);
                    mRoll       = Float.valueOf(ctrldata[1]);
                    mYaw        = Float.valueOf(ctrldata[2]);
                    mThrottle   = Float.valueOf(ctrldata[3]);

                    break;

                default:
                    showMsg("Undefined msg type");
            }
        }
    }


    class SendVirtualStickDataTask extends TimerTask {
        @Override
        public void run() {
            if (mFlightController != null && mSendVirtualStickDataTimer != null) {
                mFlightController.sendVirtualStickFlightControlData(
                        new FlightControlData(
                                mPitch, mRoll, mYaw, mThrottle
                        ), new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if (djiError != null) {
                                    showMsg(djiError.getDescription());
                                }
                            }
                        }
                );
            }

        }
    }
}

