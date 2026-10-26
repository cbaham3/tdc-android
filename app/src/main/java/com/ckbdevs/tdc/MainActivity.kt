package com.ckbdevs.tdc

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher

/**
 * The Dance Collective -- a native shell around the studio portal at
 * ckbdevs.com/tdc. Every screen is the live web portal, so website updates
 * show up in the app right away with no Play Store update.
 *
 *  - stays logged in between launches (cookies are kept)
 *  - ckbdevs.com/tdc pages stay in the app; Venmo, Cash App, phone, email
 *    and other sites open in their own apps
 *  - photo pickers for profile pictures and fundraiser banners
 *  - Excel/CSV exports save to Downloads/TDC (with Open / Share)
 *  - "Print / Save PDF" uses Android printing
 *  - logo animation while it loads, friendly offline screen with Try Again
 */
class MainActivity : Activity() {

    companion object {
        const val START_URL = "https://ckbdevs.com/tdc/"
        private val APP_HOSTS = setOf("ckbdevs.com", "www.ckbdevs.com")
        private const val APP_PATH = "/tdc"
        /** Lets the web pages know they're inside the app (hides their "Install App" button). */
        private const val UA_SUFFIX = " TDCApp/1.0 TDCAndroid/1.0"
        private const val PICK_FILE = 41
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private var splash: SplashView? = null
    private var offline: View? = null

    private var pageLoaded = false
    private var splashDone = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.INVISIBLE
        }
        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        setupInsets()
        setupWebView()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            pageLoaded = true; splashDone = true
            webView.visibility = View.VISIBLE
        } else {
            showSplash()
            webView.loadUrl(START_URL)
        }
        setupBack()
    }

    // ---------------- layout: keep the page clear of the status bar, nav bar and keyboard ----------------

    private fun setupInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)   // we place everything ourselves (edge to edge)
            root.setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                val lp = webView.layoutParams as FrameLayout.LayoutParams
                lp.setMargins(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                webView.layoutParams = lp
                offline?.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                splash?.bottomInset = bars.bottom
                insets
            }
        }
    }

    // ---------------- the web view ----------------

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            setSupportMultipleWindows(false)          // target="_blank" opens here (or outside, below)
            userAgentString = userAgentString + UA_SUFFIX
        }
        webView.addJavascriptInterface(Bridge(), "TDCAndroid")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (isAppUrl(url)) return false
                if (request.isForMainFrame) openOutside(url)
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(PAGE_HELPERS, null)
                if (offline == null) {
                    pageLoaded = true
                    maybeHideSplash()
                }
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                if (error.errorCode == ERROR_UNSUPPORTED_SCHEME) return
                showOffline(if (error.errorCode == ERROR_HOST_LOOKUP || error.errorCode == ERROR_CONNECT || error.errorCode == ERROR_TIMEOUT)
                    "Check your internet connection, then try again."
                else "The Dance Collective portal isn't loading right now. Please try again in a moment.")
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                recreate()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), PICK_FILE)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }

        // Normal (non-blob) downloads: hand them to the browser.
        webView.setDownloadListener { url, _, _, _, _ ->
            if (url.startsWith("http")) openOutside(Uri.parse(url))
        }
    }

    private fun isAppUrl(url: Uri): Boolean {
        val scheme = url.scheme?.lowercase() ?: return false
        if (scheme == "about" || scheme == "blob" || scheme == "data") return true
        if (scheme != "https" && scheme != "http") return false
        val host = url.host?.lowercase() ?: return false
        return host in APP_HOSTS && (url.path ?: "").startsWith(APP_PATH)
    }

    private fun openOutside(url: Uri) {
        try {
            val intent = if (url.scheme == "intent") Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME)
                         else Intent(Intent.ACTION_VIEW, url)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            startActivity(intent)
        } catch (e: Exception) {
            // e.g. a Venmo link with no Venmo app: try the web fallback, else say so
            val fallback = try { Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url") } catch (x: Exception) { null }
            if (fallback != null) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback))); return } catch (x: Exception) { }
            }
            Toast.makeText(this, "No app on this phone can open that link.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PICK_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // ---------------- splash + offline ----------------

    private fun showSplash() {
        val s = SplashView(this) {
            splashDone = true
            maybeHideSplash()
        }
        splash = s
        root.addView(s, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.requestApplyInsets()
        s.play()
    }

    private fun maybeHideSplash() {
        val s = splash ?: return
        if (!splashDone) return
        if (!pageLoaded) { s.startBreathing(); return }
        webView.visibility = View.VISIBLE
        s.animate().alpha(0f).setDuration(350).withEndAction {
            root.removeView(s)
            splash = null
        }.start()
    }

    private fun showOffline(message: String) {
        if (offline != null) return
        val dp = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            isClickable = true
        }
        val logo = ImageView(this).apply { setImageResource(R.drawable.splash_lines); alpha = 0.9f }
        box.addView(logo, LinearLayout.LayoutParams((130 * dp).toInt(), (130 * dp).toInt()))
        box.addView(TextView(this).apply {
            text = "Can't Connect"; setTextColor(Color.WHITE); textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(0, (14 * dp).toInt(), 0, (8 * dp).toInt())
        })
        box.addView(TextView(this).apply {
            text = message; setTextColor(Color.argb(180, 255, 255, 255)); textSize = 15f; gravity = Gravity.CENTER
            setPadding((36 * dp).toInt(), 0, (36 * dp).toInt(), (22 * dp).toInt())
        })
        box.addView(Button(this).apply {
            text = "TRY AGAIN"; setTextColor(Color.WHITE); textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(123, 63, 242)); cornerRadius = 999f
            }
            setPadding((34 * dp).toInt(), (12 * dp).toInt(), (34 * dp).toInt(), (12 * dp).toInt())
            setOnClickListener { retry() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        offline = box
        root.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        splash?.let { root.removeView(it); splash = null }
        root.requestApplyInsets()
    }

    private fun retry() {
        offline?.let { root.removeView(it) }
        offline = null
        if (!pageLoaded) {
            splashDone = false
            showSplash()
            webView.loadUrl(START_URL)
        } else {
            webView.reload()
        }
    }

    // ---------------- back button / gesture ----------------

    private fun goBack() {
        if (webView.canGoBack()) webView.goBack() else finish()
    }

    private fun setupBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goBack() }
        }
    }

    @Deprecated("Back on Android 10-12")
    override fun onBackPressed() {
        goBack()
    }

    // ---------------- lifecycle ----------------

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()   // stay logged in
    }

    override fun onDestroy() {
        root.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    // ---------------- JavaScript bridge: file saves + printing ----------------

    inner class Bridge {
        /** Called by the page helpers with an export (Excel/CSV) as base64. */
        @JavascriptInterface
        fun saveFile(base64: String, name: String, mime: String) {
            val bytes = try { Base64.decode(base64, Base64.DEFAULT) } catch (e: Exception) { return }
            val fileName = if (name.isNotBlank()) name.replace(Regex("[\\\\/:*?\"<>|]"), "_") else "download"
            // Excel files arrive as "application/octet-stream": use the type that matches the file name so "Open" finds Sheets/Excel.
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val byName = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: if (ext == "xlsx") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else null
            val type = if (mime.isBlank() || mime == "application/octet-stream") byName ?: "application/octet-stream" else mime
            runOnUiThread { saveToDownloads(bytes, fileName, type) }
        }

        @JavascriptInterface
        fun print(title: String) {
            runOnUiThread { printPage(title) }
        }
    }

    private fun saveToDownloads(bytes: ByteArray, name: String, mime: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TDC")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("no uri")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)

            AlertDialog.Builder(this)
                .setTitle("Saved")
                .setMessage("$name is in your Downloads folder (TDC).")
                .setPositiveButton("Open") { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this, "No app on this phone opens that file type. Try Share.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNeutralButton("Share") { _, _ ->
                    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(send, name))
                }
                .setNegativeButton("Done", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't save the file.", Toast.LENGTH_LONG).show()
        }
    }

    private fun printPage(title: String) {
        val pm = getSystemService(PRINT_SERVICE) as PrintManager
        val inner = webView.createPrintDocumentAdapter(title.ifBlank { "TDC" })
        // Tell the page when printing is done (it hides its print-only layout then).
        val adapter = object : PrintDocumentAdapter() {
            override fun onStart() = inner.onStart()
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: android.os.CancellationSignal?, cb: LayoutResultCallback?, extras: Bundle?) =
                inner.onLayout(old, new, cancel, cb, extras)
            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor?, cancel: android.os.CancellationSignal?, cb: WriteResultCallback?) =
                inner.onWrite(pages, dest, cancel, cb)
            override fun onFinish() {
                inner.onFinish()
                webView.evaluateJavascript("window.dispatchEvent(new Event('afterprint'))", null)
            }
        }
        pm.print(title.ifBlank { "TDC" }, adapter, PrintAttributes.Builder().build())
    }
}

/**
 * Injected after every page load:
 *  - exports made in the page (Excel/CSV "download" links to blob: or data:
 *    addresses, which Android's web view can't save on its own) go to the app
 *  - window.print() uses Android printing
 */
private const val PAGE_HELPERS = """
(function () {
  if (window.__tdcAndroid) return; window.__tdcAndroid = true;
  function send(blob, name) {
    var r = new FileReader();
    r.onload = function () { var s = String(r.result); TDCAndroid.saveFile(s.slice(s.indexOf(',') + 1), name || 'download', blob.type || 'application/octet-stream'); };
    r.readAsDataURL(blob);
  }
  function grab(a) {
    if (!a || !a.hasAttribute || !a.hasAttribute('download')) return false;
    var href = a.href || '';
    if (!/^(blob:|data:)/.test(href)) return false;
    fetch(href).then(function (r) { return r.blob(); }).then(function (b) { send(b, a.getAttribute('download')); });
    return true;
  }
  var click = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () { if (grab(this)) return; return click.apply(this, arguments); };
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
    if (a && grab(a)) e.preventDefault();
  }, true);
  window.print = function () { TDCAndroid.print(document.title || 'TDC'); };
})();
"""
