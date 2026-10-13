package com.calculator.app.data.extractor

import com.calculator.app.data.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Kör hela kedjan mot en riktig (lokal) HTTPS-server: hämtning → HTML → träfflista →
 * detaljsida → metadata. Det fångar fel som rena parsertester inte ser, till exempel
 * teckenkodning, omdirigeringar och vad som händer när sajten svarar med ett fel.
 *
 * Servern körs med ett självsignerat certifikat som bara testklienten litar på; appens
 * vanliga klient används aldrig mot den.
 */
class PipelineMockWebServerTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun startServer() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("evil.test")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()

        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()

        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .dns(Dns { host ->
                if (host == "evil.test") listOf(InetAddress.getByName("127.0.0.1"))
                else Dns.SYSTEM.lookup(host)
            })
            .build()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    /** Hämtar en sida genom appens egen hämtare, men med testklientens certifikat. */
    private fun fetchPage(path: String): String? = runBlocking {
        Fetch.page(server.url(path).toString(), client = client)
    }

    private val listingHtml = """
        <html><body>
          <div class="item">
            <a href="/video/blonde-in-the-kitchen-2024"><img data-src="/t/1.jpg" alt="Blonde in the kitchen"></a>
            <span class="duration">12:34</span>
          </div>
          <div class="item">
            <a href="/video/massage-amateur-checkup">Massage amateur checkup 720p</a>
            <span class="duration">1:05:00</span>
          </div>
          <footer><a href="/tag/blonde">Blonde</a></footer>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><head>
          <meta property="og:title" content="Blonde in the kitchen" />
          <meta property="og:description" content="Ett kök, en mixer och alldeles för lite kläder." />
          <meta property="og:video:height" content="1080" />
          <meta property="og:video:duration" content="754" />
          <meta name="keywords" content="blonde, kitchen, amateur" />
        </head><body>
          <video poster="/t/1.jpg"></video>
        </body></html>
    """.trimIndent()

    @Test
    fun signedMediaQueriesAndUserAgentArePreserved() {
        server.enqueue(MockResponse().setBody("#EXTM3U\n"))
        val mediaClient = client.newBuilder().addInterceptor(com.calculator.app.network.PrivacyInterceptor()).build()
        val request = okhttp3.Request.Builder().url(server.url("/master.m3u8?source=token&ref=signed"))
            .header("User-Agent", "Player test agent")
            .tag(com.calculator.app.network.MediaRequest::class.java, com.calculator.app.network.MediaRequest)
            .build()
        mediaClient.newCall(request).execute().use { assertTrue(it.isSuccessful) }
        val recorded = server.takeRequest()
        assertEquals("/master.m3u8?source=token&ref=signed", recorded.path)
        assertEquals("Player test agent", recorded.getHeader("User-Agent"))
    }

    @Test
    fun oversizedChunkedBodyIsRejectedWithoutTruncatingItIntoValidHtml() {
        server.enqueue(MockResponse().setChunkedBody("x".repeat(6000001), 8192))
        assertNull(runBlocking { Fetch.page(server.url("/large").toString(), client = client, attempts = 1) })
    }

    @Test
    fun cancellingAnInFlightFetchCancelsTheUnderlyingHttpCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch(Dispatchers.IO) {
            Fetch.page(server.url("/slow").toString(), client = client, attempts = 1)
        }

        assertNotNull("server should observe the request", server.takeRequest(2, TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertTrue("fetch must stop promptly when its coroutine is cancelled", job.isCancelled)
        assertEquals("cancellation must not start a retry", 1, server.requestCount)
    }

    @Test
    fun listingPageOverHttpsBecomesResults() {
        server.enqueue(MockResponse().setBody(listingHtml).setHeader("Content-Type", "text/html; charset=utf-8"))

        val html = fetchPage("/search?q=blonde")
        assertNotNull("hämtningen ska lyckas över HTTPS", html)

        val config = ListingConfig(
            siteName = "mysite",
            baseUrl = baseUrl(),
            hrefPattern = Regex("/video/([A-Za-z0-9-]+)")
        )
        val results = ExtractorSupport.parseListings(html!!, config)

        assertEquals(2, results.size)
        val first = results.first { it.id.endsWith("blonde-in-the-kitchen-2024") }
        assertEquals("Blonde in the kitchen", first.title)
        assertEquals(754, first.durationSeconds)
        assertNull("listningen har ingen kvalitetshint för den första träffen", first.qualityLabel)
        val second = results.first { it.id.endsWith("massage-amateur-checkup") }
        assertEquals("720p", second.qualityLabel)
        assertEquals(3900, second.durationSeconds)
        assertTrue("tumnageln ska bli en fullständig HTTPS-adress", first.thumbnailUrl!!.startsWith("https://"))
        assertTrue("tagglänken ska aldrig bli en träff", results.none { it.detailUrl.contains("/tag/") })

        val request = server.takeRequest()
        assertEquals("/search?q=blonde", request.path)
    }

    @Test
    fun detailPageOverHttpsBecomesMetadata() {
        server.enqueue(MockResponse().setBody(detailHtml).setHeader("Content-Type", "text/html; charset=utf-8"))

        val url = server.url("/video/blonde-in-the-kitchen-2024").toString()
        val metadata: VideoMetadata? = runBlocking { MetadataExtractor.fetch(url, "mysite", client = client) }

        assertNotNull(metadata)
        assertEquals("Blonde in the kitchen", metadata!!.title)
        assertEquals(754, metadata.durationSeconds)
        assertEquals("1080p", metadata.qualityLabel)
        assertTrue(metadata.tags.any { it.equals("kitchen", ignoreCase = true) })
        assertTrue(metadata.hasRichData)
        assertTrue(!metadata.pageMissing)
    }

    @Test
    fun aRemovedPageIsMarkedAsSuchInsteadOfLookingPlayable() {
        server.enqueue(
            MockResponse()
                .setBody("<html><body><h1>Video has been removed</h1><p>This video was deleted.</p></body></html>")
                .setHeader("Content-Type", "text/html; charset=utf-8")
        )

        val url = server.url("/video/borta").toString()
        val metadata = runBlocking { MetadataExtractor.fetch(url, "mysite", client = client) }

        assertNotNull(metadata)
        assertTrue("borttagen sida ska flaggas", metadata!!.pageMissing)
    }

    @Test
    fun serverErrorsGiveNoResultsInsteadOfCrashing() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertNull("ett serverfel ska bli null, inte ett undantag", fetchPage("/search?q=blonde"))
    }

    @Test
    fun aTemporaryServerErrorIsRetriedOnceAndThenSucceeds() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(listingHtml).setHeader("Content-Type", "text/html; charset=utf-8"))

        val html = fetchPage("/search?q=blonde")

        assertNotNull("omförsöket ska rädda hämtningen", html)
        assertTrue(html!!.contains("blonde-in-the-kitchen-2024"))
        assertEquals("första försöket + omförsöket", 2, server.requestCount)
    }

    @Test
    fun aRemovedListingIsNotFetchedTwice() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody(listingHtml).setHeader("Content-Type", "text/html; charset=utf-8"))

        assertNull(fetchPage("/search?q=blonde"))
        assertEquals("en 404 ska inte göras om", 1, server.requestCount)
    }

    @Test
    fun cleartextIsNeverFetched() {
        val cleartext = "http://localhost:${server.port}/search?q=blonde"

        val html = runBlocking { Fetch.page(cleartext, client = client) }

        assertNull("appen ska aldrig hämta över klartext", html)
        assertEquals("ingen förfrågan ska ha lämnat appen", 0, server.requestCount)
    }

    @Test
    fun redirectsAreFollowedToTheRealPage() {
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/search/blonde"))
        server.enqueue(MockResponse().setBody(listingHtml).setHeader("Content-Type", "text/html; charset=utf-8"))

        val html = fetchPage("/search?q=blonde")

        assertNotNull("omdirigeringen ska följas", html)
        assertTrue(html!!.contains("blonde-in-the-kitchen-2024"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun crossSiteRedirectIsRejectedBeforeTheRedirectTargetIsRequested() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://evil.test:${server.port}/outside")
        )

        val html = runBlocking {
            Fetch.page(server.url("/redirect-off-site").toString(), client = client, attempts = 1)
        }

        assertNull("a detail-page fetch must stay on its source host", html)
        assertEquals("only the original source URL may reach the server", 1, server.requestCount)
    }

    @Test
    fun latin1PagesAreStillReadable() {
        val body = """<div class="item"><a href="/video/blödchen-2024">Blödchen</a></div>"""
        server.enqueue(
            MockResponse()
                .setBody(okio.Buffer().write(body.toByteArray(Charsets.ISO_8859_1)))
                .setHeader("Content-Type", "text/html; charset=iso-8859-1")
        )

        val html = fetchPage("/search")

        assertNotNull(html)
        assertTrue("texten ska avkodas enligt sidans teckenkodning", html!!.contains("Blödchen"))
        assertEquals("/search", server.takeRequest().path)
    }
}
