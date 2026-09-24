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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
            .onSuccess {
                bootstrap=it
                launch { ApiClient.prefetchTodayTasks(token) }
            }
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
    var keepSignedIn by remember{mutableStateOf(true)}
    var showPassword by remember{mutableStateOf(false)}

    Box(
        Modifier.fillMaxSize()
            .background(Color(0xFF07111F))
            .padding(horizontal=24.dp, vertical=28.dp),
        contentAlignment=Alignment.Center
    ){
        Column(
            modifier=Modifier.fillMaxWidth(),
            horizontalAlignment=Alignment.CenterHorizontally
        ){
            Surface(
                modifier=Modifier.size(58.dp),
                shape=RoundedCornerShape(12.dp),
                color=LpplGreen
            ){
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        Text("LPPL",fontWeight=FontWeight.Black,fontSize=17.sp,color=Color.White)
                        Text("PMS",fontWeight=FontWeight.Bold,fontSize=10.sp,color=Color.White)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("LPPL PMS",fontWeight=FontWeight.Black,fontSize=28.sp,color=Color.White)
            Spacer(Modifier.height(5.dp))
            Text("Legend Polyfoams Pvt. Ltd.",fontSize=13.sp,color=Color(0xFF93A4BA))
            Text("Process Management System",fontSize=13.sp,color=Color(0xFF93A4BA))
            Spacer(Modifier.height(26.dp))

            Card(
                modifier=Modifier.fillMaxWidth(),
                shape=RoundedCornerShape(16.dp),
                colors=CardDefaults.cardColors(containerColor=Color(0xFF172231)),
                elevation=CardDefaults.cardElevation(defaultElevation=8.dp)
            ){
                Column(Modifier.padding(18.dp)){
                    Text("EMPLOYEE ID",fontWeight=FontWeight.Bold,fontSize=11.sp,color=Color(0xFFC4CFDC))
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(
                        value=id,
                        onValueChange={id=it},
                        placeholder={Text("Enter your Employee ID",color=Color(0xFF7D8B9D))},
                        leadingIcon={Icon(Icons.Default.Badge,null,tint=Color(0xFF91A1B5))},
                        singleLine=true,
                        modifier=Modifier.fillMaxWidth(),
                        colors=OutlinedTextFieldDefaults.colors(
                            focusedTextColor=Color.White,
                            unfocusedTextColor=Color.White,
                            focusedBorderColor=LpplGreen,
                            unfocusedBorderColor=Color(0xFF344257),
                            cursorColor=LpplGreen,
                            focusedContainerColor=Color(0xFF111B28),
                            unfocusedContainerColor=Color(0xFF111B28)
                        ),
                        shape=RoundedCornerShape(10.dp)
                    )

                    Spacer(Modifier.height(16.dp))
                    Text("PASSWORD",fontWeight=FontWeight.Bold,fontSize=11.sp,color=Color(0xFFC4CFDC))
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(
                        value=pw,
                        onValueChange={pw=it},
                        placeholder={Text("Enter your password",color=Color(0xFF7D8B9D))},
                        leadingIcon={Icon(Icons.Default.Lock,null,tint=Color(0xFF91A1B5))},
                        trailingIcon={
                            IconButton(onClick={showPassword=!showPassword}){
                                Icon(
                                    if(showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    "Show password",
                                    tint=Color(0xFF91A1B5)
                                )
                            }
                        },
                        visualTransformation=if(showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine=true,
                        modifier=Modifier.fillMaxWidth(),
                        colors=OutlinedTextFieldDefaults.colors(
                            focusedTextColor=Color.White,
                            unfocusedTextColor=Color.White,
                            focusedBorderColor=LpplGreen,
                            unfocusedBorderColor=Color(0xFF344257),
                            cursorColor=LpplGreen,
                            focusedContainerColor=Color(0xFF111B28),
                            unfocusedContainerColor=Color(0xFF111B28)
                        ),
                        shape=RoundedCornerShape(10.dp)
                    )

                    Row(
                        Modifier.fillMaxWidth().padding(top=6.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween
                    ){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Checkbox(
                                checked=keepSignedIn,
                                onCheckedChange={keepSignedIn=it},
                                colors=CheckboxDefaults.colors(checkedColor=LpplGreen)
                            )
                            Text("Keep me signed in",fontSize=12.sp,color=Color(0xFFB7C3D1))
                        }
                        Text("Forgot password?",fontSize=12.sp,color=Color(0xFF65E783),fontWeight=FontWeight.SemiBold)
                    }

                    if(error.isNotBlank()){
                        Spacer(Modifier.height(6.dp))
                        Text(error,color=Color(0xFFFF7B7B),fontSize=12.sp)
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick={onLogin(id,pw)},
                        enabled=!busy && id.isNotBlank() && pw.isNotBlank(),
                        modifier=Modifier.fillMaxWidth().height(52.dp),
                        shape=RoundedCornerShape(10.dp),
                        colors=ButtonDefaults.buttonColors(
                            containerColor=Color(0xFF40D064),
                            disabledContainerColor=Color(0xFF2E6541)
                        )
                    ){
                        if(busy){
                            CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=Color.White)
                        }else{
                            Text("Sign in to PMS",fontWeight=FontWeight.Black,color=Color(0xFF07111F))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward,null,tint=Color(0xFF07111F),modifier=Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.Shield,null,tint=Color(0xFF728196),modifier=Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Secure login • LPPL authorized users only",fontSize=11.sp,color=Color(0xFF728196))
            }
            Spacer(Modifier.height(10.dp))
            Text("LPPL PMS v1.0.1",fontSize=10.sp,color=Color(0xFF55657A))
        }
    }
}

enum class Page { HOME,TASKS,TICKETS,SHIFTS,ALERTS,MORE }

@Composable
private fun MainShell(token:String, boot:BootstrapData, onLogout:()->Unit){
    var page by remember{mutableStateOf(Page.HOME)}
    Scaffold(
        topBar={TopBar(page,boot.user,boot.unreadCount){page=Page.ALERTS}},
        bottomBar={BottomNav(page){page=it}},
        containerColor=Color(0xFFF7FAF8)
    ){pad ->
        Box(Modifier.padding(pad).fillMaxSize()){
            when(page){
                Page.HOME->DashboardScreen(token,boot,onTasks={page=Page.TASKS},onShifts={page=Page.SHIFTS})
                Page.TASKS->TasksScreen(token,boot)
                Page.TICKETS->TicketsScreen(token,boot)
                Page.SHIFTS->ShiftRosterScreen(token,boot)
                Page.ALERTS->AlertsScreen(token)
                Page.MORE->MoreScreen(boot,onShiftRoster={page=Page.SHIFTS},onLogout=onLogout)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(page:Page,user:User,unread:Int,onBell:()->Unit){
    val title=when(page){
        Page.HOME->"PMS Dashboard"
        Page.TASKS->"Tasks"
        Page.TICKETS->"Help Tickets"
        Page.SHIFTS->"Shift Roster"
        Page.ALERTS->"Notifications"
        Page.MORE->"More"
    }
    TopAppBar(
        title={
            Row(verticalAlignment=Alignment.CenterVertically){
                Surface(modifier=Modifier.size(34.dp),shape=RoundedCornerShape(8.dp),color=LpplGreen){
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            Text("LPPL",fontSize=9.sp,fontWeight=FontWeight.Black,color=Color.White)
                            Text("PMS",fontSize=7.sp,fontWeight=FontWeight.Bold,color=Color.White)
                        }
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column{
                    Text(title,fontWeight=FontWeight.Black,fontSize=17.sp,color=Color(0xFF0D1B2A))
                    Text("Legend Polyfoams",fontSize=9.sp,color=Color(0xFF64748B))
                }
            }
        },
        actions={
            IconButton(onClick=onBell){
                BadgedBox(badge={if(unread>0) Badge(containerColor=Color(0xFFE60023)){Text(if(unread>99)"99+" else "$unread",color=Color.White)}}){
                    Icon(Icons.Default.Notifications,"Notifications",tint=Color(0xFF26364A))
                }
            }
            Surface(
                modifier=Modifier.padding(end=10.dp),
                shape=RoundedCornerShape(24.dp),
                color=Color(0xFFE9FFF0),
                border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF7EE6A2))
            ){
                Row(Modifier.padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(24.dp).background(LpplGreen,CircleShape),contentAlignment=Alignment.Center){
                        Text(user.name.trim().firstOrNull()?.uppercaseChar()?.toString()?:"U",color=Color.White,fontWeight=FontWeight.Black,fontSize=11.sp)
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(user.effectiveRole,fontSize=10.sp,color=Color(0xFF16813A),fontWeight=FontWeight.SemiBold)
                }
            }
        },
        colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.White)
    )
}

@Composable
private fun BottomNav(page:Page,onChange:(Page)->Unit){
    NavigationBar(containerColor=Color.White,tonalElevation=3.dp){
        listOf(
            Page.HOME to Icons.Default.Dashboard,
            Page.TASKS to Icons.Default.TaskAlt,
            Page.TICKETS to Icons.Default.SupportAgent,
            Page.SHIFTS to Icons.Default.CalendarMonth,
            Page.MORE to Icons.Default.MoreHoriz
        ).forEach{(p,ic)->
            NavigationBarItem(
                selected=page==p,
                onClick={onChange(p)},
                colors=NavigationBarItemDefaults.colors(
                    selectedIconColor=LpplGreen,selectedTextColor=LpplDark,
                    indicatorColor=Color(0xFFE7F8EB),
                    unselectedIconColor=Color(0xFF66758B),unselectedTextColor=Color(0xFF66758B)
                ),
                icon={Icon(ic,null)},
                label={Text(when(p){
                    Page.HOME->"Home";Page.TASKS->"Tasks";Page.TICKETS->"Tickets";Page.SHIFTS->"Shifts";Page.MORE->"More"
                    else->""
                },fontSize=10.sp)}
            )
        }
    }
}

@Composable
private fun DashboardScreen(token:String,boot:BootstrapData,onTasks:()->Unit,onShifts:()->Unit){
    var data by remember{mutableStateOf<DashboardData?>(null)}
    var err by remember{mutableStateOf("")}
    val today=remember{LocalDate.now()}
    val dateText=remember(today){today.format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy",Locale.ENGLISH))}
    LaunchedEffect(Unit){
        runCatching{ApiClient.dashboard(token)}.onSuccess{data=it}.onFailure{err=it.message?:""}
        launch { ApiClient.prefetchTodayTasks(token) }
    }

    val raw=data?.raw
    val mode=raw?.get("mode")?.asString.orEmpty()
    val counts=raw?.getAsJsonObject("counts")
    val cards=raw?.getAsJsonObject("cards")
    val personal=mode=="PERSONAL" || mode.isBlank()
    val todayCount=if(personal) counts?.get("today")?.asInt?:0 else cards?.get("today")?.asInt?:0
    val completed=if(personal) counts?.get("completedToday")?.asInt?:0 else cards?.get("completedToday")?.asInt?:0
    val overdue=if(personal) counts?.get("overdue")?.asInt?:0 else cards?.get("overdue")?.asInt?:0
    val notDone=if(personal) counts?.get("notDone")?.asInt?:0 else cards?.get("notDone")?.asInt?:0
    val onLeave=if(personal) counts?.get("onLeave")?.asInt?:0 else cards?.get("onLeave")?.asInt?:0
    val openTickets=if(personal) counts?.get("openTickets")?.asInt?:0 else cards?.get("openTickets")?.asInt?:0
    val completion=if(personal) raw?.get("completionRate")?.asInt?:0 else cards?.get("completionRate")?.asInt?:0

    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFFF7FAF8)).padding(horizontal=16.dp,vertical=14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        item{
            Card(
                shape=RoundedCornerShape(16.dp),
                colors=CardDefaults.cardColors(containerColor=Color.White),
                border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFD9E2EA))
            ){
                Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Default.CalendarMonth,null,tint=Color(0xFF00A56A),modifier=Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(dateText,fontSize=10.sp,color=Color(0xFF00855A),fontWeight=FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Hello, ${boot.user.name.ifBlank{"Employee"}}",fontSize=16.sp,fontWeight=FontWeight.Black,color=Color(0xFF07111F))
                        Spacer(Modifier.height(2.dp))
                        Text("${boot.user.department.ifBlank{"LPPL"}} • ${boot.user.effectiveRole}",fontSize=10.sp,color=Color(0xFF557085))
                    }
                    Surface(shape=RoundedCornerShape(13.dp),color=Color(0xFFE9FFF1),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF82E9A9))){
                        Column(Modifier.padding(horizontal=13.dp,vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally){
                            Text("HISTORICAL",fontSize=9.sp,fontWeight=FontWeight.Bold,color=Color(0xFF14763B))
                            Text("${completion}%",fontSize=21.sp,fontWeight=FontWeight.Black,color=Color(0xFF14883E))
                            Text("Completion",fontSize=8.sp,color=Color(0xFF14883E))
                        }
                    }
                }
            }
        }

        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Text(if(personal)"TODAY & MY TASKS" else "COMPANY TODAY",fontSize=12.sp,fontWeight=FontWeight.Black,color=Color(0xFF0B1C2B))
                TextButton(onClick=onTasks,contentPadding=PaddingValues(horizontal=2.dp,vertical=0.dp)){Text("View All →",fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color(0xFF008A3E))}
            }
        }

        if(data==null && err.isBlank()){
            item{LinearProgressIndicator(Modifier.fillMaxWidth(),color=LpplGreen)}
        } else if(err.isNotBlank()){
            item{ErrorCard(err)}
        } else {
            item{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    DashboardMetricCard("Today",todayCount,"Assigned for today",Icons.Default.Schedule,Color(0xFF246BFD),Modifier.weight(1f))
                    DashboardMetricCard("Completed Today",completed,"Done & verified",Icons.Default.CheckCircle,Color(0xFF00A56A),Modifier.weight(1f))
                }
            }
            item{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    DashboardMetricCard(if(personal)"My Overdue" else "Company Overdue",overdue,"Requires action",Icons.Default.WarningAmber,Color(0xFFE60023),Modifier.weight(1f))
                    DashboardMetricCard(if(personal)"My Not Done" else "Company Not Done",notDone,"Past missed tasks",Icons.Default.Cancel,Color(0xFFF06A00),Modifier.weight(1f))
                }
            }
            item{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    DashboardMetricCard("On Leave",onLeave,"Leave marked",Icons.Default.PersonOff,Color(0xFF53657D),Modifier.weight(1f))
                    DashboardMetricCard(if(personal)"My Open Tickets" else "Open Tickets",openTickets,"Pending resolution",Icons.Default.SupportAgent,Color(0xFF9B23FF),Modifier.weight(1f))
                }
            }
        }

        item{
            Card(
                shape=RoundedCornerShape(14.dp),
                colors=CardDefaults.cardColors(containerColor=Color.White),
                border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFD9E2EA))
            ){
                Column(Modifier.padding(12.dp)){
                    Text("QUICK OPERATIONS",fontSize=10.sp,fontWeight=FontWeight.Black,color=Color(0xFF0B1C2B))
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        QuickOperation("My Today Tasks",Icons.Default.TaskAlt,Modifier.weight(1f),onTasks)
                        QuickOperation("Shift Roster",Icons.Default.CalendarMonth,Modifier.weight(1f),onShifts)
                    }
                }
            }
        }
        item{Spacer(Modifier.height(4.dp))}
    }
}

@Composable
private fun DashboardMetricCard(title:String,value:Int,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,accent:Color,modifier:Modifier){
    Card(
        modifier=modifier.height(86.dp),
        shape=RoundedCornerShape(13.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),
        border=androidx.compose.foundation.BorderStroke(1.dp,if(title.contains("Overdue"))Color(0xFFFFB4B4) else Color(0xFFDCE4EA))
    ){
        Column(Modifier.fillMaxSize().padding(11.dp),verticalArrangement=Arrangement.SpaceBetween){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Text(title,fontSize=10.sp,color=Color(0xFF27405A))
                Icon(icon,null,tint=accent,modifier=Modifier.size(16.dp))
            }
            Text(value.toString(),fontSize=20.sp,fontWeight=FontWeight.Black,color=accent)
            Text(subtitle,fontSize=8.sp,color=Color(0xFF8290A3))
        }
    }
}

@Composable
private fun QuickOperation(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier,onClick:()->Unit){
    Surface(
        modifier=modifier.clickable(onClick=onClick),
        shape=RoundedCornerShape(10.dp),
        color=Color(0xFFF8FBFD),
        border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFD7E1E8))
    ){
        Row(Modifier.padding(horizontal=10.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(icon,null,tint=Color(0xFF536D86),modifier=Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(label,Modifier.weight(1f),fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF162A3B))
            Text("→",color=Color(0xFF71849A),fontSize=14.sp)
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
private fun ShiftRosterScreen(token:String,boot:BootstrapData){
    var data by remember{mutableStateOf<ShiftResult?>(null)}
    var err by remember{mutableStateOf("")}
    LaunchedEffect(Unit){
        runCatching{ApiClient.shiftRoster(token,boot.canManageTeamShifts)}
            .onSuccess{data=it}
            .onFailure{err=it.message?:"Unable to load shift roster"}
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFFF7FAF8)).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)
    ){
        item{
            Card(
                colors=CardDefaults.cardColors(containerColor=Color.White),
                border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFD9E2EA)),
                shape=RoundedCornerShape(15.dp)
            ){
                Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.CalendarMonth,null,tint=LpplGreen,modifier=Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                    Column{
                        Text("LPPL Operational Shift Roster",fontSize=13.sp,fontWeight=FontWeight.Black,color=Color(0xFF0E6F31))
                        Text(
                            if(boot.canManageTeamShifts)"Direct-report team shift schedule" else "Your current assigned shift",
                            fontSize=10.sp,color=Color(0xFF65758B)
                        )
                    }
                }
            }
        }

        item{
            Text(
                if(boot.canManageTeamShifts)"TEAM SHIFT ROSTER" else "MY ASSIGNED SHIFT",
                fontSize=11.sp,fontWeight=FontWeight.Black,color=Color(0xFF172A3A)
            )
        }

        if(err.isNotBlank()){
            item{ErrorCard(err)}
        } else if(data==null){
            item{LinearProgressIndicator(Modifier.fillMaxWidth(),color=LpplGreen)}
        } else if(data!!.rows.isEmpty()){
            item{
                Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(13.dp)){
                    Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        Icon(Icons.Default.EventBusy,null,tint=Color(0xFF8290A3),modifier=Modifier.size(30.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No active shift assignment found",fontWeight=FontWeight.Bold,color=Color(0xFF33475B))
                        Text("Contact your reporting manager if a shift should be assigned.",fontSize=10.sp,color=Color(0xFF8290A3))
                    }
                }
            }
        } else {
            items(data!!.rows,key={it.rosterId.ifBlank{it.employeeId+it.shiftCode}}){row->
                Card(
                    colors=CardDefaults.cardColors(containerColor=Color.White),
                    border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFD9E2EA)),
                    shape=RoundedCornerShape(13.dp)
                ){
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically
                    ){
                        Column(Modifier.weight(1f)){
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Text(row.employeeName.ifBlank{boot.user.name},fontWeight=FontWeight.Black,fontSize=13.sp,color=Color(0xFF102033))
                                if(row.employeeId.isNotBlank()){
                                    Spacer(Modifier.width(6.dp))
                                    Surface(shape=RoundedCornerShape(4.dp),color=Color(0xFFF0F5F8)){
                                        Text(row.employeeId,Modifier.padding(horizontal=5.dp,vertical=2.dp),fontSize=8.sp,color=Color(0xFF53657D))
                                    }
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                listOf(row.department,row.shiftType.ifBlank{row.shiftName}).filter{it.isNotBlank()}.joinToString(" • "),
                                fontSize=10.sp,color=Color(0xFF52718C)
                            )
                            if(row.startTime.isNotBlank() || row.endTime.isNotBlank()){
                                Spacer(Modifier.height(3.dp))
                                Text("${row.startTime} - ${row.endTime}  (${row.shiftCode})",fontSize=9.sp,color=Color(0xFF6F83A0))
                            }
                        }
                        Surface(shape=RoundedCornerShape(6.dp),color=Color(0xFFF2F6F9)){
                            Text("Read-only",Modifier.padding(horizontal=7.dp,vertical=5.dp),fontSize=8.sp,color=Color(0xFF8092AA))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(boot:BootstrapData,onShiftRoster:()->Unit,onLogout:()->Unit){
    var settingsOpen by remember{mutableStateOf(false)}
    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFFF7FAF8)).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)
    ){
        item{
            InfoRow(
                Icons.Default.Person,
                "${boot.user.employeeId} · ${boot.user.name}",
                "${boot.user.department} • ${boot.user.effectiveRole}",
                onClick={}
            )
        }
        item{
            InfoRow(
                Icons.Default.CalendarMonth,
                "Shift Roster",
                if(boot.canManageTeamShifts)"View direct-report team shifts" else "View my assigned shift",
                onClick=onShiftRoster
            )
        }
        item{
            InfoRow(
                Icons.Default.Settings,
                "Settings",
                "Profile and app preferences",
                onClick={settingsOpen=!settingsOpen}
            )
        }
        if(settingsOpen){
            item{
                Card(
                    colors=CardDefaults.cardColors(containerColor=Color.White),
                    border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFD9E2EA)),
                    shape=RoundedCornerShape(13.dp)
                ){
                    Column(Modifier.padding(14.dp)){
                        Text("PROFILE & SETTINGS",fontSize=10.sp,fontWeight=FontWeight.Black,color=Color(0xFF172A3A))
                        Spacer(Modifier.height(10.dp))
                        SettingLine("Employee ID",boot.user.employeeId)
                        SettingLine("Name",boot.user.name)
                        SettingLine("Department",boot.user.department)
                        SettingLine("Designation",boot.user.designation)
                        SettingLine("Role",boot.user.effectiveRole)
                        SettingLine("App","LPPL PMS Native")
                    }
                }
            }
        }
        item{
            OutlinedButton(
                onClick=onLogout,
                modifier=Modifier.fillMaxWidth(),
                shape=RoundedCornerShape(10.dp)
            ){
                Icon(Icons.Default.Logout,null);Spacer(Modifier.width(6.dp));Text("Log out")
            }
        }
    }
}

@Composable
private fun SettingLine(label:String,value:String){
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.SpaceBetween){
        Text(label,fontSize=10.sp,color=Color(0xFF7B8A9C))
        Text(value.ifBlank{"—"},fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF20364A))
    }
}

@Composable
private fun InfoRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,sub:String,onClick:()->Unit){
    Card(
        Modifier.fillMaxWidth().clickable(onClick=onClick),
        colors=CardDefaults.cardColors(containerColor=Color.White),
        border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFD9E2EA)),
        shape=RoundedCornerShape(13.dp)
    ){
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            Surface(shape=RoundedCornerShape(9.dp),color=Color(0xFFEAF8EE)){
                Icon(icon,null,tint=LpplGreen,modifier=Modifier.padding(8.dp).size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){
                Text(title,fontWeight=FontWeight.Bold,color=Color(0xFF14283A))
                Text(sub,fontSize=10.sp,color=Color(0xFF7A8A9B))
            }
            Icon(Icons.Default.ChevronRight,null,tint=Color(0xFF91A0B1),modifier=Modifier.size(18.dp))
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
