package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSelectionTest {

    @Test
    fun normalizesProtocolRelativeAndRelativeUrls() {
        assertEquals("https://cdn.example.com/a.mp4", StreamSelection.normalize("//cdn.example.com/a.mp4"))
        assertEquals(
            "https://cdn.example.com/a.mp4",
            StreamSelection.normalize("https:\\/\\/cdn.example.com\\/a.mp4")
        )
        assertEquals(
            "https://site.com/media/a.mp4",
            StreamSelection.normalize("/media/a.mp4", "https://site.com/page/watch")
        )
        assertEquals(
            "https://site.com/dir/media/a.mp4",
            StreamSelection.normalize("media/a.mp4", "https://site.com/dir/page.html")
        )
    }

    @Test
    fun rejectsCleartextAndNonHttpValues() {
        assertNull(StreamSelection.normalize("http://cdn.example.com/a.mp4"))
        assertNull(StreamSelection.normalize(""))
        assertNull(StreamSelection.normalize(null))
        assertNull(StreamSelection.normalize("javascript:void(0)"))
    }

    @Test
    fun onlyMediaUrlsAreAccepted() {
        assertTrue(StreamSelection.isMediaUrl("https://cdn.example.com/a.mp4"))
        assertTrue(StreamSelection.isMediaUrl("https://cdn.example.com/master.m3u8?token=1"))
        assertFalse(StreamSelection.isMediaUrl("https://cdn.example.com/poster.jpg"))
        assertFalse(StreamSelection.isMediaUrl("https://cdn.example.com/segment.ts"))
        assertFalse(StreamSelection.isMediaUrl("https://cdn.example.com/manifest.mpd"))
        assertFalse(StreamSelection.isMediaUrl("https://cdn.example.com/audio.mp3"))
        assertFalse(StreamSelection.isMediaUrl("https://cdn.example.com/track.vtt"))
    }

    @Test
    fun higherQualityUrlsScoreHigher() {
        val hd = StreamSelection.score("https://cdn.example.com/video_1080p.mp4")
        val sd = StreamSelection.score("https://cdn.example.com/video_480p.mp4")
        val hls = StreamSelection.score("https://cdn.example.com/master.m3u8")
        val preview = StreamSelection.score("https://cdn.example.com/preview_1080p.mp4")

        assertTrue(hd > sd)
        assertTrue(hls > sd)
        assertTrue(preview < sd)
    }

    @Test
    fun candidatesAreRankedWithBestFirst() {
        val ranked = StreamSelection.rankCandidates(
            listOf(
                "https://cdn.example.com/preview.mp4",
                "https://cdn.example.com/video_720p.mp4",
                "https://cdn.example.com/master.m3u8",
                "https://cdn.example.com/poster.jpg"
            )
        )

        assertEquals("https://cdn.example.com/master.m3u8", ranked.first())
        assertFalse(ranked.contains("https://cdn.example.com/poster.jpg"))
        assertTrue(ranked.size == 3)
    }

    @Test
    fun extractsCandidatesFromHtmlAndJson() {
        val html = """
            <html><body>
            <video><source src="//cdn.example.com/stream_1080p.mp4" type="video/mp4"></video>
            <script>
              var player = {"file":"https:\/\/cdn.example.com\/hls\/master.m3u8","image":"poster.jpg"};
              var relative = {"src":"/media/relative_720p.mp4"};
            </script>
            </body></html>
        """.trimIndent()

        val candidates = StreamSelection.extractCandidates(html, "https://site.com/watch/123")

        assertTrue(candidates.contains("https://cdn.example.com/stream_1080p.mp4"))
        assertTrue(candidates.contains("https://cdn.example.com/hls/master.m3u8"))
        assertTrue(candidates.contains("https://site.com/media/relative_720p.mp4"))
    }

    @Test
    fun parsesHlsMasterPlaylistAndPicksTheBestVariant() {
        val manifest = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480,CODECS="avc1.4d401f,mp4a.40.2"
            480/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4500000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            https://cdn.example.com/hls/1080/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2100000,RESOLUTION=1280x720
            720/index.m3u8
        """.trimIndent()

        val variants = StreamSelection.parseVariants(manifest, "https://cdn.example.com/hls/master.m3u8")

        assertEquals(3, variants.size)
        assertEquals(1200000, variants[0].bandwidth)
        assertEquals(480, variants[0].height)
        assertEquals("https://cdn.example.com/hls/480/index.m3u8", variants[0].url)

        val best = StreamSelection.bestVariant(manifest, "https://cdn.example.com/hls/master.m3u8")
        assertNotNull(best)
        assertEquals("https://cdn.example.com/hls/1080/index.m3u8", best?.url)
        assertEquals(1080, best?.height)
        assertTrue(StreamSelection.isMasterPlaylist(manifest))
    }

    @Test
    fun detectsPlaylistsAndRemovedPages() {
        assertTrue(StreamSelection.looksLikeHlsPlaylist("\uFEFF#EXTM3U\n#EXT-X-TARGETDURATION:10"))
        assertFalse(StreamSelection.looksLikeHlsPlaylist("<html>nope</html>"))
        assertTrue(StreamSelection.looksRemoved("<h1>This video has been removed</h1>"))
        assertFalse(StreamSelection.looksRemoved("<h1>Blonde in the kitchen</h1>"))
    }

    @Test
    fun qualityLabels() {
        assertEquals("4K", StreamSelection.qualityLabel(2160))
        assertEquals("1080p", StreamSelection.qualityLabel(1080))
        assertEquals("720p", StreamSelection.qualityLabel(720))
        assertNull(StreamSelection.qualityLabel(null))
        assertEquals(1080, StreamSelection.qualityFromUrl("https://cdn.example.com/v_1080p.mp4"))
        assertEquals(2160, StreamSelection.qualityFromUrl("https://cdn.example.com/4k/master.m3u8"))
    }

    @Test
    fun contentTypeRulesRejectHtmlErrorPages() {
        assertFalse(StreamSelection.isPlayableContentType("text/html; charset=utf-8", "https://cdn/x.mp4"))
        assertFalse(StreamSelection.isPlayableContentType("application/json", "https://cdn/x.mp4"))
        assertTrue(StreamSelection.isPlayableContentType("video/mp4", "https://cdn/x.mp4"))
        assertTrue(StreamSelection.isPlayableContentType("application/octet-stream", "https://cdn/x.mp4"))
        assertTrue(StreamSelection.isPlayableContentType("", "https://cdn/x.mp4"))
        assertTrue(StreamSelection.isPlayableContentType("application/vnd.apple.mpegurl", "https://cdn/x.m3u8"))
    }
}
