package com.quarry.congregationscheduler

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class LocalAssignmentRole(val id:String, val label:String, val backup:Boolean=false)
data class LocalDepartment(val id:String, val label:String, val roles:List<LocalAssignmentRole>, val active:Boolean=true)
data class LocalMeetingRule(val id:String, val label:String, val dayOfWeek:Int, val time:String, val eligibleGroups:Set<Int>, val active:Boolean=true)
data class LocalWorkspaceConfig(val groupCount:Int, val departments:List<LocalDepartment>, val meetings:List<LocalMeetingRule>)

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
        if (!has("localConfig")) put("localConfig", configJson(defaultConfig(optJSONObject("settings") ?: JSONObject())))
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
            Person(p.optString("id"), p.optString("name"), p.optInt("group", 1), p.optString("status", "active"), p.optBoolean("noThursday"), p.optBoolean("mic"), p.optBoolean("audio"), p.optBoolean("video"), p.optBoolean("platform"), p.optBoolean("gate"), p.optString("remarks"), p.optBoolean("allowDuplicate"), p.optBoolean("ignoreMeetingConflict"), p.optBoolean("neverAutoAssign"), p.optString("assignmentPriority", "normal"), p.optString("avatarUpdatedAt").takeIf(String::isNotBlank), (p.optJSONArray("customDepartmentIds") ?: JSONArray()).let { ids -> (0 until ids.length()).map { ids.optString(it) }.toSet() })
        } }
    }
    private fun personJson(p: Person) = JSONObject().put("id",p.id).put("name",p.name).put("group",p.group).put("status",p.status).put("noThursday",p.noThursday).put("mic",p.mic).put("audio",p.audio).put("video",p.video).put("platform",p.platform).put("gate",p.gate).put("remarks",p.remarks).put("allowDuplicate",p.allowDuplicate).put("ignoreMeetingConflict",p.ignoreMeetingConflict).put("neverAutoAssign",p.neverAutoAssign).put("assignmentPriority",p.assignmentPriority).put("avatarUpdatedAt",p.avatarUpdatedAt).put("customDepartmentIds",JSONArray(p.customDepartmentIds.toList()))

    @Synchronized fun config():LocalWorkspaceConfig = parseConfig(root.optJSONObject("localConfig") ?: configJson(defaultConfig(root.optJSONObject("settings") ?: JSONObject())))

    @Synchronized fun saveConfig(value:LocalWorkspaceConfig) {
        require(value.groupCount in 1..30) { "Choose between 1 and 30 groups." }
        require(value.departments.map { it.id }.distinct().size == value.departments.size) { "Department IDs must be unique." }
        require(value.meetings.map { it.id }.distinct().size == value.meetings.size) { "Meeting IDs must be unique." }
        require(value.meetings.all { it.dayOfWeek in 1..7 }) { "Meeting day is invalid." }
        val settings=root.optJSONObject("settings") ?: JSONObject()
        val times=value.meetings.associateBy { it.id }
        settings.put("thursday",times["thursday"]?.time ?: settings.optString("thursday","18:00"))
        settings.put("saturday",times["saturday"]?.time ?: settings.optString("saturday","15:30"))
        settings.put("sunday",times["sunday"]?.time ?: settings.optString("sunday","07:45"))
        root.put("settings",settings).put("localConfig",configJson(value)); save()
    }

    private fun defaultConfig(settings:JSONObject):LocalWorkspaceConfig = LocalWorkspaceConfig(9, listOf(
        LocalDepartment("mic","Mic Men",listOf(LocalAssignmentRole("main_mic_1","Main Mic 1"),LocalAssignmentRole("main_mic_2","Main Mic 2"),LocalAssignmentRole("backup_mic_1","Backup Mic 1",true),LocalAssignmentRole("backup_mic_2","Backup Mic 2",true))),
        LocalDepartment("av","Audio / Video",listOf(LocalAssignmentRole("audio_operator","Audio Operator"),LocalAssignmentRole("video_operator","Video Operator"),LocalAssignmentRole("backup_av_operator","Backup A/V Operator",true))),
        LocalDepartment("attendant","Platform",listOf(LocalAssignmentRole("attendant_1","Platform"),LocalAssignmentRole("backup_attendant","Platform Backup",true)))
    ), listOf(
        LocalMeetingRule("thursday","Thursday",DayOfWeek.THURSDAY.value,settings.optString("thursday","18:00"),(1..9).toSet()),
        LocalMeetingRule("saturday","Saturday",DayOfWeek.SATURDAY.value,settings.optString("saturday","15:30"),(5..9).toSet()),
        LocalMeetingRule("sunday","Sunday",DayOfWeek.SUNDAY.value,settings.optString("sunday","07:45"),(1..4).toSet())
    ))
    private fun configJson(config:LocalWorkspaceConfig)=JSONObject().put("groupCount",config.groupCount)
        .put("departments",JSONArray(config.departments.map { d -> JSONObject().put("id",d.id).put("label",d.label).put("active",d.active).put("roles",JSONArray(d.roles.map { JSONObject().put("id",it.id).put("label",it.label).put("backup",it.backup) })) }))
        .put("meetings",JSONArray(config.meetings.map { m -> JSONObject().put("id",m.id).put("label",m.label).put("dayOfWeek",m.dayOfWeek).put("time",m.time).put("active",m.active).put("eligibleGroups",JSONArray(m.eligibleGroups.sorted())) }))
    private fun parseConfig(json:JSONObject):LocalWorkspaceConfig {
        val deptJson=json.optJSONArray("departments")?:JSONArray(); val meetingJson=json.optJSONArray("meetings")?:JSONArray()
        val depts=(0 until deptJson.length()).map { i -> deptJson.getJSONObject(i).let { d -> val roles=d.optJSONArray("roles")?:JSONArray(); LocalDepartment(d.optString("id"),d.optString("label"), (0 until roles.length()).map { n -> roles.getJSONObject(n).let { LocalAssignmentRole(it.optString("id"),it.optString("label"),it.optBoolean("backup")) } },d.optBoolean("active",true)) } }
        val meetings=(0 until meetingJson.length()).map { i -> meetingJson.getJSONObject(i).let { m -> val groups=m.optJSONArray("eligibleGroups")?:JSONArray(); LocalMeetingRule(m.optString("id"),m.optString("label"),m.optInt("dayOfWeek",1),m.optString("time","09:00"),(0 until groups.length()).map { groups.optInt(it) }.toSet(),m.optBoolean("active",true)) } }
        return LocalWorkspaceConfig(json.optInt("groupCount",9).coerceIn(1,30),depts,meetings)
    }

    @Synchronized fun schedule(month: YearMonth): ScheduleData {
        val settings = root.optJSONObject("settings") ?: JSONObject()
        val workspace = WorkspaceSettings(settings.optString("name", "My Congregation"), settings.optString("thursday", "18:00"), settings.optString("saturday", "15:30"), settings.optString("sunday", "07:45"))
        val setup=config()
        val months = root.getJSONObject("months")
        val saved = months.optJSONArray(month.toString()) ?: JSONArray()
        val savedByDate = (0 until saved.length()).associate { val row=saved.getJSONObject(it); "${row.optString("date")}|${row.optString("type")}" to row }
        val meetings = mutableListOf<Meeting>()
        var day = month.atDay(1)
        while (day.month == month.month) {
            setup.meetings.filter { it.active && it.dayOfWeek==day.dayOfWeek.value }.forEach { rule ->
                val old = savedByDate["${day}|${rule.id}"]
                val time = old?.optString("time")?.takeIf(String::isNotBlank) ?: rule.time
                val roles=setup.departments.filter { it.active }.flatMap { dept -> dept.roles.map { role ->
                    val key=if(dept.id=="mic") role.id else "dept:${dept.id}_${role.id}"
                    Triple(key,role.label,dept to role)
                } }
                val stored = old?.optJSONArray("slots") ?: JSONArray()
                val byKey = (0 until stored.length()).associate { stored.getJSONObject(it).optString("key") to stored.getJSONObject(it) }
                val slotList = roles.map { (key,label,pair) -> val (dept,role)=pair; val s=byKey[key]; Slot(key,label,s?.optString("personId")?.takeIf { it.isNotBlank() && it!="null" },s?.optString("name")?.takeIf { it.isNotBlank() && it!="null" },dept.id,role.backup,s?.optBoolean("locked")?:false) }
                meetings += Meeting(day.toString(),rule.id,time,slotList,null,rule.eligibleGroups.filter { it in 1..setup.groupCount }.toSet())
            }
            day=day.plusDays(1)
        }
        return ScheduleData(people(), meetings, workspace)
    }
    @Synchronized fun saveAssignment(meeting: Meeting, slot: Slot, person: Person?) {
        val month = meeting.date.take(7); val all = root.getJSONObject("months"); val rows = all.optJSONArray(month) ?: JSONArray()
        val map = (0 until rows.length()).map { rows.getJSONObject(it) }.associateBy { "${it.optString("date")}|${it.optString("type")}" }.toMutableMap()
        val entryKey="${meeting.date}|${meeting.type}"
        val row = map[entryKey] ?: JSONObject().put("date",meeting.date).put("type",meeting.type).put("time",meeting.time).put("slots",JSONArray()).also { map[entryKey]=it }
        val existing = row.optJSONArray("slots") ?: JSONArray(); val entries=(0 until existing.length()).map { existing.getJSONObject(it) }.filterNot { it.optString("key")==slot.key }.toMutableList()
        entries += JSONObject().put("key",slot.key).put("personId",person?.id).put("name",person?.name).put("locked",slot.locked)
        row.put("slots",JSONArray(entries)); all.put(month,JSONArray(map.values.toList())); save()
    }
    @Synchronized fun savePerson(person: Person): Person {
        val a=root.getJSONArray("people"); val id=person.id.ifBlank { ((0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("id")?.toLongOrNull() }.maxOrNull() ?: 0L).plus(1).toString() }
        val saved=person.copy(id=id); val rows=(0 until a.length()).map { a.getJSONObject(it) }.filterNot { it.optString("id")==id }.toMutableList(); rows += personJson(saved); root.put("people",JSONArray(rows)); save(); return saved
    }
    @Synchronized fun deletePerson(id:String) { val a=root.getJSONArray("people"); root.put("people",JSONArray((0 until a.length()).map { a.getJSONObject(it) }.filterNot { it.optString("id")==id })); save() }
    @Synchronized fun settings(value:WorkspaceSettings) {
        root.put("settings",JSONObject().put("name",value.name).put("thursday",value.thursday).put("saturday",value.saturday).put("sunday",value.sunday))
        val current=config(); saveConfig(current.copy(meetings=current.meetings.map { rule -> when(rule.id) { "thursday" -> rule.copy(time=value.thursday); "saturday" -> rule.copy(time=value.saturday); "sunday" -> rule.copy(time=value.sunday); else -> rule } }))
    }
    @Synchronized fun review(review:TaskReview) { val a=root.getJSONArray("reviews"); val rows=(0 until a.length()).map { a.getJSONObject(it) }.filterNot { it.optString("date")==review.date && it.optString("slotKey")==review.slotKey && it.optString("personId")==review.personId }.toMutableList(); rows += JSONObject().put("date",review.date).put("type",review.type).put("department",review.department).put("slotKey",review.slotKey).put("personId",review.personId).put("personName",review.personName).put("status",review.status).put("note",review.note); root.put("reviews",JSONArray(rows)); save() }
    @Synchronized fun reviews(month:YearMonth):List<TaskReview> { val a=root.getJSONArray("reviews"); return (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("date").startsWith(month.toString()) }.map { TaskReview(it.optString("date"),it.optString("type"),it.optString("department"),it.optString("slotKey"),it.optString("personId"),it.optString("personName"),it.optString("status"),it.optString("note")) } }
    @Synchronized fun allReviews():List<TaskReview> { val a=root.getJSONArray("reviews"); return (0 until a.length()).map { a.getJSONObject(it) }.map { TaskReview(it.optString("date"),it.optString("type"),it.optString("department"),it.optString("slotKey"),it.optString("personId"),it.optString("personName"),it.optString("status"),it.optString("note")) } }
    @Synchronized fun monthKeys():List<YearMonth> = root.getJSONObject("months").keys().asSequence().mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }.toList()
    @Synchronized fun savePhoto(id:String, bytes:ByteArray) { val photos=root.optJSONObject("photos")?:JSONObject().also { root.put("photos",it) }; photos.put(id,android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)); save() }
    @Synchronized fun photo(id:String):ByteArray? = root.optJSONObject("photos")?.optString(id)?.takeIf { it.isNotBlank() }?.let { runCatching { android.util.Base64.decode(it,android.util.Base64.DEFAULT) }.getOrNull() }
    @Synchronized fun removePhoto(id:String) { root.optJSONObject("photos")?.remove(id); save() }
}
