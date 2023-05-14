package com.andrewshulgin.audioactivatedwebapp

import android.net.http.SslError
import android.os.Handler
import android.os.Looper
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
        Handler(Looper.getMainLooper()).postDelayed({ view.loadUrl(url) }, 10000)
        super.onReceivedSslError(view, handler, error)
    }

    override fun onReceivedError(
        view: WebView?, req: WebResourceRequest, rerr: WebResourceError
    ) {
        super.onReceivedError(view, req, rerr)
        view?.loadData("", "text/plain", "utf-8")
        Handler(Looper.getMainLooper()).postDelayed(
            { view?.loadUrl(url) }, 2000
        )
    }

    override fun onReceivedHttpError(
        view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse?
    ) {
        if (request.url.toString() == url) {
            Handler(Looper.getMainLooper()).postDelayed(
                { view.loadUrl(url) }, 10000
            )
        }
        super.onReceivedHttpError(view, request, errorResponse)
    }
}