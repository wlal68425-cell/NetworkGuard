package com.networkguard.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.net.wifi.WifiManager
import android.content.Context
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private lateinit var scanButton: Button
    private lateinit var subnetLabel: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val pool = Executors.newFixedThreadPool(48)
    private val trusted = linkedSetOf<String>()
    private val bg = Color.rgb(8, 14, 29)
    private val panel = Color.rgb(19, 31, 53)
    private val mint = Color.rgb(91, 232, 191)
    private val white = Color.rgb(237, 243, 255)
    private val muted = Color.rgb(174, 187, 209)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(20,24,20,14) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply { text="NETWORKGUARD"; textSize=27f; setTextColor(mint); gravity=Gravity.CENTER; letterSpacing=.08f }, matchWrap())
        content.addView(TextView(this).apply { text="مركز تحليل الشبكة المحلية\nإعداد: علي ولاء جاسم • علي كرار زيدان"; textSize=14f; setTextColor(muted); gravity=Gravity.CENTER; setPadding(0,8,0,18) }, matchWrap())
        status = TextView(this).apply { text="جاهز للفحص"; textSize=17f; setTextColor(white); setPadding(16,16,16,16); setBackgroundColor(panel) }
        content.addView(status, matchWrap())
        subnetLabel = TextView(this).apply { text="سيحاول التطبيق تحديد نطاق Wi‑Fi تلقائياً"; textSize=13f; setTextColor(muted); setPadding(2,10,2,8) }
        content.addView(subnetLabel, matchWrap())
        scanButton = Button(this).apply { text="بدء الفحص الذكي"; setOnClickListener { runScan() } }
        content.addView(scanButton, matchWrap())
        val summary = TextView(this).apply { text="الميزات\n• تحديد نطاق الشبكة من عنوان الهاتف عند توفره\n• فحص متوازٍ أسرع للأجهزة المستجيبة\n• تصنيف محلي للأجهزة الموثوقة\n• إعادة الفحص عند الطلب\n\nتنبيه أمني: الجهاز الذي لا يستجيب قد يكون موجوداً لكنه يحجب اختبارات الاتصال. الجهاز غير الموثوق ليس دليلاً على الاختراق. التطبيق لا يحظر الأجهزة ولا يجمع بيانات خارج شبكتك."; textSize=13f; setTextColor(muted); setPadding(0,16,0,18) }
        content.addView(summary, matchWrap())
        results = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        content.addView(results, matchWrap())
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
    }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    @Suppress("DEPRECATION")
    private fun detectPrefix(): Pair<String,String> {
        return try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val raw = wm.connectionInfo.ipAddress
            if (raw == 0) throw Exception("no wifi ip")
            val ip = (0..3).joinToString(".") { ((raw shr (it*8)) and 255).toString() }
            val parts = ip.split(".")
            Pair(parts.take(3).joinToString(".") + ".", ip)
        } catch (_: Exception) { Pair("192.168.1.", "غير متاح — نطاق احتياطي") }
    }

    private fun runScan() {
        scanButton.isEnabled=false; results.removeAllViews()
        val (prefix, ownIp) = detectPrefix()
        subnetLabel.text = "النطاق المستهدف: ${prefix}1–254   •   عنوان الهاتف: $ownIp"
        status.text="جارٍ فحص الأجهزة بالتوازي…"
        executor.execute {
            val futures = (1..254).map { n -> pool.submit<String?> {
                val ip = prefix + n
                try { if (ip != ownIp && InetAddress.getByName(ip).isReachable(650)) ip else null }
                catch (_: Exception) { null }
            } }
            val found = futures.mapNotNull { try { it.get(12, TimeUnit.SECONDS) } catch (_: Exception) { null } }.distinct().sortedBy { it.substringAfterLast('.').toIntOrNull() ?: 999 }
            runOnUiThread {
                scanButton.isEnabled=true
                status.text="اكتمل الفحص • تم رصد ${found.size} جهاز مستجيب"
                if (found.isEmpty()) addResult("لم ترد أجهزة على اختبار الاتصال. تحقق من Wi‑Fi أو إعدادات الراوتر؛ قد تمنع بعض الأجهزة هذا النوع من الفحص.")
                found.forEach { addDevice(it) }
            }
        }
    }
    private fun addResult(message: String) { results.addView(TextView(this).apply { text=message; textSize=14f; setTextColor(white); setPadding(14,14,14,14); setBackgroundColor(panel) }.also { it.layoutParams=matchWrap() }) }
    private fun addDevice(ip: String) {
        val row=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(14,12,14,12); setBackgroundColor(panel) }
        row.addView(TextView(this).apply { text="$ip  •  ${if (trusted.contains(ip)) "موثوق" else "غير مصنّف"}"; textSize=16f; setTextColor(if (trusted.contains(ip)) mint else white) }, matchWrap())
        row.addView(Button(this).apply { text=if (trusted.contains(ip)) "إزالة من الموثوق" else "وضع علامة موثوق"; setOnClickListener { if (trusted.contains(ip)) trusted.remove(ip) else trusted.add(ip); results.removeView(row); addDevice(ip) } }, matchWrap())
        val lp=matchWrap(); lp.setMargins(0,6,0,6); results.addView(row,lp)
    }
    override fun onDestroy() { super.onDestroy(); executor.shutdownNow(); pool.shutdownNow() }
}
