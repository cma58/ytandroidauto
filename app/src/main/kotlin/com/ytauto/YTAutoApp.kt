package com.ytauto

import android.app.Application
import com.ytauto.data.AppDownloader
import org.schabi.newpipe.extractor.NewPipe

/**
 * YTAutoApp — Application-klasse
 *
 * Initialiseert de NewPipe Extractor bij het starten van de app.
 * Dit moet één keer gebeuren voordat er YouTube-data opgehaald kan worden.
 * De Application-klasse is de vroegste levenscyclus-hook hiervoor.
 */
class YTAutoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialiseer NewPipe Extractor met onze OkHttp-gebaseerde Downloader.
        // Dit stelt de HTTP-client in die NewPipe gebruikt voor alle requests.
        NewPipe.init(AppDownloader.getInstance())
    }
}
