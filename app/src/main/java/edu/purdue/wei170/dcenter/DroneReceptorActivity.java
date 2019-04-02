package edu.purdue.wei170.dcenter;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class DroneReceptorActivity extends AppCompatActivity {
    private TextView msgLogger;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dronereceptor);
        initUi();
    }

    private void initUi() {
        msgLogger = findViewById(R.id.msg_log_text);
        msgLogger.append("Started!\n");
    }
}
