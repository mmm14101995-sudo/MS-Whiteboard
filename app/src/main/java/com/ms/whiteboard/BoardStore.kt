package com.ms.whiteboard

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// خط واحد: كل نقطة = (x, y, سُمك)، وده اللي بيتحفظ
class Stroke(val pen: Pen, val color: Int, val erase: Boolean) {
    var data = FloatArray(96)
    var size = 0

    fun add(x: Float, y: Float, w: Float) {
        if (size + 3 > data.size) data = data.copyOf(data.size * 2)
        data[size] = x
        data[size + 1] = y
        data[size + 2] = w
        size += 3
    }
}

class BoardData(val w: Int, val h: Int, val strokes: List<Stroke>)

class BoardStore(private val file: File) {

    companion object {
        fun toJson(strokes: List<Stroke>, w: Int, h: Int): String {
            val arr = JSONArray()
            for (s in strokes) {
                val pts = JSONArray()
                for (i in 0 until s.size) pts.put(s.data[i].toDouble())
                arr.put(
                    JSONObject()
                        .put("p", s.pen.name)
                        .put("c", s.color)
                        .put("e", s.erase)
                        .put("d", pts)
                )
            }
            return JSONObject().put("v", 1).put("w", w).put("h", h).put("s", arr).toString()
        }

        fun fromJson(text: String): BoardData? = try {
            val o = JSONObject(text)
            val arr = o.getJSONArray("s")
            val list = ArrayList<Stroke>(arr.length())
            for (i in 0 until arr.length()) {
                val so = arr.getJSONObject(i)
                val s = Stroke(Pen.valueOf(so.getString("p")), so.getInt("c"), so.getBoolean("e"))
                val d = so.getJSONArray("d")
                var k = 0
                while (k + 2 < d.length()) {
                    s.add(d.getDouble(k).toFloat(), d.getDouble(k + 1).toFloat(), d.getDouble(k + 2).toFloat())
                    k += 3
                }
                list.add(s)
            }
            BoardData(o.getInt("w"), o.getInt("h"), list)
        } catch (e: Exception) {
            null
        }
    }

    fun read(): String? = try {
        if (file.isFile) file.readText(Charsets.UTF_8) else null
    } catch (e: Exception) {
        null
    }

    // الكتابة في ملف مؤقت الأول، عشان لو التطبيق اتقفل وسط الحفظ ما يتبوظش الملف
    fun write(json: String) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // لو الحفظ فشل، الرسم لسه شغال في الجلسة دي
        }
    }
}
