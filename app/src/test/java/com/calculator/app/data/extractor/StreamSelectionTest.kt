package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSelectionTest {

    private val masterPlaylist = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
        360/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720
        720/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=5200000,RESOLUTION=1920x1080
        1080/index.m3u8
    """.trimIndent()

    @Test
    fun normalizeOnlyAcceptsHttps() {
        assertEquals("https://cdn.example.com/v/a.mp4", StreamSelection.normalize("https://cdn.example.com/v/a.mp4"))
        assertNull(StreamSelection.normalize("http://cdn.example.com/v/a.mp4"))
        assertNull(StreamSelection.normalize("has space.mp4"))
        assertNull(StreamSelection.normalize(null))
        assertEquals(
            "https://cdn.example.com/v/a.mp4",
            StreamSelection.normalize("//cdn.example.com/v/a.mp4")
        )
    }

    @Test
    fun normalizeResolvesRelativePathsAgainstThePage() {
        assertEquals(
            "https://site.example/media/abc.mp4",
            StreamSelection.normalize("/media/abc.mp4", "https://site.example/watch/1")
        )
        assertNull(StreamSelection.normalize("/media/abc.mp4"))
    }

    @Test
    fun mediaUrlFilterRejectsJunkAndAudio() {
        assertTrue(StreamSelection.isMediaUrl("https://cdn.example.com/hls/master.m3u8"))
        assertTrue(StreamSelection.isMediaUrl("https://cdn.example.com/v/clip.mp4"))
        assertFalse(StreamSelection.isMediaUrl("https://cdn.example.com/v/clip.mpd"))
        assertFalse(StreamSelection.isMediaUrl("https://cdn.example.com/img/thumb.jpg"))
        assertFalse(StreamSelection.isMediaUrl("https://cdn.example.com/audio/track.mp3"))
        assertFalse(StreamSelection.isMediaUrl("https://cdn.example.com/v/clip.srt"))
    }

    @Test
    fun rankingPrefersHlsAndPenalisesPreviews() {
        val ranked = StreamSelection.rankCandidates(
            listOf(
                "https://cdn.example.com/preview/clip.mp4",
                "https://cdn.example.com/hls/master.m3u8"
            )
        )
        assertEquals("https://cdn.example.com/hls/master.m3u8", ranked.first())
    }

    @Test
    fun rankingPutsHigherQualityFirst() {
        val ranked = StreamSelection.rankCandidates(
            listOf(
                "https://cdn.example.com/v/clip_360p.mp4",
                "https://cdn.example.com/v/clip_1080p.mp4"
            )
        )
        assertEquals("https://cdn.example.com/v/clip_1080p.mp4", ranked.first())
    }

    @Test
    fun candidatesAreFoundInHtmlScriptsAndVideoTags() {
        val html = """
            <html><body>
            <script>var src = "https://cdn.example.com/v/a.mp4";</script>
            <script>var rel = "/media/b.mp4";</script>
            <video src="https://cdn.example.com/v/c.mp4"></video>
            <img src="https://cdn.example.com/img/d.jpg">
            </body></html>
        """.trimIndent()

        val found = StreamSelection.extractCandidates(html, "https://site.example/watch/1")

        assertTrue(found.contains("https://cdn.example.com/v/a.mp4"))
        assertTrue(found.contains("https://site.example/media/b.mp4"))
        assertTrue(found.contains("https://cdn.example.com/v/c.mp4"))
        assertFalse(found.any { it.endsWith(".jpg") })
    }

    @Test
    fun masterPlaylistVariantsAreParsedAndBestIsChosen() {
        val variants = StreamSelection.parseVariants(masterPlaylist, "https://cdn.example.com/hls/master.m3u8")

        assertEquals(3, variants.size)
        assertTrue(variants.any { it.url == "https://cdn.example.com/hls/1080/index.m3u8" })

        val best = StreamSelection.bestVariant(masterPlaylist, "https://cdn.example.com/hls/master.m3u8")
        assertEquals(1080, best?.height)
        assertEquals(5_200_000, best?.bandwidth)
    }

    @Test
    fun bestVariantFallsBackToBandwidthWithoutResolution() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1200000
            a/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=900000
            b/index.m3u8
        """.trimIndent()

        val best = StreamSelection.bestVariant(playlist, "https://cdn.example.com/hls/master.m3u8")
        assertEquals("https://cdn.example.com/hls/a/index.m3u8", best?.url)
        assertNull(best?.height)
    }

    @Test
    fun playlistDetectionWorks() {
        assertTrue(StreamSelection.looksLikeHlsPlaylist("#EXTM3U\n#EXT-X-VERSION:3"))
        assertTrue(StreamSelection.looksLikeHlsPlaylist("  \uFEFF#EXTM3U"))
        assertFalse(StreamSelection.looksLikeHlsPlaylist("{\"json\":true}"))
        assertFalse(StreamSelection.looksLikeHlsPlaylist(null))
        assertTrue(StreamSelection.isMasterPlaylist(masterPlaylist))
        assertFalse(StreamSelection.isMasterPlaylist("#EXTM3U\n#EXTINF:5,"))
    }

    @Test
    fun removedPagesAreDetected() {
        assertTrue(StreamSelection.looksRemoved("<h2>This video is unavailable</h2>"))
        assertTrue(StreamSelection.looksRemoved("Video has been removed by the uploader"))
        assertFalse(StreamSelection.looksRemoved("<h1>Welcome</h1>"))
    }

    @Test
    fun playableContentTypesAreAccepted() {
        assertTrue(StreamSelection.isPlayableContentType("video/mp4", "https://cdn.example.com/v/a.mp4"))
        assertTrue(StreamSelection.isPlayableContentType(null, "https://cdn.example.com/v/a.mp4"))
        assertTrue(StreamSelection.isPlayableContentType("application/octet-stream", "https://cdn.example.com/v/a.mp4"))
        assertTrue(
            StreamSelection.isPlayableContentType(
                "application/vnd.apple.mpegurl",
                "https://cdn.example.com/hls/master.m3u8"
            )
        )
        assertFalse(StreamSelection.isPlayableContentType("text/html; charset=utf-8", "https://cdn.example.com/v/a.mp4"))
        assertFalse(StreamSelection.isPlayableContentType("application/json", "https://cdn.example.com/v/a.mp4"))
        assertFalse(StreamSelection.isPlayableContentType("image/jpeg", "https://cdn.example.com/v/a.mp4"))
    }

    @Test
    fun qualityIsReadFromUrlAndFormatted() {
        assertEquals(1080, StreamSelection.qualityFromUrl("https://cdn.example.com/v/clip_1080p.mp4"))
        assertEquals(2160, StreamSelection.qualityFromUrl("https://cdn.example.com/v/uhd/clip.mp4"))
        assertNull(StreamSelection.qualityFromUrl("https://cdn.example.com/v/clip.mp4"))

        assertEquals("4K", StreamSelection.qualityLabel(2160))
        assertEquals("1080p", StreamSelection.qualityLabel(1080))
        assertEquals("480p", StreamSelection.qualityLabel(480))
        assertNull(StreamSelection.qualityLabel(null))
    }
}
