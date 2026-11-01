package com.ms.whiteboard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PdfDoc(val id: String, val name: String, val added: Long)

// مكتبة الـ PDF: كل ملف بينسخ جوه التطبيق، وقائمة الملفات في index.json
object PdfStore {

    private fun dir(ctx: Context): File = File(ctx.filesDir, "pdfs").apply { mkdirs() }
    private fun index(ctx: Context): File = File(dir(ctx), "index.json")

    fun file(ctx: Context, id: String): File = File(dir(ctx), "$id.pdf")

    fun list(ctx: Context): List<PdfDoc> {
        val f = index(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PdfDoc(o.getString("id"), o.getString("name"), o.getLong("added"))
            }.sortedByDescending { it.added }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(ctx: Context, docs: List<PdfDoc>) {
        val arr = JSONArray()
        for (d in docs) {
            arr.put(JSONObject().put("id", d.id).put("name", d.name).put("added", d.added))
        }
        val tmp = File(dir(ctx), "index.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(index(ctx))
    }

    fun import(ctx: Context, uri: Uri): PdfDoc? {
        val name = displayName(ctx, uri)
        val id = System.currentTimeMillis().toString()
        val out = file(ctx, id)
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { src -> out.outputStream().use { dst -> src.copyTo(dst) } }
        } catch (e: Exception) {
            out.delete()
            return null
        }
        val doc = PdfDoc(id, name, System.currentTimeMillis())
        save(ctx, listOf(doc) + list(ctx))
        return doc
    }

    fun delete(ctx: Context, id: String) {
        file(ctx, id).delete()
        save(ctx, list(ctx).filter { it.id != id })
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) {
                        val n = c.getString(i)
                        if (!n.isNullOrBlank()) return n.removeSuffix(".pdf")
                    }
                }
            }
        } catch (e: Exception) {
            // نرجع للاسم الافتراضي
        }
        return "ملف PDF"
    }
}
