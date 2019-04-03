package edu.purdue.wei170.dcenter;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import edu.purdue.wei170.dcenter.graphql.DronesQuery;
import edu.purdue.wei170.dcenter.graphql.type.Boolean_comparison_exp;
import edu.purdue.wei170.dcenter.graphql.type.Drones_bool_exp;

public class DroneListActivity extends AppCompatActivity {
    MApplication application;
    ListView listView;
    ArrayAdapter listViewAdapter;
    List<DronesQuery.Drone> droneArray;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        application = (MApplication) getApplication();
        droneArray = new ArrayList<DronesQuery.Drone>();

        setContentView(R.layout.activity_dronelist);

        initUI();
        fetchOnlineDrones();
    }

    private void fetchOnlineDrones() {
        DronesQuery dronesQuery = DronesQuery.builder().build();
        application.apolloClient().query(dronesQuery).enqueue(new ApolloCall.Callback<DronesQuery.Data>() {

            @Override
            public void onResponse(@NotNull Response<DronesQuery.Data> response) {
                Log.i("TAG", response.data().Drones().toString());
                droneArray = response.data().Drones();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listViewAdapter.clear();
                        listViewAdapter.addAll(droneArray);
                    }
                });
            }

            @Override
            public void onFailure(@NotNull ApolloException e) {
                Log.e("ERROR", e.toString());
            }
        });
    }

    private void initUI() {
        listView = (ListView) findViewById(R.id.drone_list_view);

        listViewAdapter = new DroneListAdapter(this, droneArray);
        listView.setAdapter(listViewAdapter);
    }
}
