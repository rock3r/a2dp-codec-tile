package dev.sebastiano.a2dpcodec;

import android.Manifest;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/** One short-lived connection to the currently connected A2DP output. */
final class CodecAccess implements BluetoothProfile.ServiceListener {
    static final String BLUETOOTH_OFF = "Bluetooth is off";
    static final String NO_A2DP_DEVICE = "No A2DP device connected";
    static final String A2DP_DISCONNECTED = "A2DP disconnected";
    static final String MULTIPLE_A2DP_DEVICES = "Select an active Bluetooth audio output";

    interface Callback {
        void onReady(CodecAccess access);
        void onError(String message, Throwable error);
    }

    private final Context context;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothA2dp a2dp;
    private BluetoothDevice device;
    private boolean closed;

    CodecAccess(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    void open() {
        try {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                error("Grant Bluetooth permission", null);
                return;
            }
            BluetoothManager manager = context.getSystemService(BluetoothManager.class);
            adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                error(BLUETOOTH_OFF, null);
            } else if (!adapter.getProfileProxy(context, this, BluetoothProfile.A2DP)) {
                error("A2DP unavailable", null);
            }
        } catch (Throwable failure) {
            error("Bluetooth access failed", failure);
        }
    }

    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        main.post(() -> {
            if (closed) {
                if (adapter != null) adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy);
                return;
            }
            a2dp = (BluetoothA2dp) proxy;
            try {
                List<BluetoothDevice> connected = a2dp.getConnectedDevices();
                if (connected.isEmpty()) {
                    error(NO_A2DP_DEVICE, null);
                } else {
                    device = activeDevice(connected);
                    if (device == null) {
                        error(MULTIPLE_A2DP_DEVICES, null);
                    } else {
                        callback.onReady(this);
                    }
                }
            } catch (Throwable failure) {
                error("Could not read headphones", failure);
            }
        });
    }

    @Override
    public void onServiceDisconnected(int profile) {
        main.post(() -> {
            if (!closed) error(A2DP_DISCONNECTED, null);
        });
    }

    BluetoothCodecStatus status() throws Exception {
        Method method = BluetoothA2dp.class.getMethod("getCodecStatus", BluetoothDevice.class);
        return (BluetoothCodecStatus) invoke(method, device);
    }

    String deviceName() {
        if (device == null) return null;
        String name = device.getName();
        return name == null || name.isBlank() ? "Bluetooth headphones" : name;
    }

    String deviceAddress() {
        return device == null ? null : device.getAddress();
    }

    private BluetoothDevice activeDevice(List<BluetoothDevice> connected) {
        if (connected.size() == 1) return connected.get(0);
        try {
            Method method = BluetoothA2dp.class.getMethod("getActiveDevice");
            BluetoothDevice active = (BluetoothDevice) invoke(method);
            if (active != null && connected.contains(active)) return active;
        } catch (Exception | LinkageError ignored) {
            // A vendor build may hide this method. Never guess between outputs.
        }
        return null;
    }

    void request(BluetoothCodecConfig selected) throws Exception {
        BluetoothCodecConfig.Builder builder = new BluetoothCodecConfig.Builder()
                .setCodecPriority(BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST);
        if (selected.getExtendedCodecType() != null) {
            builder.setExtendedCodecType(selected.getExtendedCodecType());
        } else {
            builder.setCodecType(selected.getCodecType());
        }
        Method method = BluetoothA2dp.class.getMethod("setCodecConfigPreference",
                BluetoothDevice.class, BluetoothCodecConfig.class);
        invoke(method, device, builder.build());
    }

    void close() {
        if (closed) return;
        closed = true;
        if (adapter != null && a2dp != null) {
            adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp);
        }
        a2dp = null;
        device = null;
    }

    private Object invoke(Method method, Object... args) throws Exception {
        try {
            return method.invoke(a2dp, args);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw failure;
        }
    }

    private void error(String message, Throwable failure) {
        if (closed) return;
        callback.onError(message, failure);
        close();
    }
}
