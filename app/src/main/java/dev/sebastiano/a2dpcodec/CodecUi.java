package dev.sebastiano.a2dpcodec;

import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.content.Context;
import android.graphics.drawable.Icon;

import java.util.ArrayList;
import java.util.List;

final class CodecUi {
    private CodecUi() {}

    static List<BluetoothCodecConfig> choices(BluetoothCodecStatus status) {
        List<BluetoothCodecConfig> result = new ArrayList<>();
        if (status == null) return result;
        for (BluetoothCodecConfig candidate : status.getCodecsSelectableCapabilities()) {
            boolean duplicate = false;
            for (BluetoothCodecConfig existing : result) {
                if (same(existing, candidate)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) result.add(candidate);
        }
        return result;
    }

    static boolean same(BluetoothCodecConfig first, BluetoothCodecConfig second) {
        if (first == null || second == null) return false;
        if (first.getExtendedCodecType() != null && second.getExtendedCodecType() != null) {
            return first.getExtendedCodecType().equals(second.getExtendedCodecType());
        }
        return first.getCodecType() == second.getCodecType();
    }

    static BluetoothCodecConfig find(List<BluetoothCodecConfig> choices, int codecType) {
        for (BluetoothCodecConfig choice : choices) {
            if (choice.getCodecType() == codecType) return choice;
        }
        return null;
    }

    static String name(BluetoothCodecConfig codec) {
        if (codec == null) return "Unknown";
        switch (codec.getCodecType()) {
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC: return "SBC";
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC: return "AAC";
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX: return "aptX";
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD: return "aptX HD";
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC: return "LDAC";
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_LC3: return "LC3";
            case BluetoothCodecConfig.SOURCE_CODEC_TYPE_OPUS: return "Opus";
            default:
                return codec.getExtendedCodecType() == null
                        ? "Codec " + codec.getCodecType()
                        : codec.getExtendedCodecType().getCodecName();
        }
    }

    static Icon icon(Context context, BluetoothCodecConfig codec) {
        int drawable;
        switch (name(codec)) {
            case "aptX HD": drawable = R.drawable.ic_codec_hd; break;
            case "aptX": drawable = R.drawable.ic_codec_aptx; break;
            case "AAC": drawable = R.drawable.ic_codec_aac; break;
            case "SBC": drawable = R.drawable.ic_codec_sbc; break;
            case "LDAC": drawable = R.drawable.ic_codec_ldac; break;
            case "LC3": drawable = R.drawable.ic_codec_lc3; break;
            case "Opus": drawable = R.drawable.ic_codec_opus; break;
            case "LHDCv5": drawable = R.drawable.ic_codec_lhdc; break;
            default: drawable = R.drawable.ic_codec_unknown;
        }
        return Icon.createWithResource(context, drawable);
    }
}
