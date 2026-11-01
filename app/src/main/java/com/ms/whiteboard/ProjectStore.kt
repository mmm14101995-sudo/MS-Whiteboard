package com.ms.whiteboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Project(val id: String, var name: String, var updated: Long)

object ProjectStore {

    private fun dir(ctx: Context): File = File(ctx.filesDir, "projects").also { it.mkdirs() }
    private fun indexFile(ctx: Context): File = File(dir(ctx), "index.json")

    fun boardFile(ctx: Context, id: String): File = File(dir(ctx), "board_$id.json")

    fun list(ctx: Context): MutableList<Project> {
        val out = mutableListOf<Project>()
        try {
            val f = indexFile(ctx)
            if (f.isFile) {
                val arr = JSONArray(f.readText(Charsets.UTF_8))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(Project(o.getString("id"), o.getString("name"), o.getLong("up")))
                }
            }
        } catch (e: Exception) {
            // لو فهرس المشاريع بايظ نرجع قايمة فاضية بدل ما التطبيق يقع
        }
        return out.sortedByDescending { it.updated }.toMutableList()
    }

    private fun saveList(ctx: Context, projects: List<Project>) {
        val arr = JSONArray()
        for (p in projects) {
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("up", p.updated))
        }
        val tmp = File(dir(ctx), "index.tmp")
        tmp.writeText(arr.toString(), Charsets.UTF_8)
        val target = indexFile(ctx)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun create(ctx: Context, name: String): Project {
        val now = System.currentTimeMillis()
        val p = Project(now.toString(), name, now)
        val all = list(ctx)
        all.add(p)
        saveList(ctx, all)
        return p
    }

    // بنحدّث وقت آخر تعديل لما نخرج من المشروع، عشان يطلع أول القايمة
    fun touch(ctx: Context, id: String) {
        val all = list(ctx)
        val p = all.find { it.id == id } ?: return
        p.updated = System.currentTimeMillis()
        saveList(ctx, all)
    }

    // لو ده أول تشغيل بعد التحديث، السبورة القديمة تتحول لأول مشروع
    fun migrateIfNeeded(ctx: Context) {
        if (list(ctx).isNotEmpty()) return
        val old = File(ctx.filesDir, "board.json")
        if (old.isFile) {
            val p = Project("first", "سبورتي الأولى", System.currentTimeMillis())
            old.renameTo(boardFile(ctx, p.id))
            saveList(ctx, listOf(p))
        }
    }
}
