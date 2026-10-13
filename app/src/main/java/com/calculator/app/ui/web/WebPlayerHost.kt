package com.calculator.app.ui.web

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Hosts the WebView and the temporary custom view requested by HTML5 video players.
 * WebChromeClient's default implementation ignores these callbacks, so without this
 * container a site's fullscreen button appears to do nothing.
 */
internal class WebPlayerHost(context: Context) : FrameLayout(context) {
    private var webView: WebView? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    var onFullscreenChanged: ((Boolean) -> Unit)? = null

    val isShowingCustomView: Boolean
        get() = customView != null

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        clipChildren = false
        clipToPadding = false
    }

    fun attachWebView(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        webView = view
        addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    /** Shows WebView's video/plugin fullscreen view above the page. */
    fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }

        (view.parent as? ViewGroup)?.removeView(view)
        customView = view
        customViewCallback = callback
        webView?.visibility = View.GONE
        addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        view.visibility = View.VISIBLE
        view.requestFocus()
        view.bringToFront()
        onFullscreenChanged?.invoke(true)
    }

    /** Returns to the page and notifies Chromium that fullscreen has ended. */
    fun hideCustomView() {
        val view = customView ?: return
        removeView(view)
        customView = null
        webView?.visibility = View.VISIBLE

        val callback = customViewCallback
        customViewCallback = null
        onFullscreenChanged?.invoke(false)
        callback?.onCustomViewHidden()
    }

    /** Releases both views safely when the player screen leaves composition. */
    fun destroyWebView() {
        onFullscreenChanged = null
        hideCustomView()

        webView?.let { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
            removeView(view)
            view.destroy()
        }
        webView = null
        removeAllViews()
    }
}
