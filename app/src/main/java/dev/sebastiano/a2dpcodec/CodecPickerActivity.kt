package dev.sebastiano.a2dpcodec

import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat

/** The Quick Settings long-press destination; only connected-device codecs appear. */
class CodecPickerActivity : ComponentActivity() {
    private val main = Handler(Looper.getMainLooper())
    private var access: CodecAccess? = null
    private var current by mutableStateOf<BluetoothCodecConfig?>(null)
    private var choices by mutableStateOf<List<BluetoothCodecConfig>>(emptyList())
    private var selectedIndex by mutableIntStateOf(-1)
    private var loading by mutableStateOf(true)
    private var switching by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var feedback by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // The modal dims the app underneath the transparent bars.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            val context = LocalContext.current
            val dark = isSystemInDarkTheme()
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                PickerDialog()
            }
        }
        connect()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        access?.close()
        super.onDestroy()
    }

    private fun connect() {
        access?.close()
        access = CodecAccess(this, object : CodecAccess.Callback {
            override fun onReady(ready: CodecAccess) {
                try {
                    val status = ready.status()
                    if (status?.codecConfig == null) {
                        showError("Codec status unavailable")
                    } else {
                        showChoices(status)
                    }
                } catch (failure: Throwable) {
                    Log.e(TAG, "Codec list failed", failure)
                    showError(if (failure is SecurityException) "Needs companion association"
                        else "Could not load codecs")
                }
            }

            override fun onError(message: String, failure: Throwable?) {
                if (failure != null) Log.e(TAG, message, failure)
                showError(message)
            }
        })
        access?.open()
    }

    private fun showChoices(status: BluetoothCodecStatus) {
        current = status.codecConfig
        choices = CodecUi.choices(status)
        selectedIndex = choices.indexOfFirst { CodecUi.same(it, current) }
        loading = false
        if (choices.isEmpty()) showError("No selectable codecs reported")
    }

    private fun showError(message: String) {
        loading = false
        error = message
        choices = emptyList()
        selectedIndex = -1
    }

    private fun applySelection() {
        val choice = choices.getOrNull(selectedIndex) ?: return
        if (CodecUi.same(choice, current)) {
            finish()
            return
        }
        switching = true
        feedback = "Switching to ${CodecUi.name(choice)}…"
        try {
            access?.request(choice) ?: throw IllegalStateException("A2DP unavailable")
            Log.i(TAG, "Requested ${CodecUi.name(choice)}")
            waitFor(choice, System.currentTimeMillis() + TIMEOUT_MS)
        } catch (failure: Throwable) {
            Log.e(TAG, "Codec request failed", failure)
            switchFailed("Could not switch codec")
        }
    }

    private fun waitFor(choice: BluetoothCodecConfig, deadline: Long) {
        main.postDelayed({
            if (isFinishing || access == null) return@postDelayed
            try {
                val observed = access?.status()?.codecConfig
                if (CodecUi.same(observed, choice)) {
                    current = observed
                    feedback = "Using ${CodecUi.name(observed)}"
                    main.postDelayed({ finish() }, 600)
                } else if (System.currentTimeMillis() < deadline) {
                    waitFor(choice, deadline)
                } else {
                    switchFailed("Could not confirm ${CodecUi.name(choice)}")
                }
            } catch (failure: Throwable) {
                Log.e(TAG, "Codec check failed", failure)
                switchFailed("Could not check codec")
            }
        }, POLL_MS)
    }

    private fun switchFailed(message: String) {
        switching = false
        feedback = "$message. Try again or close."
    }

    @Composable
    private fun PickerDialog() {
        BackHandler(enabled = switching) { /* Wait for the brief codec negotiation. */ }
        AlertDialog(
            onDismissRequest = { if (!switching) finish() },
            title = { Text("A2DP codec") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    when {
                        loading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Checking codecs…", modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                        else -> {
                            Text(
                                text = "Currently using ${CodecUi.name(current)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState())
                                    .selectableGroup(),
                            ) {
                                choices.forEachIndexed { index, codec ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .selectable(
                                                selected = selectedIndex == index,
                                                enabled = !switching,
                                                role = Role.RadioButton,
                                                onClick = { selectedIndex = index },
                                            )
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = selectedIndex == index, onClick = null)
                                        Text(
                                            text = CodecUi.name(codec),
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(start = 16.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (feedback != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = feedback!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (feedback!!.startsWith("Could not"))
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!loading && error == null) {
                    Button(
                        onClick = ::applySelection,
                        enabled = selectedIndex >= 0 && !switching,
                    ) {
                        Text(if (CodecUi.same(choices.getOrNull(selectedIndex), current)) "Done" else "Use codec")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = ::finish, enabled = !switching) {
                    Text(if (error == null) "Cancel" else "Close")
                }
            },
        )
    }

    companion object {
        private const val TAG = "A2dpCodecPicker"
        private const val POLL_MS = 250L
        private const val TIMEOUT_MS = 5000L
    }
}
