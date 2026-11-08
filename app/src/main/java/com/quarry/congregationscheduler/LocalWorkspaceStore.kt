package com.quarry.congregationscheduler

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Private, device-only workspace. The JSON file is also the portable backup format. */
class LocalWorkspaceStore(context: Context) {
    private val file = java.io.File(context.filesDir, "local-workspace.json")
    private var root = readRoot()

    @Synchronized private fun readRoot(): JSONObject = runCatching {
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    }.getOrDefault(JSONObject()).apply {
        if (!has("people")) put("people", JSONArray())
        if (!has("settings")) put("settings", JSONObject().put("name", "My Congregation").put("thursday", "18:00").put("saturday", "15:30").put("sunday", "07:45"))
        if (!has("months")) put("months", JSONObject())
        if (!has("reviews")) put("reviews", JSONArray())
    }
    @Synchronized private fun save() { file.writeText(root.toString()) }
    @Synchronized fun export(): String = root.toString(2)
    @Synchronized fun import(json: String) {
        val candidate = JSONObject(json)
        require(candidate.has("people") && candidate.has("settings") && candidate.has("months")) { "This file is not a JW Task Scheduler backup." }
        root = candidate
        if (!root.has("reviews")) root.put("reviews", JSONArray())
        save()
    }

    private fun people(): List<Person> {
        val a = root.optJSONArray("people") ?: JSONArray()
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { p ->
            Person(p.optString("id"), p.optString("name"), p.optInt("group", 1), p.optString("status", "active"), p.optBoolean("noThursday"), p.optBoolean("mic"), p.optBoolean("audio"), p.optBoolean("video"), p.optBoolean("platform"), p.optBoolean("gate"), p.optString("remarks"), p.optBoolean("allowDuplicate"), p.optBoolean("ignoreMeetingConflict"), p.optBoolean("neverAutoAssign"), p.optString("assignmentPriority", "normal"), p.optString("avatarUpdatedAt").takeIf(String::isNotBlank))
        } }
    }
    private fun personJson(p: Person) = JSONObject().put("id",p.id).put("name",p.name).put("group",p.group).put("status",p.status).put("noThursday",p.noThursday).put("mic",p.mic).put("audio",p.audio).put("video",p.video).put("platform",p.platform).put("gate",p.gate).put("remarks",p.remarks).put("allowDuplicate",p.allowDuplicate).put("ignoreMeetingConflict",p.ignoreMeetingConflict).put("neverAutoAssign",p.neverAutoAssign).put("assignmentPriority",p.assignmentPriority).put("avatarUpdatedAt",p.avatarUpdatedAt)

    @Synchronized fun schedule(month: YearMonth): ScheduleData {
        val settings = root.optJSONObject("settings") ?: JSONObject()
        val workspace = WorkspaceSettings(settings.optString("name", "My Congregation"), settings.optString("thursday", "18:00"), settings.optString("saturday", "15:30"), settings.optString("sunday", "07:45"))
        val months = root.getJSONObject("months")
        val saved = months.optJSONArray(month.toString()) ?: JSONArray()
        val savedByDate = (0 until saved.length()).associate { saved.getJSONObject(it).optString("date") to saved.getJSONObject(it) }
        val meetings = mutableListOf<Meeting>()
        var day = month.atDay(1)
        while (day.month == month.month) {
            val type = when(day.dayOfWeek) { DayOfWeek.THURSDAY -> "thursday"; DayOfWeek.SATURDAY -> "saturday"; DayOfWeek.SUNDAY -> "sunday"; else -> null }
            if (type != null) {
                val old = savedByDate[day.toString()]
                val time = old?.optString("time")?.takeIf(String::isNotBlank) ?: when(type) { "thursday" -> workspace.thursday; "saturday" -> workspace.saturday; else -> workspace.sunday }
                val slots = listOf(Triple("main_mic_1","Main Mic 1","mic"),Triple("main_mic_2","Main Mic 2","mic"),Triple("backup_mic_1","Backup Mic 1","mic"),Triple("backup_mic_2","Backup Mic 2","mic"),Triple("dept:audio_operator","Audio Operator","av"),Triple("dept:video_operator","Video Operator","av"),Triple("dept:backup_av_operator","Backup A/V Operator","av"),Triple("dept:attendant_1","Platform","attendant"),Triple("dept:backup_attendant","Platform Backup","attendant"))
                val stored = old?.optJSONArray("slots") ?: JSONArray()
                val byKey = (0 until stored.length()).associate { stored.getJSONObject(it).optString("key") to stored.getJSONObject(it) }
                val slotList = slots.map { (key,label,dept) -> val s=byKey[key]; Slot(key,label,s?.optString("personId")?.takeIf { it.isNotBlank() && it!="null" },s?.optString("name")?.takeIf { it.isNotBlank() && it!="null" },dept,key.contains("backup"),s?.optBoolean("locked")?:false) }
                meetings += Meeting(day.toString(),type,time,slotList, null)
            }
            day=day.plusDays(1)
        }
        return ScheduleData(people(), meetings, workspace)
    }
    @Synchronized fun saveAssignment(meeting: Meeting, slot: Slot, person: Person?) {
        val month = meeting.date.take(7); val all = root.getJSONObject("months"); val rows = all.optJSONArray(month) ?: JSONArray()
        val map = (0 until rows.length()).map { rows.getJSONObject(it) }.associateBy { it.optString("date") }.toMutableMap()
        val row = map[meeting.date] ?: JSONObject().put("date",meeting.date).put("type",meeting.type).put("time",meeting.time).put("slots",JSONArray()).also { map[meeting.date]=it }
        val existing = row.optJSONArray("slots") ?: JSONArray(); val entries=(0 until existing.length()).map { existing.getJSONObject(it) }.filterNot { it.optString("key")==slot.key }.toMutableList()
        entries += JSONObject().put("key",slot.key).put("personId",person?.id).put("name",person?.name).put("locked",slot.locked)
        row.put("slots",JSONArray(entries)); all.put(month,JSONArray(map.values.toList())); save()
    }
    @Synchronized fun savePerson(person: Person): Person {
        val a=root.getJSONArray("people"); val id=person.id.ifBlank { ((0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("id")?.toLongOrNull() }.maxOrNull() ?: 0L).plus(1).toString() }
        val saved=person.copy(id=id); val rows=(0 until a.length()).map { a.getJSONObject(it) }.filterNot { it.optString("id")==id }.toMutableList(); rows += personJson(saved); root.put("people",JSONArray(rows)); save(); return saved
    }
    @Synchronized fun deletePerson(id:String) { val a=root.getJSONArray("people"); root.put("people",JSONArray((0 until a.length()).map { a.getJSONObject(it) }.filterNot { it.optString("id")==id })); save() }
    @Synchronized fun settings(value:WorkspaceSettings) { root.put("settings",JSONObject().put("name",value.name).put("thursday",value.thursday).put("saturday",value.saturday).put("sunday",value.sunday)); save() }
    @Synchronized fun review(review:TaskReview) { val a=root.getJSONArray("reviews"); val rows=(0 until a.length()).map { a.getJSONObject(it) }.filterNot { it.optString("date")==review.date && it.optString("slotKey")==review.slotKey && it.optString("personId")==review.personId }.toMutableList(); rows += JSONObject().put("date",review.date).put("type",review.type).put("department",review.department).put("slotKey",review.slotKey).put("personId",review.personId).put("personName",review.personName).put("status",review.status).put("note",review.note); root.put("reviews",JSONArray(rows)); save() }
    @Synchronized fun reviews(month:YearMonth):List<TaskReview> { val a=root.getJSONArray("reviews"); return (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("date").startsWith(month.toString()) }.map { TaskReview(it.optString("date"),it.optString("type"),it.optString("department"),it.optString("slotKey"),it.optString("personId"),it.optString("personName"),it.optString("status"),it.optString("note")) } }
    @Synchronized fun allReviews():List<TaskReview> { val a=root.getJSONArray("reviews"); return (0 until a.length()).map { a.getJSONObject(it) }.map { TaskReview(it.optString("date"),it.optString("type"),it.optString("department"),it.optString("slotKey"),it.optString("personId"),it.optString("personName"),it.optString("status"),it.optString("note")) } }
    @Synchronized fun monthKeys():List<YearMonth> = root.getJSONObject("months").keys().asSequence().mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }.toList()
    @Synchronized fun savePhoto(id:String, bytes:ByteArray) { val photos=root.optJSONObject("photos")?:JSONObject().also { root.put("photos",it) }; photos.put(id,android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)); save() }
    @Synchronized fun photo(id:String):ByteArray? = root.optJSONObject("photos")?.optString(id)?.takeIf { it.isNotBlank() }?.let { runCatching { android.util.Base64.decode(it,android.util.Base64.DEFAULT) }.getOrNull() }
    @Synchronized fun removePhoto(id:String) { root.optJSONObject("photos")?.remove(id); save() }
}
