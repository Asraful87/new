package com.example.sharelyn;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SendActivity extends AppCompatActivity {

    private static final int PICK_FILES_REQUEST_CODE = 1;
    private static final int PERMISSIONS_REQUEST_CODE = 1001;
    private static final int PORT = 8988;

    private List<Uri> selectedFiles = new ArrayList<>();
    private FileAdapter fileAdapter;

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private BroadcastReceiver mReceiver;
    private IntentFilter mIntentFilter;

    private List<WifiP2pDevice> peers = new ArrayList<>();

    private Button btnSelectFiles;
    private Button btnSendFiles;
    private Button btnDiscoverPeers;
    private RecyclerView recyclerViewFiles;
    private TextView txtPeersFound;
    private TextView txtFileCount;
    private ProgressBar progressBar;

    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        initializeViews();
        setupWifiP2p();
        setupRecyclerView();
        setupListeners();
        checkAndRequestPermissions();

        executorService = Executors.newSingleThreadExecutor();
    }

    private void initializeViews() {
        btnSelectFiles = findViewById(R.id.btnSelectFiles);
        btnSendFiles = findViewById(R.id.btnSendFiles);
        btnDiscoverPeers = findViewById(R.id.btnDiscoverPeers);
        recyclerViewFiles = findViewById(R.id.recyclerViewFiles);
        txtPeersFound = findViewById(R.id.txtPeersFound);
        txtFileCount = findViewById(R.id.txtFileCount);
        progressBar = findViewById(R.id.progressBar);
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

    private void setupRecyclerView() {
        fileAdapter = new FileAdapter(selectedFiles, this);
        recyclerViewFiles.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewFiles.setAdapter(fileAdapter);
    }

    private void setupListeners() {
        btnSelectFiles.setOnClickListener(v -> openFileSelector());
        btnSendFiles.setOnClickListener(v -> sendFiles());
        btnDiscoverPeers.setOnClickListener(v -> discoverPeers());
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 (API 33) and above
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) to Android 12 (API 32)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            // Android 7 (API 24) to Android 10 (API 29)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openFileSelector() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select Files"), PICK_FILES_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILES_REQUEST_CODE && resultCode == RESULT_OK) {
            selectedFiles.clear();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri fileUri = data.getClipData().getItemAt(i).getUri();
                    selectedFiles.add(fileUri);
                }
            } else if (data.getData() != null) {
                selectedFiles.add(data.getData());
            }
            fileAdapter.notifyDataSetChanged();
            updateFileCount();
        }
    }

    private void updateFileCount() {
        txtFileCount.setText(selectedFiles.size() + " Files Selected");
        btnSendFiles.setEnabled(!selectedFiles.isEmpty() && !peers.isEmpty());
    }

    private void discoverPeers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Nearby devices permission required for discovering peers", Toast.LENGTH_LONG).show();
                return;
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission required for discovering peers", Toast.LENGTH_LONG).show();
            return;
        }

        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(SendActivity.this, "Discovering peers", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(SendActivity.this, "Peer discovery failed: " + reasonCode, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendFiles() {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (peers.isEmpty()) {
            Toast.makeText(this, "No peers available", Toast.LENGTH_SHORT).show();
            return;
        }
        connectToPeer(peers.get(0));
    }

    private void connectToPeer(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(SendActivity.this, "Connecting to peer", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(SendActivity.this, "Connection failed: " + reason, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startFileTransfer(String host) {
        executorService.execute(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, PORT), 5000);
                try (OutputStream outputStream = socket.getOutputStream()) {
                    long totalBytes = 0;
                    long sentBytes = 0;
                    for (Uri fileUri : selectedFiles) {
                        File file = new File(fileUri.getPath());
                        totalBytes += file.length();
                    }
                    for (Uri fileUri : selectedFiles) {
                        File file = new File(fileUri.getPath());
                        try (FileInputStream inputStream = new FileInputStream(file)) {
                            // Send file name
                            outputStream.write((file.getName() + "\n").getBytes());
                            // Send file size
                            outputStream.write((file.length() + "\n").getBytes());
                            // Send file content
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                                sentBytes += bytesRead;
                                final int progress = (int) ((sentBytes * 100) / totalBytes);
                                runOnUiThread(() -> updateProgress(progress));
                            }
                        }
                    }
                    runOnUiThread(() -> {
                        progressBar.setVisibility(ProgressBar.GONE);
                        Toast.makeText(SendActivity.this, "Files sent successfully", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    Toast.makeText(SendActivity.this, "File transfer failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private void updateProgress(int progress) {
        progressBar.setProgress(progress);
        // Update a TextView to show the progress percentage
    }

    private final WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            List<WifiP2pDevice> refreshedPeers = new ArrayList<>(peerList.getDeviceList());
            if (!refreshedPeers.equals(peers)) {
                peers.clear();
                peers.addAll(refreshedPeers);
                txtPeersFound.setText(peers.size() + " Peers Found");
                updateFileCount();
            }
            if (peers.isEmpty()) {
                Toast.makeText(SendActivity.this, "No peers found", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private final WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            if (info.groupFormed && !info.isGroupOwner) {
                startFileTransfer(info.groupOwnerAddress.getHostAddress());
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
        private SendActivity mActivity;

        public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, SendActivity activity) {
            this.mManager = manager;
            this.mChannel = channel;
            this.mActivity = activity;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if (mManager != null) {
                    mManager.requestPeers(mChannel, peerListListener);
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (mManager != null) {
                    mManager.requestConnectionInfo(mChannel, connectionInfoListener);
                }
            }
        }
    }
}

