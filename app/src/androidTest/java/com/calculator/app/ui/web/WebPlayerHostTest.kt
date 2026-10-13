package com.calculator.app.ui.web

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calculator.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebPlayerHostTest {
    @Test
    fun fullscreenCustomViewReplacesAndRestoresTheWebView() {
        val fullscreenEvents = mutableListOf<Boolean>()
        var callbackNotified = false

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = WebPlayerHost(activity)
                try {
                    val webView = WebView(activity)
                    val fullscreenView = View(activity)
                    host.onFullscreenChanged = { fullscreenEvents.add(it) }
                    host.attachWebView(webView)

                    host.showCustomView(
                        fullscreenView,
                        object : WebChromeClient.CustomViewCallback {
                            override fun onCustomViewHidden() {
                                callbackNotified = true
                            }
                        }
                    )

                    assertTrue(host.isShowingCustomView)
                    assertEquals(View.GONE, webView.visibility)
                    assertEquals(host, fullscreenView.parent)

                    host.hideCustomView()

                    assertFalse(host.isShowingCustomView)
                    assertEquals(View.VISIBLE, webView.visibility)
                    assertEquals(host, webView.parent)
                    assertTrue(callbackNotified)
                    assertEquals(listOf(true, false), fullscreenEvents)
                } finally {
                    host.destroyWebView()
                }
            }
        }
    }
}
