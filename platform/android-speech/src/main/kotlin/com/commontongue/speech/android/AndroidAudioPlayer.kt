package com.commontongue.speech.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.commontongue.translation.SpeechPlaybackReceipt
import kotlinx.coroutines.delay

internal class AndroidAudioPlayer(
    private val context: Context,
    private val diagnostic: (SpeechDiagnostic) -> Unit,
) : LocalAudioPlayer {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    private var interruption: SpeechCode? = null
    private var receiverRegistered = false
    private var generation = 0
    private val noisy =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                    interrupt(SpeechCode.ROUTE_DISCONNECTED)
            }
        }

    private fun interrupt(code: SpeechCode) {
        interruption = code
        // Never let a disconnected headset unexpectedly spill speech onto the speaker.
        try {
            track?.pause()
        } catch (_: IllegalStateException) {}
    }

    override suspend fun play(pcm: PcmAudio, synthesisRequestedNanos: Long): SpeechPlaybackReceipt {
        stop()
        val token = generation
        interruption = null
        val trace = PlaybackTrace(diagnostic)
        try {
            trace.record(
                PlaybackDiagnostic(
                    PlaybackStep.PCM_FORMAT,
                    PlaybackOutcome.SUCCEEDED,
                    pcmBytes = pcm.bytes.size,
                    channels = pcm.channels,
                    sampleRateHz = pcm.rate,
                )
            )
            trace.begin(PlaybackStep.FOCUS_REQUEST)
            val attributes =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            val request =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setWillPauseWhenDucked(true)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(
                        { change ->
                            if (token == generation && change != AudioManager.AUDIOFOCUS_GAIN)
                                interrupt(SpeechCode.FOCUS_LOST)
                        },
                        main,
                    )
                    .build()
            focus = request
            val focusResult = manager.requestAudioFocus(request)
            trace.record(
                PlaybackDiagnostic(
                    PlaybackStep.FOCUS_REQUEST,
                    PlaybackOutcome.SUCCEEDED,
                    focusResult = focusResult,
                )
            )
            if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                throw SpeechFault(SpeechCode.FOCUS_DENIED)
            trace.begin(PlaybackStep.NOISY_RECEIVER_REGISTER)
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (android.os.Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED)
            else {
                @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(noisy, filter)
            }
            receiverRegistered = true
            trace.record(
                PlaybackDiagnostic(PlaybackStep.NOISY_RECEIVER_REGISTER, PlaybackOutcome.SUCCEEDED)
            )
            trace.begin(PlaybackStep.TRACK_CONSTRUCT)
            val player =
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(pcm.rate)
                            .setChannelMask(
                                if (pcm.channels == 1) AudioFormat.CHANNEL_OUT_MONO
                                else AudioFormat.CHANNEL_OUT_STEREO
                            )
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.bytes.size)
                    .build()
            track = player
            trace.record(
                PlaybackDiagnostic(
                    PlaybackStep.TRACK_CONSTRUCT,
                    PlaybackOutcome.SUCCEEDED,
                    trackState = player.state,
                    playState = player.playState,
                )
            )
            val privateTypes =
                mutableSetOf(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                )
            if (android.os.Build.VERSION.SDK_INT >= 31)
                privateTypes.add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            var privateRouteSeen = player.routedDevice?.type in privateTypes
            trace.begin(PlaybackStep.ROUTING_LISTENER_REGISTER)
            player.addOnRoutingChangedListener(
                AudioRouting.OnRoutingChangedListener { routing ->
                    val routed = routing.routedDevice
                    if (routed?.type in privateTypes) privateRouteSeen = true
                    if (
                        token == generation &&
                            privateRouteSeen &&
                            routed?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    )
                        interrupt(SpeechCode.ROUTE_DISCONNECTED)
                },
                main,
            )
            trace.record(
                PlaybackDiagnostic(
                    PlaybackStep.ROUTING_LISTENER_REGISTER,
                    PlaybackOutcome.SUCCEEDED,
                    routeType = player.routedDevice?.type,
                )
            )
            loadStaticPcm(
                pcm.bytes.size,
                { player.state },
                { player.write(pcm.bytes, 0, pcm.bytes.size) },
                trace,
            )
            val requested = System.nanoTime()
            trace.begin(PlaybackStep.PLAY_INVOKE)
            player.play()
            trace.record(
                PlaybackDiagnostic(
                    PlaybackStep.PLAY_INVOKE,
                    PlaybackOutcome.SUCCEEDED,
                    trackState = player.state,
                    playState = player.playState,
                    routeType = player.routedDevice?.type,
                )
            )
            trace.begin(PlaybackStep.HEAD_INITIAL)
            val initialFrames = player.playbackHeadPosition.toLong() and 0xffffffffL
            trace.record(
                PlaybackDiagnostic(
                    PlaybackStep.HEAD_INITIAL,
                    PlaybackOutcome.SUCCEEDED,
                    frames = initialFrames,
                    routeType = player.routedDevice?.type,
                )
            )
            trace.begin(PlaybackStep.WAIT_FOR_COMPLETION)
            var lastReport = requested
            var advancementReported = false
            var receipt: SpeechPlaybackReceipt? = null
            val totalFrames = pcm.bytes.size / (pcm.channels * 2)
            val duration = totalFrames * 1000.0 / pcm.rate
            while (true) {
                interruption?.let { throw SpeechFault(it) }
                val now = System.nanoTime()
                val frames = player.playbackHeadPosition.toLong() and 0xffffffffL
                // Bounded samples: first advancement, then at most once per second, plus
                // completion.
                if (
                    frames > initialFrames &&
                        (!advancementReported || now - lastReport >= 1_000_000_000)
                ) {
                    trace.record(
                        PlaybackDiagnostic(
                            PlaybackStep.HEAD_ADVANCE,
                            PlaybackOutcome.SUCCEEDED,
                            frames = frames,
                            playState = player.playState,
                            routeType = player.routedDevice?.type,
                            milliseconds = (now - requested) / 1e6,
                        )
                    )
                    advancementReported = true
                    lastReport = now
                }
                if (frames > pcm.firstSignalFrame && receipt == null) {
                    val firstSignal =
                        now - (frames * 1e9 / pcm.rate).toLong() +
                            (pcm.firstSignalFrame * 1e9 / pcm.rate).toLong()
                    receipt =
                        SpeechPlaybackReceipt(
                            frames,
                            (firstSignal - synthesisRequestedNanos).coerceAtLeast(0) / 1e6,
                            (firstSignal - requested).coerceAtLeast(0) / 1e6,
                        )
                    diagnostic(
                        SpeechDiagnostic(
                            SpeechStage.PLAYBACK_START,
                            milliseconds = receipt.requestToAudioMilliseconds,
                            frames = frames,
                            routeType = player.routedDevice?.type,
                        )
                    )
                }
                if (frames >= totalFrames && receipt != null) {
                    trace.record(
                        PlaybackDiagnostic(
                            PlaybackStep.HEAD_COMPLETE,
                            PlaybackOutcome.SUCCEEDED,
                            frames = frames,
                            playState = player.playState,
                            routeType = player.routedDevice?.type,
                        )
                    )
                    return receipt
                }
                val elapsed = (now - requested) / 1e6
                if ((receipt == null && elapsed > 3000) || elapsed > duration + 5000) {
                    trace.record(
                        PlaybackDiagnostic(
                            PlaybackStep.WAIT_FOR_COMPLETION,
                            PlaybackOutcome.TIMED_OUT,
                            frames = frames,
                            playState = player.playState,
                            routeType = player.routedDevice?.type,
                            milliseconds = elapsed,
                            code = SpeechCode.PLAYBACK_FAILED,
                        )
                    )
                    throw SpeechFault(SpeechCode.PLAYBACK_FAILED)
                }
                delay(20)
            }
        } catch (error: Exception) {
            trace.failed(error)
            throw error
        } finally {
            stop()
        }
    }

    override fun stop() {
        generation++
        track?.let { player ->
            try {
                player.pause()
                player.flush()
            } catch (_: IllegalStateException) {}
            try {
                player.release()
            } catch (_: IllegalStateException) {}
        }
        track = null
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(noisy)
            } catch (_: IllegalArgumentException) {}
            receiverRegistered = false
        }
        focus?.let {
            try {
                manager.abandonAudioFocusRequest(it)
            } catch (_: Exception) {}
        }
        focus = null
    }
}
