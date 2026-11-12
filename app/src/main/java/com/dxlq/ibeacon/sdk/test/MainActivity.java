package com.dxlq.ibeacon.sdk.test;

import com.dxlq.ibeacon.demoapp.R;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dxlq.ibeacon.sdk.DxBeaconDevice;
import com.dxlq.ibeacon.sdk.DxBeaconManager;
import com.dxlq.ibeacon.sdk.DxConnection;
import com.dxlq.ibeacon.sdk.data.DeviceInfo;
import com.dxlq.ibeacon.sdk.data.FrameConfig;
import com.dxlq.ibeacon.sdk.frame.*;
import com.dxlq.ibeacon.sdk.listener.ConnectionListener;
import com.dxlq.ibeacon.sdk.listener.OnPasswordCallback;
import com.dxlq.ibeacon.sdk.listener.ScanListener;
import com.dxlq.ibeacon.sdk.utils.DataUtils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST = 100;

    private DxBeaconManager manager;
    private DeviceAdapter adapter;
    private List<DxBeaconDevice> devices = new ArrayList<>();
    private DxBeaconDevice connectedDevice;

    // 连接过程弹窗引用（方便随时 dismiss/show/update）
    private AlertDialog connectingDialog;
    private AlertDialog passwordDialog;

    private Button btnScan;
    private TextView tvScanStatus, tvDeviceCount;
    private ProgressBar pgScan;
    private ListView lvDevices;

    // ===== 过滤器状态（对齐原 APP：名称/MAC/RSSI 三项，各自带开关）=====
    private boolean filterOnName = false;
    private boolean filterOnMac = false;
    private boolean filterOnRssi = false;
    private String filterName = "";
    private String filterMac = "";
    private int filterRssi = -40;   // RSSI 阈值，仅显示 rssi > filterRssi 的设备

    // 扫描超时（15 秒自动 stop）
    private static final long SCAN_TIMEOUT_MS = 15_000;
    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private final Runnable scanTimeoutRunnable = () -> {
        if (manager.isScanning()) {
            manager.stopScan();
           // Toast.makeText(this, "Scan stopped (10s timeout)", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        manager = DxBeaconManager.getInstance(this);

        btnScan = findViewById(R.id.btnScan);
        pgScan = findViewById(R.id.pgScan);
        tvScanStatus = findViewById(R.id.tvScanStatus);
        tvDeviceCount = findViewById(R.id.tvDeviceCount);
        lvDevices = findViewById(R.id.lvDevices);

        adapter = new DeviceAdapter();
        lvDevices.setAdapter(adapter);

        setupScanListener();

        btnScan.setOnClickListener(v -> toggleScan());
        findViewById(R.id.btnFilter).setOnClickListener(v -> showFilterDialog());
        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            DxBeaconDevice device = devices.get(position);
            showDeviceDetailDialog(device);
        });

        requestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (connectedDevice != null && connectedDevice.connection != null
                && !connectedDevice.connection.isConnected()) {
            connectedDevice = null;
        }
        if (!manager.isScanning()) toggleScan();
        // 不自动开扫 — 用户点按钮才开始
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanHandler.removeCallbacks(scanTimeoutRunnable);
        if (manager.isScanning()) manager.stopScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanHandler.removeCallbacksAndMessages(null);
    }

    // ===== 权限 =====

    private boolean isBlePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 31) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            perms.add(Manifest.permission.BLUETOOTH);
            perms.add(Manifest.permission.BLUETOOTH_ADMIN);
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        List<String> need = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                need.add(p);
            }
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            boolean ok = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { ok = false; break; }
            }
            if (!ok) {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
            } else {
                // 权限授予后也不自动开扫 — 等用户点按钮
                Toast.makeText(this, "Permissions granted. Press Scan to start.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ===== 扫描 =====

    private void toggleScan() {
        if (manager.isScanning()) {
            scanHandler.removeCallbacks(scanTimeoutRunnable);
            manager.stopScan();
        } else {
            // 点击时也做权限门禁
            if (!isBlePermissionGranted()) {
                requestPermissions();
                return;
            }

            // 蓝牙未开启时，弹框引导用户开启，本次不启动扫描
            BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter btAdapter = btManager != null ? btManager.getAdapter() : null;
            if (btAdapter == null) {
                Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!btAdapter.isEnabled()) {
                showEnableBluetoothDialog();
                return;
            }

            devices.clear();
            adapter.notifyDataSetChanged();
            tvDeviceCount.setText("Devices (0)");
            manager.startScan();
            pgScan.setVisibility(View.VISIBLE);
            tvScanStatus.setText("Scanning...");
            btnScan.setText("Stop");
            // 10 秒后自动停止扫描
            scanHandler.removeCallbacks(scanTimeoutRunnable);
            scanHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS);
        }
    }

    // 蓝牙未开启时弹框，确认后调起系统"开启蓝牙"界面
    private void showEnableBluetoothDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Bluetooth is off")
                .setMessage("Please turn on Bluetooth to scan for nearby devices.")
                .setPositiveButton("Turn on", (dialog, which) ->
                        startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupScanListener() {
        manager.setScanListener(new ScanListener() {
            @Override
            public void onDeviceFound(DxBeaconDevice device) {
                updateDeviceList();
                runOnUiThread(() -> {
                    pgScan.setVisibility(View.VISIBLE);
                    tvScanStatus.setText("Scanning...");
                    btnScan.setText("Stop");
                });
            }
            @Override
            public void onDeviceUpdated(DxBeaconDevice device) {
                updateDeviceList();
            }
            @Override
            public void onScanFailed(int errorCode) {
                runOnUiThread(() -> {
                    pgScan.setVisibility(View.GONE);
                    tvScanStatus.setText("Scan failed");
                    btnScan.setText("Scan");
                });
            }
            @Override
            public void onScanStopped() {
                runOnUiThread(() -> {
                    pgScan.setVisibility(View.GONE);
                    tvScanStatus.setText("");
                    btnScan.setText("Scan");
                });
            }
        });
    }

    private void updateDeviceList() {
        runOnUiThread(() -> {
            List<DxBeaconDevice> all = manager.getDevices();
            devices = applyFilter(all);
            tvDeviceCount.setText("Devices (" + devices.size() + ")");
            adapter.notifyDataSetChanged();
        });
    }

    /**
     * 按过滤器状态过滤设备列表。
     * 名称：大小写不敏感的子串匹配；MAC：子串匹配；RSSI：rssi > filterRssi。
     * 所有开启的条件为"与"关系，必须同时满足。
     */
    private List<DxBeaconDevice> applyFilter(List<DxBeaconDevice> all) {
        if (!filterOnName && !filterOnMac && !filterOnRssi) {
            return all;
        }
        List<DxBeaconDevice> result = new ArrayList<>();
        for (DxBeaconDevice d : all) {
            if (filterOnName) {
                String name = d.name;
                if (name == null || !name.toUpperCase().contains(filterName.toUpperCase())) {
                    continue;
                }
            }
            if (filterOnMac) {
                if (!d.macAddress.toUpperCase().contains(filterMac.toUpperCase())) {
                    continue;
                }
            }
            if (filterOnRssi) {
                if (!(d.rssi > filterRssi)) {
                    continue;
                }
            }
            result.add(d);
        }
        return result;
    }

    // ===== 过滤对话框 =====

    private void showFilterDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_filter, null);
        EditText etName = view.findViewById(R.id.etFilterName);
        EditText etMac = view.findViewById(R.id.etFilterMac);
        Switch swName = view.findViewById(R.id.swFilterName);
        Switch swMac = view.findViewById(R.id.swFilterMac);
        Switch swRssi = view.findViewById(R.id.swFilterRssi);
        SeekBar sbRssi = view.findViewById(R.id.sbFilterRssi);
        TextView tvRssi = view.findViewById(R.id.tvFilterRssi);

        // 回填当前状态
        etName.setText(filterName);
        etMac.setText(filterMac);
        swName.setChecked(filterOnName);
        swMac.setChecked(filterOnMac);
        swRssi.setChecked(filterOnRssi);
        // progress 0~100 映射 RSSI -100~0
        sbRssi.setProgress(filterRssi + 100);
        tvRssi.setText("RSSI > " + filterRssi + " dBm");

        sbRssi.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int rssi = progress - 100;
                tvRssi.setText("RSSI > " + rssi + " dBm");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Filter")
                .setView(view)
                .setPositiveButton("OK", (d, which) -> {
                    // 保存过滤条件
                    filterOnName = swName.isChecked();
                    filterOnMac = swMac.isChecked();
                    filterOnRssi = swRssi.isChecked();
                    filterName = etName.getText().toString().trim();
                    filterMac = etMac.getText().toString().trim();
                    filterRssi = sbRssi.getProgress() - 100;
                    // 立即刷新列表
                    updateDeviceList();
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    // ===== 设备详情弹窗（展示广播帧 + 连接按钮）=====

    private void showDeviceDetailDialog(DxBeaconDevice device) {
        StringBuilder sb = new StringBuilder();
        //sb.append("MAC: ").append(device.macAddress).append("\n");
        //sb.append("Name: ").append(device.name != null ? device.name : "Unknown").append("\n");
        sb.append("MAC: ").append(device.macAddress).append("\n");
        sb.append("RSSI: ").append(device.rssi).append(" dBm\n");

        // 电池电量（优先自家广播解析的 SOC%，其次 TLM 帧的电压转 SOC）
        DxBatteryFrame bf = device.frameHandler.getBatteryFrame();
        if (bf != null && bf.batteryPercent > 0) {
            sb.append("Battery: ").append(bf.batteryPercent).append("%\n");
        }

        // 广播间隔（对齐原 APP "<-> "+adv.timeinv+" ms"）
        String interval = device.frameHandler.getTimeInterval();
        if (interval != null && !interval.equals("--")) {
            sb.append("Interval: ").append(interval).append(" ms\n");
        }
        sb.append("\n");

        List<DxFrame> frames = device.frameHandler.getAllFrames();
        // 只显示原 APP deviceAdv boolean 覆盖的帧类型（ibeacon/uid/url/tlm/acc/user）
        // Battery 是数据字段（batsoc），不是帧类型，不在 frame section 显示
        List<DxFrame> displayFrames = new ArrayList<>();
        for (DxFrame f : frames) {
            if (f.type != FrameType.BATTERY) displayFrames.add(f);
        }
        if (displayFrames.isEmpty()) {
            sb.append("(no parsed frames yet, wait for more broadcasts)");
        } else {
            sb.append("=== ").append(displayFrames.size()).append(" frame(s) ===\n");
            for (DxFrame f : displayFrames) {
                sb.append("\n— ").append(f.type.getDisplayName()).append(" —\n");
                appendFrameDetails(sb, f, device);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(device.name)
                .setMessage(sb.toString())
                .setPositiveButton("Connect", null)
                .setNegativeButton("Close", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                connectDevice(device);
            });
        });
        dialog.show();
        TextView messageTv = dialog.findViewById(android.R.id.message);
        if(messageTv != null){
            messageTv.setTextSize(14);
        }
    }

    private void appendFrameDetails(StringBuilder sb, DxFrame f, DxBeaconDevice device) {
        switch (f.type) {
            case BATTERY: {
                DxBatteryFrame bf = device.frameHandler.getBatteryFrame();
                if (bf != null) sb.append("SOC: ").append(bf.batteryPercent).append("%");
                break;
            }
            case IBEACON: {
                DxIBeaconFrame ib = device.frameHandler.getIBeaconFrame();
                if (ib != null) {
                    sb.append("UUID: ").append(ib.proximityUuid).append("\n");
                    sb.append("Major: ").append(ib.major).append("   ");
                    sb.append("Minor: ").append(ib.minor).append("\n");
                    sb.append("RSSI(1m): ").append(ib.txPower);
                }
                break;
            }
            case EDDYSTONE_UID: {
                DxEddystoneUidFrame uid = device.frameHandler.getUidFrame();
                if (uid != null) {
                    sb.append("Namespace: ").append(uid.namespaceId).append("\n");
                    sb.append("Instance: ").append(uid.instanceId).append("\n");
                    sb.append("RSSI(0m): ").append(uid.txPower);
                }
                break;
            }
            case EDDYSTONE_URL: {
                DxEddystoneUrlFrame url = device.frameHandler.getUrlFrame();
                if (url != null) {
                    sb.append("URL: ").append(url.fullUrl).append("\n");
                    sb.append("RSSI(0m): ").append(url.txPower);
                }
                break;
            }
            case EDDYSTONE_TLM: {
                DxEddystoneTlmFrame tlm = device.frameHandler.getTlmFrame();
                if (tlm != null) {
                   // sb.append("Version: ").append(tlm.version).append("\n");
                    sb.append("Battery: ").append((int) tlm.batteryVoltageMv).append(" mV\n");
                    sb.append("Temp: ").append(tlm.temperatureC).append(" °C\n");
                    sb.append("Adv Count: ").append(tlm.advCount).append("\n");
                    sb.append("Uptime: ").append(tlm.secondsSinceBoot).append(" s");
                }
                break;
            }
            case ACC: {
                DxAccFrame acc = device.frameHandler.getAccFrame();
                if (acc != null) {
                    sb.append("X: ").append(String.format("%.2f", acc.xAxis_mg)).append(" mg\n");
                    sb.append("Y: ").append(String.format("%.2f", acc.yAxis_mg)).append(" mg\n");
                    sb.append("Z: ").append(String.format("%.2f", acc.zAxis_mg)).append(" mg");
                }
                break;
            }
            case USER: {
                DxUserDataFrame uf = device.frameHandler.getUserFrame();
                if (uf != null && uf.rawData != null) {
                    sb.append("Adv Data(hex): ").append(DataUtils.getHex(uf.rawData));
                }
                break;
            }
            default:
                sb.append("(type=").append(f.type.getDisplayName()).append(")");
        }
    }

    // ===== 连接 =====

    private void connectDevice(DxBeaconDevice device) {
        // 正在连接中（含等待输入密码）忽略重复触发，否则会同时发起两条连接，出现两个密码框和一次"连接异常"
        if (connectingDialog != null && connectingDialog.isShowing()) return;
        if (passwordDialog != null && passwordDialog.isShowing()) return;
        if (manager.isScanning()) manager.stopScan();

        connectingDialog = new AlertDialog.Builder(this)
                .setTitle("Connecting...")
                .setMessage("Connecting to " + device.macAddress)
                .setCancelable(false)
                .create();
        connectingDialog.show();

        manager.connect(device, new ConnectionListener() {
            @Override
            public void onStateChanged(DxBeaconDevice d, int state) {
                runOnUiThread(() -> {
                    switch (state) {
                        case DxConnection.STATE_CONNECTING:
                            updateConnectingDialog("Connecting...");
                            break;
                        case DxConnection.STATE_CONNECTED:
                            updateConnectingDialog("Connected, discovering services...");
                            break;
                        case DxConnection.STATE_AUTHENTICATING:
                            updateConnectingDialog("Authenticating...");
                            break;
                        case DxConnection.STATE_READING_FRAMES:
                            updateConnectingDialog("Reading frames...");
                            break;
                        case DxConnection.STATE_READING_INFO:
                            updateConnectingDialog("Reading device info...");
                            break;
                        case DxConnection.STATE_COMPLETED:
                            safeDismiss(connectingDialog);
                            safeDismiss(passwordDialog);
                            connectedDevice = d;
                            gotoFrameActivity(d);
                            break;
                        case DxConnection.STATE_AUTH_FAILED:
                            safeDismiss(connectingDialog);
                            safeDismiss(passwordDialog);
                            break;
                        case DxConnection.STATE_DISCONNECTED:
                            safeDismiss(connectingDialog);
                            safeDismiss(passwordDialog);
                            Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                            break;
                    }
                });
            }

            @Override
            public void onProgress(DxBeaconDevice d, int current, int total, String stepDesc) {
                runOnUiThread(() -> {
                    String msg = "Reading: " + stepDesc + "  (" + current + "/" + total + ")";
                    updateConnectingDialog(msg);
                });
            }

            @Override
            public void onFrameListRead(DxBeaconDevice d, FrameConfig[] frames) {}

            @Override
            public void onDeviceInfoRead(DxBeaconDevice d, DeviceInfo info) {}

            @Override
            public void onPasswordRequired(DxBeaconDevice d, OnPasswordCallback callback) {
                runOnUiThread(() -> {
                    safeDismiss(connectingDialog);
                    showPasswordDialog(d, callback);
                });
            }

            @Override
            public void onCommandResponse(DxBeaconDevice d, int cmd, byte[] payload) {}

            @Override
            public void onError(DxBeaconDevice d, int code, String msg) {
                runOnUiThread(() -> {
                    safeDismiss(connectingDialog);
                    safeDismiss(passwordDialog);
                    String tip;
                    switch (code) {
                        case -10: tip = "Password timeout, device disconnected"; break;
                        case -11: tip = "Wrong password"; break;
                        default:  tip = "Error(" + code + "): " + msg;
                    }
                    Toast.makeText(MainActivity.this, tip, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateConnectingDialog(String msg) {
        if (connectingDialog != null && connectingDialog.isShowing()) {
            connectingDialog.setMessage(msg);
        }
    }

    /** 安全关闭对话框：无论是否正在显示都尝试 dismiss，并置空引用防止残留 */
    private void safeDismiss(AlertDialog d) {
        if (d != null) {
            try { d.dismiss(); } catch (Exception ignored) {}
        }
    }

    private void showPasswordDialog(DxBeaconDevice device, OnPasswordCallback callback) {
        // 已有一个密码框在显示时先关掉旧框，避免两个密码框叠在一起、关掉上面的后下面那个再也关不掉
        safeDismiss(passwordDialog);

        View view = LayoutInflater.from(this).inflate(R.layout.inputpwd, null);
        EditText etPwd = view.findViewById(R.id.etPassword);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setTitle("Password Required")
                .setCancelable(false)
                .create();
        passwordDialog = dialog;

        view.findViewById(R.id.btnPwdOk).setOnClickListener(v -> {
            String pwd = etPwd.getText().toString().trim();
            if (pwd.isEmpty()) return;
            // 关闭的是"自己"这个对话框，而不是全局最新的那个引用
            safeDismiss(dialog);
            // 密码发送后，重新 show connectingDialog 显示后续读进度
            if (connectingDialog != null) {
                connectingDialog.setMessage("Authenticating + Reading...");
                if (!connectingDialog.isShowing()) connectingDialog.show();
            }
            if (callback != null) {
                callback.onPassword(pwd);
            } else {
                Toast.makeText(MainActivity.this, "No password callback", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btnPwdCancel).setOnClickListener(v -> {
            safeDismiss(dialog);
            if (device.connection != null) device.connection.disconnect();
        });

        dialog.show();
    }

    private void gotoFrameActivity(DxBeaconDevice device) {
        android.content.Intent intent = new android.content.Intent(this, FrameActivity.class);
        intent.putExtra("mac", device.macAddress);
        startActivity(intent);
    }

    // ===== Device Adapter =====

    private class DeviceAdapter extends BaseAdapter {
        @Override public int getCount() { return devices.size(); }
        @Override public Object getItem(int i) { return devices.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_device, parent, false);
            }
            DxBeaconDevice d = devices.get(position);
            ((TextView) v.findViewById(R.id.tvName)).setText(d.name != null ? d.name : "Unknown");
            ((TextView) v.findViewById(R.id.tvAddress)).setText(d.macAddress);
            ((TextView) v.findViewById(R.id.tvRssi)).setText(d.rssi + " dBm");

            // 显示已识别的帧类型（严格对齐原 APP deviceAdv boolean：ibeacon/uid/url/tlm/acc/user）
            // Battery (batsoc) 是数据字段，不是帧类型标签
            TextView tvFrames = v.findViewById(R.id.tvFrames);
            List<DxFrame> allFrames = d.frameHandler.getAllFrames();
            StringBuilder sb = new StringBuilder();
            for (DxFrame f : allFrames) {
                if (f.type == FrameType.BATTERY) continue;
                if (sb.length() > 0) sb.append(" · ");
                sb.append(f.type.getDisplayName());
            }
            if (sb.length() > 0) {
                tvFrames.setText(sb.toString());
                tvFrames.setVisibility(View.VISIBLE);
            } else {
                tvFrames.setVisibility(View.GONE);
            }
            return v;
        }
    }
}
