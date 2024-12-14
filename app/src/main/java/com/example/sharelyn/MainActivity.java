package com.example.sharelyn;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize buttons
        Button btnHotspot = findViewById(R.id.btnHotspot);
        Button btnWifiDirect = findViewById(R.id.btnWifiDirect);
        Button btnWebShare = findViewById(R.id.btnWebShare);

        // Load animation
        Animation buttonScale = AnimationUtils.loadAnimation(this, R.anim.button_scale);

        // Hotspot Sharing Button
        btnHotspot.setOnClickListener(v -> {
            v.startAnimation(buttonScale); // Apply animation
            Toast.makeText(MainActivity.this, "Hotspot Sharing Clicked", Toast.LENGTH_SHORT).show();
            openSendActivity();
        });

        // Wi-Fi Direct Button
        btnWifiDirect.setOnClickListener(v -> {
            v.startAnimation(buttonScale); // Apply animation
            Toast.makeText(MainActivity.this, "Wi-Fi Direct Clicked", Toast.LENGTH_SHORT).show();
            openReceiveActivity();
        });

        // Web Share Button
        btnWebShare.setOnClickListener(v -> {
            v.startAnimation(buttonScale); // Apply animation
            Toast.makeText(MainActivity.this, "Web Share Clicked", Toast.LENGTH_SHORT).show();
            openWebShareActivity();
        });
    }

    private void openSendActivity() {
        Intent intent = new Intent(this, SendActivity.class);
        startActivity(intent);
    }

    private void openReceiveActivity() {
//        Intent intent = new Intent(this, ReceiveActivity.class);
//        startActivity(intent);
    }

    private void openWebShareActivity() {
//        Intent intent = new Intent(this, WebShareActivity.class);
//        startActivity(intent);
    }
}