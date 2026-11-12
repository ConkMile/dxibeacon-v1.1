package com.dxlq.ibeacon.sdk.test;

import com.dxlq.ibeacon.demoapp.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.dxlq.ibeacon.sdk.DxBeaconDevice;
import com.dxlq.ibeacon.sdk.DxBeaconManager;
import com.dxlq.ibeacon.sdk.DxConnection;
import com.dxlq.ibeacon.sdk.data.DeviceInfo;
import com.dxlq.ibeacon.sdk.data.FrameConfig;
import com.dxlq.ibeacon.sdk.listener.ConnectionListener;
import com.dxlq.ibeacon.sdk.slot.*;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

/**
 * Frame Settings — 复刻原 APP settingActivity
 * ViewPager + 底部 3 Tab (Frame / Setting / Device)
 */
public class FrameActivity extends AppCompatActivity {

    private DxBeaconDevice device;
    private DxConnection connection;

    // 顶部栏
    private ImageButton btnBack;
    private TextView tvTitle;

    // ViewPager
    private ViewPager viewPager;
    private List<View> pageViews;
    private View viewFrame, viewSetting, viewDevice;

    // Frame Tab
    private ListView lvSlots;
    private SlotAdapter slotAdapter;

    // Setting Tab
    private ListView lvSetting;
    private InfoAdapter settingAdapter;

    // Device Tab
    private ListView lvDevice;
    private InfoAdapter deviceAdapter;

    // 底部 Tab UI
    private LinearLayout tabFrame, tabSetting, tabDevice;
    private ImageView icon0, icon1, icon2;
    private TextView mtv0, mtv1, mtv2;

    // 连接状态监听
    private final ConnectionListener stateListener = new ConnectionListener() {
        @Override public void onStateChanged(DxBeaconDevice d, int state) {
            if (state == DxConnection.STATE_DISCONNECTED) {
                runOnUiThread(() -> {
                    Toast.makeText(FrameActivity.this, "Device disconnected", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }
        @Override public void onFrameListRead(DxBeaconDevice d, FrameConfig[] f) {}
        @Override public void onDeviceInfoRead(DxBeaconDevice d, DeviceInfo info) {}
        @Override public void onPasswordRequired(DxBeaconDevice d, com.dxlq.ibeacon.sdk.listener.OnPasswordCallback cb) {}
        @Override public void onCommandResponse(DxBeaconDevice d, int cmd, byte[] payload) {}
        @Override public void onError(DxBeaconDevice d, int code, String msg) {}
    };

    private static final int REQ_SLOT_EDIT = 1001;

    // Setting Tab 项（对齐原项目 listd，Trigger 已移到 SlotEditActivity）
    private static final String[] SETTING_NAMES = {
            "Factory Reset", "Equipment Switch", "Power Off", "Change Password"
    };

    // Device Tab 项（对齐原项目 lists）
    private static final String[] DEVICE_NAMES = {
            "Battery", "MAC", "Manufacturer", "Chip",
            "Production", "Firmware", "Hardware"
    };

    // Tab 文字颜色
    private static final int TAB_SEL_COLOR = 0xFF1976D2;
    private static final int TAB_UNSEL_COLOR = 0xFF999999;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_frame);

        String mac = getIntent().getStringExtra("mac");
        if (mac == null) { finish(); return; }

        DxBeaconManager manager = DxBeaconManager.getInstance(this);
        device = null;
        for (DxBeaconDevice d : manager.getDevices()) {
            if (d.macAddress.equals(mac)) { device = d; break; }
        }
        if (device == null || device.connection == null) {
            Toast.makeText(this, "Device not connected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        connection = device.connection;

        // 顶部栏
        btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        btnBack.setOnClickListener(v -> finish());

        // 底部 Tab
        tabFrame = findViewById(R.id.tabFrame);
        tabSetting = findViewById(R.id.tabSetting);
        tabDevice = findViewById(R.id.tabDevice);
        icon0 = findViewById(R.id.icon0);
        icon1 = findViewById(R.id.icon1);
        icon2 = findViewById(R.id.icon2);
        mtv0 = findViewById(R.id.mtv0);
        mtv1 = findViewById(R.id.mtv1);
        mtv2 = findViewById(R.id.mtv2);

        // ViewPager + 3 个页面
        LayoutInflater lf = getLayoutInflater();
        viewFrame = lf.inflate(R.layout.tab_frame, null);
        viewSetting = lf.inflate(R.layout.tab_setting, null);
        viewDevice = lf.inflate(R.layout.tab_device, null);
        pageViews = new ArrayList<>();
        pageViews.add(viewFrame);
        pageViews.add(viewSetting);
        pageViews.add(viewDevice);

        viewPager = findViewById(R.id.pager);
        viewPager.setAdapter(new MyPagerAdapter(pageViews));
        viewPager.setCurrentItem(0);
        viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override public void onPageScrolled(int i, float v, int i1) {}
            @Override public void onPageSelected(int i) { setTabDisplay(i); }
            @Override public void onPageScrollStateChanged(int i) {}
        });

        // Frame Tab — 槽位列表
        lvSlots = viewFrame.findViewById(R.id.lvSlots);
        setupSlotList();

        // Setting Tab — 按钮列表
        lvSetting = viewSetting.findViewById(R.id.lvSetting);
        setupSettingList();

        // Device Tab — 设备信息
        lvDevice = viewDevice.findViewById(R.id.lvDevice);
        setupDeviceList();

        // 底部 Tab 点击
        tabFrame.setOnClickListener(v -> viewPager.setCurrentItem(0));
        tabSetting.setOnClickListener(v -> viewPager.setCurrentItem(1));
        tabDevice.setOnClickListener(v -> viewPager.setCurrentItem(2));

        setTabDisplay(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (connection != null) {
            connection.addConnectionListener(stateListener);
            // 如果进来时已经断了（比如设备刚关机），直接 finish
            if (!connection.isConnected()) {
                Toast.makeText(this, "Device disconnected", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (connection != null) {
            connection.removeConnectionListener(stateListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connection != null) {
            connection.removeConnectionListener(stateListener);
            connection.disconnect();
        }
    }

    // ========== ViewPager Adapter ==========

    private static class MyPagerAdapter extends PagerAdapter {
        private final List<View> views;
        MyPagerAdapter(List<View> v) { this.views = v; }
        @Override public int getCount() { return views.size(); }
        @Override public boolean isViewFromObject(View v, Object o) { return v == o; }
        @Override public Object instantiateItem(ViewGroup container, int position) {
            container.addView(views.get(position));
            return views.get(position);
        }
        @Override public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView(views.get(position));
        }
    }

    /** 底部 Tab 高亮切换（对齐原项目 setdisplay） */
    private void setTabDisplay(int page) {
        tvTitle.setText(page == 0 ? "Frame" : page == 1 ? "Setting" : "Device");
        icon0.setAlpha(page == 0 ? 1f : 0.5f);
        icon1.setAlpha(page == 1 ? 1f : 0.5f);
        icon2.setAlpha(page == 2 ? 1f : 0.5f);
        mtv0.setTextColor(page == 0 ? TAB_SEL_COLOR : TAB_UNSEL_COLOR);
        mtv1.setTextColor(page == 1 ? TAB_SEL_COLOR : TAB_UNSEL_COLOR);
        mtv2.setTextColor(page == 2 ? TAB_SEL_COLOR : TAB_UNSEL_COLOR);
    }

    // ========== Frame Tab — 槽位列表 ==========

    private void setupSlotList() {
        slotAdapter = new SlotAdapter();
        lvSlots.setAdapter(slotAdapter);
        lvSlots.setOnItemClickListener((parent, view, position, id) -> onSlotClick(position));
    }

    private class SlotAdapter extends BaseAdapter {
        @Override public int getCount() { return 7; }
        @Override public Object getItem(int pos) { return pos; }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(FrameActivity.this)
                        .inflate(R.layout.item_slot, parent, false);
            }
            TextView tvNum = convertView.findViewById(R.id.tvSlotNum);
            TextView tvTitle = convertView.findViewById(R.id.tvSlotTitle);
            TextView tvType = convertView.findViewById(R.id.tvSlotType);

            // 槽位标题 "Slot N"（任何槽位都可以是任意帧类型，不按位置固定类型名）
            tvNum.setText("");
            tvTitle.setText("Slot " + (position + 1));

            // 帧类型来自实际读取到的数据，未启用则显示 NoData
            FrameConfig fc = getFrameConfig(position);
            if (fc != null && fc.isEnabled()) {
                tvType.setText(fc.getSlotName());
                tvType.setTextColor(0xFF1976D2);
            } else {
                tvType.setText("NoData");
                tvType.setTextColor(0xFF999999);
            }
            return convertView;
        }
    }

    private FrameConfig getFrameConfig(int slot) {
        FrameConfig[] frames = connection.frames;
        if (frames != null && slot >= 0 && slot < frames.length) return frames[slot];
        return null;
    }

    // ========== Setting Tab — 按钮列表 ==========

    private void setupSettingList() {
        String[] vals = new String[SETTING_NAMES.length];
        vals[0] = "";                  // Factory Reset
        vals[1] = equipSwitchSummary();
        vals[2] = "";                  // Power Off
        vals[3] = "";                  // Change Password

        settingAdapter = new InfoAdapter(SETTING_NAMES, vals, true);
        lvSetting.setAdapter(settingAdapter);
        lvSetting.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0: confirmFactoryReset(); break;
                case 1: showEquipmentSwitchDialog(); break;
                case 2: confirmPowerOff(); break;
                case 3: showChangePassword(); break;
            }
        });
    }

    private String equipSwitchSummary() {
        byte[] sw = connection.equipmentSwitch;
        if (sw == null || sw.length < 3) return "?";
        return (sw[0] != 0 ? "ON" : "off") + " / "
                + (sw[1] != 0 ? "ON" : "off") + " / "
                + (sw[2] != 0 ? "ON" : "off");
    }

    // ========== Device Tab — 设备信息 ==========

    private void setupDeviceList() {
        String[] vals = new String[DEVICE_NAMES.length];
        DeviceInfo info = connection.deviceInfo;
        vals[0] = info != null ? info.batteryMv + " mV" : "?";
        vals[1] = info != null ? nullSafe(info.mac) : device.macAddress;
        vals[2] = info != null ? nullSafe(info.manufacturer) : "?";
        vals[3] = info != null ? nullSafe(info.chip) : "?";
        vals[4] = info != null ? nullSafe(info.product) : "?";
        vals[5] = info != null ? nullSafe(info.firmwareVersion) : "?";
        vals[6] = info != null ? nullSafe(info.hardwareVersion) : "?";

        deviceAdapter = new InfoAdapter(DEVICE_NAMES, vals, false);
        lvDevice.setAdapter(deviceAdapter);
    }

    // ========== 通用列表适配器 ==========

    /** Setting / Device 页共用 */
    private class InfoAdapter extends BaseAdapter {
        private final String[] names;
        private final String[] vals;
        private final boolean showArrow;

        InfoAdapter(String[] n, String[] v, boolean arrow) {
            this.names = n; this.vals = v; this.showArrow = arrow;
        }

        @Override public int getCount() { return names.length; }
        @Override public Object getItem(int pos) { return pos; }
        @Override public long getItemId(int pos) { return pos; }

        /** 更新某一行的值并刷新列表（列表值数组只在初始化时生成，改数据后必须显式更新） */
        void setValue(int position, String value) {
            vals[position] = value;
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(FrameActivity.this)
                        .inflate(R.layout.item_info, parent, false);
            }
            TextView tvName = convertView.findViewById(R.id.tvItemName);
            TextView tvVal = convertView.findViewById(R.id.tvItemVal);
            ImageView ivArrow = convertView.findViewById(R.id.ivArrow);

            tvName.setText(names[position]);
            tvVal.setText(vals[position] != null ? vals[position] : "");
            ivArrow.setVisibility(showArrow ? View.VISIBLE : View.GONE);
            return convertView;
        }
    }

    private String nullSafe(String s) { return s != null && !s.isEmpty() ? s : "?"; }

    // ========== Equipment Switch 对话框 ==========

    private void showEquipmentSwitchDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_equip_switch, null);
        SwitchMaterial s0 = view.findViewById(R.id.swEquip0);
        SwitchMaterial s1 = view.findViewById(R.id.swEquip1);
        SwitchMaterial s2 = view.findViewById(R.id.swEquip2);

        byte[] sw = connection.equipmentSwitch;
        if (sw != null && sw.length >= 3) {
            s0.setChecked(sw[0] != 0);
            s1.setChecked(sw[1] != 0);
            s2.setChecked(sw[2] != 0);
        }

        new AlertDialog.Builder(this)
                .setTitle("Equipment Switch")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {
                    byte[] out = new byte[3];
                    out[0] = (byte) (s0.isChecked() ? 1 : 0);
                    out[1] = (byte) (s1.isChecked() ? 1 : 0);
                    out[2] = (byte) (s2.isChecked() ? 1 : 0);
                    // 对齐原 APP：先下发 0x22 设置开关，再由 SDK 在 200ms 后下发 0x60 保存
                    connection.writeEquipmentSwitch(out, new DxOperationCallback() {
                        @Override public void onSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(FrameActivity.this,
                                        "Equipment Switch saved!", Toast.LENGTH_SHORT).show();
                                // 刷新 Setting Tab 的开关摘要（值数组需重新生成，只 notify 不会变）
                                settingAdapter.setValue(1, equipSwitchSummary());
                            });
                        }
                        @Override public void onError(int code, String message) {
                            runOnUiThread(() -> Toast.makeText(FrameActivity.this,
                                    "Save failed: " + message, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ========== Change Password ==========

    private void showChangePassword() {
        EditText et = new EditText(this);
        et.setHint("New Password (6 characters)");
        // 密码可以是 ASCII 字符 + 数字，不限定纯数字；用可见密码类型便于核对输入
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setView(et)
                .setPositiveButton("Change", (d, w) -> {
                    String pwd = et.getText().toString().trim();
                    // 与原 APP 一致：密码固定 6 位（ASCII 字符，1 字符 = 1 字节）
                    if (pwd.length() != 6) {
                        Toast.makeText(this, "Password must be 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    connection.writeChangePassword(pwd, new DxOperationCallback() {
                        @Override public void onSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(FrameActivity.this,
                                        "Password changed. Please reconnect with the new password.",
                                        Toast.LENGTH_LONG).show();
                                disconnectAndBackToScan();
                            });
                        }
                        @Override public void onError(int code, String message) {
                            runOnUiThread(() -> Toast.makeText(FrameActivity.this,
                                    "Failed: " + message, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // 主动断开并退回扫描界面（改密码、恢复出厂、关机这类操作后，原连接没有继续保留的意义）
    private void disconnectAndBackToScan() {
        if (connection != null) {
            // 先移除状态监听，否则主动断开时还会再弹一次 "Device disconnected"
            connection.removeConnectionListener(stateListener);
            connection.disconnect();
        }
        finish();
    }

    // ========== 点击槽位 → 参数编辑 ==========

    private void onSlotClick(int slot) {
        // 点击槽位 → 进入 SlotEditActivity（对齐原 APP FramesetActivity 模式）
        Intent intent = new Intent(this, SlotEditActivity.class);
        intent.putExtra("mac", device.macAddress);
        intent.putExtra("slot", slot);
        startActivityForResult(intent, REQ_SLOT_EDIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SLOT_EDIT && resultCode == RESULT_OK) {
            // 槽位编辑完成，刷新帧列表
            slotAdapter.notifyDataSetChanged();
        }
    }

    // ========== Power Off / Factory Reset ==========

    private void confirmPowerOff() {
        new AlertDialog.Builder(this)
                .setTitle("Power Off")
                .setMessage("Are you sure? Device will shut down immediately.")
                .setPositiveButton("Power Off", (d, w) -> connection.writePowerOff(new DxOperationCallback() {
                    @Override public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(FrameActivity.this, "Device powering off...", Toast.LENGTH_LONG).show();
                            // 设备即将关机，主动断开并退回扫描界面
                            disconnectAndBackToScan();
                        });
                    }
                    @Override public void onError(int c, String m) {
                        runOnUiThread(() -> Toast.makeText(FrameActivity.this, "Failed: " + m, Toast.LENGTH_SHORT).show());
                    }
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmFactoryReset() {
        new AlertDialog.Builder(this)
                .setTitle("Factory Reset")
                .setMessage("⚠️  All settings will be lost!\n\nDevice will reset and you will return to the scan screen.")
                .setPositiveButton("Reset", (d, w) -> connection.writeRestoreFactory(new DxOperationCallback() {
                    @Override public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(FrameActivity.this, "Reset OK!", Toast.LENGTH_SHORT).show();
                            // 设备已恢复出厂，主动断开并退回扫描界面
                            disconnectAndBackToScan();
                        });
                    }
                    @Override public void onError(int c, String m) {
                        runOnUiThread(() -> Toast.makeText(FrameActivity.this, "Failed: " + m, Toast.LENGTH_SHORT).show());
                    }
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int px) {
        return (int) (px * getResources().getDisplayMetrics().density + 0.5f);
    }
}
