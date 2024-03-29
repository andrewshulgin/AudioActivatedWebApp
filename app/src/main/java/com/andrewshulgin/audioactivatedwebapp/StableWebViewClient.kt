package com.andrewshulgin.audioactivatedwebapp

import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class StableWebViewClient(private val url: String) : WebViewClient() {
    override fun onReceivedSslError(
        view: WebView, handler: SslErrorHandler?, error: SslError
    ) {
        Log.d("SSL", error.toString())
        // TODO: solve Let's Encrypt issue on Android 6
        handler?.proceed()
        // Handler(Looper.getMainLooper()).postDelayed({ view.loadUrl(url) }, 10000)
        // super.onReceivedSslError(view, handler, error)
    }

    override fun onReceivedError(
        view: WebView?, req: WebResourceRequest, rerr: WebResourceError
    ) {
        Log.d("WEB", rerr.toString())
        if (req.isForMainFrame) {
            val offlinePage = view?.context?.resources?.openRawResource(R.raw.offline)
            if (offlinePage != null) {
                view.loadDataWithBaseURL(
                    "file:///android_asset/",
                    String(offlinePage.readBytes()),
                    "text/html",
                    "utf-8",
                    null
                )
            } else {
                super.onReceivedError(view, req, rerr)
            }
            Handler(Looper.getMainLooper()).postDelayed(
                { view?.loadUrl(url) }, 2000
            )
        }
    }

    override fun onReceivedHttpError(
        view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse?
    ) {
        Log.d("HTTP", errorResponse?.statusCode.toString())
        if (request.url.toString() == url) {
            Handler(Looper.getMainLooper()).postDelayed(
                { view.loadUrl(url) }, 10000
            )
        }
        super.onReceivedHttpError(view, request, errorResponse)
    }
}
