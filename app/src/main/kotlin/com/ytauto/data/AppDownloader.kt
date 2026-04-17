package com.ytauto.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * AppDownloader — OkHttp-implementatie van de NewPipe Downloader
 *
 * NewPipe Extractor heeft een [Downloader] nodig om HTTP-requests te doen.
 * Deze klasse implementeert dat met OkHttp, wat betrouwbaarder en sneller is
 * dan de standaard HttpURLConnection.
 *
 * Singleton-patroon: er hoeft maar één instantie te bestaan.
 */
class AppDownloader private constructor() : Downloader() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Voert een HTTP-request uit namens NewPipe Extractor.
     *
     * @param request Het Request-object van NewPipe met URL, method, headers, en body.
     * @return Een Response-object dat NewPipe kan verwerken.
     * @throws ReCaptchaException Als YouTube een CAPTCHA eist (HTTP 429).
     */
    override fun execute(request: Request): Response {
        val url = request.url()
        val httpMethod = request.httpMethod()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        // Bouw het OkHttp Request-object
        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            // Voeg een standaard User-Agent toe zodat YouTube ons niet blokkeert
            .header("User-Agent", USER_AGENT)

        // Voeg alle headers van NewPipe toe
        headers?.forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        // Stel de HTTP-methode in met optionele body
        val body = dataToSend?.toRequestBody()
        when (httpMethod) {
            "GET" -> requestBuilder.get()
            "HEAD" -> requestBuilder.head()
            "POST" -> requestBuilder.post(body ?: byteArrayOf().toRequestBody())
            else -> requestBuilder.method(httpMethod, body)
        }

        // Voer het request uit
        val response = client.newCall(requestBuilder.build()).execute()

        // YouTube stuurt HTTP 429 als het denkt dat je een bot bent
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("Rate limited by YouTube", url)
        }

        // Lees de response body en headers
        val responseBody = response.body?.string().orEmpty()
        val responseHeaders = response.headers.toMultimap()

        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            url
        )
    }

    companion object {
        // User-Agent die YouTube accepteert (desktop browser)
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"

        @Volatile
        private var instance: AppDownloader? = null

        /**
         * Geeft de singleton-instantie terug. Thread-safe.
         */
        fun getInstance(): AppDownloader {
            return instance ?: synchronized(this) {
                instance ?: AppDownloader().also { instance = it }
            }
        }
    }
}
