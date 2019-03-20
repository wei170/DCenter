package edu.purdue.wei170.dcenter;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class MainMenuActivity extends AppCompatActivity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_mainmenu);

        final Button rcBtn = findViewById(R.id.open_controller);
        final Button rptrBtn = findViewById(R.id.open_receptor);

        rcBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(MainMenuActivity.this, ControllerActivity.class);
                startActivity(intent);
            }
        });

        rptrBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(MainMenuActivity.this, ConnectionActivity.class);
                startActivity(intent);
            }
        });
    }
}
