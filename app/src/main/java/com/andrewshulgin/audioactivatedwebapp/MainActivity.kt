package com.andrewshulgin.audioactivatedwebapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.PermissionChecker
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout


class MainActivity : Activity() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var sharedPref: SharedPreferences? = null
    private var sleepHandler: Handler? = null
    private var webView: WebView? = null

    companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 11111
        const val AUDIO_PERMISSION_REQUEST_CODE = 11112
    }

    private val serviceIntent by lazy {
        Intent(applicationContext, AudioService::class.java)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPref = getSharedPreferences(
            getString(R.string.preference_file_key), MODE_PRIVATE
        )

        with(sharedPref!!.edit()) {
            if (intent.hasExtra("url")) {
                putString("url", intent.getStringExtra("url"))
            } else if (!sharedPref!!.contains("url")) {
                putString("url", getString(R.string.default_url))
            }
            if (intent.hasExtra("wake_rms")) {
                putInt("wake_rms", intent.getIntExtra("wake_rms", 1000))
            } else if (!sharedPref!!.contains("wake_rms")) {
                putInt("wake_rms", 1000)
            }
            if (intent.hasExtra("wake_peak")) {
                putInt("wake_peak", intent.getIntExtra("wake_peak", -1))
            } else if (!sharedPref!!.contains("wake_peak")) {
                putInt("wake_peak", -1)
            }
            if (intent.hasExtra("debug_rms")) {
                putBoolean("debug_rms", intent.getBooleanExtra("debug_rms", false))
            } else if (!sharedPref!!.contains("debug_rms")) {
                putBoolean("debug_rms", false)
            }
            if (intent.hasExtra("wake_timeout")) {
                putLong("wake_timeout", intent.getLongExtra("wake_timeout", 10))
            } else if (!sharedPref!!.contains("wake_timeout")) {
                putLong("wake_timeout", 10)
            }
            apply()
        }

        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AudioActivatedWebApp::WifiLock"
        )
        wifiLock.acquire()

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                AUDIO_PERMISSION_REQUEST_CODE
            )
        } else {
            ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
        }
        val url = sharedPref!!.getString("url", "about:blank")
        val frameLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            webViewClient = StableWebViewClient(url!!)
            loadUrl(url)
        }

        frameLayout.addView(webView)
        setContentView(frameLayout)
        initialize()
    }

    private fun initialize() {
        sleepHandler?.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.hide(
                WindowInsets.Type.systemBars() or
                        WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.statusBars()
            )
        } else {
            window.decorView.apply {
                systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
            }
        }
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            setShowWhenLocked(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        if (wakeLock == null) {
            val powerManager = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AudioActivatedWebApp::WakeLock"
            )
        }
        wakeLock?.acquire(sharedPref!!.getLong("wake_timeout", 10) * 1000)
        webView?.evaluateJavascript("typeof play === 'function' && play()", null)

        sleepHandler = Handler(Looper.getMainLooper())
        sleepHandler?.postDelayed({
            if (wakeLock!!.isHeld) {
                wakeLock?.release()
            }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            webView?.evaluateJavascript("typeof pause === 'function' && pause()", null)
        }, sharedPref!!.getLong("wake_timeout", 10) * 1000)
    }

    override fun onResume() {
        super.onResume()
        initialize()
    }
}
