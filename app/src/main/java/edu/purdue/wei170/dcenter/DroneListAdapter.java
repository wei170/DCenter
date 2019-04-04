package edu.purdue.wei170.dcenter;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import edu.purdue.wei170.dcenter.graphql.DronesQuery;

public class DroneListAdapter extends ArrayAdapter<DronesQuery.Drone> {
    private Context context;

    public DroneListAdapter(@NonNull Context context, @NonNull List<DronesQuery.Drone> objects) {
        super(context, R.layout.item_drone, objects);
        this.context = context;
    }

    public View getView(int position, View droneListView, ViewGroup parent) {
        DronesQuery.Drone drone = getItem(position);
        final Integer droneId = drone.id();
        final String droneName = drone.name();
        final String droneSerialNumber = drone.serialNumber();

        // Check if an existing view is being reused, otherwise inflate the view
        if (droneListView == null) {
            droneListView = LayoutInflater.from(getContext()).inflate(R.layout.item_drone, parent, false);
        }

        // Lookup view for data population
        TextView droneNameView = (TextView) droneListView.findViewById(R.id.drone_name);
        TextView droneSerialNumberView = (TextView) droneListView.findViewById(R.id.drone_serial_number);

        // Populate the data into the template view using the data object
        droneNameView.setText(droneName);
        droneSerialNumberView.setText(droneSerialNumber);

        droneNameView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, ManualControllerActivity.class);

                intent.putExtra("droneId", droneId);
                context.startActivity(intent);
            }
        });

        // Return the completed view to render on screen
        return droneListView;
    }

}
