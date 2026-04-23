package com.ytauto.ui.car

import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ytauto.service.PlaybackService

/**
 * VideoPlayerScreen - De "Video-Hack" voor Android Auto.
 */
@UnstableApi
class VideoPlayerScreen(carContext: CarContext) : Screen(carContext) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            Log.d("VideoPlayerScreen", "Surface available, setting to controller")
            controller?.setVideoSurface(surfaceContainer.surface)
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            Log.d("VideoPlayerScreen", "Surface destroyed")
            controller?.setVideoSurface(null)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { invalidate() }
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) { invalidate() }
        override fun onPlaybackStateChanged(state: Int) { invalidate() }
    }

    init {
        val sessionToken = SessionToken(carContext, ComponentName(carContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(carContext, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val mediaController = controllerFuture?.get()
                controller = mediaController
                mediaController?.addListener(playerListener)

                // Activeer Video Mode in de service
                val args = Bundle().apply { putBoolean(PlaybackService.EXTRA_VIDEO_MODE, true) }
                mediaController?.sendCustomCommand(
                    SessionCommand(PlaybackService.ACTION_TOGGLE_VIDEO_MODE, Bundle.EMPTY),
                    args
                )

                invalidate()
                Log.d("VideoPlayerScreen", "Connected and Video Mode requested")
            } catch (e: Exception) {
                Log.e("VideoPlayerScreen", "Error connecting", e)
            }
        }, MoreExecutors.directExecutor())

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java)
                    .setSurfaceCallback(surfaceCallback)
            }

            override fun onStop(owner: LifecycleOwner) {
                // Schakel video mode uit bij verlaten
                val args = Bundle().apply { putBoolean(PlaybackService.EXTRA_VIDEO_MODE, false) }
                controller?.sendCustomCommand(
                    SessionCommand(PlaybackService.ACTION_TOGGLE_VIDEO_MODE, Bundle.EMPTY),
                    args
                )
                carContext.getCarService(AppManager::class.java)
                    .setSurfaceCallback(null)
            }
        })
    }

    override fun onGetTemplate(): Template {
        val title = controller?.currentMediaItem?.mediaMetadata?.title ?: "YouTube Video Mode"
        val artist = controller?.currentMediaItem?.mediaMetadata?.artist ?: "Selecteer een nummer"

        val isPlaying = controller?.isPlaying == true
        val playPauseAction = Action.Builder()
            .setTitle(if (isPlaying) "Pauze" else "Play")
            .setOnClickListener {
                controller?.let { c ->
                    if (c.isPlaying) c.pause() else c.play()
                }
                invalidate()
            }
            .build()

        val nextAction = Action.Builder()
            .setTitle("Volgende")
            .setOnClickListener {
                controller?.seekToNext()
                invalidate()
            }
            .build()

        val closeAction = Action.Builder()
            .setTitle("Sluit Video")
            .setOnClickListener { screenManager.pop() }
            .build()

        val speedAction = Action.Builder()
            .setTitle("${controller?.playbackParameters?.speed ?: 1.0}x")
            .setOnClickListener {
                controller?.let { c ->
                    val currentSpeed = c.playbackParameters.speed
                    val nextSpeed = when {
                        currentSpeed < 1.0f -> 1.0f
                        currentSpeed < 1.5f -> 1.5f
                        currentSpeed < 2.0f -> 2.0f
                        else -> 0.75f
                    }
                    c.setPlaybackSpeed(nextSpeed)
                }
                invalidate()
            }
            .build()

        return NavigationTemplate.Builder()
            .setNavigationInfo(
                MessageInfo.Builder(title)
                    .setText(artist)
                    .build()
            )
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(playPauseAction)
                    .addAction(nextAction)
                    .addAction(speedAction)
                    .addAction(closeAction)
                    .build()
            )
            .setBackgroundColor(androidx.car.app.model.CarColor.BLUE)
            .build()
    }
}
