package com.ms.whiteboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// بيانات الإصدار الجديد من ملف update.json
data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

object UpdateChecker {

    // ملف معلومات الإصدار في المستودع (لازم يبقى على فرع main)
    private const val INFO_URL =
        "https://raw.githubusercontent.com/mmm14101995-sudo/MS-Whiteboard/main/update.json"

    private val main = Handler(Looper.getMainLooper())

    // الزرار اللي بيتضاف في الشاشة الرئيسية
    fun button(activity: Activity): TextView = TextView(activity).apply {
        text = "تحديث"
        textSize = 13f
        setTextColor(Theme.TEXT)
        gravity = Gravity.CENTER
        setPadding(Theme.dp(activity, 12f), Theme.dp(activity, 8f), Theme.dp(activity, 12f), Theme.dp(activity, 8f))
        background = Theme.box(activity, 0x1AFFFFFF, 12f)
        setOnClickListener { check(activity) }
    }

    private fun check(activity: Activity) {
        toast(activity, "بنشوف التحديثات...")
        Thread {
            val info = try {
                fetchInfo()
            } catch (e: Exception) {
                null
            }
            main.post {
                if (activity.isFinishing) return@post
                if (info == null) {
                    toast(activity, "مقدرناش نوصل للسيرفر، جرّب تاني")
                    return@post
                }
                if (info.versionCode.toLong() <= localVersion(activity)) {
                    toast(activity, "أنت على أحدث نسخة")
                } else {
                    confirm(activity, info)
                }
            }
        }.start()
    }

    private fun fetchInfo(): UpdateInfo {
        val conn = URL(INFO_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val j = JSONObject(text)
        return UpdateInfo(j.getInt("versionCode"), j.getString("versionName"), j.getString("apkUrl"))
    }

    @Suppress("DEPRECATION")
    private fun localVersion(c: Context): Long {
        val pi = c.packageManager.getPackageInfo(c.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }

    private fun confirm(activity: Activity, info: UpdateInfo) {
        AlertDialog.Builder(activity)
            .setTitle("فيه نسخة جديدة")
            .setMessage("الإصدار ${info.versionName} متاح. تنزّله دلوقتي؟")
            .setPositiveButton("نزّل") { _, _ -> download(activity, info) }
            .setNegativeButton("لاحقاً", null)
            .show()
    }

    private fun download(activity: Activity, info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setMessage("لازم تسمح للبرنامج يثبّت التحديثات، وبعدين اضغط تحديث تاني.")
                .setPositiveButton("افتح الإعدادات") { _, _ ->
                    val i = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(i)
                }
                .setNegativeButton("إلغاء", null)
                .show()
            return
        }

        toast(activity, "بنحمّل التحديث...")
        Thread {
            val file = File(activity.cacheDir, "update.apk")
            try {
                val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.inputStream.use { inp -> file.outputStream().use { out -> inp.copyTo(out) } }
                conn.disconnect()
                main.post {
                    if (!activity.isFinishing) install(activity, file)
                }
            } catch (e: Exception) {
                main.post { toast(activity, "فشل التحميل، جرّب تاني") }
            }
        }.start()
    }

    private fun install(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

    private fun toast(c: Context, msg: String) {
        Toast.makeText(c, msg, Toast.LENGTH_LONG).show()
    }
}
