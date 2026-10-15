package dev.sebastiano.a2dpcodec;

import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.util.List;

/** Tap to renegotiate the current codec; long press to choose another codec. */
public final class CodecResetTile extends TileService {
    private static final String TAG = "A2dpCodecTile";
    private static final long POLL_MS = 250;
    private static final long TRANSITION_TIMEOUT_MS = 5000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private CodecAccess access;
    private BluetoothCodecConfig current;
    private boolean running;
    private boolean sawIntermediate;
    private boolean listening;
    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (listening && !running) refresh();
        }
    };

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.i(TAG, "Listening; refreshing codec icon");
        if (!listening) {
            IntentFilter filter = new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(connectionReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(connectionReceiver, filter);
            }
            listening = true;
        }
        if (running) return;
        refresh();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        stopReceiving();
        if (!running) closeAccess();
    }

    @Override
    public void onClick() {
        super.onClick();
        Tile tile = getQsTile();
        if (running || tile == null || tile.getState() != Tile.STATE_ACTIVE) return;
        running = true;
        show("Resetting " + CodecUi.name(current) + "…", Tile.STATE_UNAVAILABLE);
        connect(true);
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        stopReceiving();
        closeAccess();
        super.onDestroy();
    }

    private void refresh() {
        show("Checking codec…", Tile.STATE_UNAVAILABLE);
        connect(false);
    }

    private void stopReceiving() {
        if (!listening) return;
        listening = false;
        unregisterReceiver(connectionReceiver);
    }

    private void connect(boolean reset) {
        closeAccess();
        access = new CodecAccess(this, new CodecAccess.Callback() {
            @Override
            public void onReady(CodecAccess ready) {
                try {
                    BluetoothCodecStatus status = ready.status();
                    if (status == null || status.getCodecConfig() == null) {
                        finish("Codec status unavailable", false);
                    } else if (reset) {
                        reset(status);
                    } else {
                        current = status.getCodecConfig();
                        Log.i(TAG, "Current codec " + CodecUi.name(current));
                        show(CodecUi.name(current), Tile.STATE_ACTIVE);
                        closeAccess();
                    }
                } catch (Throwable failure) {
                    fail("Codec check failed", failure);
                }
            }

            @Override
            public void onError(String message, Throwable failure) {
                if (failure != null) Log.e(TAG, message, failure);
                finish(message, false);
            }
        });
        access.open();
    }

    private void reset(BluetoothCodecStatus status) throws Exception {
        current = status.getCodecConfig();
        List<BluetoothCodecConfig> choices = CodecUi.choices(status);
        BluetoothCodecConfig target = null;
        for (BluetoothCodecConfig choice : choices) {
            if (CodecUi.same(choice, current)) {
                target = choice;
                break;
            }
        }
        if (target == null) {
            finish("Current codec unavailable", false);
            return;
        }

        BluetoothCodecConfig intermediate = CodecUi.find(choices,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC);
        if (CodecUi.same(intermediate, target)) {
            intermediate = CodecUi.find(choices,
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD);
        }
        if (intermediate == null || CodecUi.same(intermediate, target)) {
            intermediate = CodecUi.find(choices,
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC);
        }
        if (intermediate == null || CodecUi.same(intermediate, target)) {
            for (BluetoothCodecConfig choice : choices) {
                if (!CodecUi.same(choice, target)) {
                    intermediate = choice;
                    break;
                }
            }
        }
        if (intermediate == null || CodecUi.same(intermediate, target)) {
            finish("No alternate codec available", false);
            return;
        }

        final BluetoothCodecConfig restore = target;
        final BluetoothCodecConfig temporary = intermediate;
        sawIntermediate = false;
        access.request(temporary);
        Log.i(TAG, "Requested temporary " + CodecUi.name(temporary));
        waitFor(temporary, System.currentTimeMillis() + TRANSITION_TIMEOUT_MS,
                () -> {
                    sawIntermediate = true;
                    restore(restore);
                }, () -> {
                    Log.w(TAG, "Temporary codec not observed; restoring " + CodecUi.name(restore));
                    restore(restore);
                });
    }

    private void restore(BluetoothCodecConfig target) {
        try {
            access.request(target);
            Log.i(TAG, "Requested restore " + CodecUi.name(target));
            waitFor(target, System.currentTimeMillis() + TRANSITION_TIMEOUT_MS,
                    () -> finish(sawIntermediate ? "Reset · " + CodecUi.name(target)
                            : "Restored · " + CodecUi.name(target), sawIntermediate),
                    () -> finish("Could not confirm " + CodecUi.name(target), false));
        } catch (Throwable failure) {
            fail("Restore failed", failure);
        }
    }

    private void waitFor(BluetoothCodecConfig target, long deadline,
            Runnable success, Runnable timeout) {
        main.postDelayed(() -> {
            if (!running || access == null) return;
            try {
                BluetoothCodecStatus status = access.status();
                BluetoothCodecConfig observed = status == null ? null : status.getCodecConfig();
                if (CodecUi.same(observed, target)) {
                    current = observed;
                    success.run();
                } else if (System.currentTimeMillis() < deadline) {
                    waitFor(target, deadline, success, timeout);
                } else {
                    if (observed != null) current = observed;
                    timeout.run();
                }
            } catch (Throwable failure) {
                fail("Codec check failed", failure);
            }
        }, POLL_MS);
    }

    private void fail(String message, Throwable failure) {
        Log.e(TAG, message, failure);
        finish(failure instanceof SecurityException ? "Needs companion association" : message,
                false);
    }

    private void finish(String message, boolean success) {
        running = false;
        boolean disconnected = CodecAccess.NO_A2DP_DEVICE.equals(message)
                || CodecAccess.A2DP_DISCONNECTED.equals(message)
                || CodecAccess.BLUETOOTH_OFF.equals(message);
        if (disconnected || "Needs companion association".equals(message)
                || "Grant Bluetooth permission".equals(message)) {
            current = null;
        }
        show(message, disconnected ? Tile.STATE_UNAVAILABLE
                : success ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        closeAccess();
        sawIntermediate = false;
        Log.i(TAG, message);
    }

    private void closeAccess() {
        if (access != null) access.close();
        access = null;
    }

    private void show(String message, int state) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setLabel("A2DP codec");
        tile.setSubtitle(message);
        tile.setContentDescription("A2DP codec: " + CodecUi.name(current)
                + ". " + message + ". Tap to reset; long press to choose codec.");
        tile.setStateDescription(message);
        tile.setIcon(CodecUi.icon(this, current));
        tile.setState(state);
        tile.updateTile();
    }
}
