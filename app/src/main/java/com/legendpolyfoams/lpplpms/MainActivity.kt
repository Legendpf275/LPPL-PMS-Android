package com.legendpolyfoams.lpplpms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val LpplGreen = Color(0xFF39A844)
private val LpplDark = Color(0xFF16752A)
private val Bg = Color(0xFFF4F7F4)
private val TextPrimary = Color(0xFF17211A)
private val TextMuted = Color(0xFF6B756D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(57,168,68)
        window.navigationBarColor = android.graphics.Color.WHITE
        setContent { LpplApp(SessionStore(this)) }
    }
}

@Composable
private fun LpplApp(session: SessionStore) {
    var token by remember { mutableStateOf(session.token) }
    var bootstrap by remember { mutableStateOf<BootstrapData?>(null) }
    var loading by remember { mutableStateOf(token.isNotBlank()) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token) {
        if (token.isBlank()) { loading=false; bootstrap=null; return@LaunchedEffect }
        loading=true; error=""
        runCatching { ApiClient.bootstrap(token) }
            .onSuccess { bootstrap=it }
            .onFailure { error=it.message ?: "Unable to connect"; session.clear(); token="" }
        loading=false
    }

    MaterialTheme(colorScheme = lightColorScheme(primary=LpplGreen, secondary=LpplDark, background=Bg, surface=Color.White)) {
        Surface(Modifier.fillMaxSize(), color=Bg) {
            when {
                token.isBlank() -> LoginScreen(
                    busy=loading,
                    error=error,
                    onLogin={id,pw ->
                        scope.launch {
                            loading=true; error=""
                            runCatching{ApiClient.login(id,pw)}
                                .onSuccess{session.token=it.token; token=it.token; bootstrap=BootstrapData(user=it.user)}
                                .onFailure{error=it.message ?: "Login failed"}
                            loading=false
                        }
                    }
                )
                loading || bootstrap==null -> LoadingScreen(error)
                else -> MainShell(token,bootstrap!!,onLogout={scope.launch{ApiClient.logout(token);session.clear();token=""}})
            }
        }
    }
}

@Composable
private fun LoadingScreen(error:String){
    Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            CircularProgressIndicator(color=LpplGreen)
            if(error.isNotBlank()){Spacer(Modifier.height(12.dp));Text(error,color=Color.Red,fontSize=13.sp)}
        }
    }
}

@Composable
private fun LoginScreen(busy:Boolean,error:String,onLogin:(String,String)->Unit){
    var id by remember{mutableStateOf("")}
    var pw by remember{mutableStateOf("")}
    Box(Modifier.fillMaxSize().background(Color(0xFFF0F8F1)).padding(22.dp),contentAlignment=Alignment.Center){
        Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(3.dp)){
            Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Surface(
                    modifier=Modifier.width(190.dp).height(78.dp),
                    shape=RoundedCornerShape(12.dp),
                    color=Color.White,
                    border=androidx.compose.foundation.BorderStroke(2.dp, LpplGreen)
                ){
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            Text("LPPL",fontWeight=FontWeight.Black,fontSize=26.sp,color=LpplGreen)
                            Text("PMS",fontWeight=FontWeight.ExtraBold,fontSize=15.sp,color=LpplDark)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("LPPL PMS",fontWeight=FontWeight.Black,fontSize=25.sp,color=LpplDark)
                Text("Process Management System",fontSize=13.sp,color=TextMuted)
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(value=id,onValueChange={id=it},label={Text("Employee ID")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value=pw,onValueChange={pw=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
                if(error.isNotBlank()){Spacer(Modifier.height(8.dp));Text(error,color=Color(0xFFC62828),fontSize=12.sp)}
                Spacer(Modifier.height(16.dp))
                Button(onClick={onLogin(id,pw)},enabled=!busy && id.isNotBlank() && pw.isNotBlank(),modifier=Modifier.fillMaxWidth().height(48.dp),shape=RoundedCornerShape(12.dp)){
                    if(busy) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=Color.White) else Text("Log in",fontWeight=FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                Text("Only active LPPL employees can enter this portal.",fontSize=11.sp,color=TextMuted)
                Text("Native build 1.0.1",fontSize=10.sp,color=Color(0xFF9AA29B))
            }
        }
    }
}

enum class Page { HOME,TASKS,TICKETS,ALERTS,MORE }

@Composable
private fun MainShell(token:String, boot:BootstrapData, onLogout:()->Unit){
    var page by remember{mutableStateOf(Page.HOME)}
    Scaffold(
        topBar={TopBar(page,boot.user,boot.unreadCount){page=Page.ALERTS}},
        bottomBar={BottomNav(page){page=it}},
        containerColor=Bg
    ){pad ->
        Box(Modifier.padding(pad).fillMaxSize()){
            when(page){
                Page.HOME->DashboardScreen(token,boot)
                Page.TASKS->TasksScreen(token,boot)
                Page.TICKETS->TicketsScreen(token,boot)
                Page.ALERTS->AlertsScreen(token)
                Page.MORE->MoreScreen(boot,onLogout)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(page:Page,user:User,unread:Int,onBell:()->Unit){
    TopAppBar(
        title={Text(when(page){
            Page.HOME->"Dashboard"
            Page.TASKS->"Tasks"
            Page.TICKETS->"Help Tickets"
            Page.ALERTS->"Notifications"
            Page.MORE->"More"
        },fontWeight=FontWeight.Bold)},
        actions={
            IconButton(onClick=onBell){
                BadgedBox(badge={if(unread>0) Badge{Text(if(unread>99)"99+" else "$unread")}}){
                    Icon(Icons.Default.Notifications,"Notifications")
                }
            }
            Box(Modifier.padding(end=12.dp).size(38.dp).background(Color.White,CircleShape),contentAlignment=Alignment.Center){
                Text(
                    user.name.trim().split(" ").mapNotNull{it.firstOrNull()?.toString()}.take(2).joinToString(""),
                    color=LpplDark,fontWeight=FontWeight.Black
                )
            }
        },
        colors=TopAppBarDefaults.topAppBarColors(containerColor=LpplGreen,titleContentColor=Color.White,actionIconContentColor=Color.White)
    )
}

@Composable
private fun BottomNav(page:Page,onChange:(Page)->Unit){
    NavigationBar(containerColor=Color.White){
        listOf(
            Page.HOME to Icons.Default.Home,
            Page.TASKS to Icons.Default.Checklist,
            Page.TICKETS to Icons.Default.ConfirmationNumber,
            Page.ALERTS to Icons.Default.Notifications,
            Page.MORE to Icons.Default.MoreHoriz
        ).forEach{(p,ic)->
            NavigationBarItem(
                selected=page==p,
                onClick={onChange(p)},
                icon={Icon(ic,null)},
                label={Text(when(p){
                    Page.HOME->"Home";Page.TASKS->"Tasks";Page.TICKETS->"Tickets";Page.ALERTS->"Alerts";Page.MORE->"More"
                })}
            )
        }
    }
}

@Composable
private fun DashboardScreen(token:String,boot:BootstrapData){
    var data by remember{mutableStateOf<DashboardData?>(null)}
    var err by remember{mutableStateOf("")}
    LaunchedEffect(Unit){
        runCatching{ApiClient.dashboard(token)}.onSuccess{data=it}.onFailure{err=it.message?:""}
    }
    LazyColumn(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            Text("Good day, ${boot.user.name}",fontWeight=FontWeight.Black,fontSize=24.sp,color=TextPrimary)
            Text("${boot.user.designation} · ${boot.user.department}",fontSize=13.sp,color=TextMuted)
        }
        if(data==null){
            item{if(err.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth(),color=LpplGreen) else ErrorCard(err)}
        } else {
            val raw=data!!.raw
            val mode=raw.get("mode")?.asString.orEmpty()
            if(mode=="PERSONAL"){
                val c=raw.getAsJsonObject("counts")
                val metrics=listOf(
                    "Today" to (c?.get("today")?.asInt?:0),
                    "Completed Today" to (c?.get("completedToday")?.asInt?:0),
                    "My Overdue" to (c?.get("overdue")?.asInt?:0),
                    "My Not Done" to (c?.get("notDone")?.asInt?:0),
                    "On Leave" to (c?.get("onLeave")?.asInt?:0),
                    "Open Tickets" to (c?.get("openTickets")?.asInt?:0)
                )
                items(metrics.chunked(2)){row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        row.forEach{m->MetricCard(m.first,m.second.toString(),Modifier.weight(1f))}
                        if(row.size==1)Spacer(Modifier.weight(1f))
                    }
                }
                item{MetricCard("Completion Rate","${raw.get("completionRate")?.asInt?:0}%",Modifier.fillMaxWidth())}
                raw.getAsJsonObject("team")?.let{t->
                    item{Text("Team",fontWeight=FontWeight.Bold,fontSize=18.sp)}
                    item{
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                            MetricCard("Employees",(t.get("employees")?.asInt?:0).toString(),Modifier.weight(1f))
                            MetricCard("Overdue",(t.get("overdue")?.asInt?:0).toString(),Modifier.weight(1f))
                        }
                    }
                }
            } else {
                val cards=raw.getAsJsonObject("cards")
                item{
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        MetricCard("Company Overdue",(cards?.get("overdue")?.asInt?:0).toString(),Modifier.weight(1f))
                        MetricCard("Company Not Done",(cards?.get("notDone")?.asInt?:0).toString(),Modifier.weight(1f))
                    }
                }
                item{
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        MetricCard("Open Tickets",(cards?.get("openTickets")?.asInt?:0).toString(),Modifier.weight(1f))
                        MetricCard("Completion Rate","${cards?.get("completionRate")?.asInt?:0}%",Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label:String,value:String,modifier:Modifier){
    Card(modifier,shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
        Column(Modifier.padding(14.dp)){
            Text(label.uppercase(),fontSize=10.sp,color=TextMuted,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value,fontSize=24.sp,fontWeight=FontWeight.Black,color=if(label.contains("Overdue")||label.contains("Not Done"))Color(0xFFD32F2F) else LpplGreen)
        }
    }
}

@Composable
private fun TasksScreen(token:String,boot:BootstrapData){
    var scopeSel by remember{mutableStateOf("MY")}
    var tab by remember{mutableStateOf("today")}
    var result by remember{mutableStateOf<TaskResult?>(null)}
    var err by remember{mutableStateOf("")}
    var busyId by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    fun reload(){
        scope.launch{
            err=""
            runCatching{ApiClient.tasks(token,scopeSel,tab)}
                .onSuccess{result=it}
                .onFailure{err=it.message?:""}
        }
    }
    LaunchedEffect(scopeSel,tab){reload()}
    Column(Modifier.fillMaxSize()){
        if(boot.canViewTeamTasks) Segmented(listOf("MY" to "My","TEAM" to "Team"),scopeSel){scopeSel=it}
        TaskTabs(tab){tab=it}
        if(err.isNotBlank()) ErrorCard(err)
        val list=result?.tasks
        if(list==null){
            LinearProgressIndicator(Modifier.fillMaxWidth(),color=LpplGreen)
        } else if(list.isEmpty()){
            EmptyState("No ${tab.replaceFirstChar{it.uppercase()}} tasks")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                items(list,key={it.instanceId}){task->
                    TaskRow(task,busyId==task.instanceId,onDone={
                        busyId=task.instanceId
                        scope.launch{
                            runCatching{ApiClient.completeTask(token,task.instanceId)}
                                .onSuccess{
                                    // Required LPPL behavior: after Done, remain on Today.
                                    tab="today"
                                    result=result?.copy(tasks=result!!.tasks.filterNot{it.instanceId==task.instanceId})
                                    reload()
                                }
                                .onFailure{err=it.message?:"Unable to complete task"}
                            busyId=""
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun TaskTabs(selected:String,onSelect:(String)->Unit){
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf(
            "today" to "Today","upcoming" to "Upcoming","overdue" to "Overdue",
            "notdone" to "Not Done","onleave" to "On Leave","completed" to "Completed"
        ).forEach{(k,l)->FilterChip(selected=selected==k,onClick={onSelect(k)},label={Text(l)})}
    }
}

@Composable
private fun TaskRow(t:TaskItem,busy:Boolean,onDone:()->Unit){
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
        Column(Modifier.padding(11.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(t.taskId,fontWeight=FontWeight.Bold,fontSize=12.sp,color=LpplDark)
                    Spacer(Modifier.width(6.dp))
                    AssistChip(onClick={},label={Text(t.frequency.ifBlank{"TASK"},fontSize=10.sp)})
                }
                StatusChip(t.status)
            }
            Text(t.title,fontWeight=FontWeight.SemiBold,fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=TextPrimary)
            Spacer(Modifier.height(5.dp))
            Text(
                listOf(t.dueDate,t.category,if(t.employeeName.isNotBlank())t.employeeName else "").filter{it.isNotBlank()}.joinToString(" • "),
                fontSize=11.sp,color=TextMuted,maxLines=1,overflow=TextOverflow.Ellipsis
            )
            if(t.canComplete && t.status.lowercase()!="completed"){
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                    Button(onClick=onDone,enabled=!busy,contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.height(34.dp)){
                        if(busy) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=Color.White)
                        else {
                            Icon(Icons.Default.CheckCircle,null,Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mark Done",fontSize=12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketsScreen(token:String,boot:BootstrapData){
    var scopeSel by remember{mutableStateOf("MY")}
    var data by remember{mutableStateOf<TicketResult?>(null)}
    var status by remember{mutableStateOf("ALL")}
    var filtersOpen by remember{mutableStateOf(false)}
    var err by remember{mutableStateOf("")}
    LaunchedEffect(Unit){
        runCatching{ApiClient.tickets(token)}.onSuccess{data=it}.onFailure{err=it.message?:""}
    }
    Box(Modifier.fillMaxSize()){
        Column(Modifier.fillMaxSize()){
            if(boot.canViewTeamTickets) Segmented(listOf("MY" to "My","TEAM" to "Team"),scopeSel){scopeSel=it}
            Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=6.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("ALL","OPEN","IN PROGRESS","WAITING","RESOLVED","CLOSED").forEach{s->
                        FilterChip(selected=status==s,onClick={status=s},label={Text(s.lowercase().replaceFirstChar{it.uppercase()},fontSize=11.sp)})
                    }
                }
                IconButton(onClick={filtersOpen=!filtersOpen}){Icon(Icons.Default.Tune,"Filters")}
            }
            if(filtersOpen){
                Card(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
                    Column(Modifier.padding(10.dp)){
                        Text("Filters",fontWeight=FontWeight.Bold)
                        Text("Department, category, urgency and date filters are collapsed by default.",fontSize=11.sp,color=TextMuted)
                        TextButton(onClick={filtersOpen=false}){Text("Collapse")}
                    }
                }
            }
            if(err.isNotBlank()) ErrorCard(err)
            val list=(if(scopeSel=="TEAM") data?.team else data?.mine)?.filter{
                status=="ALL" || it.status.uppercase()==status || (status=="OPEN" && it.status.uppercase() in listOf("OPEN","ASSIGNED"))
            }
            if(list==null){
                LinearProgressIndicator(Modifier.fillMaxWidth(),color=LpplGreen)
            } else if(list.isEmpty()){
                EmptyState("No tickets")
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    items(list,key={it.ticketId}){TicketRow(it)}
                }
            }
        }
        FloatingActionButton(
            onClick={},
            containerColor=LpplGreen,
            contentColor=Color.White,
            modifier=Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ){Icon(Icons.Default.Add,"New Ticket")}
    }
}

@Composable
private fun TicketRow(t:TicketItem){
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
        Column(Modifier.padding(11.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                StatusChip(t.status)
                Text(t.ticketId,fontSize=11.sp,fontWeight=FontWeight.Bold,color=TextMuted)
            }
            Spacer(Modifier.height(5.dp))
            Text(t.description,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text("${t.department} • ${t.category} • ${t.urgency}",fontSize=11.sp,color=TextMuted)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text("By ${t.raisedByName}",fontSize=11.sp,color=TextMuted,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                Text(t.dueDate,fontSize=11.sp,color=if(t.isOverdue)Color.Red else TextMuted)
            }
        }
    }
}

@Composable
private fun AlertsScreen(token:String){
    var rows by remember{mutableStateOf<List<NotificationItem>?>(null)}
    var err by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    fun load(){
        scope.launch{
            runCatching{ApiClient.notifications(token)}.onSuccess{rows=it}.onFailure{err=it.message?:""}
        }
    }
    LaunchedEffect(Unit){load()}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.End){
            TextButton(onClick={scope.launch{ApiClient.markAllNotificationsRead(token);load()}}){Text("Mark all read")}
        }
        if(err.isNotBlank())ErrorCard(err)
        if(rows==null) LinearProgressIndicator(Modifier.fillMaxWidth(),color=LpplGreen)
        else LazyColumn(Modifier.padding(horizontal=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(rows!!,key={it.id}){n->
                Card(colors=CardDefaults.cardColors(containerColor=if(n.isRead)Color.White else Color(0xFFEAF7EC))){
                    Column(Modifier.padding(12.dp)){
                        Text(n.title,fontWeight=FontWeight.Bold,fontSize=13.sp)
                        Text(n.message,fontSize=12.sp,color=TextMuted,maxLines=3,overflow=TextOverflow.Ellipsis)
                        Text(n.createdOn,fontSize=10.sp,color=TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(boot:BootstrapData,onLogout:()->Unit){
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{InfoRow(Icons.Default.Person,"${boot.user.employeeId} · ${boot.user.name}",boot.user.effectiveRole)}
        if(boot.canManageTeamShifts)item{InfoRow(Icons.Default.Schedule,"Shift Roster","Manage direct-report shifts")}
        item{InfoRow(Icons.Default.Settings,"Settings","Profile and preferences")}
        item{
            OutlinedButton(onClick=onLogout,modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Default.Logout,null);Spacer(Modifier.width(6.dp));Text("Log out")
            }
        }
    }
}

@Composable
private fun InfoRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,sub:String){
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)){
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(icon,null,tint=LpplGreen);Spacer(Modifier.width(12.dp))
            Column{Text(title,fontWeight=FontWeight.Bold);Text(sub,fontSize=11.sp,color=TextMuted)}
        }
    }
}

@Composable
private fun StatusChip(status:String){
    val pair=when(status.lowercase()){
        "completed","closed","resolved"->Color(0xFFDDF4E1) to Color(0xFF1B7C2D)
        "overdue"->Color(0xFFFFE4E4) to Color(0xFFB71C1C)
        "in progress"->Color(0xFFFFF0CF) to Color(0xFF9A6200)
        else->Color(0xFFEAF0EC) to Color(0xFF4F5B52)
    }
    Box(Modifier.background(pair.first,RoundedCornerShape(20.dp)).padding(horizontal=8.dp,vertical=3.dp)){
        Text(status.ifBlank{"Pending"},fontSize=10.sp,fontWeight=FontWeight.Bold,color=pair.second)
    }
}

@Composable
private fun Segmented(items:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit){
    Row(Modifier.fillMaxWidth().padding(10.dp).background(Color(0xFFE3EFE5),RoundedCornerShape(16.dp)).padding(4.dp)){
        items.forEach{(k,l)->
            Box(
                Modifier.weight(1f).background(if(selected==k)Color.White else Color.Transparent,RoundedCornerShape(12.dp))
                    .clickable{onSelect(k)}.padding(vertical=9.dp),
                contentAlignment=Alignment.Center
            ){
                Text(l,fontWeight=FontWeight.Bold,color=if(selected==k)LpplDark else TextMuted)
            }
        }
    }
}

@Composable
private fun ErrorCard(msg:String){
    Card(Modifier.fillMaxWidth().padding(10.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFFFEBEE))){
        Text(msg,Modifier.padding(10.dp),color=Color(0xFFB71C1C),fontSize=12.sp)
    }
}

@Composable
private fun EmptyState(msg:String){
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(msg,color=TextMuted,fontSize=13.sp)}
}
