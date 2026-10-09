package com.commontongue.prototype.ui.conversation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.commontongue.prototype.R
import com.commontongue.prototype.ui.theme.ConversationAppearance
import com.commontongue.prototype.ui.theme.ConversationTheme
import com.commontongue.translation.*

@Composable
fun ConversationScreen(
    state: ConversationState,
    theme: ConversationTheme,
    selectTheme: (ConversationTheme) -> Unit,
    press: (TalkSide) -> Long?,
    release: (Long) -> Unit,
    cancelPress: (Long) -> Unit,
    replay: () -> Unit,
    cancel: () -> Unit,
    clear: () -> Unit,
    retry: () -> Unit,
    microphoneSettings: () -> Unit,
    developer: @Composable () -> Unit = {},
    export: @Composable () -> Unit = {},
) {
    var settings by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.brand_mark), null, Modifier.size(42.dp))
                    Column {
                        Text(
                            "Common Tongue",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            "Real conversations. A smaller world.",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                TextButton(
                    onClick = { settings = true },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text("Settings")
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp).semantics {
                        liveRegion = LiveRegionMode.Polite
                    }
                ) {
                    Text(state.status(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.modelsReady) "Local processing · No internet needed"
                        else "Your conversation stays on this phone",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (theme == ConversationTheme.TRAVEL) TravelHorizon()
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TalkControl(
                    TalkSide.ENGLISH,
                    state,
                    Modifier.weight(1f).fillMaxHeight(),
                    press,
                    release,
                    cancelPress,
                )
                TalkControl(
                    TalkSide.SPANISH,
                    state,
                    Modifier.weight(1f).fillMaxHeight(),
                    press,
                    release,
                    cancelPress,
                )
            }
            if (
                state.stage in
                    setOf(
                        TurnStage.STARTING,
                        TurnStage.LISTENING,
                        TurnStage.RECOGNIZING,
                        TurnStage.TRANSLATING,
                        TurnStage.SYNTHESIZING,
                        TurnStage.SPEAKING,
                        TurnStage.LOADING_MODELS,
                    )
            ) {
                OutlinedButton(
                    onClick = cancel,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Cancel")
                }
            }
            state.problem?.let {
                Text(
                    it.plain(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (it == TurnProblem.MICROPHONE_SETTINGS || it == TurnProblem.MICROPHONE_DENIED)
                    OutlinedButton(onClick = microphoneSettings) { Text("Microphone settings") }
                else if (it != TurnProblem.RESOURCES_MISSING && it != TurnProblem.BACKGROUND)
                    OutlinedButton(onClick = retry) { Text("Try again") }
            }
            if (!state.modelsReady && state.stage == TurnStage.CANCELLED) {
                OutlinedButton(onClick = retry) { Text("Prepare offline resources") }
            }
            ResultCard(state.recognized, state.translated, state.side, theme)
            Button(
                onClick = replay,
                enabled =
                    state.replayAvailable &&
                        state.stage !in
                            setOf(
                                TurnStage.LISTENING,
                                TurnStage.STARTING,
                                TurnStage.RECOGNIZING,
                                TurnStage.TRANSLATING,
                                TurnStage.SYNTHESIZING,
                            ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text("Replay translation", style = MaterialTheme.typography.titleMedium)
            }
            if (state.recent.isNotEmpty()) {
                Text("This conversation", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Temporary · clears when you close or clear the conversation",
                    style = MaterialTheme.typography.bodySmall,
                )
                val visibleRecent =
                    if (
                        state.recent.lastOrNull()?.let {
                            it.recognized == state.recognized && it.translated == state.translated
                        } == true
                    )
                        state.recent.dropLast(1)
                    else state.recent
                visibleRecent.asReversed().forEach { turn ->
                    ResultCard(turn.recognized, turn.translated, turn.side, theme, recent = true)
                }
            }
            TextButton(onClick = clear, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Clear conversation")
            }
            Text(
                "One phone is enough. Two phones are better.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.stage == TurnStage.RESOURCES_REQUIRED) {
                Text("This build needs installed offline language resources.")
                developer()
            }
        }
    }
    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("Settings") },
            confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Appearance", style = MaterialTheme.typography.titleLarge)
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    ConversationTheme.entries.forEach { candidate ->
                        ConversationAppearance(candidate) {
                            Surface(
                                onClick = { selectTheme(candidate) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier =
                                    Modifier.fillMaxWidth().semantics {
                                        selected = candidate == theme
                                    },
                            ) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        candidate.title +
                                            if (candidate == theme) " · Selected" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        candidate.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Text("English", Modifier.padding(8.dp))
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Text("Español", Modifier.padding(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "Recent turns stay in memory. Clear conversation removes the text and replay audio.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    developer()
                    export()
                }
            },
        )
    }
}

@Composable
private fun TalkControl(
    side: TalkSide,
    state: ConversationState,
    modifier: Modifier,
    press: (TalkSide) -> Long?,
    release: (Long) -> Unit,
    cancel: (Long) -> Unit,
) {
    val enabled = state.modelsReady
    val name = if (side == TalkSide.ENGLISH) "English" else "Español"
    val prompt =
        if (side == TalkSide.ENGLISH) "Hold to speak English"
        else "Mantén presionado para hablar español"
    val listening = state.side == side && state.stage == TurnStage.LISTENING
    var accessibilityPress by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.stage, state.side) {
        if (state.side != side || state.stage !in setOf(TurnStage.STARTING, TurnStage.LISTENING))
            accessibilityPress = null
    }
    val currentPress by rememberUpdatedState(press)
    val currentRelease by rememberUpdatedState(release)
    val currentCancel by rememberUpdatedState(cancel)
    DisposableEffect(enabled) {
        onDispose {
            accessibilityPress?.let(currentCancel)
            accessibilityPress = null
        }
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color =
            if (!enabled) colors.surfaceVariant
            else if (side == TalkSide.ENGLISH) colors.primaryContainer
            else colors.secondaryContainer,
        contentColor =
            if (!enabled) colors.onSurfaceVariant
            else if (side == TalkSide.ENGLISH) colors.onPrimaryContainer
            else colors.onSecondaryContainer,
        modifier =
            modifier
                .heightIn(min = 152.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = prompt
                    stateDescription =
                        if (!enabled) "Unavailable until offline resources are ready"
                        else if (listening) "Listening. Release to translate."
                        else "Ready. Hold to speak."
                    if (!enabled) disabled()
                    onClick(
                        if (accessibilityPress != null) "Finish recording" else "Start recording"
                    ) {
                        if (!enabled) false
                        else {
                            val token = accessibilityPress
                            if (token == null) accessibilityPress = currentPress(side)
                            else {
                                currentRelease(token)
                                accessibilityPress = null
                            }
                            true
                        }
                    }
                }
                .onKeyEvent { event ->
                    if (!enabled || event.key !in setOf(Key.Enter, Key.Spacebar)) false
                    else {
                        if (event.type == KeyEventType.KeyDown && accessibilityPress == null)
                            accessibilityPress = currentPress(side)
                        if (event.type == KeyEventType.KeyUp) {
                            accessibilityPress?.let(currentRelease)
                            accessibilityPress = null
                        }
                        true
                    }
                }
                .focusable(enabled)
                .pointerInput(enabled, side) {
                    if (enabled)
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val token = currentPress(side)
                            var released = false
                            try {
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    up.consume()
                                    token?.let(currentRelease)
                                    released = true
                                }
                            } finally {
                                if (!released) token?.let(currentCancel)
                            }
                        }
                },
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Icon(painterResource(R.drawable.ic_microphone), null, Modifier.size(28.dp))
            Text(
                if (listening) "Listening…" else prompt,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (side == TalkSide.ENGLISH) "→ Español" else "→ English",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ResultCard(
    source: String,
    translated: String,
    side: TalkSide?,
    theme: ConversationTheme,
    recent: Boolean = false,
) {
    val shape =
        if (theme == ConversationTheme.CONVERSATION)
            RoundedCornerShape(
                24.dp,
                24.dp,
                if (side == TalkSide.ENGLISH) 4.dp else 24.dp,
                if (side == TalkSide.SPANISH) 4.dp else 24.dp,
            )
        else RoundedCornerShape(24.dp)
    Surface(
        Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (side == TalkSide.SPANISH) "Source · Español" else "Source · English",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                source.ifEmpty { "Your words will appear here." },
                style = MaterialTheme.typography.bodyLarge,
            )
            HorizontalDivider()
            Text(
                if (side == TalkSide.SPANISH) "Translation · English" else "Translation · Español",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                translated.ifEmpty { "A conversation starts with you." },
                style =
                    if (recent) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun TravelHorizon() {
    val foreground = MaterialTheme.colorScheme.primary
    val warm = MaterialTheme.colorScheme.secondaryContainer
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        drawCircle(
            warm,
            radius = size.height * .5f,
            center = androidx.compose.ui.geometry.Offset(size.width * .8f, size.height * .45f),
        )
        val ridge =
            Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width * .15f, size.height * .35f)
                lineTo(size.width * .3f, size.height * .85f)
                lineTo(size.width * .5f, size.height * .15f)
                lineTo(size.width * .72f, size.height)
                close()
            }
        drawPath(ridge, foreground.copy(alpha = .18f))
    }
}

private fun ConversationState.status() =
    when (stage) {
        TurnStage.IDLE -> if (modelsReady) "Offline ready · Ready to speak" else "Getting ready…"
        TurnStage.LOADING_MODELS -> "Pack installed · Loading models…"
        TurnStage.STARTING -> "Getting the microphone ready…"
        TurnStage.LISTENING -> "Listening… Release to translate"
        TurnStage.RECOGNIZING -> "Understanding…"
        TurnStage.TRANSLATING -> "Translating…"
        TurnStage.SYNTHESIZING -> "Preparing the offline voice…"
        TurnStage.SPEAKING -> "Speaking…"
        TurnStage.COMPLETE -> "Ready for the next turn"
        TurnStage.CANCELLED -> "Turn cancelled · Hold to speak again"
        TurnStage.ERROR -> "Let's try again"
        TurnStage.RESOURCES_REQUIRED -> "Offline language resources are not installed"
    }

private fun TurnProblem.plain() =
    when (this) {
        TurnProblem.RESOURCES_MISSING -> "Offline language resources are missing or incomplete."
        TurnProblem.PREPARATION_FAILED -> "The local engine could not get ready. Try again."
        TurnProblem.MICROPHONE_DENIED ->
            "Microphone permission is required. Hold a talk button to allow it."
        TurnProblem.MICROPHONE_SETTINGS ->
            "Allow microphone access in your phone's app settings, then return here."
        TurnProblem.MICROPHONE_UNAVAILABLE ->
            "The microphone is unavailable. Close any other recording app and try again."
        TurnProblem.NO_SPEECH,
        TurnProblem.RECOGNITION_FAILED ->
            "I couldn't understand that. Hold the button a little longer and try again."
        TurnProblem.TRANSLATION_FAILED -> "Translation failed. Try again."
        TurnProblem.OFFLINE_VOICE_UNAVAILABLE ->
            "An offline voice is unavailable. Check your phone's speech settings."
        TurnProblem.SYNTHESIS_FAILED -> "The offline voice could not finish. Try again."
        TurnProblem.PLAYBACK_FAILED ->
            "Playback failed. Check your volume and audio output, then try again."
        TurnProblem.AUDIO_UNAVAILABLE -> "Replay audio is no longer available. Make another turn."
        TurnProblem.OFFLINE_REQUIRED ->
            "A verified offline engine is required. No online service was used."
        TurnProblem.PARTIAL_RECOGNITION ->
            "Only part of your speech was understood. Please make a shorter turn."
        TurnProblem.PARTIAL_TRANSLATION ->
            "The translation is incomplete. Please make a shorter turn."
        TurnProblem.BACKGROUND -> "The turn stopped when you left the app. Hold to speak again."
    }
