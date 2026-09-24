package com.legendpolyfoams.lpplpms

import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class User(
    val userId:String="", val employeeId:String="", val name:String="", val department:String="",
    val designation:String="", val effectiveRole:String="Employee", val isManager:Boolean=false, val profilePhotoUrl:String=""
)
data class TaskItem(
    val taskId:String="", val instanceId:String="", val title:String="", val category:String="", val department:String="",
    val employeeName:String="", val employeeId:String="", val dueDate:String="", val frequency:String="", val status:String="Pending",
    val proofRequired:Boolean=false, val canComplete:Boolean=true, val canTransfer:Boolean=false
)
data class TicketItem(
    val ticketId:String="", val description:String="", val department:String="", val category:String="", val urgency:String="",
    val status:String="", val raisedByName:String="", val raisedByEmployeeId:String="", val assignedToName:String="",
    val assignedToEmployeeId:String="", val createdOn:String="", val dueDate:String="",
    val isCreatedByMe:Boolean=false, val isAssignedToMe:Boolean=false,
    val isCreatedByTeam:Boolean=false, val isAssignedToTeam:Boolean=false, val isOverdue:Boolean=false
)
data class NotificationItem(val id:String="",val type:String="",val title:String="",val message:String="",val createdOn:String="",val isRead:Boolean=false)
data class BootstrapData(
    val user:User=User(), val canViewTeamTasks:Boolean=false, val canViewTeamTickets:Boolean=false,
    val canManageTeamShifts:Boolean=false, val departments:List<String> = emptyList(), val ticketCategories:List<String> = emptyList(),
    val priorities:List<String> = emptyList(), val unreadCount:Int=0
)
data class DashboardData(val raw:JsonObject=JsonObject())
data class LoginResult(val token:String,val user:User)
data class TaskResult(val tasks:List<TaskItem>, val counts:Map<String,Int> = emptyMap())
data class TicketResult(val mine:List<TicketItem>,val team:List<TicketItem>,val canViewTeam:Boolean)
data class ShiftRow(
    val rosterId:String="", val userId:String="", val employeeId:String="", val employeeName:String="",
    val department:String="", val designation:String="", val shiftId:String="", val shiftCode:String="", val shiftName:String="",
    val shiftType:String="", val startTime:String="", val endTime:String="", val effectiveFrom:String="", val effectiveTo:String="",
    val note:String=""
)
data class ShiftOption(
    val shiftId:String="", val shiftCode:String="", val shiftName:String="", val shiftType:String="",
    val startTime:String="", val endTime:String=""
)
data class ShiftEmployee(
    val userId:String="", val employeeId:String="", val name:String="", val department:String="", val designation:String=""
)
data class ShiftResult(
    val rows:List<ShiftRow> = emptyList(),
    val shifts:List<ShiftOption> = emptyList(),
    val employees:List<ShiftEmployee> = emptyList()
)
data class ProfileData(
    val userId:String="", val employeeId:String="", val name:String="", val email:String="", val phone:String="",
    val department:String="", val designation:String="", val role:String="", val isManager:Boolean=false,
    val profilePhotoDataUri:String=""
)

class ApiException(message:String):Exception(message)

object ApiClient {
    private val gson=GsonBuilder().create()
    private data class CacheEntry<T>(val at:Long,val value:T)
    private val taskCache=mutableMapOf<String,CacheEntry<TaskResult>>()
    private var ticketCache:CacheEntry<TicketResult>?=null
    private const val CACHE_MS=120000L
    private val client=OkHttpClient.Builder()
        .connectTimeout(25,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS).writeTimeout(35,TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true).build()
    private val media="text/plain; charset=utf-8".toMediaType()

    private suspend fun call(action:String, token:String?=null, payload:JsonObject=JsonObject()):JsonObject = withContext(Dispatchers.IO){
        val root=JsonObject().apply {
            addProperty("action",action)
            if(!token.isNullOrBlank()) addProperty("token",token)
            payload.entrySet().forEach{add(it.key,it.value)}
        }
        val req=Request.Builder().url(BuildConfig.BASE_API_URL).post(gson.toJson(root).toRequestBody(media)).build()
        client.newCall(req).execute().use { res ->
            val text=res.body?.string().orEmpty()
            if(!res.isSuccessful) throw ApiException("Server error ${res.code}")
            val obj=try{
                JsonParser.parseString(text).asJsonObject
            }catch(e:Exception){
                val lower=text.lowercase()
                if(lower.contains("<html") || lower.contains("<!doctype") || lower.contains("script function not found")) {
                    throw ApiException("Mobile API is not active on the PMS deployment. Add/deploy Mobile_API.gs, then try again.")
                }
                throw ApiException("PMS returned an invalid response")
            }
            if(!(obj.get("success")?.asBoolean ?: false)) throw ApiException(obj.get("error")?.asString ?: "Request failed")
            obj
        }
    }

    private fun jo(parent:JsonObject,key:String):JsonObject = parent.get(key)?.takeIf{it.isJsonObject}?.asJsonObject ?: JsonObject()
    private fun s(o:JsonObject,k:String)=o.get(k)?.takeIf{!it.isJsonNull}?.asString ?: ""
    private fun b(o:JsonObject,k:String)=o.get(k)?.takeIf{!it.isJsonNull}?.asBoolean ?: false
    private fun i(o:JsonObject,k:String)=o.get(k)?.takeIf{!it.isJsonNull}?.asInt ?: 0
    private fun arr(o:JsonObject,k:String):JsonArray=o.get(k)?.takeIf{it.isJsonArray}?.asJsonArray ?: JsonArray()

    private fun parseUser(o:JsonObject)=User(s(o,"userId"),s(o,"employeeId"),s(o,"name"),s(o,"department"),s(o,"designation"),s(o,"effectiveRole").ifBlank{"Employee"},b(o,"isManager"),s(o,"profilePhotoUrl"))
    private fun parseTask(o:JsonObject)=TaskItem(s(o,"taskId"),s(o,"instanceId"),s(o,"title"),s(o,"category"),s(o,"department"),s(o,"employeeName"),s(o,"employeeId"),s(o,"dueDate"),s(o,"frequency"),s(o,"status").ifBlank{"Pending"},b(o,"proofRequired"),b(o,"canComplete"),b(o,"canTransfer"))
    private fun parseTicket(o:JsonObject)=TicketItem(
        s(o,"ticketId"),s(o,"description"),s(o,"department"),s(o,"category"),s(o,"urgency"),s(o,"status"),
        s(o,"raisedByName"),s(o,"raisedByEmployeeId"),s(o,"assignedToName"),s(o,"assignedToEmployeeId"),
        s(o,"createdOn"),s(o,"dueDate"),b(o,"isCreatedByMe"),b(o,"isAssignedToMe"),
        b(o,"isCreatedByTeam"),b(o,"isAssignedToTeam"),b(o,"isOverdue")
    )
    private fun parseShiftOption(o:JsonObject)=ShiftOption(
        s(o,"ShiftID"),s(o,"ShiftCode"),s(o,"ShiftName"),s(o,"ShiftType"),s(o,"StartTime"),s(o,"EndTime")
    )
    private fun parseShift(o:JsonObject)=ShiftRow(
        s(o,"RosterID"),s(o,"UserID"),s(o,"EmployeeID"),s(o,"EmployeeName"),
        s(o,"Department"),s(o,"Designation"),s(o,"ShiftID"),s(o,"ShiftCode"),s(o,"ShiftName"),
        s(o,"ShiftType"),s(o,"StartTime"),s(o,"EndTime"),s(o,"EffectiveFrom"),s(o,"EffectiveTo"),s(o,"Note")
    )
    private fun parseShiftEmployee(o:JsonObject)=ShiftEmployee(
        s(o,"UserID"),s(o,"EmployeeID"),s(o,"Name"),s(o,"Department"),s(o,"Designation")
    )

    suspend fun login(employeeId:String,password:String):LoginResult{
        val p=JsonObject().apply{
            addProperty("employeeId",employeeId)
            addProperty("password",password)
            addProperty("userAgent","LPPL PMS Native Android")
        }
        val r=call("login",payload=p)
        return LoginResult(s(r,"token"),parseUser(jo(r,"user")))
    }

    suspend fun bootstrap(token:String):BootstrapData{
        val d=jo(call("bootstrap",token),"data")
        d.get("taskBundle")?.takeIf{it.isJsonObject}?.asJsonObject?.let{ cacheTaskBundle(token,it) }
        return BootstrapData(
            parseUser(jo(d,"user")),
            b(d,"canViewTeamTasks"),b(d,"canViewTeamTickets"),b(d,"canManageTeamShifts"),
            arr(d,"departments").map{it.asString},arr(d,"ticketCategories").map{it.asString},
            arr(d,"priorities").map{it.asString},i(d,"unreadCount")
        )
    }

    private fun cacheTaskBundle(token:String,d:JsonObject){
        val tabs=jo(d,"tabs")
        val counts=mutableMapOf<String,Int>()
        d.get("counts")?.takeIf{it.isJsonObject}?.asJsonObject?.entrySet()?.forEach{counts[it.key]=it.value.asInt}
        val now=System.currentTimeMillis()
        listOf("today","upcoming","overdue","notdone","onleave","completed").forEach { tab ->
            val list=arr(tabs,tab).map{parseTask(it.asJsonObject)}
            taskCache["$token|MY|$tab"]=CacheEntry(now,TaskResult(list,counts))
        }
    }

    suspend fun dashboard(token:String):DashboardData=DashboardData(jo(call("dashboard",token),"data"))

    suspend fun tasks(token:String,scope:String,tab:String,force:Boolean=false):TaskResult{
        val key="$token|$scope|$tab"
        val now=System.currentTimeMillis()
        if(!force) taskCache[key]?.takeIf{now-it.at<CACHE_MS}?.let{return it.value}
        val p=JsonObject().apply{addProperty("scope",scope);addProperty("tab",tab)}
        val d=jo(call("get_tasks",token,p),"data")
        val list=arr(d,"tasks").map{parseTask(it.asJsonObject)}
        val counts=mutableMapOf<String,Int>()
        d.get("counts")?.takeIf{it.isJsonObject}?.asJsonObject?.entrySet()?.forEach{counts[it.key]=it.value.asInt}
        return TaskResult(list,counts).also{taskCache[key]=CacheEntry(now,it)}
    }

    suspend fun prefetchTaskBundle(token:String){
        runCatching {
            val d=jo(call("get_task_bundle",token),"data")
            cacheTaskBundle(token,d)
        }
    }

    suspend fun prefetchTodayTasks(token:String){ prefetchTaskBundle(token) }

    suspend fun completeTask(token:String,instanceId:String,remark:String="",proofBase64:String?=null,proofFileName:String?=null,proofMime:String?=null){
        val p=JsonObject().apply{
            addProperty("instanceId",instanceId)
            addProperty("remark",remark)
            if(proofBase64!=null)addProperty("proofBase64",proofBase64)
            if(proofFileName!=null)addProperty("proofFileName",proofFileName)
            if(proofMime!=null)addProperty("proofMimeType",proofMime)
        }
        call("complete_task",token,p)
        taskCache.keys.filter{it.startsWith("$token|")}.forEach{taskCache.remove(it)}
    }

    suspend fun tickets(token:String,force:Boolean=false):TicketResult{
        val now=System.currentTimeMillis()
        if(!force) ticketCache?.takeIf{now-it.at<CACHE_MS}?.let{return it.value}
        val d=jo(call("get_tickets",token),"data")
        return TicketResult(
            arr(d,"mine").map{parseTicket(it.asJsonObject)},
            arr(d,"team").map{parseTicket(it.asJsonObject)},
            b(d,"canViewTeam")
        ).also{ticketCache=CacheEntry(now,it)}
    }

    suspend fun shiftRoster(token:String,canManageTeam:Boolean):ShiftResult{
        val action=if(canManageTeam) "get_shift_roster" else "get_my_shift"
        val d=jo(call(action,token),"data")
        return ShiftResult(
            arr(d,"rows").map{parseShift(it.asJsonObject)},
            arr(d,"shifts").map{parseShiftOption(it.asJsonObject)},
            arr(d,"employees").map{parseShiftEmployee(it.asJsonObject)}
        )
    }

    suspend fun saveShiftRoster(
        token:String,
        rosterId:String,
        userId:String,
        shiftId:String,
        effectiveFrom:String,
        effectiveTo:String,
        note:String
    ){
        val input=JsonObject().apply{
            addProperty("RosterID",rosterId)
            addProperty("UserID",userId)
            addProperty("ShiftID",shiftId)
            addProperty("EffectiveFrom",effectiveFrom)
            addProperty("EffectiveTo",effectiveTo)
            addProperty("Note",note)
        }
        val p=JsonObject().apply{add("input",input)}
        call("save_shift_roster",token,p)
        taskCache.clear()
    }

    suspend fun profile(token:String):ProfileData{
        val d=jo(call("get_profile",token),"data")
        return ProfileData(
            s(d,"userId"),s(d,"employeeId"),s(d,"name"),s(d,"email"),s(d,"phone"),
            s(d,"department"),s(d,"designation"),s(d,"role"),b(d,"isManager"),s(d,"profilePhotoDataUri")
        )
    }

    suspend fun notifications(token:String):List<NotificationItem>{
        val d=jo(call("get_notifications",token),"data")
        return arr(d,"rows").map{
            val o=it.asJsonObject
            NotificationItem(s(o,"id"),s(o,"type"),s(o,"title"),s(o,"message"),s(o,"createdOn"),b(o,"isRead"))
        }
    }

    suspend fun markAllNotificationsRead(token:String){call("mark_all_notifications_read",token)}
    suspend fun logout(token:String){
        taskCache.clear(); ticketCache=null
        runCatching{call("logout",token)}
    }
}
