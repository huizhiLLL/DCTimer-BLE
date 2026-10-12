package com.dctimer.activity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.dctimer.APP;
import com.dctimer.R;
import com.dctimer.model.BLEDevice;
import com.dctimer.model.SmartCubeTraining;
import com.dctimer.util.GanRobotBleClient;
import com.dctimer.util.GanRobotController;
import com.dctimer.util.GanRobotExecutor;
import com.dctimer.util.GanRobotSessionState;
import com.dctimer.util.Utils;
import com.dctimer.widget.CustomToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.lang.ref.WeakReference;

public class GanRobotActivity extends AppCompatActivity {
    private static final String TAG = "GanRobotActivity";
    private static final int REQUEST_ENABLE_BLUETOOTH = 31;
    private static final int REQUEST_BLE_PERMISSION = 32;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_DISCONNECTING = 3;
    private static final long SCAN_TIMEOUT_MS = 10000L;
    private static final long DISCONNECT_TIMEOUT_MS = 4000L;
    public static final String EXTRA_PREFILL_SCRAMBLE = "extra_prefill_scramble";
    public static final String EXTRA_PREFILL_SCRAMBLE_DISPLAY = "extra_prefill_scramble_display";

    private static class ScrambleResolutionResult {
        final String standardScramble;
        final boolean useMainTargetState;

        ScrambleResolutionResult(String standardScramble, boolean useMainTargetState) {
            this.standardScramble = standardScramble;
            this.useMainTargetState = useMainTargetState;
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<String> scannedDeviceNames = new ArrayList<>();
    private static volatile int sharedConnectionState = STATE_DISCONNECTED;
    private static final Handler autoConnectHandler = new Handler(Looper.getMainLooper());
    private static WeakReference<GanRobotActivity> activeActivityRef = new WeakReference<>(null);
    private static final GanRobotBleClient.Callback robotBleCallback = new GanRobotBleClient.Callback() {
        @Override
        public void onDeviceListChanged(List<BLEDevice> devices) {
            GanRobotActivity activity = activeActivityRef.get();
            if (activity != null) {
                activity.runOnUiThread(() -> activity.refreshRobotDeviceList(devices));
            }
        }

        @Override
        public void onScanFailed() {
            GanRobotActivity activity = activeActivityRef.get();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (activity.scanProgress != null) {
                        activity.scanProgress.setVisibility(View.GONE);
                    }
                });
            }
        }

        @Override
        public void onConnected() {
            sharedConnectionState = STATE_CONNECTED;
            GanRobotActivity activity = activeActivityRef.get();
            if (activity != null) {
                activity.runOnUiThread(() -> activity.handleRobotConnected());
            } else {
                showAutoConnectSuccessToast();
            }
        }

        @Override
        public void onDisconnected(BLEDevice device) {
            sharedConnectionState = STATE_DISCONNECTED;
            GanRobotActivity activity = activeActivityRef.get();
            if (activity != null) {
                activity.runOnUiThread(() -> activity.handleRobotDisconnected());
            }
        }

        @Override
        public void onUnsupportedDevice() {
            sharedConnectionState = STATE_DISCONNECTED;
            GanRobotActivity activity = activeActivityRef.get();
            if (activity != null) {
                activity.runOnUiThread(() -> activity.handleRobotConnectionError(R.string.ble_device_not_supported));
            }
        }

        @Override
        public void onConnectFailed() {
            sharedConnectionState = STATE_DISCONNECTED;
            GanRobotActivity activity = activeActivityRef.get();
            if (activity != null) {
                activity.runOnUiThread(() -> activity.handleRobotConnectionError(R.string.connect_fail));
            }
        }
    };
    private final GanRobotExecutor.Listener robotExecutorListener = new GanRobotExecutor.Listener() {
        @Override
        public void onStatus(String message) {
            appendStatus(message);
        }

        @Override
        public void onToast(int messageResId) {
            Toast.makeText(GanRobotActivity.this, messageResId, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onRemainingChanged(int remaining) {
            upsertRemainingStatus(remaining);
        }

        @Override
        public void onSendingChanged() {
            updateConnectionUi();
        }
    };
    private final Runnable stopScanRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };

    private LinearLayout rootLayout;
    private TextView tvConnectionState;
    private TextView tvRobotStatus;
    private EditText etScramble;
    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnSend;
    private Button btnClear;
    private Button btnSolve;
    private CheckBox cbAutoConnect;
    private Spinner spinnerButtonAction;
    private ProgressBar progressConnecting;
    private int uiMode;
    private int pendingRobotButtonAction = GanRobotController.ACTION_NONE;

    private BluetoothAdapter bluetoothAdapter;
    private AlertDialog scanDialog;
    private ProgressBar scanProgress;
    private ArrayAdapter<String> scanAdapter;
    private String prefillRawScramble = "";
    private String prefillDisplayScramble = "";
    private String latestRemainingStatusLine;
    private final Runnable forceDisconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (getConnectionState() == STATE_DISCONNECTING) {
                closeRobotConnection();
                setConnectionState(STATE_DISCONNECTED);
                appendStatus(getString(R.string.gan_robot_status_disconnected));
            }
        }
    };

    public static GanRobotActivity getActiveActivity() {
        return activeActivityRef.get();
    }

    public static boolean isConnectedAndReady() {
        return GanRobotBleClient.isReady();
    }

    public void requestRobotButtonAction(int action) {
        if (action == GanRobotController.ACTION_NONE) {
            return;
        }
        pendingRobotButtonAction = action;
        performPendingRobotButtonActionIfPossible();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindow();
        setContentView(R.layout.activity_gan_robot);
        bindViews();
        setupToolbar();
        applyThemeColors();
        updateConnectionUi();
        if (getConnectionState() == STATE_CONNECTED) {
            appendStatus(getString(R.string.gan_robot_connected));
        } else {
            appendStatus(getString(R.string.gan_robot_disconnected));
        }
        pendingRobotButtonAction = GanRobotController.ACTION_NONE;
        String prefillScramble = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_PREFILL_SCRAMBLE);
        prefillRawScramble = TextUtils.isEmpty(prefillScramble) ? "" : prefillScramble.trim();
        String prefillDisplayScramble = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_PREFILL_SCRAMBLE_DISPLAY);
        String initialScramble = !TextUtils.isEmpty(prefillDisplayScramble)
                ? prefillDisplayScramble
                : convertStandardScrambleToDisplay(prefillScramble);
        this.prefillDisplayScramble = TextUtils.isEmpty(initialScramble) ? "" : initialScramble.trim();
        if (!TextUtils.isEmpty(initialScramble)) {
            etScramble.setText(initialScramble.trim());
            etScramble.setSelection(etScramble.length());
        }
        uiMode = getResources().getConfiguration().uiMode;
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
        GanRobotBleClient.setCallback(robotBleCallback);
        GanRobotBleClient.initBluetoothAdapter(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        performPendingRobotButtonActionIfPossible();
    }

    private void setupWindow() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
    }

    private void bindViews() {
        rootLayout = findViewById(R.id.layout);
        tvConnectionState = findViewById(R.id.tv_connection_state);
        tvRobotStatus = findViewById(R.id.tv_robot_status);
        etScramble = findViewById(R.id.et_scramble);
        btnConnect = findViewById(R.id.btn_connect_robot);
        btnDisconnect = findViewById(R.id.btn_disconnect_robot);
        btnSend = findViewById(R.id.btn_send_scramble);
        btnClear = findViewById(R.id.btn_clear_scramble);
        btnSolve = findViewById(R.id.btn_solve_cube);
        cbAutoConnect = findViewById(R.id.cb_auto_connect_robot);
        progressConnecting = findViewById(R.id.progress_connecting);
        if (cbAutoConnect != null) {
            cbAutoConnect.setChecked(isAutoConnectEnabled(this));
            cbAutoConnect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                saveAutoConnectEnabled(isChecked);
                if (isChecked) {
                    GanRobotBleClient.maybeAutoConnect(this);
                }
            });
        }

        spinnerButtonAction = findViewById(R.id.spinner_button_action);
        if (spinnerButtonAction != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item,
                    new String[]{
                            getString(R.string.gan_robot_button_action_solve),
                            getString(R.string.gan_robot_button_action_scramble),
                            getString(R.string.gan_robot_button_action_none)
                    });
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerButtonAction.setAdapter(adapter);
            int savedAction = GanRobotController.getRobotButtonAction();
            spinnerButtonAction.setSelection(GanRobotController.getSelectionForAction(savedAction));
            spinnerButtonAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    int action = GanRobotController.getActionForSelection(position);
                    GanRobotController.saveRobotButtonAction(GanRobotActivity.this, action);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                beginConnectFlow();
            }
        });
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disableAutoConnectForManualDisconnect();
                disconnectRobot();
            }
        });
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitScramble();
            }
        });
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (etScramble != null) {
                    etScramble.setText("");
                }
            }
        });
        btnSolve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GanRobotExecutor.solveFromSmartCubeState();
            }
        });
        etScramble.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable editable) {
                updateConnectionUi();
            }
        });
    }

    private void saveAutoConnectEnabled(boolean enabled) {
        SharedPreferences sharedPreferences = getSharedPreferences(GanRobotBleClient.PREF_NAME, MODE_PRIVATE);
        sharedPreferences.edit().putBoolean(GanRobotBleClient.PREF_GAN_ROBOT_AUTO_CONNECT, enabled).apply();
    }

    private void disableAutoConnectForManualDisconnect() {
        saveAutoConnectEnabled(false);
        if (cbAutoConnect != null && cbAutoConnect.isChecked()) {
            cbAutoConnect.setChecked(false);
        }
    }

    private static boolean isAutoConnectEnabled(Context context) {
        return GanRobotBleClient.isAutoConnectEnabled(context);
    }

    private void setupToolbar() {
        CustomToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.action_gan_robot);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(APP.getBackgroundColor());
        toolbar.setItemColor(APP.getTextColor());
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    private void applyThemeColors() {
        rootLayout.setBackgroundColor(APP.getBackgroundColor());
        int gray = Utils.grayScale(APP.getBackgroundColor());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int visibility = gray > 200 ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && gray > 200) {
                visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(visibility);
            getWindow().setStatusBarColor(APP.getBackgroundColor());
            getWindow().setNavigationBarColor(APP.getBackgroundColor());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(gray > 200 ? 0x44000000 : APP.getBackgroundColor());
            getWindow().setNavigationBarColor(APP.getBackgroundColor());
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.uiMode != uiMode) {
            uiMode = newConfig.uiMode;
            if ((uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
            recreate();
        }
    }

    private void beginConnectFlow() {
        int state = getConnectionState();
        if (state == STATE_CONNECTING || state == STATE_CONNECTED || state == STATE_DISCONNECTING) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
            return;
        }
        String[] permissions = getBlePermissions();
        if (permissions.length > 0 && !hasPermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_BLE_PERMISSION);
            return;
        }
        if (!isLocationEnabled()) {
            Toast.makeText(this, R.string.ble_location_disabled, Toast.LENGTH_SHORT).show();
            return;
        }
        openScanDialog();
    }

    private void openScanDialog() {
        if (scanDialog != null && scanDialog.isShowing()) {
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_gan_robot_devices, null);
        ListView listView = view.findViewById(R.id.lv_devices);
        scanProgress = view.findViewById(R.id.progress);
        scanAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, scannedDeviceNames);
        listView.setAdapter(scanAdapter);
        listView.setOnItemClickListener((adapterView, itemView, i, l) -> {
            if (i < 0 || i >= scannedDeviceNames.size()) {
                return;
            }
            if (scanDialog != null) {
                scanDialog.dismiss();
            }
            connectRobot(i);
        });

        scanDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.gan_robot_scan_title)
                .setView(view)
                .setNegativeButton(R.string.btn_cancel, null)
                .setOnDismissListener(dialogInterface -> stopScan())
                .show();
        startScan();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void startScan() {
        scannedDeviceNames.clear();
        if (scanAdapter != null) {
            scanAdapter.notifyDataSetChanged();
        }
        if (scanProgress != null) {
            scanProgress.setVisibility(View.VISIBLE);
        }
        appendStatus(getString(R.string.gan_robot_scanning));
        mainHandler.removeCallbacks(stopScanRunnable);
        mainHandler.postDelayed(stopScanRunnable, SCAN_TIMEOUT_MS);
        if (!GanRobotBleClient.initBluetoothAdapter(this)) {
            mainHandler.removeCallbacks(stopScanRunnable);
            if (scanProgress != null) {
                scanProgress.setVisibility(View.GONE);
            }
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }
        GanRobotBleClient.startScan(this);
    }

    private void stopScan() {
        mainHandler.removeCallbacks(stopScanRunnable);
        if (scanProgress != null) {
            scanProgress.setVisibility(View.GONE);
        }
        GanRobotBleClient.stopScan();
        if (scannedDeviceNames.isEmpty()) {
            appendStatus(getString(R.string.gan_robot_no_device));
        }
    }

    private void refreshRobotDeviceList(List<BLEDevice> devices) {
        scannedDeviceNames.clear();
        if (devices != null) {
            for (BLEDevice device : devices) {
                if (device == null || TextUtils.isEmpty(device.getAddress())) {
                    continue;
                }
                scannedDeviceNames.add(String.format(Locale.US, "%s (%s)", device.getName(), device.getAddress()));
            }
        }
        if (scanAdapter != null) {
            scanAdapter.notifyDataSetChanged();
        }
    }

    private void connectRobot(int deviceIndex) {
        if (deviceIndex < 0 || deviceIndex >= scannedDeviceNames.size()) {
            return;
        }
        GanRobotBleClient.closeConnection();
        setConnectionState(STATE_CONNECTING);
        appendStatus(getString(R.string.gan_robot_connecting));
        try {
            GanRobotBleClient.connectScannedDevice(this, deviceIndex);
        } catch (SecurityException e) {
            setConnectionState(STATE_DISCONNECTED);
            appendStatus(getString(R.string.connect_fail));
            Log.e(TAG, "connectGatt failed", e);
            Toast.makeText(this, R.string.connect_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectRobot() {
        if (!GanRobotBleClient.hasGatt()) {
            mainHandler.removeCallbacks(forceDisconnectRunnable);
            setConnectionState(STATE_DISCONNECTED);
            return;
        }
        mainHandler.removeCallbacks(forceDisconnectRunnable);
        setConnectionState(STATE_DISCONNECTING);
        appendStatus(getString(R.string.gan_robot_disconnecting));
        try {
            GanRobotBleClient.disconnect();
            mainHandler.postDelayed(forceDisconnectRunnable, DISCONNECT_TIMEOUT_MS);
        } catch (SecurityException e) {
            Log.e(TAG, "disconnect failed", e);
            closeRobotConnection();
            setConnectionState(STATE_DISCONNECTED);
        }
    }

    private void closeRobotConnection() {
        GanRobotBleClient.closeConnection();
    }

    private void submitScramble() {
        if (getConnectionState() != STATE_CONNECTED || !GanRobotBleClient.isReady()) {
            Toast.makeText(this, R.string.gan_robot_wait_connect, Toast.LENGTH_SHORT).show();
            return;
        }
        if (GanRobotExecutor.isSending()) {
            Toast.makeText(this, R.string.gan_robot_send_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        final String displayScramble = etScramble.getText() == null ? "" : etScramble.getText().toString();
        final String orientationLabel = getActiveScrambleOrientationLabel();
        ScrambleResolutionResult scrambleResolution = resolveStandardScrambleForSubmit(displayScramble);
        final String scramble = scrambleResolution.standardScramble;
        final boolean useMainTargetState = scrambleResolution.useMainTargetState;
        GanRobotSessionState.setLatestMainScramble(scramble);
        GanRobotSessionState.setUseMainTargetState(useMainTargetState);
        runOnUiThread(() -> appendStatus("Scramble orientation: " + orientationLabel));

        GanRobotExecutor.executeScramble(scramble, useMainTargetState);
    }

    private void performRobotButtonAction(int action) {
        if (action == GanRobotController.ACTION_SOLVE) {
            GanRobotExecutor.solveFromSmartCubeState();
            return;
        }
        if (action == GanRobotController.ACTION_SCRAMBLE) {
            submitScramble();
        }
    }

    private void performPendingRobotButtonActionIfPossible() {
        if (pendingRobotButtonAction == GanRobotController.ACTION_NONE) {
            return;
        }
        if (getConnectionState() != STATE_CONNECTED || GanRobotExecutor.isSending() || GanRobotSessionState.isRobotMoving()
                || !GanRobotBleClient.isReady()) {
            return;
        }
        int action = pendingRobotButtonAction;
        pendingRobotButtonAction = GanRobotController.ACTION_NONE;
        performRobotButtonAction(action);
    }

    private ScrambleResolutionResult resolveStandardScrambleForSubmit(String displayScramble) {
        String normalizedInputDisplay = normalizeScrambleString(displayScramble);
        String normalizedPrefillDisplay = normalizeScrambleString(prefillDisplayScramble);
        if (!TextUtils.isEmpty(prefillRawScramble)
                && !TextUtils.isEmpty(normalizedInputDisplay)
                && !TextUtils.isEmpty(normalizedPrefillDisplay)
                && TextUtils.equals(normalizedInputDisplay, normalizedPrefillDisplay)) {
            return new ScrambleResolutionResult(prefillRawScramble, true);
        }
        return new ScrambleResolutionResult(convertDisplayScrambleToStandard(displayScramble), false);
    }

    private String normalizeScrambleString(String scramble) {
        if (TextUtils.isEmpty(scramble)) {
            return "";
        }
        return scramble
                .replace('\u2019', '\'')
                .replace('\uFF07', '\'')
                .replace('\n', ' ')
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.US);
    }

    public static boolean isSending() {
        return GanRobotExecutor.isSending();
    }

    private void handleRobotConnected() {
        mainHandler.removeCallbacks(forceDisconnectRunnable);
        updateConnectionUi();
        appendStatus(getString(R.string.gan_robot_connected));
    }

    private void handleRobotDisconnected() {
        mainHandler.removeCallbacks(forceDisconnectRunnable);
        updateConnectionUi();
        appendStatus(getString(R.string.gan_robot_status_disconnected));
    }

    private void handleRobotConnectionError(int messageResId) {
        mainHandler.removeCallbacks(forceDisconnectRunnable);
        updateConnectionUi();
        appendStatus(getString(messageResId));
    }

    private void setConnectionState(int state) {
        sharedConnectionState = state;
        updateConnectionUi();
    }

    private int getConnectionState() {
        return sharedConnectionState;
    }

    private void updateConnectionUi() {
        if (tvConnectionState == null) {
            return;
        }
        int stateText;
        int state = getConnectionState();
        switch (state) {
            case STATE_CONNECTING:
                stateText = R.string.gan_robot_connecting;
                break;
            case STATE_CONNECTED:
                stateText = R.string.gan_robot_connected;
                break;
            case STATE_DISCONNECTING:
                stateText = R.string.gan_robot_disconnecting;
                break;
            default:
                stateText = R.string.gan_robot_disconnected;
                break;
        }
        tvConnectionState.setText(stateText);
        boolean connected = state == STATE_CONNECTED;
        boolean connecting = state == STATE_CONNECTING || state == STATE_DISCONNECTING;
        btnConnect.setVisibility(connected ? View.GONE : View.VISIBLE);
        btnDisconnect.setVisibility(connected ? View.VISIBLE : View.GONE);
        boolean sending = GanRobotExecutor.isSending();
        btnConnect.setEnabled(!connecting && !sending);
        btnDisconnect.setEnabled(connected && !sending);
        boolean hasInput = etScramble != null
                && etScramble.getText() != null
                && !TextUtils.isEmpty(etScramble.getText().toString().trim());
        btnSend.setEnabled(!sending && hasInput);
        if (btnClear != null) {
            btnClear.setEnabled(!sending);
        }
        if (btnSolve != null) {
            btnSolve.setEnabled(!sending && connected);
        }
        if (cbAutoConnect != null) {
            cbAutoConnect.setEnabled(!connecting);
        }
        progressConnecting.setVisibility(connecting ? View.VISIBLE : View.GONE);
        performPendingRobotButtonActionIfPossible();
    }

    private void appendStatus(String message) {
        if (TextUtils.isEmpty(message)) {
            return;
        }
        String current = tvRobotStatus.getText() == null ? "" : tvRobotStatus.getText().toString();
        String next = current.isEmpty()
                ? message
                : current + "\n" + message;
        tvRobotStatus.setText(next);
    }

    private void upsertRemainingStatus(int remaining) {
        String nextLine = "RX fff2: remaining=" + remaining;
        String current = tvRobotStatus.getText() == null ? "" : tvRobotStatus.getText().toString();
        if (!TextUtils.isEmpty(latestRemainingStatusLine) && !TextUtils.isEmpty(current)) {
            String[] lines = current.split("\\n");
            StringBuilder rebuilt = new StringBuilder(current.length());
            for (String line : lines) {
                if (TextUtils.equals(line, latestRemainingStatusLine)) {
                    continue;
                }
                if (rebuilt.length() > 0) {
                    rebuilt.append('\n');
                }
                rebuilt.append(line);
            }
            current = rebuilt.toString();
        }
        latestRemainingStatusLine = nextLine;
        String next = TextUtils.isEmpty(current) ? nextLine : current + "\n" + nextLine;
        tvRobotStatus.setText(next);
    }

    private boolean isTrainingScrambleMode() {
        return SmartCubeTraining.isSmart333Training(APP.scrambleIdx);
    }

    private int getActiveScrambleOrientation() {
        return isTrainingScrambleMode() ? APP.smartCubeTrainingOrientation : 0;
    }

    private String getActiveScrambleOrientationLabel() {
        int orientation = getActiveScrambleOrientation();
        String[] faces = getResources().getStringArray(R.array.opt_smart_cube_faces);
        int[] pair = Utils.getSmartCubeOrientationPair(orientation);
        return getString(R.string.smart_cube_orientation_format, faces[pair[0]], faces[pair[1]]);
    }

    private String convertDisplayScrambleToStandard(String displayScramble) {
        return convertScrambleOrientation(displayScramble, false);
    }

    private String convertStandardScrambleToDisplay(String standardScramble) {
        return convertScrambleOrientation(standardScramble, true);
    }

    private String convertScrambleOrientation(String scramble, boolean standardToDisplay) {
        if (TextUtils.isEmpty(scramble)) {
            return scramble;
        }
        int orientation = getActiveScrambleOrientation();
        if (orientation == 0) {
            return scramble;
        }
        String[] moves = scramble.replace('\n', ' ').trim().split("\\s+");
        StringBuilder converted = new StringBuilder(scramble.length() + 8);
        for (String move : moves) {
            if (TextUtils.isEmpty(move)) {
                continue;
            }
            int moveIndex = parseScrambleMoveIndex(move);
            if (moveIndex < 0) {
                appendScrambleToken(converted, move);
                continue;
            }
            int mappedMove = standardToDisplay
                    ? Utils.orientSmartCubeMove(moveIndex, orientation)
                    : Utils.unorientSmartCubeMove(moveIndex, orientation);
            String mappedToken = formatScrambleMoveIndex(mappedMove);
            appendScrambleToken(converted, TextUtils.isEmpty(mappedToken) ? move : mappedToken);
        }
        return converted.toString();
    }

    private void appendScrambleToken(StringBuilder builder, String token) {
        if (TextUtils.isEmpty(token)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(token);
    }

    private int parseScrambleMoveIndex(String move) {
        if (TextUtils.isEmpty(move) || move.length() < 1) {
            return -1;
        }
        int axis;
        switch (move.charAt(0)) {
            case 'U': axis = 0; break;
            case 'R': axis = 3; break;
            case 'F': axis = 6; break;
            case 'D': axis = 9; break;
            case 'L': axis = 12; break;
            case 'B': axis = 15; break;
            default: return -1;
        }
        if (move.length() >= 2) {
            char suffix = move.charAt(1);
            if (suffix == '2') {
                axis += 1;
            } else if (suffix == '\'') {
                axis += 2;
            }
        }
        return axis;
    }

    private String formatScrambleMoveIndex(int moveIdx) {
        if (moveIdx < 0 || moveIdx >= 18) {
            return "";
        }
        char face = "URFDLB".charAt(moveIdx / 3);
        int power = moveIdx % 3;
        if (power == 1) {
            return face + "2";
        }
        if (power == 2) {
            return face + "'";
        }
        return String.valueOf(face);
    }

    private String[] getBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new String[] { Manifest.permission.ACCESS_FINE_LOCATION };
        }
        return new String[0];
    }

    private boolean hasPermissions(String[] permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        try {
            LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return manager.isLocationEnabled();
            }
            return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    public static void postOnMainThread(Runnable r) {
        autoConnectHandler.post(r);
    }

    public static Context robotContext() {
        GanRobotActivity act = activeActivityRef == null ? null : activeActivityRef.get();
        return act != null ? act : APP.getInstance();
    }

    private static void showAutoConnectSuccessToast() {
        GanRobotActivity activity = activeActivityRef.get();
        if (activity != null) {
            activity.runOnUiThread(() ->
                    Toast.makeText(activity, R.string.gan_robot_connected, Toast.LENGTH_SHORT).show()
            );
        } else {
            Context context = APP.getInstance();
            if (context != null) {
                autoConnectHandler.post(() ->
                        Toast.makeText(context, R.string.gan_robot_connected, Toast.LENGTH_SHORT).show()
                );
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activeActivityRef = new WeakReference<>(this);
        GanRobotBleClient.setCallback(robotBleCallback);
        GanRobotExecutor.setListener(robotExecutorListener);
        if (GanRobotBleClient.isReady()) {
            sharedConnectionState = STATE_CONNECTED;
        }
        updateConnectionUi();
        GanRobotBleClient.maybeAutoConnect(this);
        performPendingRobotButtonActionIfPossible();
    }

    @Override
    protected void onPause() {
        GanRobotExecutor.clearListener(robotExecutorListener);
        if (activeActivityRef.get() == this) {
            activeActivityRef.clear();
        }
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_OK) {
                beginConnectFlow();
            } else {
                appendStatus(getString(R.string.gan_robot_status_bluetooth_disabled));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLE_PERMISSION) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.permission_deny, Toast.LENGTH_SHORT).show();
                    appendStatus(getString(R.string.permission_deny));
                    return;
                }
            }
            beginConnectFlow();
        }
    }

    @Override
    public void onBackPressed() {
        if (scanDialog != null && scanDialog.isShowing()) {
            scanDialog.dismiss();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        stopScan();
        mainHandler.removeCallbacks(forceDisconnectRunnable);
        super.onDestroy();
    }

}
