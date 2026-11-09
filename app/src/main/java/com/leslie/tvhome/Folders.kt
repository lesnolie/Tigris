package com.leslie.tvhome

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AppFolder(val id: String, val name: String, val members: List<String>)

/** Stored separately from app order, so grouping never deletes the user's ordering or apps. */
class Folders(ctx: Context) {
    private val sp = ctx.getSharedPreferences("desktop_folders", Context.MODE_PRIVATE)
    fun read(): List<AppFolder> = runCatching {
        val array = JSONArray(sp.getString("folders", "[]"))
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val members = obj.getJSONArray("members")
            AppFolder(obj.getString("id"), obj.getString("name"),
                (0 until members.length()).map { members.getString(it) }.distinct())
        }
    }.getOrDefault(emptyList())

    fun save(folders: List<AppFolder>) {
        val array = JSONArray()
        folders.forEach { f -> array.put(JSONObject().put("id", f.id).put("name", f.name)
            .put("members", JSONArray(f.members))) }
        sp.edit().putString("folders", array.toString()).apply()
    }

    fun create(name: String, pkg: String): AppFolder {
        val f = AppFolder("folder:" + java.util.UUID.randomUUID(), name, listOf(pkg))
        save(read().map { it.copy(members = it.members - pkg) } + f)
        return f
    }

    fun move(pkg: String, target: String?) = save(read().map {
        it.copy(members = (it.members - pkg) + if (it.id == target) listOf(pkg) else emptyList())
    })
}
