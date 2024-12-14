package com.example.sharelyn;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReceiveActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 1001;
    private static final int PORT = 8988;

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private BroadcastReceiver mReceiver;
    private IntentFilter mIntentFilter;

    private List<WifiP2pDevice> peers = new ArrayList<>();

    private Button btnStartReceiving;
    private TextView txtStatus;
    private ProgressBar progressBar;
    private TextView txtProgressPercentage;

    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive);

        initializeViews();
        setupWifiP2p();
        setupListeners();
        checkAndRequestPermissions();

        executorService = Executors.newSingleThreadExecutor();
    }

    private void initializeViews() {
        btnStartReceiving = findViewById(R.id.btnStartReceiving);
        txtStatus = findViewById(R.id.txtStatus);
        progressBar = findViewById(R.id.progressBar);
        txtProgressPercentage = findViewById(R.id.txtProgressPercentage);
    }

    private void setupWifiP2p() {
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    private void setupListeners() {
        btnStartReceiving.setOnClickListener(v -> startReceiving());
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startReceiving() {
        setDeviceStatus(true);
        txtStatus.setText("Waiting for connection...");
    }

    private void setDeviceStatus(boolean isReceiver) {
        WifiP2pManager.ActionListener actionListener = new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                String status = isReceiver ? "Ready to receive" : "Ready to send";
                Toast.makeText(ReceiveActivity.this, status, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(ReceiveActivity.this, "Failed to set device status", Toast.LENGTH_SHORT).show();
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mManager.createGroup(mChannel, actionListener);
    }

    private void receiveFiles() {
        executorService.execute(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(PORT);
                txtStatus.setText("Waiting for sender to connect...");

                Socket client = serverSocket.accept();
                txtStatus.setText("Connected. Receiving files...");

                InputStream inputStream = client.getInputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;

                while (true) {
                    // Read file name
                    StringBuilder fileNameBuilder = new StringBuilder();
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        String chunk = new String(buffer, 0, bytesRead);
                        if (chunk.contains("\n")) {
                            fileNameBuilder.append(chunk.substring(0, chunk.indexOf("\n")));
                            break;
                        }
                        fileNameBuilder.append(chunk);
                    }
                    String fileName = fileNameBuilder.toString();

                    if (fileName.isEmpty()) {
                        break; // No more files
                    }

                    // Read file size
                    StringBuilder fileSizeBuilder = new StringBuilder();
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        String chunk = new String(buffer, 0, bytesRead);
                        if (chunk.contains("\n")) {
                            fileSizeBuilder.append(chunk.substring(0, chunk.indexOf("\n")));
                            break;
                        }
                        fileSizeBuilder.append(chunk);
                    }
                    long fileSize = Long.parseLong(fileSizeBuilder.toString());

                    // Receive file content
                    File outputFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                    FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                    long totalBytesRead = 0;

                    while (totalBytesRead < fileSize && (bytesRead = inputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        updateProgress(totalBytesRead, fileSize);
                    }

                    fileOutputStream.close();
                }

                inputStream.close();
                client.close();
                serverSocket.close();

                runOnUiThread(() -> {
                    txtStatus.setText("Files received successfully");
                    progressBar.setVisibility(View.GONE);
                    txtProgressPercentage.setVisibility(View.GONE);
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    txtStatus.setText("Error receiving files");
                    progressBar.setVisibility(View.GONE);
                    txtProgressPercentage.setVisibility(View.GONE);
                });
            }
        });
    }

    private void updateProgress(long bytesReceived, long totalBytes) {
        int progress = (int) ((bytesReceived * 100) / totalBytes);
        runOnUiThread(() -> {
            progressBar.setProgress(progress);
            txtProgressPercentage.setText(progress + "%");
        });
    }

    private final WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            if (info.groupFormed && info.isGroupOwner) {
                receiveFiles();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    private class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
        private WifiP2pManager mManager;
        private WifiP2pManager.Channel mChannel;
        private ReceiveActivity mActivity;

        public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, ReceiveActivity activity) {
            this.mManager = manager;
            this.mChannel = channel;
            this.mActivity = activity;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (mManager != null) {
                    mManager.requestConnectionInfo(mChannel, connectionInfoListener);
                }
            }
        }
    }
}

