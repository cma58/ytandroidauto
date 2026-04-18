package com.ytauto.remote

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

class PartyServer(private val onLinkReceived: (String) -> Unit) {
    private var server: NettyApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, port = 8080) {
            routing {
                get("/") {
                    call.respondText(
                        "<html><body><h1>YTAuto Party Mode</h1>" +
                        "<p>Stuur een YouTube of Spotify link naar de auto:</p>" +
                        "<form action='/add' method='get'>" +
                        "<input type='text' name='url' style='width:80%;' placeholder='YouTube/Spotify Link'>" +
                        "<button type='submit' style='padding:10px 20px; background:#f00; color:#fff; border:none; border-radius:5px;'>Speel af</button>" +
                        "</form>" +
                        "<h2>Huidige Opties</h2>" +
                        "<ul>" +
                        "<li>Plak een YouTube URL (Video of Music)</li>" +
                        "<li>Plak een Spotify Track URL</li>" +
                        "</ul>" +
                        "</body></html>",
                        ContentType.Text.Html
                    )
                }
                get("/add") {
                    val url = call.parameters["url"]
                    if (!url.isNullOrBlank()) {
                        onLinkReceived(url)
                        call.respondText("Track toegevoegd! Veel plezier.")
                    } else {
                        call.respondText("Geen geldige URL ontvangen.")
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}
