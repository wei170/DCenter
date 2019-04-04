package edu.purdue.wei170.dcenter;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.rx2.Rx2Apollo;

import java.util.ArrayList;
import java.util.List;

import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import edu.purdue.wei170.dcenter.graphql.DronesQuery;
import edu.purdue.wei170.dcenter.graphql.InsertDroneStatusMutation;
import edu.purdue.wei170.dcenter.graphql.InsertDronesMutation;
import edu.purdue.wei170.dcenter.graphql.UpdateDroneStatusMutation;
import edu.purdue.wei170.dcenter.graphql.type.DroneStatus_bool_exp;
import edu.purdue.wei170.dcenter.graphql.type.DroneStatus_insert_input;
import edu.purdue.wei170.dcenter.graphql.type.DroneStatus_set_input;
import edu.purdue.wei170.dcenter.graphql.type.Drones_bool_exp;
import edu.purdue.wei170.dcenter.graphql.type.Drones_insert_input;
import edu.purdue.wei170.dcenter.graphql.type.Integer_comparison_exp;
import edu.purdue.wei170.dcenter.graphql.type.Text_comparison_exp;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;

public class DroneReceptorActivity extends AppCompatActivity {

    private MApplication application;
    private TextView msgLogger;
    private FlightController mFlightController;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private String droneSerialNumber;
    private Integer droneStatusId;
    private double droneLocationLat, droneLocationLng;

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
        super.onDestroy();
        disposables.dispose();
    }

    private void initUi() {
        msgLogger = findViewById(R.id.msg_log_text);
        msgLogger.setSingleLine(false);
        msgLogger.setMovementMethod(new ScrollingMovementMethod());
        msgLogger.append("Started!\n");
        initFlightController();
    }

    private void initFlightController() {

        BaseProduct product = DJIApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
        }

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

        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(@NonNull FlightControllerState djiFlightControllerCurrentState) {
                    observeFlightControllerStatus(djiFlightControllerCurrentState);
                }
            });
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
                                // get the DroneStatus
                                droneStatusId = drones.get(0).status().id();
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
                        msgLogger.append(String.format("The drone %s is registered\n", droneSerialNumber));
                    }

                    @Override
                    public void onError(Throwable e) {
                        msgLogger.append(e.getMessage() + "\n");
                        Log.e("Apollo", e.getMessage());
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
                    @Override
                    public void onNext(Response<InsertDroneStatusMutation.Data> dataResponse) {
                        // Get the DroneStatus
                        droneStatusId = dataResponse.data().insert_DroneStatus().returning().get(0).id();

                        msgLogger.append(String.format("Register the DroneStatus for drone %s with id %d\n", droneSerialNumber, droneStatusId));
                    }

                    @Override
                    public void onError(Throwable e) {
                        msgLogger.append(e.getMessage() + "\n");
                        Log.e("Apollo", e.getMessage());
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
        msgLogger.append(String.format("Get the drone location => lat: %s, lng: %s\n", droneLocationLat, droneLocationLng));

        // Build the Apollo mutation call
        if (droneStatusId != null) {
            UpdateDroneStatusMutation updateDSMut = UpdateDroneStatusMutation.builder()
                    .where(DroneStatus_bool_exp.builder()
                            .id(Integer_comparison_exp.builder()._eq(droneStatusId).build())
                            .build())
                    ._set(DroneStatus_set_input.builder()
                            .locLat(String.valueOf(droneLocationLat))
                            .locLng(String.valueOf(droneLocationLng))
                            .build())
                    .build();
            ApolloCall<UpdateDroneStatusMutation.Data> updateDSMutCall = application.apolloClient().mutate(updateDSMut);

            // Listen to the Apollo Mutation call
            disposables.add(Rx2Apollo.from(updateDSMutCall).subscribe());
        }
    }
}

