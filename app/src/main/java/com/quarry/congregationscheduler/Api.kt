package com.quarry.congregationscheduler

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek

private const val API_BASE = "https://main.congregation-scheduler-pro.pages.dev/"

class TokenStore(context: Context) {
    private val prefs by lazy {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "secure_session", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    fun getAccess() = prefs.getString("access", null)
    fun getRefresh() = prefs.getString("refresh", null)
    fun save(access: String, refresh: String) = prefs.edit().putString("access", access).putString("refresh", refresh).apply()
    fun saveAccess(access: String) = prefs.edit().putString("access", access).apply()
    fun clear() = prefs.edit().clear().apply()
}

data class Person(
    val id: String, val name: String, val group: Int, val status: String, val noThursday: Boolean,
    val mic: Boolean, val audio: Boolean, val video: Boolean, val platform: Boolean, val gate: Boolean = false,
    val remarks: String = "", val allowDuplicate: Boolean = false, val ignoreMeetingConflict: Boolean = false,
    val neverAutoAssign: Boolean = false, val assignmentPriority: String = "normal", val avatarUpdatedAt: String? = null,
    val customDepartmentIds: Set<String> = emptySet()
)
data class Slot(val key: String, val label: String, val personId: String?, val name: String?, val department: String, val backup: Boolean = false, val locked: Boolean = false)
data class Meeting(val date: String, val type: String, val time: String, val slots: List<Slot>, val rawMic: JSONObject?, val eligibleGroups: Set<Int>? = null, val displayLabel: String? = null, val dayOfWeek: Int? = null)
data class UserInfo(val id: String, val username: String, val displayName: String, val role: String, val permissions: String)
data class WorkspaceSettings(val name: String, val thursday: String, val saturday: String, val sunday: String)
data class ScheduleData(val people: List<Person>, val meetings: List<Meeting>, val settings: WorkspaceSettings)
data class ManagedUser(val id: String, val username: String, val displayName: String, val role: String, val active: Boolean)
data class TaskReview(val date: String, val type: String, val department: String, val slotKey: String, val personId: String, val personName: String, val status: String, val note: String = "")

class ApiClient(context: Context) {
    private val tokens = TokenStore(context)
    fun hasSession(): Boolean = !tokens.getAccess().isNullOrBlank() || !tokens.getRefresh().isNullOrBlank()

    suspend fun login(username: String, password: String): UserInfo = withContext(Dispatchers.IO) {
        val data = raw("api/auth/mobile-login", "POST", JSONObject().put("username", username).put("password", password), null)
        tokens.save(data.getString("access_token"), data.getString("refresh_token"))
        parseUser(data.getJSONObject("user"))
    }

    suspend fun logout() = withContext(Dispatchers.IO) { tokens.clear() }

    private fun raw(path: String, method: String, body: JSONObject?, bearer: String?): JSONObject {
        val connection = URL(API_BASE + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "JWTaskScheduler-Android/1.0")
        connection.setRequestProperty("Cache-Control", "no-cache, no-store")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        if (!bearer.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $bearer")
        if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        val result = try { JSONObject(text) } catch (_: Exception) { JSONObject().put("error", "The server returned an invalid response.") }
        if (status !in 200..299) throw ApiException(status, result.optString("error", "Request failed ($status)."))
        return result
    }

    private fun refreshAccess(): String {
        val refresh = tokens.getRefresh() ?: throw ApiException(401, "Please sign in again.")
        val data = raw("api/auth/mobile-refresh", "POST", JSONObject().put("refresh_token", refresh), null)
        val access = data.getString("access_token")
        tokens.save(access, data.getString("refresh_token"))
        return access
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        var token = tokens.getAccess() ?: throw ApiException(401, "Please sign in again.")
        try { return raw(path, method, body, token) } catch (e: ApiException) {
            if (e.code != 401) throw e
            token = refreshAccess()
            return raw(path, method, body, token)
        }
    }

    suspend fun getMe(): UserInfo = withContext(Dispatchers.IO) { parseUser(request("api/auth/me").getJSONObject("user")) }

    suspend fun loadWorkspaceConfig(): LocalWorkspaceConfig = withContext(Dispatchers.IO) {
        parseWorkspaceConfig(request("api/workspace/config").getJSONObject("config"))
    }

    suspend fun saveWorkspaceConfig(config: LocalWorkspaceConfig): LocalWorkspaceConfig = withContext(Dispatchers.IO) {
        val saved=request("api/workspace/config","PUT",JSONObject().put("config",workspaceConfigJson(config))).getJSONObject("config")
        parseWorkspaceConfig(saved)
    }

    private fun parseWorkspaceConfig(value: JSONObject): LocalWorkspaceConfig {
        val departmentRows=value.optJSONArray("departments") ?: JSONArray()
        val meetingRows=value.optJSONArray("meetings") ?: JSONArray()
        val departments=(0 until departmentRows.length()).map { i -> departmentRows.getJSONObject(i).let { row ->
            val roles=row.optJSONArray("roles") ?: JSONArray()
            LocalDepartment(row.optString("id"),row.optString("label"),(0 until roles.length()).map { j -> roles.getJSONObject(j).let { role ->
                LocalAssignmentRole(role.optString("id"),role.optString("label"),role.optBoolean("backup"),role.optBoolean("active",true))
            }},row.optBoolean("active",true))
        }}
        val meetings=(0 until meetingRows.length()).map { i -> meetingRows.getJSONObject(i).let { row ->
            val groups=row.optJSONArray("eligibleGroups") ?: JSONArray()
            LocalMeetingRule(row.optString("id"),row.optString("label"),row.optInt("dayOfWeek"),row.optString("time"),
                (0 until groups.length()).map { groups.optInt(it) }.toSet(),row.optBoolean("active",true))
        }}
        return LocalWorkspaceConfig(value.optInt("groupCount",9),departments,meetings)
    }

    private fun workspaceConfigJson(config: LocalWorkspaceConfig)=JSONObject()
        .put("groupCount",config.groupCount)
        .put("departments",JSONArray(config.departments.map { dept -> JSONObject().put("id",dept.id).put("label",dept.label).put("active",dept.active)
            .put("roles",JSONArray(dept.roles.map { role -> JSONObject().put("id",role.id).put("label",role.label).put("backup",role.backup).put("active",role.active) })) }))
        .put("meetings",JSONArray(config.meetings.map { meeting -> JSONObject().put("id",meeting.id).put("label",meeting.label)
            .put("dayOfWeek",meeting.dayOfWeek).put("time",meeting.time).put("active",meeting.active)
            .put("eligibleGroups",JSONArray(meeting.eligibleGroups.sorted())) }))

    suspend fun loadSchedule(month: YearMonth = YearMonth.now(), workspaceConfig: LocalWorkspaceConfig? = null): ScheduleData = withContext(Dispatchers.IO) {
        val config=workspaceConfig ?: loadWorkspaceConfig()
        val peopleRows=request("api/micmen").optJSONArray("mic_men") ?: JSONArray()
        val people=(0 until peopleRows.length()).map { i -> peopleRows.getJSONObject(i).let { p ->
            val custom=runCatching { JSONArray(p.optString("custom_department_ids_json","[]")) }.getOrDefault(JSONArray())
            Person(p.optString("id"),p.optString("name"),p.optInt("group_number",1),p.optString("status","active"),p.optInt("no_thursday")!=0,
                p.optInt("role_mic",0)!=0,p.optInt("role_audio")!=0,p.optInt("role_video")!=0,p.optInt("role_attendant")!=0,p.optInt("role_gate")!=0,
                p.optString("remarks"),p.optBoolean("allow_duplicate"),p.optBoolean("ignore_meeting_conflict"),p.optBoolean("never_auto_assign"),
                p.optString("assignment_priority","normal"),p.optString("avatar_updated_at").takeIf { it.isNotBlank()&&it!="null" },
                (0 until custom.length()).map { custom.optString(it) }.toSet())
        }}
        val response=request("api/workspace/schedule?month=$month")
        val rows=response.optJSONArray("meetings") ?: JSONArray()
        val meetings=(0 until rows.length()).map { i -> rows.getJSONObject(i).let { row ->
            val slotsJson=row.optJSONArray("slots") ?: JSONArray()
            val slots=(0 until slotsJson.length()).map { j -> slotsJson.getJSONObject(j).let { slot ->
                Slot(slot.optString("role_id"),slot.optString("role_label"),slot.optString("person_id").takeIf { it.isNotBlank()&&it!="null" },
                    slot.optString("person_name").takeIf { it.isNotBlank()&&it!="null" },slot.optString("department_id"),
                    slot.optBoolean("backup"),slot.optBoolean("locked"))
            }}
            val groupJson=row.optJSONArray("eligible_groups") ?: JSONArray()
            Meeting(row.optString("meeting_date"),row.optString("meeting_id"),row.optString("meeting_time"),slots,null,
                (0 until groupJson.length()).map { groupJson.optInt(it) }.toSet(),row.optString("meeting_label"),row.optInt("day_of_week"))
        }}.sortedWith(compareBy({it.date},{it.type}))
        val settingsObj=request("api/settings").optJSONObject("settings") ?: JSONObject()
        val legacyTimes=config.meetings.associateBy { it.dayOfWeek }
        val settings=WorkspaceSettings(settingsObj.optString("congregation_name","Congregation"),
            legacyTimes[4]?.time ?: "18:00",legacyTimes[6]?.time ?: "15:30",legacyTimes[7]?.time ?: "07:45")
        ScheduleData(people,meetings,settings)
    }

    suspend fun loadTaskReviews(month: YearMonth = YearMonth.now()): List<TaskReview> = withContext(Dispatchers.IO) {
        val rows=request("api/workspace/task-reviews?month=$month").optJSONArray("reviews") ?: JSONArray()
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let { r -> TaskReview(
            r.optString("meeting_date"),r.optString("meeting_type"),r.optString("department"),r.optString("slot_key"),
            r.optString("person_id"),r.optString("person_name"),r.optString("status"),r.optString("note")
        )}}
    }

    suspend fun saveTaskReview(meeting: Meeting, slot: Slot, status: String) = withContext(Dispatchers.IO) {
        request("api/workspace/task-reviews","POST",JSONObject()
            .put("meeting_date",meeting.date).put("meeting_id",meeting.type).put("department_id",slot.department)
            .put("role_id",slot.key).put("person_id",slot.personId?.toIntOrNull()).put("status",status))
    }

    suspend fun assign(meeting: Meeting, slot: Slot, person: Person?): Unit = withContext(Dispatchers.IO) {
        request("api/workspace/assignments","POST",JSONObject()
            .put("meeting_date",meeting.date).put("meeting_id",meeting.type).put("meeting_time",meeting.time)
            .put("department_id",slot.department).put("role_id",slot.key)
            .put("person_id",person?.id?.toIntOrNull()).put("locked",slot.locked))
    }

    suspend fun createPerson(person: Person) = withContext(Dispatchers.IO) {
        request("api/micmen", "POST", personPayload(person))
    }

    suspend fun updatePerson(person: Person) = withContext(Dispatchers.IO) {
        request("api/micmen", "PUT", personPayload(person).put("id", person.id.toInt()))
    }

    suspend fun deletePerson(id: String) = withContext(Dispatchers.IO) {
        request("api/micmen?id=$id", "DELETE")
    }

    suspend fun uploadPersonAvatar(id: String, webp: ByteArray) = withContext(Dispatchers.IO) { personAvatarRequest(id, "POST", webp) }
    suspend fun downloadPersonAvatar(id: String): ByteArray? = withContext(Dispatchers.IO) { personAvatarRequest(id, "GET", null) }
    suspend fun deletePersonAvatar(id: String) = withContext(Dispatchers.IO) { personAvatarRequest(id, "DELETE", null) }

    private fun personAvatarRequest(id: String, method: String, webp: ByteArray?): ByteArray? {
        fun call(token: String): Pair<Int, ByteArray> {
            val connection = URL(API_BASE + "api/micmen/avatar?id=$id").openConnection() as HttpURLConnection
            connection.requestMethod = method; connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", if (method == "GET") "image/webp" else "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (method == "POST") {
                val boundary = "----CspPersonAvatar${System.nanoTime()}"
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connection.outputStream.use { out ->
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"avatar\"; filename=\"avatar.webp\"\r\nContent-Type: image/webp\r\n\r\n".toByteArray())
                    out.write(webp ?: byteArrayOf()); out.write("\r\n--$boundary--\r\n".toByteArray())
                }
            }
            val status = connection.responseCode; val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: byteArrayOf(); connection.disconnect(); return status to bytes
        }
        var token = tokens.getAccess() ?: throw ApiException(401, "Please sign in again.")
        var (status, bytes) = call(token)
        if (status == 401) { token = refreshAccess(); val retry = call(token); status = retry.first; bytes = retry.second }
        if (method == "GET" && status == 404) return null
        if (status !in 200..299) {
            val message = try { JSONObject(String(bytes, Charsets.UTF_8)).optString("error", "Request failed ($status).") } catch (_: Exception) { "Request failed ($status)." }
            throw ApiException(status, message)
        }
        return if (method == "GET") bytes else null
    }

    private fun personPayload(person: Person) = JSONObject()
        .put("name", person.name.trim()).put("group_number", person.group).put("status", person.status)
        .put("no_thursday", person.noThursday).put("remarks", person.remarks)
        .put("role_mic", person.mic).put("role_audio", person.audio).put("role_video", person.video).put("role_attendant", person.platform).put("role_gate", person.gate)
        .put("allow_duplicate", person.allowDuplicate).put("ignore_meeting_conflict", person.ignoreMeetingConflict)
        .put("never_auto_assign", person.neverAutoAssign).put("assignment_priority", person.assignmentPriority)
        .put("custom_department_ids", JSONArray(person.customDepartmentIds.toList()))

    suspend fun loadUsers(): List<ManagedUser> = withContext(Dispatchers.IO) {
        val rows = request("api/users").optJSONArray("users") ?: JSONArray()
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let { row ->
            val active = when (val value = row.opt("is_active")) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.equals("true", ignoreCase = true) || value == "1"
                else -> true
            }
            ManagedUser(row.optString("id"), row.optString("username"), row.optString("display_name"), row.optString("role"), active)
        } }
    }

    suspend fun createUser(username: String, displayName: String, password: String, confirmPassword: String, role: String, departments: List<String>, actions: List<String>) = withContext(Dispatchers.IO) {
        request("api/users", "POST", JSONObject()
            .put("username", username).put("display_name", displayName).put("password", password)
            .put("confirm_password", confirmPassword).put("role", role)
            .put("permissions", JSONObject().put("departments", JSONArray(departments)).put("actions", JSONArray(actions))))
    }

    suspend fun setUserActive(user: ManagedUser, active: Boolean) = withContext(Dispatchers.IO) {
        request("api/users", "PUT", JSONObject().put("id", user.id.toInt()).put("role", user.role).put("is_active", active))
    }

    suspend fun deleteUser(user: ManagedUser) = withContext(Dispatchers.IO) {
        val token = tokens.getAccess() ?: throw ApiException(401, "Please sign in again.")
        try { raw("api/users?id=${user.id}", "DELETE", null, token) } catch (e: ApiException) {
            if (e.code != 401) throw e
            raw("api/users?id=${user.id}", "DELETE", null, refreshAccess())
        }
    }

    suspend fun saveWorkspace(settings: WorkspaceSettings): WorkspaceSettings = withContext(Dispatchers.IO) {
        val updated = request("api/settings", "PUT", JSONObject().put("congregation_name", settings.name).put("thursday_time", settings.thursday).put("saturday_time", settings.saturday).put("sunday_time", settings.sunday)).optJSONObject("settings") ?: JSONObject()
        WorkspaceSettings(updated.optString("congregation_name", settings.name), updated.optString("thursday_time", settings.thursday), updated.optString("saturday_time", settings.saturday), updated.optString("sunday_time", settings.sunday))
    }

    suspend fun saveProfile(displayName: String): UserInfo = withContext(Dispatchers.IO) {
        val data = request("api/account/profile", "PUT", JSONObject().put("display_name", displayName))
        parseUser(data.getJSONObject("user"))
    }

    suspend fun uploadAvatar(webp: ByteArray) = withContext(Dispatchers.IO) { avatarRequest("POST", webp) }
    suspend fun downloadAvatar(): ByteArray? = withContext(Dispatchers.IO) { avatarRequest("GET", null) }
    suspend fun deleteAvatar() = withContext(Dispatchers.IO) { avatarRequest("DELETE", null) }

    private fun avatarRequest(method: String, webp: ByteArray?): ByteArray? {
        fun call(token: String): Pair<Int, ByteArray> {
            val connection = URL(API_BASE + "api/account/avatar").openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", if (method == "GET") "image/webp" else "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (method == "POST") {
                val boundary = "----CspAvatar${System.nanoTime()}"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connection.outputStream.use { out ->
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"avatar\"; filename=\"avatar.webp\"\r\nContent-Type: image/webp\r\n\r\n".toByteArray())
                    out.write(webp ?: byteArrayOf())
                    out.write("\r\n--$boundary--\r\n".toByteArray())
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: byteArrayOf()
            connection.disconnect()
            return status to bytes
        }
        var token = tokens.getAccess() ?: throw ApiException(401, "Please sign in again.")
        var (status, bytes) = call(token)
        if (status == 401) {
            token = refreshAccess()
            val retry = call(token)
            status = retry.first; bytes = retry.second
        }
        if (method == "GET" && status == 404) return null
        if (status !in 200..299) {
            val message = try { JSONObject(String(bytes, Charsets.UTF_8)).optString("error", "Request failed ($status).") } catch (_: Exception) { "Request failed ($status)." }
            throw ApiException(status, message)
        }
        return if (method == "GET") bytes else null
    }

    private fun parseUser(u: JSONObject) = UserInfo(u.optString("id"), u.optString("username"), u.optString("display_name"), u.optString("role"), u.optString("permissions_json"))
    private fun parseLocks(json: String): JSONObject = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
    private data class MutableMeeting(val date: String, val type: String, val time: String, val slots: MutableList<Slot>, val raw: JSONObject?)
}

class ApiException(val code: Int, override val message: String) : Exception(message)
