package com.chayu.volumecontrol

import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class RingTuning(
    val thickness: Float = 17.522581f,
    val waveAmplitude: Float = 0.39892474f,
    val waveWavelength: Float = 38.670967f,
    val waveSpeed: Float = 10.806452f,
    val springDamping: Float = 0.7583871f,
    val springStiffness: Float = 443.87097f,
)

private fun SharedPreferences.readRingTuning(): RingTuning {
    return RingTuning(
        thickness = getFloat("thickness", 17.522581f),
        waveAmplitude = getFloat("wave_amplitude", 0.39892474f).coerceIn(0f, 1f),
        waveWavelength = getFloat("wave_wavelength", 38.670967f).coerceIn(12f, 48f),
        waveSpeed = getFloat("wave_speed", 10.806452f).coerceIn(2f, 32f),
        springDamping = getFloat("spring_damping", 0.7583871f),
        springStiffness = getFloat("spring_stiffness", 443.87097f),
    )
}

private fun SharedPreferences.saveRingTuning(tuning: RingTuning) {
    edit()
        .putFloat("thickness", tuning.thickness)
        .putFloat("wave_amplitude", tuning.waveAmplitude)
        .putFloat("wave_wavelength", tuning.waveWavelength)
        .putFloat("wave_speed", tuning.waveSpeed)
        .putFloat("spring_damping", tuning.springDamping)
        .putFloat("spring_stiffness", tuning.springStiffness)
        .apply()
}

private data class ActiveMedia(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artwork: android.graphics.Bitmap?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

private fun readLocalIpAddress(context: android.content.Context): String {
    try {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager.activeNetwork ?: return "未连接网络"
        val address = connectivityManager
            .getLinkProperties(activeNetwork)
            ?.linkAddresses
            ?.asSequence()
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        return address?.hostAddress ?: "未连接网络"
    } catch (_: Exception) {
        return "未连接网络"
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSessionManager: MediaSessionManager
    private val notificationListenerComponent by lazy {
        android.content.ComponentName(this, MediaNotificationListener::class.java)
    }
    private var activeController: MediaController? = null
    private var mediaAccessEnabled by mutableStateOf(false)
    private var activeMedia by mutableStateOf<ActiveMedia?>(null)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionPollJob: Job? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refreshCurrentMedia()
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = refreshCurrentMedia()
        override fun onSessionDestroyed() = refreshCurrentMedia()
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener {
        refreshCurrentMedia()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        setContent {
            VolumeControlTheme {
                var currentVolume by remember { mutableIntStateOf(readCurrentVolume()) }
                val maximumVolume = remember { readMaximumVolume() }

                VolumeControlScreen(
                    currentVolume = currentVolume,
                    maximumVolume = maximumVolume,
                    media = activeMedia,
                    hasMediaAccess = mediaAccessEnabled,
                    onEnableMediaAccess = {
                        startActivity(android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onPrevious = { activeController?.transportControls?.skipToPrevious() },
                    onPlayPause = {
                        activeController?.let { controller ->
                            if (activeMedia?.isPlaying == true) controller.transportControls.pause()
                            else controller.transportControls.play()
                        }
                    },
                    onNext = { activeController?.transportControls?.skipToNext() },
                    onSeek = { progress ->
                        activeMedia?.durationMs?.takeIf { it > 0L }?.let { duration ->
                            activeController?.transportControls?.seekTo(
                                (progress * duration).toLong()
                            )
                        }
                    },
                    readCurrentVolume = { readCurrentVolume() },
                    onVolumeUp = {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE,
                            0,
                        )
                        currentVolume = readCurrentVolume()
                    },
                    onVolumeDown = {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER,
                            0,
                        )
                        currentVolume = readCurrentVolume()
                    },
                    onMute = {
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            0,
                            0,
                        )
                        currentVolume = readCurrentVolume()
                    },
                    onVolumeChange = { volume ->
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            volume.coerceIn(0, maximumVolume),
                            0,
                        )
                        currentVolume = readCurrentVolume()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMediaSessionListener()
    }

    override fun onPause() {
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        super.onPause()
    }

    override fun onDestroy() {
        stopPositionPolling()
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshMediaSessionListener() {
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            mediaSessionManager.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                notificationListenerComponent,
            )
            mediaAccessEnabled = true
            refreshCurrentMedia()
        } catch (_: SecurityException) {
            mediaAccessEnabled = false
            activeMedia = null
        }
    }

    private fun refreshCurrentMedia() {
        if (!mediaAccessEnabled) return
        val controller = try {
            mediaSessionManager.getActiveSessions(notificationListenerComponent).firstOrNull()
        } catch (_: SecurityException) {
            mediaAccessEnabled = false
            activeMedia = null
            return
        }
        if (controller != activeController) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = controller
            controller?.registerCallback(controllerCallback)
        }
        val metadata = controller?.metadata
        val state = controller?.playbackState
        val isPlaying = state?.state == android.media.session.PlaybackState.STATE_PLAYING
        activeMedia = if (controller == null || metadata == null) null else ActiveMedia(
            packageName = controller.packageName,
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().ifBlank { "未知曲目" },
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty().ifBlank { "未知艺术家" },
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART),
            isPlaying = isPlaying,
            positionMs = state?.position?.coerceAtLeast(0L) ?: 0L,
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
        )
        if (isPlaying) startPositionPolling() else stopPositionPolling()
    }

    private fun startPositionPolling() {
        if (positionPollJob?.isActive == true) return
        positionPollJob = scope.launch {
            while (isActive) {
                delay(200L)
                val pos = activeController?.playbackState?.position?.coerceAtLeast(0L) ?: continue
                activeMedia = activeMedia?.copy(positionMs = pos)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

    private fun readCurrentVolume(): Int =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private fun readMaximumVolume(): Int =
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VolumeControlScreen(
    currentVolume: Int,
    maximumVolume: Int,
    media: ActiveMedia?,
    hasMediaAccess: Boolean,
    onEnableMediaAccess: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    readCurrentVolume: () -> Int,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onMute: () -> Unit,
    onVolumeChange: (Int) -> Unit,
) {
    val targetProgress = if (maximumVolume > 0) currentVolume.toFloat() / maximumVolume else 0f
    val context = LocalContext.current
    val tuningPreferences = remember {
        context.getSharedPreferences("ring_tuning", android.content.Context.MODE_PRIVATE)
    }
    val localIpAddress = remember { readLocalIpAddress(context) }
    var tuning by remember { mutableStateOf(tuningPreferences.readRingTuning()) }
    var tuningPageVisible by remember { mutableStateOf(false) }
    var draftTuning by remember { mutableStateOf(tuning) }
    var previewVolume by remember { mutableIntStateOf(currentVolume) }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(
            dampingRatio = tuning.springDamping,
            stiffness = tuning.springStiffness,
        ),
        label = "volumeProgress",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Text(
                text = "媒体音量 · V${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                contentAlignment = Alignment.Center,
            ) {
                PreviewVolumeButton(
                    label = "−",
                    onClick = onVolumeDown,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                VolumeRingPreview(
                    currentVolume = currentVolume,
                    targetProgress = targetProgress,
                    animatedProgress = animatedProgress,
                    tuning = tuning,
                    maximumVolume = maximumVolume,
                    onVolumeChange = onVolumeChange,
                    modifier = Modifier.size(210.dp),
                )
                PreviewVolumeButton(
                    label = "+",
                    onClick = onVolumeUp,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onMute,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("静音")
                }
                FilledTonalButton(
                    onClick = {
                        draftTuning = tuning
                        previewVolume = readCurrentVolume().coerceIn(0, maximumVolume)
                        tuningPageVisible = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("调节音量环")
                }
            }

            NowPlayingCard(
                media = media,
                hasMediaAccess = hasMediaAccess,
                onEnableMediaAccess = onEnableMediaAccess,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onSeek = onSeek,
            )
            }
        }

        Text(
            text = "本机 IP：$localIpAddress",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )

        if (tuningPageVisible) {
            BackHandler {
                tuningPageVisible = false
            }
            RingTuningScreen(
                previewVolume = previewVolume,
                maximumVolume = maximumVolume,
                tuning = draftTuning,
                onTuningChange = { draftTuning = it },
                onReset = { draftTuning = RingTuning() },
                onPreviewVolumeChange = { previewVolume = it.coerceIn(0, maximumVolume) },
                onCancel = { tuningPageVisible = false },
                onComplete = {
                    tuning = draftTuning
                    tuningPreferences.saveRingTuning(draftTuning)
                    tuningPageVisible = false
                },
            )
        }
    }
}

@Composable
private fun NowPlayingCard(
    media: ActiveMedia?,
    hasMediaAccess: Boolean,
    onEnableMediaAccess: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        when {
            !hasMediaAccess -> Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("正在播放", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "开启通知使用权后，显示系统当前媒体的封面、曲目信息与播放控制。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = onEnableMediaAccess) {
                    Text("允许读取媒体")
                }
            }

            media == null -> Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("正在播放", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("暂未发现活跃的媒体播放", style = MaterialTheme.typography.bodyMedium)
            }

            else -> Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MediaArtwork(media = media)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("正在播放", style = MaterialTheme.typography.labelLarge)
                        Text(
                            media.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(media.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        media.album?.takeIf { it.isNotBlank() }?.let { album ->
                            Text(album, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }

                val progress = if (media.durationMs > 0L) {
                    (media.positionMs.toFloat() / media.durationMs).coerceIn(0f, 1f)
                } else 0f
                var isSeeking by remember { mutableStateOf(false) }
                var seekValue by remember { mutableFloatStateOf(0f) }
                Slider(
                    value = if (isSeeking) seekValue else progress,
                    onValueChange = { value ->
                        isSeeking = true
                        seekValue = value
                    },
                    onValueChangeFinished = {
                        isSeeking = false
                        onSeek(seekValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = SliderDefaults.colors(
                        // M3's low-emphasis outline token keeps the remaining track visible
                        // against this card's secondaryContainer background.
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatDuration(media.positionMs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        if (media.durationMs > 0L) formatDuration(media.durationMs) else "直播",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "上一首",
                        )
                    }
                    Button(
                        onClick = onPlayPause,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Icon(
                            imageVector = if (media.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (media.isPlaying) "暂停" else "播放",
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "下一首",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaArtwork(media: ActiveMedia) {
    Surface(
        modifier = Modifier.size(96.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (media.artwork != null) {
            Image(
                bitmap = media.artwork.asImageBitmap(),
                contentDescription = "${media.title} 封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text("♫", fontSize = 36.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VolumeRingPreview(
    currentVolume: Int,
    targetProgress: Float,
    animatedProgress: Float,
    tuning: RingTuning,
    maximumVolume: Int = 0,
    onVolumeChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var ringWidthPx by remember { mutableStateOf(0f) }
    var ringHeightPx by remember { mutableStateOf(0f) }
    val volumeChange = onVolumeChange
    val latestVolume = rememberUpdatedState(currentVolume)
    val latestVolumeChange = rememberUpdatedState(onVolumeChange)
    val gestureModifier = if (volumeChange == null || maximumVolume <= 0) {
        Modifier
    } else {
        Modifier
            .onSizeChanged {
                ringWidthPx = it.width.toFloat()
                ringHeightPx = it.height.toFloat()
            }
            .pointerInput(maximumVolume, ringWidthPx, ringHeightPx) {
                var lastAngle = 0.0
                var accumulatedVolumeDelta = 0.0
                var startVolume = 0
                var trackingRing = false

                detectDragGestures(
                    onDragStart = { position ->
                        val centerX = ringWidthPx / 2f
                        val centerY = ringHeightPx / 2f
                        val distance = hypot(
                            (position.x - centerX).toDouble(),
                            (position.y - centerY).toDouble(),
                        ).toFloat()
                        val radius = minOf(ringWidthPx, ringHeightPx) / 2f
                        trackingRing = abs(distance - radius * 0.9f) < radius * 0.22f
                        lastAngle = atan2(
                            (position.y - centerY).toDouble(),
                            (position.x - centerX).toDouble(),
                        )
                        accumulatedVolumeDelta = 0.0
                        startVolume = latestVolume.value
                    },
                    onDrag = { change, _ ->
                        if (!trackingRing) return@detectDragGestures

                        val centerX = ringWidthPx / 2f
                        val centerY = ringHeightPx / 2f
                        val angle = atan2(
                            (change.position.y - centerY).toDouble(),
                            (change.position.x - centerX).toDouble(),
                        )
                        var angleDelta = angle - lastAngle
                        if (angleDelta > PI) angleDelta -= PI * 2
                        if (angleDelta < -PI) angleDelta += PI * 2
                        lastAngle = angle
                        accumulatedVolumeDelta += angleDelta / (PI * 2) * maximumVolume
                        latestVolumeChange.value?.invoke(
                            (startVolume + accumulatedVolumeDelta).roundToInt(),
                        )
                        change.consume()
                    },
                    onDragEnd = { trackingRing = false },
                    onDragCancel = { trackingRing = false },
                )
            }
    }

    val wavelengthDp = tuning.waveWavelength.roundToInt()
    Box(contentAlignment = Alignment.Center, modifier = modifier.then(gestureModifier)) {
        CircularWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            color = if (currentVolume == 0) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            stroke = Stroke(
                width = tuning.thickness.dp.value,
            ),
            trackStroke = Stroke(
                width = tuning.thickness.dp.value,
            ),
            // Keep this lambda tied to tuning. Material3 refreshes the cached wave path when
            // the amplitude provider changes; a remembered provider leaves that path stale
            // after returning from the tuning screen.
            amplitude = { _: Float -> tuning.waveAmplitude },
            wavelength = wavelengthDp.dp,
            waveSpeed = tuning.waveSpeed.coerceAtLeast(2f).dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val percentage = if (maximumVolume > 0) {
                (currentVolume.toFloat() / maximumVolume * 100).roundToInt()
            } else 0
            val digits = percentage.toString()
            val volumeNumberStyle = MaterialTheme.typography.displayLarge.copy(
                shadow = Shadow(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f),
                    offset = Offset(0f, 2f),
                    blurRadius = 6f,
                ),
            )
            var previousPercentage by remember { mutableIntStateOf(percentage) }
            val direction = if (percentage >= previousPercentage) 1 else -1
            LaunchedEffect(percentage) {
                previousPercentage = percentage
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                for ((index, char) in digits.withIndex()) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            (slideInVertically { height -> direction * height } + fadeIn())
                                .togetherWith(slideOutVertically { height -> -direction * height } + fadeOut())
                        },
                        label = "digit_$index",
                        modifier = Modifier.widthIn(min = 28.dp),
                    ) { c ->
                        Text(
                            text = c.toString(),
                            style = volumeNumberStyle,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Text(
                    text = "%",
                    style = volumeNumberStyle,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (currentVolume == 0) {
                Text(
                    text = "已静音",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RingTuningScreen(
    previewVolume: Int,
    maximumVolume: Int,
    tuning: RingTuning,
    onTuningChange: (RingTuning) -> Unit,
    onReset: () -> Unit,
    onPreviewVolumeChange: (Int) -> Unit,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
) {
    val previewProgress = if (maximumVolume > 0) {
        previewVolume.toFloat() / maximumVolume
    } else 0f
    val animatedPreviewProgress by animateFloatAsState(
        targetValue = previewProgress,
        animationSpec = spring(
            dampingRatio = tuning.springDamping,
            stiffness = tuning.springStiffness,
        ),
        label = "previewVolumeProgress",
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("音量环调参", style = MaterialTheme.typography.titleLarge)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(246.dp),
                contentAlignment = Alignment.Center,
            ) {
                PreviewVolumeButton(
                    label = "−",
                    onClick = { onPreviewVolumeChange(previewVolume - 1) },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                VolumeRingPreview(
                    currentVolume = previewVolume,
                    targetProgress = previewProgress,
                    animatedProgress = animatedPreviewProgress,
                    tuning = tuning,
                    maximumVolume = maximumVolume,
                    onVolumeChange = onPreviewVolumeChange,
                    modifier = Modifier.size(210.dp),
                )
                PreviewVolumeButton(
                    label = "+",
                    onClick = { onPreviewVolumeChange(previewVolume + 1) },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TuningSlider("Ring Thickness", tuning.thickness, 8f..32f, "%.0f dp") {
                    onTuningChange(tuning.copy(thickness = it))
                }
                TuningSlider(
                    label = "Wave Strength",
                    value = tuning.waveAmplitude,
                    range = 0f..1f,
                    valueFormat = "%.0f%%",
                    valueMultiplier = 100f,
                ) {
                    onTuningChange(tuning.copy(waveAmplitude = it))
                }
                TuningSlider("Wave Wavelength", tuning.waveWavelength, 12f..48f, "%.0f dp") {
                    onTuningChange(tuning.copy(waveWavelength = it))
                }
                TuningSlider("Wave Speed", tuning.waveSpeed, 2f..32f, "%.0f dp/s") {
                    onTuningChange(tuning.copy(waveSpeed = it))
                }
                TuningSlider("Progress Damping", tuning.springDamping, 0.3f..1f, "%.2f") {
                    onTuningChange(tuning.copy(springDamping = it))
                }
                TuningSlider("Progress Stiffness", tuning.springStiffness, 100f..700f, "%.0f") {
                    onTuningChange(tuning.copy(springStiffness = it))
                }
                FilledTonalButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(onClick = onComplete, modifier = Modifier.weight(1f)) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun PreviewVolumeButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = label,
            fontSize = 28.sp,
            lineHeight = 28.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueFormat: String,
    valueMultiplier: Float = 1f,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueFormat.format(value * valueMultiplier), color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun VolumeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(
            text = label,
            fontSize = 36.sp,
            lineHeight = 36.sp,
            textAlign = TextAlign.Center,
        )
    }
}
