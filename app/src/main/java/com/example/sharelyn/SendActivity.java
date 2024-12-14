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
import android.os.AsyncTask;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        // Initialize views
        btnSelectFiles = findViewById(R.id.btnSelectFiles);
        btnSendFiles = findViewById(R.id.btnSendFiles);
        btnDiscoverPeers = findViewById(R.id.btnDiscoverPeers);
        recyclerViewFiles = findViewById(R.id.recyclerViewFiles);
        txtPeersFound = findViewById(R.id.txtPeersFound);
        txtFileCount = findViewById(R.id.txtFileCount);
        progressBar = findViewById(R.id.progressBar);

        // Check and request permissions
        checkAndRequestPermissions();

        // WiFi Direct setup
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        // Setup RecyclerView
        fileAdapter = new FileAdapter(selectedFiles, this);
        recyclerViewFiles.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewFiles.setAdapter(fileAdapter);

        // Select files button
        btnSelectFiles.setOnClickListener(v -> openFileSelector());

        // Send files button
        btnSendFiles.setOnClickListener(v -> sendFiles());

        // Discover peers button
        btnDiscoverPeers.setOnClickListener(v -> discoverPeers());

        // Prepare WiFi Direct intent filter
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // Initial UI update
        updateFileCount();
    }

    private void updateFileCount() {
        txtFileCount.setText(selectedFiles.size() + " Files Selected");
        btnSendFiles.setEnabled(!selectedFiles.isEmpty() && !peers.isEmpty());
        btnSendFiles.setBackgroundTintList(
                ContextCompat.getColorStateList(this,
                        (!selectedFiles.isEmpty() && !peers.isEmpty())
                                ? R.color.enabled_button
                                : R.color.disabled_button
                )
        );
    }

    private void checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET
        };

        List<String> permissionsNeeded = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allPermissionsGranted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions Denied", Toast.LENGTH_SHORT).show();
                finish(); // Close activity if permissions are not granted
            }
        }
    }

    private void discoverPeers() {
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(SendActivity.this, "Discovering peers", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(SendActivity.this, "Peer discovery failed", Toast.LENGTH_SHORT).show();
            }
        });
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
                // Multiple files selected
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri fileUri = data.getClipData().getItemAt(i).getUri();
                    selectedFiles.add(fileUri);
                }
            } else if (data.getData() != null) {
                // Single file selected
                selectedFiles.add(data.getData());
            }
            fileAdapter.notifyDataSetChanged();
            updateFileCount();
        }
    }

    private void sendFiles() {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Initiate WiFi Direct connection and file transfer
        connectAndSendFiles();
    }

    private void connectAndSendFiles() {
        if (peers.isEmpty()) {
            Toast.makeText(this, "No peers available", Toast.LENGTH_SHORT).show();
            return;
        }

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = peers.get(0).deviceAddress; // Select first peer
        config.wps.setup = WpsInfo.PBC;

        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(SendActivity.this, "Connecting to peer", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(SendActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startFileTransfer(String host) {
        new FileTransferTask().execute(host);
    }

    private class FileTransferTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(ProgressBar.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String host = params[0];
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, PORT), 5000);
                OutputStream outputStream = socket.getOutputStream();

                for (Uri fileUri : selectedFiles) {
                    File file = new File(fileUri.getPath());
                    byte[] buffer = new byte[1024];
                    FileInputStream inputStream = new FileInputStream(file);

                    // Send file name
                    outputStream.write(file.getName().getBytes());
                    outputStream.write('\n');

                    // Send file size
                    outputStream.write(String.valueOf(file.length()).getBytes());
                    outputStream.write('\n');

                    // Send file content
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    inputStream.close();
                }

                outputStream.close();
                socket.close();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressBar.setVisibility(ProgressBar.GONE);
            if (success) {
                Toast.makeText(SendActivity.this, "Files sent successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(SendActivity.this, "File transfer failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            peers.clear();
            peers.addAll(peerList.getDeviceList());

            if (!peers.isEmpty()) {
                txtPeersFound.setText(peers.size() + " Peers Found");
                updateFileCount();
            } else {
                Toast.makeText(SendActivity.this, "No peers found", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            if (info.groupFormed && info.isGroupOwner) {
                // Start file transfer as group owner
                startFileTransfer(info.groupOwnerAddress.getHostAddress());
            } else if (info.groupFormed) {
                // Start file transfer as client
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

    private class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
        private WifiP2pManager mManager;
        private WifiP2pManager.Channel mChannel;
        private SendActivity mActivity;

        public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, SendActivity activity) {
            super();
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