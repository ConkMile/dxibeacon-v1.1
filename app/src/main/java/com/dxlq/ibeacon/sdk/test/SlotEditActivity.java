package com.dxlq.ibeacon.sdk.test;

import com.dxlq.ibeacon.demoapp.R;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.dxlq.ibeacon.sdk.DxBeaconDevice;
import com.dxlq.ibeacon.sdk.DxBeaconManager;
import com.dxlq.ibeacon.sdk.DxConnection;
import com.dxlq.ibeacon.sdk.data.DeviceInfo;
import com.dxlq.ibeacon.sdk.data.FrameConfig;
import com.dxlq.ibeacon.sdk.listener.ConnectionListener;
import com.dxlq.ibeacon.sdk.listener.OnPasswordCallback;
import com.dxlq.ibeacon.sdk.slot.*;

import java.util.HashMap;
import java.util.Map;
/**
 * 单槽位编辑界面 — 对齐原 APP FramesetActivity 模式
 * 点击帧列表某槽位后进入，可切换帧类型、填参数、设 Trigger
 */
public class SlotEditActivity extends AppCompatActivity {

    private DxConnection connection;
    private int slotIndex;
    private FrameConfig frameConfig;   // 当前槽位的帧数据（可能为 null 或未启用）

    // ===== 帧类型表 =====
    private static final String[] TYPE_NAMES = {
            "Device Info", "iBeacon", "Eddystone UID",
            "Eddystone URL", "Eddystone TLM", "ACC", "UserData"
    };
    private static final int[] TYPE_CODES = {
            FrameConfig.FRAME_TYPE_INFO, FrameConfig.FRAME_TYPE_IBEACON,
            FrameConfig.FRAME_TYPE_UID, FrameConfig.FRAME_TYPE_URL,
            FrameConfig.FRAME_TYPE_TLM, FrameConfig.FRAME_TYPE_ACC,
            FrameConfig.FRAME_TYPE_USER
    };

    // ===== Trigger 类型表 =====
    private static final String[] TRIGGER_TYPE_NAMES = {
            "Double click button",   // pos 0 → raw type 1
            "Three click button",    // pos 1 → raw type 2
            "Device move"            // pos 2 → raw type 3
    };

    // ===== UI 引用 =====
    private Spinner spFrameType;
    private LinearLayout fieldContainer;
    private SeekBar sbInterval, sbPower, sbTriggerTime;
    private TextView tvIntervalLabel, tvPowerLabel, tvTriggerTimeLabel;
    private SwitchCompat swTrigger;
    private LinearLayout triggerOptions;
    private Spinner spTriggerType;
    private Button btnSave, btnDisable;

    // 帧专属 EditText 映射 (key → EditText)
    private Map<String, EditText> editMap = new HashMap<>();
    private int currentTypeCode;

    // 连接状态监听：设备断开时退出编辑界面，回到扫描页
    private final ConnectionListener stateListener = new ConnectionListener() {
        @Override public void onStateChanged(DxBeaconDevice d, int state) {
            if (state == DxConnection.STATE_DISCONNECTED) {
                runOnUiThread(() -> {
                    Toast.makeText(SlotEditActivity.this, "Device disconnected", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }
        @Override public void onFrameListRead(DxBeaconDevice d, FrameConfig[] f) {}
        @Override public void onDeviceInfoRead(DxBeaconDevice d, DeviceInfo info) {}
        @Override public void onPasswordRequired(DxBeaconDevice d, OnPasswordCallback cb) {}
        @Override public void onCommandResponse(DxBeaconDevice d, int cmd, byte[] payload) {}
        @Override public void onError(DxBeaconDevice d, int code, String msg) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_slot_edit);

        String mac = getIntent().getStringExtra("mac");
        slotIndex = getIntent().getIntExtra("slot", 0);
        if (mac == null) { finish(); return; }

        // 从设备管理器取连接
        DxBeaconManager manager = DxBeaconManager.getInstance(this);
        DxBeaconDevice device = null;
        for (DxBeaconDevice d : manager.getDevices()) {
            if (d.macAddress.equals(mac)) { device = d; break; }
        }
        if (device == null || device.connection == null) {
            Toast.makeText(this, "Device not connected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        connection = device.connection;

        // 注册连接状态监听：设备断开时自动退出
        connection.addConnectionListener(stateListener);

        // 顶部栏
        ((TextView) findViewById(R.id.tvTitle)).setText("Slot " + (slotIndex + 1));
        ((ImageButton) findViewById(R.id.btnBack)).setOnClickListener(v -> finish());

        // 帧类型 Spinner
        spFrameType = findViewById(R.id.spFrameType);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, TYPE_NAMES);
        spFrameType.setAdapter(typeAdapter);

        // 公共参数 SeekBar
        fieldContainer = findViewById(R.id.fieldContainer);
        sbInterval = findViewById(R.id.sbInterval);
        sbPower = findViewById(R.id.sbPower);
        tvIntervalLabel = findViewById(R.id.tvIntervalLabel);
        tvPowerLabel = findViewById(R.id.tvPowerLabel);
        sbInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int ms = (progress + 1) * 100;
                tvIntervalLabel.setText("Adv Interval: " + ms + "ms");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        sbPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                tvPowerLabel.setText("TX Power: " + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Trigger
        swTrigger = findViewById(R.id.swTrigger);
        triggerOptions = findViewById(R.id.triggerOptions);
        spTriggerType = findViewById(R.id.spTriggerType);
        sbTriggerTime = findViewById(R.id.sbTriggerTime);
        tvTriggerTimeLabel = findViewById(R.id.tvTriggerTimeLabel);
        ArrayAdapter<String> trigAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, TRIGGER_TYPE_NAMES);
        spTriggerType.setAdapter(trigAdapter);
        swTrigger.setOnCheckedChangeListener((btn, checked) ->
                triggerOptions.setVisibility(checked ? View.VISIBLE : View.GONE));
        sbTriggerTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                tvTriggerTimeLabel.setText("Trigger Time[S]: " + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // 按钮
        btnSave = findViewById(R.id.btnSave);
        btnDisable = findViewById(R.id.btnDisable);
        btnSave.setOnClickListener(v -> onSave());
        btnDisable.setOnClickListener(v -> onDisable());

        // 帧类型切换 → 重建字段区
        // setSelection 会异步触发 onItemSelected（延迟到 layout 阶段），
        // 对齐原 APP FramesetActivity.setFrameShow → loadData(index) 的行为：
        // 切换到某帧类型时，从设备上查找该类型的已有配置（可能在其他槽位），
        // 找到则用已有数据回填，找不到才用默认值。
        spFrameType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                currentTypeCode = TYPE_CODES[pos];
                FrameConfig existing = findFrameByType(currentTypeCode);
                if (existing != null && existing.rawData != null) {
                    ParsedValues pv = parseRawData(existing.rawData, currentTypeCode);
                    rebuildFieldViews(currentTypeCode, pv);
                } else {
                    rebuildFieldViews(currentTypeCode);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // 加载当前帧数据到 UI
        loadFrameData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connection != null) {
            connection.removeConnectionListener(stateListener);
        }
    }

    // ========== 加载当前帧数据 ==========

    /** 在所有槽位中查找指定帧类型的配置（该类型可能在其他槽位） */
    private FrameConfig findFrameByType(int typeCode) {
        FrameConfig[] frames = connection.frames;
        if (frames == null) return null;
        for (FrameConfig fc : frames) {
            if (fc != null && fc.getFrameType() == typeCode) return fc;
        }
        return null;
    }

    private void loadFrameData() {
        FrameConfig[] frames = connection.frames;
        if (frames != null && slotIndex >= 0 && slotIndex < frames.length) {
            frameConfig = frames[slotIndex];
        }

        android.util.Log.d("SlotEdit", "loadFrameData slotIndex=" + slotIndex
                + " frameConfig=" + (frameConfig != null)
                + " frameType=0x" + (frameConfig != null ? Integer.toHexString(frameConfig.getFrameType()) : "null")
                + " enabled=" + (frameConfig != null && frameConfig.isEnabled())
                + " rawData=" + (frameConfig != null && frameConfig.rawData != null ? frameConfig.rawData.length + "B" : "null"));

        // 确定帧类型默认选中项
        int selectPos = 0; // 默认 Device Info
        if (frameConfig != null && frameConfig.isEnabled()) {
            for (int i = 0; i < TYPE_CODES.length; i++) {
                if (TYPE_CODES[i] == frameConfig.getFrameType()) { selectPos = i; break; }
            }
        }
        currentTypeCode = TYPE_CODES[selectPos];
        spFrameType.setSelection(selectPos);

        // 解析帧数据
        ParsedValues pv = (frameConfig != null && frameConfig.rawData != null)
                ? parseRawData(frameConfig.rawData, currentTypeCode) : new ParsedValues();

        android.util.Log.d("SlotEdit", "loadFrameData parsed: name=" + pv.name
                + " uuid=" + pv.uuid
                + " major=" + pv.opt("major", null)
                + " advInterval=" + pv.opt("advInterval", null)
                + " txPower=" + pv.opt("txPower", null));

        // 公共参数
        int intervalMs = pv.optInt("advInterval", 200);
        sbInterval.setProgress(SlotConfig.advIntervalMsToRaw(intervalMs) - 1);
        int txPower = pv.optInt("txPower", 0);
        sbPower.setProgress(SlotConfig.clampTxPower(txPower));

        // Trigger
        boolean trigOn = frameConfig != null && frameConfig.triggerOn;
        swTrigger.setChecked(trigOn);
        triggerOptions.setVisibility(trigOn ? View.VISIBLE : View.GONE);
        int trigType = frameConfig != null ? frameConfig.triggerType : 1;
        spTriggerType.setSelection(rawTypeToSpinnerPos(trigType));
        // 触发时长范围 0~100 秒，默认 10 秒，对齐原 APP（triggersb.setMax(100) / setProgress(10)）
        int trigTime = frameConfig != null ? frameConfig.triggerTime : 10;
        sbTriggerTime.setProgress(Math.max(0, Math.min(100, trigTime)));

        // 帧专属字段
        rebuildFieldViews(currentTypeCode, pv);
    }

    // ========== 重建帧专属字段 ==========

    private void rebuildFieldViews(int typeCode) {
        rebuildFieldViews(typeCode, null);
    }

    private void rebuildFieldViews(int typeCode, ParsedValues pv) {
        fieldContainer.removeAllViews();
        editMap.clear();
        if (pv == null) pv = new ParsedValues();

        switch (typeCode) {
            case FrameConfig.FRAME_TYPE_INFO:
                addEdit("name", "Device Name", pv.name != null ? pv.name : "My-Beacon");
                break;
            case FrameConfig.FRAME_TYPE_IBEACON:
                addEdit("uuid", "Proximity UUID (32 hex)",
                        pv.uuid != null ? pv.uuid : "E2C56DB5DFFB48D2B060D0F5A71096E0");
                addEdit("major", "Major (0-65535)", pv.opt("major", "1"));
                addEdit("minor", "Minor (0-65535)", pv.opt("minor", "0"));
                addEdit("distanceTxPower", "RSSI @1m (dBm)", pv.opt("distanceTxPower", "-59"));
                break;
            case FrameConfig.FRAME_TYPE_UID:
                addEdit("namespaceId", "Namespace ID (20 hex)",
                        pv.namespaceId != null ? pv.namespaceId : "0102030405060708090A");
                addEdit("instanceId", "Instance ID (12 hex)",
                        pv.instanceId != null ? pv.instanceId : "112233445566");
                addEdit("distanceTxPower", "RSSI @0m (dBm)", pv.opt("distanceTxPower", "-59"));
                break;
            case FrameConfig.FRAME_TYPE_URL:
                addEdit("schemePrefix", "Scheme (0=http 1=https 2=...)", pv.opt("schemePrefix", "0"));
                addEdit("urlContent", "URL Content (max 17 chars)",
                        pv.urlContent != null ? pv.urlContent : "example.com");
                addEdit("distanceTxPower", "RSSI @0m (dBm)", pv.opt("distanceTxPower", "-59"));
                break;
            case FrameConfig.FRAME_TYPE_TLM:
            case FrameConfig.FRAME_TYPE_ACC:
                // TLM / ACC 只有公共参数
                break;
            case FrameConfig.FRAME_TYPE_USER:
                addEdit("userData", "User Data (hex)",
                        pv.userDataHex != null ? pv.userDataHex : "01020304");
                break;
        }
    }

    private void addEdit(String key, String label, String value) {
        android.util.Log.d("SlotEdit", "addEdit key=" + key + " label=" + label + " value=" + value);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(0xFF333333);
        tv.setPadding(0, 8, 0, 2);
        fieldContainer.addView(tv);

        EditText et = new EditText(this);
        et.setText(value);
        et.setTextSize(14);
        et.setPadding(24, 16, 24, 16);
        et.setBackgroundColor(0xFFFFFFFF);
        et.setSingleLine(true);
        if (key.equals("major") || key.equals("minor") || key.equals("distanceTxPower")
                || key.equals("schemePrefix")) {
            et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }
        fieldContainer.addView(et);
        editMap.put(key, et);
    }

    // ========== 保存 ==========

    private void onSave() {
        // 校验 + 构建 SlotConfig
        int intervalMs = (sbInterval.getProgress() + 1) * 100;
        int intervalRaw = SlotConfig.advIntervalMsToRaw(intervalMs);
        int txPower = SlotConfig.clampTxPower(sbPower.getProgress());

        SlotConfig config;
        switch (currentTypeCode) {
            case FrameConfig.FRAME_TYPE_INFO: {
                InfoSlotConfig c = new InfoSlotConfig();
                c.deviceName = textOf("name");
                c.advInterval = intervalRaw;
                c.txPower = txPower;
                config = c;
                break;
            }
            case FrameConfig.FRAME_TYPE_IBEACON: {
                IBeaconSlotConfig c = new IBeaconSlotConfig();
                c.proximityUuid = textOf("uuid").replace("-", "");
                c.major = intOf("major", 1);
                c.minor = intOf("minor", 0);
                c.distanceTxPower = intOf("distanceTxPower", -59);
                c.advInterval = intervalRaw;
                c.txPower = txPower;
                config = c;
                break;
            }
            case FrameConfig.FRAME_TYPE_UID: {
                UidSlotConfig c = new UidSlotConfig();
                c.namespaceId = textOf("namespaceId").replace("-", "");
                c.instanceId = textOf("instanceId").replace("-", "");
                c.distanceTxPower = intOf("distanceTxPower", -59);
                c.advInterval = intervalRaw;
                c.txPower = txPower;
                config = c;
                break;
            }
            case FrameConfig.FRAME_TYPE_URL: {
                UrlSlotConfig c = new UrlSlotConfig();
                c.schemePrefix = Math.max(0, Math.min(3, intOf("schemePrefix", 0)));
                c.urlBytes = textOf("urlContent").getBytes();
                c.distanceTxPower = intOf("distanceTxPower", -59);
                c.advInterval = intervalRaw;
                c.txPower = txPower;
                config = c;
                break;
            }
            case FrameConfig.FRAME_TYPE_TLM: {
                TlmSlotConfig c = new TlmSlotConfig();
                c.advInterval = intervalRaw;
                c.txPower = txPower;
                config = c;
                break;
            }
            case FrameConfig.FRAME_TYPE_ACC: {
                AccSlotConfig c = new AccSlotConfig();
                c.advInterval = intervalRaw;
                c.txPower = txPower;
                config = c;
                break;
            }
            case FrameConfig.FRAME_TYPE_USER: {
                UserSlotConfig c = new UserSlotConfig();
                String hex = textOf("userData");
                c.userData = hex != null ? hexToBytes(hex) : new byte[0];
                c.advInterval = intervalRaw;
                c.txPower = txPower;
                config = c;
                break;
            }
            default:
                Toast.makeText(this, "Unknown frame type", Toast.LENGTH_SHORT).show();
                return;
        }

        // 校验
        String err = config.validate();
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            return;
        }

        // Trigger 参数
        boolean trigOn = swTrigger.isChecked();
        int trigType = spinnerPosToRawType(spTriggerType.getSelectedItemPosition());
        int trigTime = sbTriggerTime.getProgress();

        btnSave.setEnabled(false);
        btnDisable.setEnabled(false);
        Toast.makeText(this, "Writing...", Toast.LENGTH_SHORT).show();

        connection.writeSlot(slotIndex, config, trigOn, trigType, trigTime, new DxWriteCallback() {
            @Override public void onProgress(com.dxlq.ibeacon.sdk.slot.WriteStage stage) {
                runOnUiThread(() -> {
                    if (stage == com.dxlq.ibeacon.sdk.slot.WriteStage.CONFIG_SAVED) {
                        Toast.makeText(SlotEditActivity.this, "Saved, verifying...", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(SlotEditActivity.this, "Write OK!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
            @Override public void onError(int code, String message) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnDisable.setEnabled(true);
                    String tip;
                    switch (code) {
                        case DxConnection.ERR_FRAME_MUST_ONE: tip = "At least one slot must be enabled"; break;
                        case DxConnection.ERR_FRAME_CONFLICT: tip = "Frame type conflict with another slot"; break;
                        case DxConnection.ERR_VERIFY_FAILED: tip = "Verify failed: " + message; break;
                        case DxConnection.ERR_INVALID_PARAM: tip = "Invalid param: " + message; break;
                        default: tip = "Write failed(code=" + code + "): " + message;
                    }
                    Toast.makeText(SlotEditActivity.this, tip, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ========== 禁用槽位 ==========

    private void onDisable() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Disable Slot " + (slotIndex + 1))
                .setMessage("Set this slot to NoData?")
                .setPositiveButton("Disable", (d, w) -> {
                    btnSave.setEnabled(false);
                    btnDisable.setEnabled(false);
                    Toast.makeText(this, "Disabling...", Toast.LENGTH_SHORT).show();
                    connection.writeDisableSlot(slotIndex, new DxWriteCallback() {
                        @Override public void onProgress(com.dxlq.ibeacon.sdk.slot.WriteStage stage) {
                            runOnUiThread(() -> {
                                if (stage == com.dxlq.ibeacon.sdk.slot.WriteStage.CONFIG_SAVED) {
                                    Toast.makeText(SlotEditActivity.this, "Saved, verifying...", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        @Override public void onSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(SlotEditActivity.this, "Slot disabled", Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                                finish();
                            });
                        }
                        @Override public void onError(int code, String message) {
                            runOnUiThread(() -> {
                                btnSave.setEnabled(true);
                                btnDisable.setEnabled(true);
                                String tip;
                                switch (code) {
                                    case DxConnection.ERR_FRAME_MUST_ONE: tip = "At least one slot must be enabled"; break;
                                    case DxConnection.ERR_VERIFY_FAILED: tip = "Verify failed: " + message; break;
                                    default: tip = "Disable failed(code=" + code + "): " + message;
                                }
                                Toast.makeText(SlotEditActivity.this, tip, Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ========== 工具方法 ==========

    private String textOf(String key) {
        EditText et = editMap.get(key);
        return et != null ? et.getText().toString().trim() : "";
    }
    private int intOf(String key, int def) {
        try { return Integer.parseInt(textOf(key)); } catch (Exception e) { return def; }
    }
    private byte[] hexToBytes(String hex) {
        hex = hex.replace(" ", "").trim();
        if (hex.length() % 2 != 0) hex = "0" + hex;
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /** 从 rawData 解析帧字段值（对齐原 APP initTrigger + FramesetActivity 布局） */
    private ParsedValues parseRawData(byte[] raw, int typeCode) {
        ParsedValues pv = new ParsedValues();
        if (raw == null || raw.length < 6) {
            android.util.Log.w("SlotEdit", "parseRawData raw too short: len=" + (raw != null ? raw.length : 0));
            return pv;
        }

        android.util.Log.d("SlotEdit", "parseRawData typeCode=0x" + Integer.toHexString(typeCode)
                + " len=" + raw.length + " hex=" + bytesToHex(raw, 0, Math.min(raw.length, 40)));

        int len = raw.length;
        int tailIdx = len - 6;
        if (tailIdx < 0) tailIdx = 0;

        boolean needsDistance = (typeCode == FrameConfig.FRAME_TYPE_IBEACON
                || typeCode == FrameConfig.FRAME_TYPE_UID
                || typeCode == FrameConfig.FRAME_TYPE_URL);
        if (needsDistance) {
            // iBeacon/UID/URL 的 distance(RSSI) 字段位于 len-6
            int distanceRaw = raw[tailIdx] & 0xFF;
            pv.put("distanceTxPower", String.valueOf((byte) distanceRaw));
        }
        // 非 distance 类型跳过 len-6 的保留字节（对齐原 APP initTrigger 的 else 分支 index++）
        tailIdx++;

        int intervalRaw = raw[tailIdx] & 0x0F;
        pv.put("advInterval", String.valueOf(intervalRaw * 100));
        tailIdx++;
        pv.put("txPower", String.valueOf(raw[tailIdx] & 0x07));

        switch (typeCode) {
            case FrameConfig.FRAME_TYPE_INFO:
                if (len >= 29) {
                    byte[] nameBytes = new byte[28];
                    System.arraycopy(raw, 1, nameBytes, 0, 28);
                    pv.name = new String(nameBytes).trim();
                }
                break;
            case FrameConfig.FRAME_TYPE_IBEACON:
                if (len >= 21) {
                    pv.uuid = bytesToHex(raw, 1, 16);
                    int major = (raw[17] & 0xFF) << 8 | (raw[18] & 0xFF);
                    pv.put("major", String.valueOf(major));
                    int minor = (raw[19] & 0xFF) << 8 | (raw[20] & 0xFF);
                    pv.put("minor", String.valueOf(minor));
                }
                break;
            case FrameConfig.FRAME_TYPE_UID:
                if (len >= 17) {
                    pv.namespaceId = bytesToHex(raw, 1, 10);
                    pv.instanceId = bytesToHex(raw, 11, 6);
                }
                break;
            case FrameConfig.FRAME_TYPE_URL:
                if (len >= 3) {
                    pv.put("schemePrefix", String.valueOf(raw[1] & 0x03));
                    int bodyLen = Math.min(len - 2, 17);
                    byte[] urlBody = new byte[bodyLen];
                    System.arraycopy(raw, 2, urlBody, 0, bodyLen);
                    int end = bodyLen - 1;
                    while (end >= 0 && urlBody[end] == 0) end--;
                    if (end < 0) end = -1;
                    pv.urlContent = new String(urlBody, 0, end + 1).trim();
                }
                break;
            case FrameConfig.FRAME_TYPE_USER:
                if (len >= 2) {
                    int dataLen = Math.min(len - 1, 21);
                    byte[] ud = new byte[dataLen];
                    System.arraycopy(raw, 1, ud, 0, dataLen);
                    int end = dataLen - 1;
                    while (end >= 0 && ud[end] == 0) end--;
                    if (end < 0) end = -1;
                    pv.userDataHex = bytesToHex(ud, 0, end + 1);
                }
                break;
        }
        return pv;
    }

    private static String bytesToHex(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", data[offset + i] & 0xFF));
        }
        return sb.toString();
    }

    private static int spinnerPosToRawType(int pos) { return pos + 1; }
    private static int rawTypeToSpinnerPos(int rawType) {
        if (rawType <= 1) return 0;
        if (rawType == 2) return 1;
        return 2;
    }

    /** 解析结果容器 */
    private static class ParsedValues {
        Map<String, String> values = new HashMap<>();
        String name, uuid, namespaceId, instanceId, urlContent, userDataHex;
        void put(String k, String v) { values.put(k, v); }
        String opt(String k, String def) {
            String v = values.get(k);
            return v != null ? v : def;
        }
        int optInt(String k, int def) {
            String v = values.get(k);
            if (v == null) return def;
            try { return Integer.parseInt(v); } catch (Exception e) { return def; }
        }
    }
}
