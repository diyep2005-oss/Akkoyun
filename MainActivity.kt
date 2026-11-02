package com.gokboru.browser

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.RecognizerIntent
import android.text.InputType
import android.text.TextUtils
import android.text.format.Formatter
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale
import java.io.ByteArrayInputStream

val NAVY = 0xFF0A1626.toInt(); val BLUE = 0xFF1E3A8A.toInt(); val GOLD = 0xFFF5B942.toInt()
val GREEN = 0xFF2E8B57.toInt(); val RED = 0xFFB91C1C.toInt(); val SOFT = 0xFFB8C7E0.toInt()
const val NIGHT_JS = "(function(){if(document.getElementById('gbnight'))return;var s=document.createElement('style');s.id='gbnight';s.innerHTML='html{filter:invert(1) hue-rotate(180deg)!important;background:#fff}img,video,canvas,picture{filter:invert(1) hue-rotate(180deg)!important}';document.head.appendChild(s)})()"
const val READ_JS = """(function(){var a=document.querySelector('article')||document.querySelector('main')||document.body;var ps=[].slice.call(a.querySelectorAll('h1,h2,h3,p,li,blockquote')).map(function(e){return '<'+e.tagName+'>'+e.innerHTML+'</'+e.tagName+'>'}).join('');var t=document.title;document.body.innerHTML='<div style="max-width:680px;margin:0 auto;padding:20px;font:19px/1.7 Georgia,serif"><h1>'+t+'</h1>'+ps+'</div>';document.body.style.cssText='background:#F5ECD7;color:#222';[].forEach.call(document.querySelectorAll('a'),function(x){x.style.color='#8a5a00'})})()"""
const val DESK_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

class Tab(val web: WebView, val incognito: Boolean) { var title = "Yeni Sekme"; var home = true }

class MainActivity : Activity() {
    private val tabs = mutableListOf<Tab>()
    private var cur: Tab? = null
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var urlBar: EditText
    private lateinit var bar: ProgressBar
    private lateinit var homeView: View
    private lateinit var tabBtn: TextView
    private lateinit var defUA: String
    private var custom: View? = null
    private var dlView: View? = null
    private var dlTab = 0
    @Volatile private var blocked = 0
    private var statsTv: TextView? = null
    private var fxTv: TextView? = null
    private var prayerTv: TextView? = null
    private var fxText = "💵 Kurlar yükleniyor..."
    private var prayerText = "🕌 Namaz vakitleri yükleniyor..."
    private var lastDash = 0L
    private var tts: TextToSpeech? = null
    private var weatherTv: TextView? = null
    private var weatherText = "🌤 Hava durumu yükleniyor..."
    private var locked = false
    private var adSwitch: Switch? = null
    private val handler = Handler(Looper.getMainLooper())
    private var customCb: WebChromeClient.CustomViewCallback? = null
    @Volatile private var adOn = true
    private val prefs by lazy { getSharedPreferences("gb", 0) }
    private val engines = listOf("Google" to "https://www.google.com/search?q=", "Bing" to "https://www.bing.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=", "Yandex" to "https://yandex.com.tr/search/?text=")
    private val ads = listOf("doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google", "adnxs.com",
        "taboola.com", "outbrain.com", "criteo.", "pubmatic.com", "rubiconproject.com", "adsrvr.org", "amazon-adsystem.com",
        "scorecardresearch.com", "moatads.com", "popads.net", "propellerads", "adform.net", "smartadserver.com")
    private val defaults = listOf("Google" to "google.com", "YouTube" to "youtube.com", "X" to "x.com", "Instagram" to "instagram.com",
        "Facebook" to "facebook.com", "Wikipedia" to "tr.wikipedia.org", "E-Devlet" to "turkiye.gov.tr")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun flag(k: String, d: Boolean = false) = prefs.getBoolean(k, d)
    private fun toggle(k: String, d: Boolean = false): Boolean { val n = !flag(k, d); prefs.edit().putBoolean(k, n).apply(); return n }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dlg() = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
    private fun round(fill: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(22).toFloat(); setStroke(dp(1), stroke) }
    private fun tv(t: String, sp: Float, c: Int, b: Boolean) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(c); gravity = Gravity.CENTER; setPadding(0, dp(3), 0, dp(3))
        if (b) { typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f } }
    private fun iconBtn(t: String, f: () -> Unit) = TextView(this).apply {
        text = t; textSize = 20f; setTextColor(GOLD); gravity = Gravity.CENTER; setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(44)) }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val old = getSharedPreferences("gb", 0).getString("crash", null)
        if (old != null) { getSharedPreferences("gb", 0).edit().remove("crash").commit(); showCrash(old); return }
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            getSharedPreferences("gb", 0).edit().putString("crash", android.util.Log.getStackTraceString(e)).commit()
            android.os.Process.killProcess(android.os.Process.myPid()) }
        try { init() } catch (e: Throwable) { showCrash(android.util.Log.getStackTraceString(e)) }
    }

    private fun showCrash(t: String) {
        val short = if (t.length > 3200) t.take(1200) + "\n...\n" + t.takeLast(2000) else t
        val tvx = TextView(this).apply { text = "HATA RAPORU (bu ekranın görüntüsünü gönder)\n\n" + short
            setTextColor(Color.WHITE); textSize = 11f; setTextIsSelectable(true); setPadding(dp(12), dp(40), dp(12), dp(12)) }
        val btn = Button(this).apply { text = "Yine de başlat"
            setOnClickListener { try { init() } catch (e: Throwable) { tvx.text = android.util.Log.getStackTraceString(e).takeLast(3000) } } }
        val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(NAVY); addView(tvx); addView(btn) }
        setContentView(ScrollView(this).apply { setBackgroundColor(NAVY); addView(lay) })
    }

    private fun init() {
        defUA = WebSettings.getDefaultUserAgent(this)
        adOn = flag("ad", true)
        blocked = prefs.getInt("blocked", 0)
        root = FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(NAVY) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(4), dp(6)) }
        urlBar = EditText(this).apply {
            hint = "Ara veya adres yaz"; setHintTextColor(0xFF8FA3C0.toInt()); setTextColor(Color.WHITE); textSize = 15f
            isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_GO; inputType = InputType.TYPE_TEXT_VARIATION_URI
            background = null; setPadding(dp(16), 0, dp(4), 0)
            setOnEditorActionListener { _, _, _ -> go(text.toString()); true }
            setOnFocusChangeListener { _, f -> if (f) post { selectAll() } } }
        val pill = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = round(0xFF12233D.toInt(), GOLD) }
        pill.addView(urlBar, LinearLayout.LayoutParams(0, -1, 1f)); pill.addView(iconBtn("🎤") { voice() }); pill.addView(iconBtn("📷") { qr() })
        top.addView(pill, LinearLayout.LayoutParams(0, dp(46), 1f))
        top.addView(iconBtn("🕶") { lockThen { newTab(true) } }.apply { background = round(0xFF12233D.toInt(), GOLD)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { leftMargin = dp(8) } })
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = ColorStateList.valueOf(GOLD); visibility = View.GONE }
        content = FrameLayout(this)
        val nav = LinearLayout(this).apply { setBackgroundColor(0xFF0D1E36.toInt()); setPadding(0, dp(6), 0, dp(6)) }
        fun item(ic: String, label: String, f: () -> Unit): TextView {
            val i = tv(ic, 20f, GOLD, false)
            nav.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setOnClickListener { f() }; addView(i); addView(tv(label, 10f, SOFT, false)) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            return i }
        item("⌂", "Ana Sayfa") { cur?.let { it.home = true; show(it) } }
        item("★", "Favoriler") { showList("Favoriler", "fav") }
        item("↺", "Geçmiş") { showList("Geçmiş", "hist") }
        tabBtn = item("1", "Sekmeler") { tabsDialog() }
        item("≡", "Menü") { menu() }
        col.addView(top); col.addView(bar, LinearLayout.LayoutParams(-1, dp(3)))
        col.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(View(this).apply { setBackgroundColor(0x66F5B942) }, LinearLayout.LayoutParams(-1, dp(1)))
        col.addView(nav)
        root.addView(col); setContentView(root)
        homeView = buildHome()
        newTab(false)
        intent?.data?.let { go(it.toString()) }
    }

    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); i?.data?.let { newTab(false); go(it.toString()) } }

    private fun cardBg() = GradientDrawable().apply { setColor(0xE60D1B30.toInt()); cornerRadius = dp(18).toFloat(); setStroke(dp(1), 0x88F5B942.toInt()) }
    private fun lpTop(m: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(m) }
    private fun lpW(w: Float) = LinearLayout.LayoutParams(0, -2, w)

    private fun buildHome(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ImageView(this).apply { setImageResource(resources.getIdentifier("hero", "drawable", packageName)); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(-1, -2))
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(4), dp(14), dp(24)) }
        col.addView(inner, LinearLayout.LayoutParams(-1, -2))

        val qrow = LinearLayout(this)
        fun qbtn(ic: String, l: String, f: () -> Unit) {
            val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = cardBg(); setPadding(0, dp(12), 0, dp(10)); setOnClickListener { f() } }
            b.addView(tv(ic, 21f, GOLD, false)); b.addView(tv(l, 10f, Color.WHITE, false))
            qrow.addView(b, lpW(1f).apply { leftMargin = dp(3); rightMargin = dp(3) }) }
        qbtn("★", "Favoriler") { showList("Favoriler", "fav") }
        qbtn("↺", "Geçmiş") { showList("Geçmiş", "hist") }
        qbtn("⬇", "İndirilenler") { showDownloads() }
        qbtn("🔍", "Lens") { lens() }
        qbtn("⚙", "Ayarlar") { menu() }
        inner.addView(qrow, lpTop(6))

        val sec = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = cardBg(); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val l1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        l1.addView(tv("🛡 Güvenli Tarama", 14f, GREEN, true).apply { gravity = Gravity.START; letterSpacing = 0f })
        l1.addView(tv("Zararlı site koruması açık ✓", 11f, SOFT, false).apply { gravity = Gravity.START })
        val l2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        l2.addView(tv("Reklam Engelleyici", 13f, Color.WHITE, true).apply { gravity = Gravity.START; letterSpacing = 0f })
        statsTv = tv("", 11f, SOFT, false).apply { gravity = Gravity.START }
        l2.addView(statsTv)
        adSwitch = Switch(this).apply { isChecked = adOn; thumbTintList = ColorStateList.valueOf(GOLD); trackTintList = ColorStateList.valueOf(BLUE)
            setOnCheckedChangeListener { _, c -> adOn = c; prefs.edit().putBoolean("ad", c).apply() } }
        sec.addView(l1, lpW(1f)); sec.addView(l2, lpW(1f)); sec.addView(adSwitch)
        inner.addView(sec, lpTop(10))

        val qa = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = cardBg(); setPadding(dp(12), dp(10), dp(12), dp(10)) }
        val qh = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        qh.addView(tv("🔥 Hızlı Erişim", 14f, Color.WHITE, true).apply { gravity = Gravity.START; letterSpacing = 0f }, lpW(1f))
        qh.addView(tv("✎ Düzenle", 12f, GOLD, false).apply { setOnClickListener { editShortcuts() } })
        qa.addView(qh)
        val grid = GridLayout(this).apply { columnCount = 5 }
        val sc = defaults.toMutableList(); val c = list("sc")
        for (i in 0 until c.length()) sc.add(c.getJSONObject(i).getString("t") to c.getJSONObject(i).getString("u"))
        val colors = listOf(BLUE, GREEN, RED, 0xFF7C3AED.toInt())
        val cw = (resources.displayMetrics.widthPixels - dp(28) - dp(24)) / 5
        sc.forEachIndexed { i, (n, u) ->
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(4)); setOnClickListener { go(u) } }
            cell.addView(tv(n.take(1), 20f, Color.WHITE, true).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(colors[i % 4]); setStroke(dp(1), GOLD) } }, LinearLayout.LayoutParams(dp(46), dp(46)))
            cell.addView(tv(n, 10f, SOFT, false).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            grid.addView(cell, GridLayout.LayoutParams().apply { width = cw }) }
        val add = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(4)); setOnClickListener { addShortcut() } }
        add.addView(tv("+", 24f, GOLD, true).apply { background = round(0x00000000, GOLD) }, LinearLayout.LayoutParams(dp(46), dp(46)))
        add.addView(tv("Ekle", 10f, SOFT, false))
        grid.addView(add, GridLayout.LayoutParams().apply { width = cw })
        qa.addView(grid)
        inner.addView(qa, lpTop(10))

        val wc = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = cardBg(); setPadding(dp(14), dp(10), dp(6), dp(10)) }
        val wl = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        weatherTv = tv(weatherText, 15f, Color.WHITE, true).apply { gravity = Gravity.START; letterSpacing = 0f }
        val now = java.util.Date(); val tr = Locale("tr", "TR")
        wl.addView(weatherTv)
        wl.addView(tv("📅 " + java.text.SimpleDateFormat("d MMMM yyyy", tr).format(now), 12f, SOFT, false).apply { gravity = Gravity.START })
        wl.addView(tv(java.text.SimpleDateFormat("EEEE", tr).format(now), 12f, SOFT, false).apply { gravity = Gravity.START })
        wc.addView(wl, lpW(1f))
        wc.addView(ImageView(this).apply { setImageResource(resources.getIdentifier("mosque", "drawable", packageName)); scaleType = ImageView.ScaleType.CENTER_CROP }, LinearLayout.LayoutParams(dp(130), dp(84)))
        inner.addView(wc, lpTop(10))

        val mk = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = cardBg(); setPadding(dp(14), dp(10), dp(14), dp(10)) }
        fxTv = tv(fxText, 14f, Color.WHITE, false).apply { gravity = Gravity.START }
        prayerTv = tv(prayerText, 12f, SOFT, false).apply { gravity = Gravity.START; setOnClickListener { cityDialog() } }
        mk.addView(fxTv); mk.addView(prayerTv)
        inner.addView(mk, lpTop(10))

        return ScrollView(this).apply { addView(col)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF07101F.toInt(), 0xFF0A1626.toInt(), 0xFF0E2140.toInt())) }
    }

    private fun editShortcuts() {
        val c = list("sc"); if (c.length() == 0) { toast("Eklediğin kısayol yok"); return }
        val names = Array(c.length()) { c.getJSONObject(it).getString("t") }
        dlg().setTitle("Silmek için dokun").setItems(names) { _, i ->
            val n = JSONArray(); for (j in 0 until c.length()) if (j != i) n.put(c.get(j))
            prefs.edit().putString("sc", n.toString()).apply(); homeView = buildHome(); cur?.let { if (it.home) show(it) } }.show()
    }

    private fun addShortcut() {
        val et = EditText(this).apply { hint = "ornek.com"; setTextColor(Color.WHITE); isSingleLine = true }
        dlg().setTitle("Kısayol ekle").setView(et).setPositiveButton("Ekle") { _, _ ->
            val u = et.text.toString().trim(); if (u.isNotEmpty()) {
                addList("sc", u.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/").replaceFirstChar { it.uppercase() }, u, false)
                homeView = buildHome(); cur?.let { if (it.home) show(it) } } }.show()
    }

    private fun newTab(inc: Boolean) { val t = Tab(makeWeb(inc), inc); tabs.add(t); show(t); updCount() }
    private fun updCount() { tabBtn.text = tabs.size.toString(); tabBtn.background = round(0x00000000, GOLD).apply { cornerRadius = dp(5).toFloat() }; tabBtn.setPadding(dp(8), 0, dp(8), 0) }
    private fun show(t: Tab) {
        cur = t; content.removeAllViews()
        content.addView(if (t.home) homeView else t.web)
        urlBar.setText(if (t.home) "" else t.web.url ?: ""); urlBar.setTextColor(if (t.incognito) GOLD else Color.WHITE)
        bar.visibility = View.GONE
        if (t.home) refreshDash()
    }
    private fun closeTab(t: Tab) { content.removeAllViews(); tabs.remove(t); t.web.destroy(); if (tabs.isEmpty()) newTab(false) else show(tabs.last()); updCount() }

    private fun applyPrefs(w: WebView) {
        w.settings.userAgentString = if (flag("desk")) DESK_UA else defUA
        w.settings.blockNetworkImage = flag("txt"); w.settings.textZoom = prefs.getInt("zoom", 100)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWeb(inc: Boolean) = WebView(this).apply {
        setBackgroundColor(NAVY)
        settings.apply { javaScriptEnabled = true; domStorageEnabled = true; loadWithOverviewMode = true; useWideViewPort = true
            setSupportZoom(true); safeBrowsingEnabled = true; builtInZoomControls = true; displayZoomControls = false; mediaPlaybackRequiresUserGesture = false
            if (inc) { cacheMode = WebSettings.LOAD_NO_CACHE; saveFormData = false } }
        applyPrefs(this)
        setOnLongClickListener {
            val h = hitTestResult
            if (h.type == WebView.HitTestResult.IMAGE_TYPE || h.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) { imageMenu(h.extra ?: ""); true } else false }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                if (adOn) { val h = r.url.host ?: return null
                    if (ads.any { h.contains(it) }) { blocked++; return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) } }
                return null }
            override fun onPageStarted(v: WebView, u: String, f: Bitmap?) { if (cur?.web === v && !urlBar.hasFocus()) urlBar.setText(u) }
            override fun onPageFinished(v: WebView, u: String) {
                if (flag("night")) v.evaluateJavascript(NIGHT_JS, null)
                tabs.firstOrNull { it.web === v }?.let { t -> t.title = v.title ?: u; if (!t.incognito) addList("hist", t.title, u) } }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) { if (cur?.web === v) { bar.progress = p; bar.visibility = if (p < 100) View.VISIBLE else View.GONE } }
            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                custom = view; customCb = cb; view.setBackgroundColor(Color.BLACK); root.addView(view)
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN) }
            override fun onHideCustomView() { hideCustom() }
        }
        setDownloadListener { url, ua, cd, mime, _ ->
            val fn = URLUtil.guessFileName(url, cd, mime)
            val r = DownloadManager.Request(Uri.parse(url)).setMimeType(mime).addRequestHeader("User-Agent", ua)
                .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "").setTitle(fn)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Gökbörü/" + fn)
            val dm = this@MainActivity.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            saveDlId(dm.enqueue(r)); toast("İndirme başladı (Menü > İndirmeler)") }
    }

    private fun dlIds(): List<Long> = (prefs.getString("dlids", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
    private fun saveDlId(id: Long) = prefs.edit().putString("dlids", (listOf(id) + dlIds()).take(100).joinToString(",")).apply()
    private fun closeDownloads() { dlView?.let { root.removeView(it) }; dlView = null }

    private fun showDownloads() {
        if (dlView != null) return
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(NAVY); isClickable = true }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        head.addView(iconBtn("←") { closeDownloads() })
        head.addView(tv("İndirmeler", 18f, Color.WHITE, true).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })
        val tabsRow = LinearLayout(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        var update: () -> Unit = {}
        val tabViews = listOf("Aktif", "Tamamlanan").mapIndexed { i, l -> tv(l, 14f, GOLD, true).apply { setOnClickListener { dlTab = i; update() } } }
        tabViews.forEach { tabsRow.addView(it, LinearLayout.LayoutParams(0, dp(44), 1f)) }
        update = {
            tabViews.forEachIndexed { i, t -> t.setTextColor(if (i == dlTab) GOLD else SOFT); t.setBackgroundColor(if (i == dlTab) 0xFF12233D.toInt() else 0x00000000) }
            fillDownloads(body) }
        page.addView(head); page.addView(tabsRow)
        page.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(page, FrameLayout.LayoutParams(-1, -1)); dlView = page
        val tick = object : Runnable { override fun run() { if (dlView !== page) return; update(); handler.postDelayed(this, 800) } }
        handler.post(tick)
    }

    private fun fillDownloads(body: LinearLayout) {
        body.removeAllViews()
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val ids = dlIds(); var n = 0
        val fmt = { b: Long -> Formatter.formatShortFileSize(this, b) }
        fun act(l: String, c: Int, f: () -> Unit) = tv(l, 13f, c, true).apply { setPadding(dp(14), dp(8), dp(14), dp(2)); setOnClickListener { f() } }
        if (ids.isNotEmpty()) dm.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val name = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Dosya"
                val got = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val tot = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val active = st != DownloadManager.STATUS_SUCCESSFUL && st != DownloadManager.STATUS_FAILED
                if (active != (dlTab == 0)) continue
                n++
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL
                    background = round(0xFF12233D.toInt(), BLUE).apply { cornerRadius = dp(14).toFloat() }; setPadding(dp(14), dp(12), dp(14), dp(10)) }
                row.addView(tv(name, 14f, Color.WHITE, false).apply { gravity = Gravity.START; maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE })
                val actions = LinearLayout(this).apply { gravity = Gravity.END }
                if (active) {
                    val pct = if (tot > 0) (got * 100 / tot).toInt() else 0
                    row.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = pct; progressTintList = ColorStateList.valueOf(GOLD) },
                        LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(8); bottomMargin = dp(6) })
                    row.addView(tv(if (tot > 0) "%$pct • ${fmt(got)} / ${fmt(tot)}" else "Bekleniyor...", 12f, SOFT, false).apply { gravity = Gravity.START })
                    actions.addView(act("✕ İptal", RED) { dm.remove(id) })
                } else if (st == DownloadManager.STATUS_SUCCESSFUL) {
                    row.addView(tv("Tamamlandı • ${fmt(tot)}", 12f, GREEN, false).apply { gravity = Gravity.START })
                    val open = {
                        try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(dm.getUriForDownloadedFile(id), dm.getMimeTypeForDownloadedFile(id)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch (e: Exception) { toast("Açacak uygulama bulunamadı") } }
                    row.setOnClickListener { open() }
                    actions.addView(act("Aç", GOLD) { open() }); actions.addView(act("🗑 Sil", RED) { dm.remove(id) })
                } else {
                    row.addView(tv("Başarısız", 12f, RED, false).apply { gravity = Gravity.START })
                    actions.addView(act("🗑 Kaldır", RED) { dm.remove(id) })
                }
                row.addView(actions)
                body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            } }
        if (n == 0) body.addView(tv(if (dlTab == 0) "Aktif indirme yok" else "Tamamlanan indirme yok", 14f, SOFT, false).apply { setPadding(0, dp(40), 0, 0) })
    }

    private fun fetch(u: String, cb: (String?) -> Unit) { Thread {
        val r = try { java.net.URL(u).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }.getInputStream().bufferedReader().use { it.readText() } } catch (e: Exception) { null }
        runOnUiThread { cb(r) } }.start() }

    private fun refreshDash() {
        statsTv?.text = "$blocked engellendi"; adSwitch?.isChecked = adOn
        fxTv?.text = fxText; prayerTv?.text = prayerText; weatherTv?.text = weatherText
        if (System.currentTimeMillis() - lastDash > 600000) { lastDash = System.currentTimeMillis(); loadFx(); loadPrayer(); loadWeather() }
    }
    private fun loadFx() = fetch("https://open.er-api.com/v6/latest/USD") { r ->
        try { val rt = JSONObject(r!!).getJSONObject("rates"); val usd = rt.getDouble("TRY"); val eur = usd / rt.getDouble("EUR")
            fxText = "💵 USD ₺%.2f     💶 EUR ₺%.2f".format(usd, eur); fxTv?.text = fxText } catch (e: Exception) { } }
    private fun loadPrayer() {
        val city = prefs.getString("city", "Istanbul") ?: "Istanbul"
        fetch("https://api.aladhan.com/v1/timingsByCity?city=" + Uri.encode(city) + "&country=Turkey&method=13") { r ->
            try { val t = JSONObject(r!!).getJSONObject("data").getJSONObject("timings")
                fun g(k: String) = t.getString(k).take(5)
                prayerText = "🕌 $city (değiştirmek için dokun)\nİmsak ${g("Fajr")} • Güneş ${g("Sunrise")} • Öğle ${g("Dhuhr")}\nİkindi ${g("Asr")} • Akşam ${g("Maghrib")} • Yatsı ${g("Isha")}"
            } catch (e: Exception) { prayerText = "🕌 Namaz vakitleri alınamadı" }
            prayerTv?.text = prayerText } }
    private fun cityDialog() {
        val cities = arrayOf("Istanbul", "Ankara", "Izmir", "Bursa", "Antalya", "Konya", "Adana", "Gaziantep", "Kayseri", "Trabzon", "Erzurum", "Diyarbakir", "Samsun", "Eskisehir")
        dlg().setTitle("Şehir seç").setItems(cities) { _, i -> prefs.edit().putString("city", cities[i]).apply(); prayerText = "🕌 Yükleniyor..."; prayerTv?.text = prayerText; loadPrayer(); loadWeather() }.show()
    }
    private fun speak(w: WebView) {
        if (tts?.isSpeaking == true) { tts?.stop(); toast("Durduruldu"); return }
        w.evaluateJavascript("document.body.innerText") { raw ->
            val txt = try { JSONTokener(raw).nextValue() as String } catch (e: Exception) { "" }
            if (txt.isBlank()) { toast("Okunacak metin yok"); return@evaluateJavascript }
            val read = { t: TextToSpeech -> t.language = Locale("tr", "TR")
                txt.chunked(3500).forEachIndexed { i, c -> t.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "gb$i") } }
            val ex = tts
            if (ex != null) read(ex) else { tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) tts?.let(read) } } }
    }
    override fun onPause() { prefs.edit().putInt("blocked", blocked).apply(); locked = flag("lock") && cur?.incognito == true; super.onPause() }
    override fun onResume() {
        super.onResume()
        if (locked) { content.visibility = View.INVISIBLE
            auth("Gizli sekmeyi aç", { content.visibility = View.VISIBLE; locked = false }, {
                locked = false; content.visibility = View.VISIBLE
                val n = tabs.firstOrNull { !it.incognito }; if (n != null) show(n) else newTab(false) }) }
    }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }

    private fun qr() {
        try { com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let { go(it) } }
            .addOnFailureListener { toast("QR tarayıcı açılamadı (Google Play hizmetleri gerekli)") }
        } catch (e: Exception) { toast("QR tarayıcı kullanılamıyor") }
    }

    private fun auth(title: String, ok: () -> Unit, fail: () -> Unit = {}) {
        try {
            val bp = android.hardware.biometrics.BiometricPrompt.Builder(this).setTitle(title)
                .setNegativeButton("Vazgeç", mainExecutor) { _, _ -> fail() }.build()
            bp.authenticate(CancellationSignal(), mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) { ok() }
                override fun onAuthenticationError(c: Int, m: CharSequence?) { if (c != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED) fail() } })
        } catch (e: Exception) { toast("Parmak izi/ekran kilidi kullanılamıyor"); fail() }
    }
    private fun lockThen(a: () -> Unit) { if (flag("lock")) auth("Gizli sekmeyi aç", a) else a() }
    private fun toggleLock() {
        if (flag("lock")) auth("Kilidi kapat", { toggle("lock"); toast("Gizli sekme kilidi kapalı") })
        else auth("Parmak izini doğrula", { toggle("lock"); toast("Gizli sekmeler artık parmak iziyle korunuyor") }, { toast("Doğrulama yapılamadı. Telefonda parmak izi tanımlı mı?") })
    }

    private fun loadWeather() {
        val city = prefs.getString("city", "Istanbul") ?: "Istanbul"
        fetch("https://geocoding-api.open-meteo.com/v1/search?name=" + Uri.encode(city) + "&count=1&countryCode=TR") { g ->
            try { val r0 = JSONObject(g!!).getJSONArray("results").getJSONObject(0)
                fetch("https://api.open-meteo.com/v1/forecast?latitude=" + r0.getDouble("latitude") + "&longitude=" + r0.getDouble("longitude") + "&current=temperature_2m,weather_code&timezone=auto") { w ->
                    try { val c = JSONObject(w!!).getJSONObject("current")
                        val d = when (c.getInt("weather_code")) { 0 -> "☀️ Açık"; 1, 2 -> "⛅ Parçalı bulutlu"; 3 -> "☁️ Bulutlu"; 45, 48 -> "🌫 Sisli"
                            in 51..57 -> "🌦 Çisenti"; in 61..67 -> "🌧 Yağmurlu"; in 71..77 -> "❄️ Karlı"; in 80..82 -> "🌧 Sağanak"
                            85, 86 -> "🌨 Kar sağanağı"; in 95..99 -> "⛈ Fırtına"; else -> "🌡" }
                        weatherText = d + "  " + Math.round(c.getDouble("temperature_2m")) + "°C • " + city; weatherTv?.text = weatherText
                    } catch (e: Exception) { } }
            } catch (e: Exception) { } }
    }

    private fun lens() {
        for (i in listOf(Intent(Intent.ACTION_VIEW, Uri.parse("google://lens")), packageManager.getLaunchIntentForPackage("com.google.ar.lens"))) {
            if (i != null) { try { startActivity(i); return } catch (e: Exception) { } } }
        toast("Google Lens uygulaması yok, web sürümü açılıyor"); newTab(false); go("https://lens.google.com/")
    }

    private fun imageMenu(u: String) {
        if (!u.startsWith("http")) { toast("Bu görsel Lens ile aranamıyor"); return }
        dlg().setItems(arrayOf("🔍 Google Lens ile ara", "🖼 Resmi yeni sekmede aç", "⬇ Resmi indir")) { _, i ->
            when (i) {
                0 -> { newTab(false); go("https://lens.google.com/uploadbyurl?url=" + Uri.encode(u)) }
                1 -> { newTab(false); go(u) }
                2 -> { val fn = URLUtil.guessFileName(u, null, null)
                    val r = DownloadManager.Request(Uri.parse(u)).setTitle(fn)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Gökbörü/" + fn)
                    saveDlId((getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)); toast("İndirme başladı") }
            } }.show()
    }

    private fun hideCustom() {
        custom?.let { root.removeView(it) }; custom = null; customCb?.onCustomViewHidden(); customCb = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    private fun go(q: String) {
        val t = cur ?: return; val s = q.trim(); if (s.isEmpty()) return
        val u = when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            !s.contains(" ") && s.contains(".") -> "https://$s"
            else -> engines[prefs.getInt("eng", 0)].second + Uri.encode(s) }
        t.home = false; show(t); urlBar.setText(u); urlBar.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(urlBar.windowToken, 0)
        t.web.loadUrl(u)
    }

    private fun list(k: String) = try { JSONArray(prefs.getString(k, "[]")) } catch (e: Exception) { JSONArray() }
    private fun addList(k: String, t: String, u: String, front: Boolean = true) {
        val old = list(k); val n = JSONArray(); if (front) n.put(JSONObject().put("t", t).put("u", u))
        for (i in 0 until old.length()) { if (n.length() >= 200) break; if (old.getJSONObject(i).getString("u") != u) n.put(old.get(i)) }
        if (!front) n.put(JSONObject().put("t", t).put("u", u))
        prefs.edit().putString(k, n.toString()).apply()
    }
    private fun showList(title: String, k: String) {
        val a = list(k); if (a.length() == 0) { toast("Liste boş"); return }
        val names = Array(a.length()) { a.getJSONObject(it).getString("t").ifEmpty { a.getJSONObject(it).getString("u") } }
        dlg().setTitle(title).setItems(names) { _, i -> go(a.getJSONObject(i).getString("u")) }
            .setNegativeButton("Temizle") { _, _ -> prefs.edit().remove(k).apply() }.show()
    }

    private fun tabsDialog() {
        val names = Array(tabs.size) { (if (tabs[it].incognito) "🕶 " else "") + (if (tabs[it].home) "Ana Sayfa" else tabs[it].title) }
        dlg().setTitle("Sekmeler (${tabs.size})").setItems(names) { _, i -> if (tabs[i].incognito) lockThen { show(tabs[i]) } else show(tabs[i]) }
            .setPositiveButton("＋ Yeni") { _, _ -> newTab(false) }.setNeutralButton("🕶 Gizli") { _, _ -> lockThen { newTab(true) } }
            .setNegativeButton("✕ Kapat") { _, _ -> cur?.let { closeTab(it) } }.show()
    }

    private fun menu() {
        fun o(l: String, k: String, d: Boolean = false) = "$l  ${if (flag(k, d)) "●" else "○"}"
        val items = arrayOf("＋ Yeni sekme", "🕶 Yeni gizli sekme", "★ Favorilere ekle", "⏱ Geçmiş", "⬇ İndirmeler",
            o("🛡 Reklam engelleyici", "ad", true), o("🌙 Gece modu", "night"), o("🖥 Masaüstü görünümü", "desk"), o("🖼 Sadece metin", "txt"),
            "🔎 Sayfada ara", "🔤 Yazı boyutu", "🌐 Sayfayı çevir (TR)", "📸 Ekran görüntüsü", "↗ Paylaş", "▶ İleri",
            "🔍 Arama motoru: ${engines[prefs.getInt("eng", 0)].first}", "🧹 Verileri temizle",
            "📖 Okuma modu", "🔊 Sayfayı sesli oku / durdur", "📄 PDF olarak kaydet", "⭐ Varsayılan tarayıcı yap", "💌 Arkadaşlarına öner",
            "📷 QR kod tara", o("🔒 Gizli sekme parmak izi kilidi", "lock"), "🔍 Google Lens (görselle ara)", "⟳ Yenile", "✕ Çıkış")
        dlg().setItems(items) { _, i ->
            val w = cur?.web
            when (i) {
                0 -> newTab(false)
                1 -> lockThen { newTab(true) }
                2 -> if (cur?.home == false) { addList("fav", cur!!.title, w!!.url ?: ""); toast("Favorilere eklendi") }
                3 -> showList("Geçmiş", "hist")
                4 -> showDownloads()
                5 -> { adOn = toggle("ad", true); toast(if (adOn) "Reklam engelleyici açık" else "Reklam engelleyici kapalı") }
                6 -> { val on = toggle("night"); tabs.forEach { it.web.evaluateJavascript(if (on) NIGHT_JS else "var e=document.getElementById('gbnight');if(e)e.remove()", null) } }
                7 -> { toggle("desk"); tabs.forEach { applyPrefs(it.web) }; w?.reload() }
                8 -> { toggle("txt"); tabs.forEach { applyPrefs(it.web) }; w?.reload() }
                9 -> { val et = EditText(this).apply { setTextColor(Color.WHITE); isSingleLine = true }
                    dlg().setTitle("Sayfada ara").setView(et).setPositiveButton("Ara") { _, _ -> w?.findAllAsync(et.text.toString()) }
                        .setNeutralButton("Sonraki") { _, _ -> w?.findNext(true) }.show() }
                10 -> dlg().setTitle("Yazı boyutu").setItems(arrayOf("Küçük", "Normal", "Büyük", "Çok büyük")) { _, j ->
                    prefs.edit().putInt("zoom", intArrayOf(85, 100, 125, 150)[j]).apply(); tabs.forEach { applyPrefs(it.web) } }.show()
                11 -> if (cur?.home == false) go("https://translate.google.com/translate?sl=auto&tl=tr&u=" + Uri.encode(w?.url ?: ""))
                12 -> if (w != null && cur?.home == false) shot(w)
                13 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, w?.url ?: ""), "Paylaş"))
                14 -> w?.goForward()
                15 -> dlg().setTitle("Arama motoru").setItems(engines.map { it.first }.toTypedArray()) { _, j -> prefs.edit().putInt("eng", j).apply() }.show()
                16 -> { prefs.edit().remove("hist").apply(); CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData()
                    tabs.forEach { it.web.clearCache(true); it.web.clearHistory() }; toast("Temizlendi") }
                17 -> if (w != null && cur?.home == false) w.evaluateJavascript(READ_JS, null)
                18 -> if (w != null && cur?.home == false) speak(w)
                19 -> if (w != null && cur?.home == false) (getSystemService(PRINT_SERVICE) as android.print.PrintManager).print("Gokboru_" + System.currentTimeMillis(), w.createPrintDocumentAdapter("Gokboru"), null)
                20 -> try { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (e: Exception) { toast("Ayarlar > Uygulamalar > Varsayılan uygulamalar") }
                21 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Gökbörü Tarayıcı'yı dene! 🐺 Reklam engelleyici, namaz vakti, okuma modu ve daha fazlası."), "Öner"))
                22 -> qr()
                23 -> toggleLock()
                24 -> lens()
                25 -> w?.reload()
                26 -> finish()
            } }.show()
    }

    private fun shot(w: WebView) {
        val b = Bitmap.createBitmap(w.width, w.height, Bitmap.Config.ARGB_8888); w.draw(Canvas(b))
        val v = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "gokboru_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Gokboru") }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)?.let { u ->
            contentResolver.openOutputStream(u)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; toast("Galeriye kaydedildi") }
    }

    private fun voice() {
        try { startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR"), 7) } catch (e: Exception) { toast("Sesli arama desteklenmiyor") }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        if (r == 7 && c == RESULT_OK) d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { go(it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val t = cur
        when {
            dlView != null -> closeDownloads()
            custom != null -> hideCustom()
            t != null && !t.home && t.web.canGoBack() -> t.web.goBack()
            t != null && !t.home -> { t.home = true; show(t) }
            tabs.size > 1 && t != null -> closeTab(t)
            else -> super.onBackPressed()
        }
    }
}
