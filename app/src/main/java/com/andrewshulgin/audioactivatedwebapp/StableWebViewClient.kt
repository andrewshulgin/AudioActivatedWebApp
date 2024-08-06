package com.andrewshulgin.audioactivatedwebapp

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslCertificate
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
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


class StableWebViewClient(private val url: String) : WebViewClient() {
    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError) {
        var trusted = false
        when (error.primaryError) {
            SslError.SSL_UNTRUSTED -> {
                Log.d(
                    "SSL",
                    "The certificate authority ${error.certificate.issuedBy.dName} is not trusted"
                )
                trusted = validateSslCertificateChain(view?.context, error.certificate)
            }

            else -> Log.d("SSL", "SSL error ${error.primaryError}")
        }

        Log.d("SSL", "The certificate authority validation result is $trusted")
        if (trusted) {
            handler.proceed()
        } else {
            super.onReceivedSslError(view, handler, error)
        }
    }

    private fun validateSslCertificateChain(
        context: Context?,
        sslCertificate: SslCertificate
    ): Boolean {
        val issuerCert = when (sslCertificate.issuedBy.cName) {
            "ISRG Root X1" -> R.raw.isrg_root_x1
            "ISRG Root X2" -> R.raw.isrg_root_x2
            "Let's Encrypt Authority X3" -> R.raw.lets_encrypt_x3
            "E1" -> R.raw.lets_encrypt_e1
            "E5" -> R.raw.lets_encrypt_e5
            "E6" -> R.raw.lets_encrypt_e6
            "E7" -> R.raw.lets_encrypt_e7
            "E8" -> R.raw.lets_encrypt_e8
            "E9" -> R.raw.lets_encrypt_e9
            "R3" -> R.raw.lets_encrypt_r3
            "R10" -> R.raw.lets_encrypt_r10
            "R11" -> R.raw.lets_encrypt_r11
            "R12" -> R.raw.lets_encrypt_r12
            "R13" -> R.raw.lets_encrypt_r13
            "R14" -> R.raw.lets_encrypt_r14
            else -> null
        }
        if (issuerCert == null) {
            return false
        }
        try {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val issuerX509Certificate = certificateFactory
                .generateCertificate(
                    context?.resources!!.openRawResource(issuerCert)
                ) as X509Certificate
            val x509Certificate = certificateFactory
                .generateCertificate(
                    ByteArrayInputStream(
                        SslCertificate.saveState(sslCertificate)
                            .getByteArray("x509-certificate")
                    )
                ) as X509Certificate
            issuerX509Certificate.checkValidity()
            x509Certificate.verify(issuerX509Certificate.publicKey)
            return true
        } catch (_: Exception) {

        }
        return false
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
