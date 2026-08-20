/*
 * Miuix settings page for IME bridge visuals, host routing, and language.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private enum class ActiveOverlay {
    ExplainHostTarget,
    ExplainShowRecordingArea,
    ExplainLongPressSwitchIme,
    ExplainHideIdleWaveformInImeSwitch,
    ExplainRecordingOnlyWaveform,
    ExplainTapToToggle,
}

private val HostTargets = listOf(
    BridgeContract.HOST_TARGET_AUTO,
    BridgeContract.HOST_TARGET_PRO,
    BridgeContract.HOST_TARGET_OPEN_SOURCE,
)

@Composable
internal fun BridgeVisualSettingsApp(activity: Activity) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = controller) {
        BridgeVisualSettingsScreen(activity = activity)
    }
}

@Composable
private fun BridgeVisualSettingsScreen(activity: Activity) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var visualConfig by remember {
        mutableStateOf(BridgeVisualPrefs.readForSettings(context))
    }
    var activeOverlay by remember { mutableStateOf<ActiveOverlay?>(null) }
    var explainedHostTarget by remember { mutableStateOf(false) }
    var explainedShowRecordingArea by remember { mutableStateOf(false) }
    var explainedLongPressSwitchIme by remember { mutableStateOf(false) }
    var explainedHideIdleWaveformInImeSwitch by remember { mutableStateOf(false) }
    var explainedRecordingOnlyWaveform by remember { mutableStateOf(false) }
    var explainedTapToToggleRecording by remember { mutableStateOf(false) }
    var pendingChecked by remember { mutableStateOf<Boolean?>(null) }
    var pendingHostTarget by remember { mutableStateOf<String?>(null) }

    val languageTags = remember { BridgeLocalePrefs.supportedTags().toList() }
    val currentLanguageTag = BridgeLocalePrefs.read(context)
    val languageLabels = languageTags.map { tag -> languageLabel(tag) }
    val selectedLanguageIndex = languageTags.indexOf(currentLanguageTag).coerceAtLeast(0)
    val hostTargetLabels = HostTargets.map { target -> hostTargetLabel(target) }
    val selectedHostIndex = HostTargets.indexOf(visualConfig.hostTarget).coerceAtLeast(0)
    val imeOptions = remember(context, visualConfig.switchImeTargetId) {
        loadImeOptions(context, visualConfig.switchImeTargetId)
    }
    val selectedImeIndex = imeOptions.indexOfFirst { option ->
        option.id == visualConfig.switchImeTargetId
    }.coerceAtLeast(0)
    val imeLabels = imeOptions.map { option -> option.label }
    val docsOpenFailed = stringResource(R.string.bridge_docs_open_failed)
    val docsUrl = stringResource(R.string.bridge_docs_url)

    fun saveConfig(config: BridgeVisualPrefs.VisualConfig) {
        visualConfig = config
        BridgeVisualPrefs.saveForSettings(context, config)
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            SmallTopAppBar(title = stringResource(R.string.bridge_visual_title))
        },
        snackbarHost = {
            SnackbarHost(state = snackbarHostState)
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            ) {
                item(key = "docs") {
                    Card(modifier = Modifier.settingsCard()) {
                        ArrowPreference(
                            title = stringResource(R.string.bridge_docs_title),
                            summary = stringResource(R.string.bridge_docs_summary),
                            onClick = {
                                try {
                                    activity.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(docsUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                } catch (_: Throwable) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(docsOpenFailed)
                                    }
                                }
                            },
                        )
                    }
                }
                item(key = "general") {
                    Card(modifier = Modifier.settingsCard()) {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.bridge_language_title),
                            summary = stringResource(R.string.bridge_language_summary),
                            items = languageLabels,
                            selectedIndex = selectedLanguageIndex,
                            onSelectedIndexChange = { index ->
                                val selected = languageTags.getOrNull(index) ?: return@OverlayDropdownPreference
                                if (selected != currentLanguageTag) {
                                    BridgeLocalePrefs.save(context, selected)
                                    activity.recreate()
                                }
                            },
                        )
                        OverlayDropdownPreference(
                            title = stringResource(R.string.bridge_host_target_title),
                            summary = stringResource(R.string.bridge_host_target_summary),
                            items = hostTargetLabels,
                            selectedIndex = selectedHostIndex,
                            onSelectedIndexChange = { index ->
                                val selected = HostTargets.getOrNull(index)
                                    ?: return@OverlayDropdownPreference
                                if (selected == visualConfig.hostTarget) return@OverlayDropdownPreference
                                if (explainedHostTarget) {
                                    saveConfig(visualConfig.withHostTarget(selected))
                                } else {
                                    pendingHostTarget = selected
                                    activeOverlay = ActiveOverlay.ExplainHostTarget
                                }
                            },
                        )
                    }
                }
                item(key = "recording") {
                    Card(modifier = Modifier.settingsCard()) {
                        SwitchPreference(
                            title = stringResource(R.string.bridge_show_recording_area_title),
                            summary = stringResource(R.string.bridge_show_recording_area_summary),
                            checked = visualConfig.showRecordingArea,
                            onCheckedChange = { requested ->
                                if (explainedShowRecordingArea) {
                                    saveConfig(visualConfig.withShowRecordingArea(requested))
                                } else {
                                    pendingChecked = requested
                                    activeOverlay = ActiveOverlay.ExplainShowRecordingArea
                                }
                            },
                        )
                        if (visualConfig.showRecordingArea) {
                            SwitchPreference(
                                title = stringResource(R.string.bridge_long_press_switch_ime_title),
                                summary = stringResource(R.string.bridge_long_press_switch_ime_summary),
                                checked = visualConfig.longPressSwitchIme,
                                onCheckedChange = { requested ->
                                    if (explainedLongPressSwitchIme) {
                                        saveConfig(visualConfig.withLongPressSwitchIme(requested))
                                    } else {
                                        pendingChecked = requested
                                        activeOverlay = ActiveOverlay.ExplainLongPressSwitchIme
                                    }
                                },
                            )
                            if (visualConfig.longPressSwitchIme) {
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.bridge_switch_ime_target_title),
                                    summary = stringResource(R.string.bridge_switch_ime_target_summary),
                                    items = imeLabels,
                                    selectedIndex = selectedImeIndex,
                                    onSelectedIndexChange = { index ->
                                        val selected = imeOptions.getOrNull(index)
                                            ?: return@OverlayDropdownPreference
                                        if (selected.id == visualConfig.switchImeTargetId) {
                                            return@OverlayDropdownPreference
                                        }
                                        saveConfig(visualConfig.withSwitchImeTargetId(selected.id))
                                    },
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.bridge_hide_idle_waveform_ime_switch_title),
                                    summary = stringResource(R.string.bridge_hide_idle_waveform_ime_switch_summary),
                                    checked = visualConfig.hideIdleWaveformInImeSwitch,
                                    onCheckedChange = { requested ->
                                        if (explainedHideIdleWaveformInImeSwitch) {
                                            saveConfig(
                                                visualConfig.withHideIdleWaveformInImeSwitch(requested),
                                            )
                                        } else {
                                            pendingChecked = requested
                                            activeOverlay = ActiveOverlay.ExplainHideIdleWaveformInImeSwitch
                                        }
                                    },
                                )
                            } else {
                                SwitchPreference(
                                    title = stringResource(R.string.bridge_recording_only_waveform_title),
                                    summary = stringResource(R.string.bridge_recording_only_waveform_summary),
                                    checked = visualConfig.showWaveformOnlyWhileRecording,
                                    onCheckedChange = { requested ->
                                        if (explainedRecordingOnlyWaveform) {
                                            saveConfig(
                                                visualConfig.withShowWaveformOnlyWhileRecording(requested),
                                            )
                                        } else {
                                            pendingChecked = requested
                                            activeOverlay = ActiveOverlay.ExplainRecordingOnlyWaveform
                                        }
                                    },
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.bridge_tap_to_toggle_recording_title),
                                    summary = stringResource(R.string.bridge_tap_to_toggle_recording_summary),
                                    checked = visualConfig.tapToToggleRecording,
                                    onCheckedChange = { requested ->
                                        if (explainedTapToToggleRecording) {
                                            saveConfig(visualConfig.withTapToToggleRecording(requested))
                                        } else {
                                            pendingChecked = requested
                                            activeOverlay = ActiveOverlay.ExplainTapToToggle
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "preview") {
                    SmallTitle(text = stringResource(R.string.bridge_visual_preview_title))
                    Card(
                        modifier = Modifier.settingsCard(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.bridge_visual_preview_idle),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote1,
                        )
                        WaveformPreview(
                            recording = false,
                            visualConfig = visualConfig,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                        if (!visualConfig.isImeSwitchMode()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                            Text(
                                text = stringResource(R.string.bridge_visual_preview_recording),
                                color = MiuixTheme.colorScheme.primary,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                            WaveformPreview(
                                recording = true,
                                visualConfig = visualConfig,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                }
                item(key = "summary") {
                    Text(
                        text = stringResource(R.string.bridge_visual_summary),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    )
                }
                item(key = "size") {
                    Card(modifier = Modifier.settingsCard()) {
                        SliderPreference(
                            value = visualConfig.widthDp.toFloat(),
                            onValueChange = { value ->
                                val width = value.roundToInt()
                                if (width != visualConfig.widthDp) {
                                    saveConfig(visualConfig.withSize(width, visualConfig.heightDp))
                                }
                            },
                            title = stringResource(R.string.bridge_visual_width_label),
                            valueText = stringResource(
                                R.string.bridge_visual_dp_value,
                                visualConfig.widthDp,
                            ),
                            valueRange = BridgeVisualPrefs.MIN_WIDTH_DP.toFloat()..
                                BridgeVisualPrefs.MAX_WIDTH_DP.toFloat(),
                            steps = BridgeVisualPrefs.MAX_WIDTH_DP - BridgeVisualPrefs.MIN_WIDTH_DP - 1,
                        )
                        SliderPreference(
                            value = visualConfig.heightDp.toFloat(),
                            onValueChange = { value ->
                                val height = value.roundToInt()
                                if (height != visualConfig.heightDp) {
                                    saveConfig(visualConfig.withSize(visualConfig.widthDp, height))
                                }
                            },
                            title = stringResource(R.string.bridge_visual_height_label),
                            summary = stringResource(R.string.bridge_visual_height_hint),
                            valueText = stringResource(
                                R.string.bridge_visual_dp_value,
                                visualConfig.heightDp,
                            ),
                            valueRange = BridgeVisualPrefs.MIN_HEIGHT_DP.toFloat()..
                                BridgeVisualPrefs.MAX_HEIGHT_DP.toFloat(),
                            steps = BridgeVisualPrefs.MAX_HEIGHT_DP - BridgeVisualPrefs.MIN_HEIGHT_DP - 1,
                        )
                        SliderPreference(
                            value = visualConfig.triggerDelayMs.toFloat(),
                            onValueChange = { value ->
                                val delayMs = value.roundToInt()
                                if (delayMs != visualConfig.triggerDelayMs) {
                                    saveConfig(visualConfig.withTriggerDelayMs(delayMs))
                                }
                            },
                            title = stringResource(R.string.bridge_visual_trigger_delay_label),
                            summary = stringResource(R.string.bridge_visual_trigger_delay_hint),
                            valueText = stringResource(
                                R.string.bridge_visual_trigger_delay_value,
                                visualConfig.triggerDelayMs,
                            ),
                            valueRange = BridgeVisualPrefs.MIN_TRIGGER_DELAY_MS.toFloat()..
                                BridgeVisualPrefs.MAX_TRIGGER_DELAY_MS.toFloat(),
                        )
                    }
                }
                item(key = "reset") {
                    Button(
                        modifier = Modifier.settingsCard().fillMaxWidth(),
                        onClick = { saveConfig(BridgeVisualPrefs.defaults()) },
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text(text = stringResource(R.string.bridge_visual_restore_defaults))
                    }
                }
                item(key = "bottomSpacer") {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            FeatureExplanationDialog(
                activeOverlay = activeOverlay,
                onDismissRequest = {
                    pendingChecked = null
                    pendingHostTarget = null
                    activeOverlay = null
                },
                onConfirm = {
                    when (activeOverlay) {
                        ActiveOverlay.ExplainHostTarget -> {
                            explainedHostTarget = true
                            pendingHostTarget?.let { saveConfig(visualConfig.withHostTarget(it)) }
                            pendingHostTarget = null
                            activeOverlay = null
                        }
                        ActiveOverlay.ExplainShowRecordingArea -> {
                            explainedShowRecordingArea = true
                            pendingChecked?.let { saveConfig(visualConfig.withShowRecordingArea(it)) }
                            pendingChecked = null
                            activeOverlay = null
                        }
                        ActiveOverlay.ExplainLongPressSwitchIme -> {
                            explainedLongPressSwitchIme = true
                            pendingChecked?.let { saveConfig(visualConfig.withLongPressSwitchIme(it)) }
                            pendingChecked = null
                            activeOverlay = null
                        }
                        ActiveOverlay.ExplainHideIdleWaveformInImeSwitch -> {
                            explainedHideIdleWaveformInImeSwitch = true
                            pendingChecked?.let {
                                saveConfig(visualConfig.withHideIdleWaveformInImeSwitch(it))
                            }
                            pendingChecked = null
                            activeOverlay = null
                        }
                        ActiveOverlay.ExplainRecordingOnlyWaveform -> {
                            explainedRecordingOnlyWaveform = true
                            pendingChecked?.let {
                                saveConfig(visualConfig.withShowWaveformOnlyWhileRecording(it))
                            }
                            pendingChecked = null
                            activeOverlay = null
                        }
                        ActiveOverlay.ExplainTapToToggle -> {
                            explainedTapToToggleRecording = true
                            pendingChecked?.let { saveConfig(visualConfig.withTapToToggleRecording(it)) }
                            pendingChecked = null
                            activeOverlay = null
                        }
                        else -> activeOverlay = null
                    }
                },
            )

        }
    }
}

@Composable
private fun FeatureExplanationDialog(
    activeOverlay: ActiveOverlay?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val explanation = explanationCopy(activeOverlay)
    OverlayDialog(
        show = explanation != null,
        title = explanation?.first.orEmpty(),
        summary = explanation?.second.orEmpty(),
        onDismissRequest = onDismissRequest,
    ) {
        Row {
            TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(20.dp))
            TextButton(
                text = stringResource(android.R.string.ok),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun explanationCopy(activeOverlay: ActiveOverlay?): Pair<String, String>? {
    val titleRes: Int
    val offRes: Int
    val onRes: Int
    when (activeOverlay) {
        ActiveOverlay.ExplainHostTarget -> {
            titleRes = R.string.bridge_host_target_title
            offRes = R.string.feature_bridge_host_target_off_desc
            onRes = R.string.feature_bridge_host_target_on_desc
        }
        ActiveOverlay.ExplainShowRecordingArea -> {
            titleRes = R.string.bridge_show_recording_area_title
            offRes = R.string.feature_bridge_show_recording_area_off_desc
            onRes = R.string.feature_bridge_show_recording_area_on_desc
        }
        ActiveOverlay.ExplainLongPressSwitchIme -> {
            titleRes = R.string.bridge_long_press_switch_ime_title
            offRes = R.string.feature_bridge_long_press_switch_ime_off_desc
            onRes = R.string.feature_bridge_long_press_switch_ime_on_desc
        }
        ActiveOverlay.ExplainHideIdleWaveformInImeSwitch -> {
            titleRes = R.string.bridge_hide_idle_waveform_ime_switch_title
            offRes = R.string.feature_bridge_hide_idle_waveform_ime_switch_off_desc
            onRes = R.string.feature_bridge_hide_idle_waveform_ime_switch_on_desc
        }
        ActiveOverlay.ExplainRecordingOnlyWaveform -> {
            titleRes = R.string.bridge_recording_only_waveform_title
            offRes = R.string.feature_bridge_recording_only_waveform_off_desc
            onRes = R.string.feature_bridge_recording_only_waveform_on_desc
        }
        ActiveOverlay.ExplainTapToToggle -> {
            titleRes = R.string.bridge_tap_to_toggle_recording_title
            offRes = R.string.feature_bridge_tap_to_toggle_recording_off_desc
            onRes = R.string.feature_bridge_tap_to_toggle_recording_on_desc
        }
        else -> return null
    }
    return stringResource(titleRes) to (stringResource(offRes) + "\n\n" + stringResource(onRes))
}

@Composable
private fun WaveformPreview(
    recording: Boolean,
    visualConfig: BridgeVisualPrefs.VisualConfig,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context -> BridgeWaveformPreviewView(context, recording) },
        update = { view -> view.setVisualConfig(visualConfig) },
        modifier = modifier,
    )
}

@Composable
private fun languageLabel(tag: String): String {
    return when (BridgeLocalePrefs.normalize(tag)) {
        BridgeLocalePrefs.TAG_EN -> stringResource(R.string.bridge_language_english)
        BridgeLocalePrefs.TAG_ZH_CN -> stringResource(R.string.bridge_language_zh_cn)
        BridgeLocalePrefs.TAG_ZH_TW -> stringResource(R.string.bridge_language_zh_tw)
        else -> stringResource(R.string.bridge_language_system)
    }
}

@Composable
private fun hostTargetLabel(hostTarget: String): String {
    return when (BridgeContract.normalizeHostTarget(hostTarget)) {
        BridgeContract.HOST_TARGET_PRO -> stringResource(R.string.bridge_host_target_pro)
        BridgeContract.HOST_TARGET_OPEN_SOURCE -> stringResource(R.string.bridge_host_target_open_source)
        else -> stringResource(R.string.bridge_host_target_auto)
    }
}

private fun Modifier.settingsCard(): Modifier {
    return padding(horizontal = 12.dp).padding(bottom = 12.dp)
}

private data class ImeOption(val id: String, val label: String)

private fun loadImeOptions(
    context: android.content.Context,
    selectedId: String,
): List<ImeOption> {
    val noneLabel = context.getString(R.string.bridge_switch_ime_target_none)
    val options = mutableListOf(ImeOption("", noneLabel))
    BridgeImeSwitcher.enabledOptions(context).forEach { option ->
        if (option.id.isEmpty()) return@forEach
        options += ImeOption(option.id, option.label.ifBlank { option.id })
    }
    if (selectedId.isNotEmpty() && options.none { option -> option.id == selectedId }) {
        options.add(1, ImeOption(selectedId, selectedId))
    }
    return options
}
