package com.ytauto.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.media3.common.util.UnstableApi
import com.ytauto.ui.car.VideoPlayerScreen

class YTCarAppSession : Session() {
    @OptIn(UnstableApi::class)
    override fun onCreateScreen(intent: Intent): Screen {
        return VideoPlayerScreen(carContext)
    }
}
