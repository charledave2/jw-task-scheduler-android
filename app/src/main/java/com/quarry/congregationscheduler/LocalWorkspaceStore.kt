package com.quarry.congregationscheduler

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LocalAssignmentRole(val id:String, val label:String, val backup:Boolean=false, val active:Boolean=true)
data class LocalDepartment(val id:String, val label:String, val roles:List<LocalAssignmentRole>, val active:Boolean=true)
data class LocalMeetingRule(val id:String, val label:String, val dayOfWeek:Int, val time:String, val eligibleGroups:Set<Int>, val active:Boolean=true)
data class LocalWorkspaceConfig(val groupCount:Int, val departments:List<LocalDepartment>, val meetings:List<LocalMeetingRule>)

/** Private, device-only workspace. The JSON file is also the portable backup format. */
class LocalWorkspaceStore(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val file = java.io.File(appContext.filesDir, "local-workspace.json")
    private val backupPreferences = appContext.getSharedPreferences("local_auto_backup", Context.MODE_PRIVATE)
    private var root = readRoot()

    @Synchronized private fun readRoot(): JSONObject = runCatching {
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    }.getOrDefault(JSONObject()).apply {
        if (!has("people")) put("people", JSONArray())
        if (!has("settings")) put("settings", JSONObject().put("name", "My Congregation").put("thursday", "18:00").put("saturday", "15:30").put("sunday", "07:45"))
        if (!has("months")) put("months", JSONObject())
        if (!has("reviews")) put("reviews", JSONArray())
        val settings=optJSONObject("settings") ?: JSONObject()
        if (!has("localConfig")) put("localConfig", configJson(defaultConfig(settings)))
        else {
            val people=optJSONArray("people") ?: JSONArray()
            val months=optJSONObject("months") ?: JSONObject()
            val reviews=optJSONArray("reviews") ?: JSONArray()
            val stored=optJSONObject("localConfig")
            val hasSavedWork=people.length()>0 || months.keys().hasNext() || reviews.length()>0
            if(!hasSavedWork && stored != null && parseConfig(stored) == legacyDefaultConfig(settings)) {
                put("localConfig",configJson(defaultConfig(settings)))
            }
        }
    }

    data class AutoBackupInfo(val enabled:Boolean, val folderName:String?, val lastSaved:String?, val error:String?)

    @Synchronized private fun save() {
        file.writeText(root.toString())
        if (backupPreferences.getBoolean("enabled", false)) {
            runCatching { writeAutomaticBackup() }
                .onSuccess { backupPreferences.edit().putString("last_saved", Instant.now().toString()).remove("last_error").apply() }
                .onFailure { backupPreferences.edit().putString("last_error", it.message ?: "Automatic backup failed.").apply() }
        }
    }

    @Synchronized fun autoBackupInfo():AutoBackupInfo {
        val uri=backupPreferences.getString("tree_uri",null)?.let(Uri::parse)
        return AutoBackupInfo(
            backupPreferences.getBoolean("enabled",false),
            uri?.let(::folderDisplayName),
            backupPreferences.getString("last_saved",null)?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a")) }.getOrNull() },
            backupPreferences.getString("last_error",null)
        )
    }

    @Synchronized fun setAutoBackupEnabled(enabled:Boolean) {
        if(enabled) require(backupPreferences.getString("tree_uri",null)!=null) { "Choose a backup folder first." }
        backupPreferences.edit().putBoolean("enabled",enabled).remove("last_error").apply()
        if(enabled) {
            try {
                writeAutomaticBackup()
                backupPreferences.edit().putString("last_saved",Instant.now().toString()).remove("last_error").apply()
            } catch(error:Exception) {
                backupPreferences.edit().putString("last_error",error.message ?: "Automatic backup failed.").apply()
                throw error
            }
        }
    }

    @Synchronized fun configureAutoBackupFolder(uri:Uri):String {
        backupPreferences.edit().putString("tree_uri",uri.toString()).putBoolean("enabled",true).remove("last_error").apply()
        return try {
            writeAutomaticBackup()
            backupPreferences.edit().putString("last_saved",Instant.now().toString()).apply()
            folderDisplayName(uri)
        } catch(error:Exception) {
            backupPreferences.edit().putString("last_error",error.message ?: "Automatic backup failed.").apply()
            throw error
        }
    }

    private fun folderDisplayName(treeUri:Uri):String = runCatching {
        val docUri=DocumentsContract.buildDocumentUriUsingTree(treeUri,DocumentsContract.getTreeDocumentId(treeUri))
        resolver.query(docUri,arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { cursor ->
            if(cursor.moveToFirst()) cursor.getString(0) else null
        } ?: treeUri.lastPathSegment ?: "Selected folder"
    }.getOrDefault("Selected folder")

    private fun writeAutomaticBackup() {
        val treeUri=backupPreferences.getString("tree_uri",null)?.let(Uri::parse) ?: error("Choose a backup folder first.")
        val fileName="JW-Task-Scheduler-Auto.bcdv"
        val treeId=DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri=DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,treeId)
        var target:Uri?=null
        resolver.query(childrenUri,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { cursor ->
            val idColumn=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while(cursor.moveToNext()) {
                if(nameColumn>=0 && cursor.getString(nameColumn)==fileName) {
                    target=DocumentsContract.buildDocumentUriUsingTree(treeUri,cursor.getString(idColumn))
                    break
                }
            }
        }
        val backupUri=target ?: DocumentsContract.createDocument(resolver,treeUri,"application/octet-stream",fileName)
            ?: error("Could not create the .bcdv backup file in the selected folder.")
        val snapshot=JSONObject(root.toString()).put("backupMetadata",JSONObject()
            .put("format","JW Task Scheduler Device Backup")
            .put("createdAt",Instant.now().toString())
            .put("fileExtension",".bcdv"))
        resolver.openOutputStream(backupUri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(snapshot.toString(2)) }
            ?: error("Could not write the automatic backup file.")
    }

    @Synchronized fun export(): String = JSONObject(root.toString()).put("backupMetadata",JSONObject()
        .put("format","JW Task Scheduler Device Backup")
        .put("createdAt",Instant.now().toString())
        .put("fileExtension",".bcdv")).toString(2)

    @Synchronized fun import(json: String) {
        val candidate = JSONObject(json)
        require(candidate.has("people") && candidate.has("settings") && candidate.has("months")) { "This file is not a JW Task Scheduler backup." }
        root = candidate
        root.remove("backupMetadata")
        if (!root.has("reviews")) root.put("reviews", JSONArray())
        if (!root.has("localConfig")) root.put("localConfig",configJson(defaultConfig(root.optJSONObject("settings") ?: JSONObject())))
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
        require(value.groupCount in 0..30) { "Choose between 0 and 30 groups." }
        require(people().none { it.group > value.groupCount }) { "Move personnel in groups above the new limit before reducing the group count." }
        require(value.departments.map { it.id }.distinct().size == value.departments.size) { "Department IDs must be unique." }
        require(value.meetings.map { it.id }.distinct().size == value.meetings.size) { "Meeting IDs must be unique." }
        require(value.meetings.all { it.dayOfWeek in 1..7 }) { "Meeting day is invalid." }
        val settings=root.optJSONObject("settings") ?: JSONObject()
        value.meetings.firstOrNull { it.active && it.dayOfWeek==DayOfWeek.THURSDAY.value }?.let { settings.put("thursday",it.time) }
        value.meetings.firstOrNull { it.active && it.dayOfWeek==DayOfWeek.SATURDAY.value }?.let { settings.put("saturday",it.time) }
        value.meetings.firstOrNull { it.active && it.dayOfWeek==DayOfWeek.SUNDAY.value }?.let { settings.put("sunday",it.time) }
        root.put("settings",settings).put("localConfig",configJson(value)); save()
    }

    private fun defaultConfig(settings:JSONObject):LocalWorkspaceConfig = LocalWorkspaceConfig(0, emptyList(), emptyList())

    private fun legacyDefaultConfig(settings:JSONObject):LocalWorkspaceConfig = LocalWorkspaceConfig(9, listOf(
        LocalDepartment("mic","Mic Men",listOf(LocalAssignmentRole("main_mic_1","Main Mic 1"),LocalAssignmentRole("main_mic_2","Main Mic 2"),LocalAssignmentRole("backup_mic_1","Backup Mic 1",true),LocalAssignmentRole("backup_mic_2","Backup Mic 2",true))),
        LocalDepartment("av","Audio / Video",listOf(LocalAssignmentRole("audio_operator","Audio Operator"),LocalAssignmentRole("video_operator","Video Operator"),LocalAssignmentRole("backup_av_operator","Backup A/V Operator",true))),
        LocalDepartment("attendant","Platform",listOf(LocalAssignmentRole("attendant_1","Platform"),LocalAssignmentRole("backup_attendant","Platform Backup",true)))
    ), listOf(
        LocalMeetingRule("thursday","Thursday",DayOfWeek.THURSDAY.value,settings.optString("thursday","18:00"),(1..9).toSet()),
        LocalMeetingRule("saturday","Saturday",DayOfWeek.SATURDAY.value,settings.optString("saturday","15:30"),(5..9).toSet()),
        LocalMeetingRule("sunday","Sunday",DayOfWeek.SUNDAY.value,settings.optString("sunday","07:45"),(1..4).toSet())
    ))
    private fun configJson(config:LocalWorkspaceConfig)=JSONObject().put("groupCount",config.groupCount)
        .put("departments",JSONArray(config.departments.map { d -> JSONObject().put("id",d.id).put("label",d.label).put("active",d.active).put("roles",JSONArray(d.roles.map { JSONObject().put("id",it.id).put("label",it.label).put("backup",it.backup).put("active",it.active) })) }))
        .put("meetings",JSONArray(config.meetings.map { m -> JSONObject().put("id",m.id).put("label",m.label).put("dayOfWeek",m.dayOfWeek).put("time",m.time).put("active",m.active).put("eligibleGroups",JSONArray(m.eligibleGroups.sorted())) }))
    private fun parseConfig(json:JSONObject):LocalWorkspaceConfig {
        val deptJson=json.optJSONArray("departments")?:JSONArray(); val meetingJson=json.optJSONArray("meetings")?:JSONArray()
        val depts=(0 until deptJson.length()).map { i -> deptJson.getJSONObject(i).let { d -> val roles=d.optJSONArray("roles")?:JSONArray(); LocalDepartment(d.optString("id"),d.optString("label"), (0 until roles.length()).map { n -> roles.getJSONObject(n).let { LocalAssignmentRole(it.optString("id"),it.optString("label"),it.optBoolean("backup"),it.optBoolean("active",true)) } },d.optBoolean("active",true)) } }
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
            val activeRules=setup.meetings.filter { it.active && it.dayOfWeek==day.dayOfWeek.value }
            val archivedHistoricalRules=if(day.isBefore(LocalDate.now())) savedByDate.keys.mapNotNull { key ->
                val parts=key.split("|",limit=2); if(parts.firstOrNull()!=day.toString()) null else setup.meetings.firstOrNull { it.id==parts.getOrNull(1) && !it.active }
            } else emptyList()
            (activeRules+archivedHistoricalRules).distinctBy { it.id }.forEach { rule ->
                val old = savedByDate["${day}|${rule.id}"]
                val time = old?.optString("time")?.takeIf(String::isNotBlank) ?: rule.time
                val roles=setup.departments.flatMap { dept ->
                    val isArchivedHistorical = !dept.active && day.isBefore(LocalDate.now()) && old?.optJSONArray("slots")?.let { slots ->
                        dept.roles.any { role ->
                            val savedKey = if (dept.id == "mic") role.id else "dept:${dept.id}_${role.id}"
                            (0 until slots.length()).any { i -> slots.optJSONObject(i)?.optString("key") == savedKey }
                        }
                    } == true
                    if(!dept.active && !isArchivedHistorical) emptyList() else dept.roles.mapNotNull { role ->
                    val key=if(dept.id=="mic") role.id else "dept:${dept.id}_${role.id}"
                    val isArchivedRoleHistorical = !role.active && day.isBefore(LocalDate.now()) && old?.optJSONArray("slots")?.let { slots -> (0 until slots.length()).any { i -> slots.optJSONObject(i)?.optString("key")==key } }==true
                    if(!role.active && !isArchivedRoleHistorical) null else Triple(key,role.label,dept to role)
                } }
                val stored = old?.optJSONArray("slots") ?: JSONArray()
                val byKey = (0 until stored.length()).associate { stored.getJSONObject(it).optString("key") to stored.getJSONObject(it) }
                val slotList = roles.map { (key,label,pair) -> val (dept,role)=pair; val s=byKey[key]; Slot(key,label,s?.optString("personId")?.takeIf { it.isNotBlank() && it!="null" },s?.optString("name")?.takeIf { it.isNotBlank() && it!="null" },dept.id,role.backup,s?.optBoolean("locked")?:false) }
                meetings += Meeting(day.toString(),rule.id,time,slotList,null,rule.eligibleGroups.filter { it in 1..setup.groupCount }.toSet(),rule.label)
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
        val settings=root.optJSONObject("settings") ?: JSONObject()
        settings.put("name",value.name).put("thursday",value.thursday).put("saturday",value.saturday).put("sunday",value.sunday)
        root.put("settings",settings)
        val current=config(); val times=mapOf(DayOfWeek.THURSDAY.value to value.thursday, DayOfWeek.SATURDAY.value to value.saturday, DayOfWeek.SUNDAY.value to value.sunday)
        val meetings=current.meetings.map { rule -> rule.copy(time=times[rule.dayOfWeek] ?: rule.time) }
        root.put("localConfig",configJson(current.copy(meetings=meetings))); save()
    }
    @Synchronized fun review(review:TaskReview) { val a=root.getJSONArray("reviews"); val rows=(0 until a.length()).map { a.getJSONObject(it) }.filterNot { it.optString("date")==review.date && it.optString("slotKey")==review.slotKey && it.optString("personId")==review.personId }.toMutableList(); rows += JSONObject().put("date",review.date).put("type",review.type).put("department",review.department).put("slotKey",review.slotKey).put("personId",review.personId).put("personName",review.personName).put("status",review.status).put("note",review.note); root.put("reviews",JSONArray(rows)); save() }
    @Synchronized fun reviews(month:YearMonth):List<TaskReview> { val a=root.getJSONArray("reviews"); return (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("date").startsWith(month.toString()) }.map { TaskReview(it.optString("date"),it.optString("type"),it.optString("department"),it.optString("slotKey"),it.optString("personId"),it.optString("personName"),it.optString("status"),it.optString("note")) } }
    @Synchronized fun allReviews():List<TaskReview> { val a=root.getJSONArray("reviews"); return (0 until a.length()).map { a.getJSONObject(it) }.map { TaskReview(it.optString("date"),it.optString("type"),it.optString("department"),it.optString("slotKey"),it.optString("personId"),it.optString("personName"),it.optString("status"),it.optString("note")) } }
    @Synchronized fun monthKeys():List<YearMonth> = root.getJSONObject("months").keys().asSequence().mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }.toList()
    @Synchronized fun savePhoto(id:String, bytes:ByteArray) { val photos=root.optJSONObject("photos")?:JSONObject().also { root.put("photos",it) }; photos.put(id,android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)); save() }
    @Synchronized fun photo(id:String):ByteArray? = root.optJSONObject("photos")?.optString(id)?.takeIf { it.isNotBlank() }?.let { runCatching { android.util.Base64.decode(it,android.util.Base64.DEFAULT) }.getOrNull() }
    @Synchronized fun removePhoto(id:String) { root.optJSONObject("photos")?.remove(id); save() }
}
