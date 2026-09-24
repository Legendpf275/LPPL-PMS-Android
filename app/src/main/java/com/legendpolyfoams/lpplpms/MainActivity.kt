package com.legendpolyfoams.lpplpms

import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.app.DatePickerDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val LpplGreen = Color(0xFF39A844)
private val LpplDark = Color(0xFF16752A)
private val Bg = Color(0xFFF7FAF8)
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
                if(ApiClient.cachedTasks(token,"MY","today")==null) launch { ApiClient.prefetchTaskBundle(token) }
                launch { runCatching { ApiClient.dashboard(token) } }
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
    var taskOpenTab by remember{mutableStateOf("today")}
    var profile by remember{mutableStateOf<ProfileData?>(null)}
    var unread by remember(token){mutableIntStateOf(boot.unreadCount)}

    LaunchedEffect(token){
        runCatching{ApiClient.profile(token)}.onSuccess{profile=it}
    }
    LaunchedEffect(token,page){
        while(true){
            runCatching{ApiClient.notificationCount(token)}.onSuccess{unread=it}
            delay(60000)
        }
    }

    Scaffold(
        topBar={TopBar(page,boot.user,profile,unread){page=Page.ALERTS}},
        bottomBar={BottomNav(page){page=it}},
        containerColor=Color(0xFFF7FAF8)
    ){pad ->
        Box(Modifier.padding(pad).fillMaxSize()){
            when(page){
                Page.HOME->DashboardScreen(
                    token,boot,
                    onTaskTab={tab->taskOpenTab=tab;page=Page.TASKS},
                    onTickets={page=Page.TICKETS},
                    onShifts={page=Page.SHIFTS}
                )
                Page.TASKS->TasksScreen(token,boot,taskOpenTab)
                Page.TICKETS->TicketsScreen(token,boot)
                Page.SHIFTS->ShiftRosterScreen(token,boot)
                Page.ALERTS->AlertsScreen(token){unread=it}
                Page.MORE->MoreScreen(boot,profile,onShiftRoster={page=Page.SHIFTS},onLogout=onLogout)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(page:Page,user:User,profile:ProfileData?,unread:Int,onBell:()->Unit){
    val title=when(page){
        Page.HOME->"PMS Dashboard"
        Page.TASKS->"Tasks"
        Page.TICKETS->"Help Tickets"
        Page.SHIFTS->"Shift Roster"
        Page.ALERTS->"Notifications"
        Page.MORE->"More"
    }
    val initials=user.name.trim().split(Regex("\\s+")).mapNotNull{it.firstOrNull()?.uppercaseChar()?.toString()}.take(2).joinToString("").ifBlank{"U"}
    TopAppBar(
        title={
            Row(verticalAlignment=Alignment.CenterVertically){
                Surface(modifier=Modifier.size(38.dp),shape=RoundedCornerShape(8.dp),color=Color.White){
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(0.dp)){
                            Text("LPPL",fontSize=10.sp,lineHeight=10.sp,fontWeight=FontWeight.Black,color=LpplGreen)
                            Text("PMS",fontSize=9.sp,lineHeight=9.sp,fontWeight=FontWeight.Black,color=LpplDark)
                        }
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column{
                    Text(title,fontWeight=FontWeight.Black,fontSize=17.sp,color=Color.White)
                    Text("Legend Polyfoams Pvt. Ltd.",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFFE5F6E9),maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        },
        actions={
            IconButton(onClick=onBell){
                BadgedBox(badge={if(unread>0) Badge(containerColor=Color(0xFFE60023)){Text(if(unread>99)"99+" else unread.toString(),color=Color.White)}}){
                    Icon(Icons.Default.Notifications,"Notifications",tint=Color.White)
                }
            }
            Surface(
                modifier=Modifier.padding(end=10.dp),
                shape=RoundedCornerShape(24.dp),
                color=Color(0xFF238F3A),
                border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF86E49B))
            ){
                Row(Modifier.padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
                    ProfileAvatar(profile?.profilePhotoDataUri.orEmpty(),user.name,26.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(initials,fontSize=11.sp,color=Color.White,fontWeight=FontWeight.Black)
                }
            }
        },
        colors=TopAppBarDefaults.topAppBarColors(containerColor=LpplGreen)
    )
}

@Composable
private fun ProfileAvatar(dataUri:String,name:String,size:androidx.compose.ui.unit.Dp){
    val imageBitmap=remember(dataUri){
        if(dataUri.isBlank()) null else runCatching{
            val raw=dataUri.substringAfter(",",dataUri)
            val bytes=Base64.decode(raw,Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if(imageBitmap!=null){
        Image(
            bitmap=imageBitmap,
            contentDescription="Profile photo",
            modifier=Modifier.size(size).clip(CircleShape),
            contentScale=ContentScale.Crop
        )
    }else{
        Box(Modifier.size(size).background(LpplGreen,CircleShape),contentAlignment=Alignment.Center){
            Text(name.trim().firstOrNull()?.uppercaseChar()?.toString()?:"U",color=Color.White,fontWeight=FontWeight.Black,fontSize=11.sp)
        }
    }
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
private fun DashboardScreen(
    token:String,
    boot:BootstrapData,
    onTaskTab:(String)->Unit,
    onTickets:()->Unit,
    onShifts:()->Unit
){
    var data by remember{mutableStateOf(ApiClient.cachedDashboard())}
    var err by remember{mutableStateOf("")}
    val today=remember{LocalDate.now()}
    val dateText=remember(today){today.format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy",Locale.ENGLISH))}
    LaunchedEffect(token){
        runCatching{ApiClient.dashboard(token)}.onSuccess{data=it}.onFailure{err=it.message?:""}
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
    val greetingName=boot.user.name.ifBlank { "Employee" }
    val greetingDepartment=boot.user.department.ifBlank { "LPPL" }

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
                        Text("Hello, " + greetingName,fontSize=16.sp,fontWeight=FontWeight.Black,color=Color(0xFF07111F))
                        Spacer(Modifier.height(2.dp))
                        Text(greetingDepartment + " • " + boot.user.effectiveRole,fontSize=10.sp,color=Color(0xFF557085))
                    }
                    Surface(shape=RoundedCornerShape(13.dp),color=Color(0xFFE9FFF1),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF82E9A9))){
                        Column(Modifier.padding(horizontal=13.dp,vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally){
                            Text("HISTORICAL",fontSize=9.sp,fontWeight=FontWeight.Bold,color=Color(0xFF14763B))
                            Text(completion.toString()+"%",fontSize=21.sp,fontWeight=FontWeight.Black,color=Color(0xFF14883E))
                            Text("Completion",fontSize=8.sp,color=Color(0xFF14883E))
                        }
                    }
                }
            }
        }

        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Text(if(personal)"TODAY & MY TASKS" else "COMPANY TODAY",fontSize=12.sp,fontWeight=FontWeight.Black,color=Color(0xFF0B1C2B))
                TextButton(onClick={onTaskTab("today")},contentPadding=PaddingValues(horizontal=2.dp,vertical=0.dp)){
                    Text("View All →",fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color(0xFF008A3E))
                }
            }
        }

        if(data==null && err.isBlank()){
            item{LinearProgressIndicator(Modifier.fillMaxWidth(),color=LpplGreen)}
        } else if(err.isNotBlank()){
            item{ErrorCard(err)}
        } else {
            item{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    DashboardMetricCard("Today",todayCount,"Assigned for today",Icons.Default.Schedule,Color(0xFF246BFD),Modifier.weight(1f)){onTaskTab("today")}
                    DashboardMetricCard("Completed Today",completed,"Done & verified",Icons.Default.CheckCircle,Color(0xFF00A56A),Modifier.weight(1f)){onTaskTab("completed")}
                }
            }
            item{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    DashboardMetricCard(if(personal)"My Overdue" else "Company Overdue",overdue,"Requires action",Icons.Default.WarningAmber,Color(0xFFE60023),Modifier.weight(1f)){onTaskTab("overdue")}
                    DashboardMetricCard(if(personal)"My Not Done" else "Company Not Done",notDone,"Past missed tasks",Icons.Default.Cancel,Color(0xFFF06A00),Modifier.weight(1f)){onTaskTab("notdone")}
                }
            }
            item{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    DashboardMetricCard("On Leave",onLeave,"Leave marked",Icons.Default.PersonOff,Color(0xFF53657D),Modifier.weight(1f)){onTaskTab("onleave")}
                    DashboardMetricCard(if(personal)"My Open Tickets" else "Open Tickets",openTickets,"Pending resolution",Icons.Default.SupportAgent,Color(0xFF9C27FF),Modifier.weight(1f)){onTickets()}
                }
            }
        }

        item{
            Card(
                shape=RoundedCornerShape(16.dp),
                colors=CardDefaults.cardColors(containerColor=Color.White),
                border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFD9E2EA))
            ){
                Column(Modifier.padding(12.dp)){
                    Text("QUICK OPERATIONS",fontSize=10.sp,fontWeight=FontWeight.Black,color=Color(0xFF172A3A))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        QuickOperation("My Today Tasks",Icons.Default.TaskAlt,Modifier.weight(1f)){onTaskTab("today")}
                        QuickOperation("Shift Roster",Icons.Default.CalendarMonth,Modifier.weight(1f)){onShifts()}
                    }
                }
            }
        }
        item{Spacer(Modifier.height(4.dp))}
    }
}

@Composable
private fun DashboardMetricCard(
    title:String,
    value:Int,
    subtitle:String,
    icon:androidx.compose.ui.graphics.vector.ImageVector,
    accent:Color,
    modifier:Modifier,
    onClick:()->Unit
){
    Card(
        modifier=modifier.height(86.dp).clickable(onClick=onClick),
        shape=RoundedCornerShape(13.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),
        border=androidx.compose.foundation.BorderStroke(1.5.dp,accent)
    ){
        Column(Modifier.fillMaxSize().padding(11.dp),verticalArrangement=Arrangement.SpaceBetween){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Text(title,fontSize=10.sp,color=Color(0xFF27405A))
                Icon(icon,null,tint=accent,modifier=Modifier.size(16.dp))
            }
            Text(value.toString(),fontSize=20.sp,fontWeight=FontWeight.Black,color=accent)
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(subtitle,fontSize=8.sp,color=Color(0xFF8290A3),modifier=Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight,null,tint=Color(0xFFA7B2BF),modifier=Modifier.size(13.dp))
            }
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
private fun TasksScreen(token:String,boot:BootstrapData,initialTab:String="today"){
    var scopeSel by remember{mutableStateOf("MY")}
    var tab by remember(initialTab){mutableStateOf(initialTab)}
    var result by remember(token,scopeSel,tab){mutableStateOf(ApiClient.cachedTasks(token,scopeSel,tab))}
    var err by remember{mutableStateOf("")}
    var busyId by remember{mutableStateOf("")}
    var transferOpen by remember{mutableStateOf(false)}
    var selecting by remember{mutableStateOf(false)}
    val selectedIds= remember { mutableStateListOf<String>() }
    var transferTargets by remember{mutableStateOf<List<TransferTarget>>(emptyList())}
    var transferError by remember{mutableStateOf("")}
    var transferDepartment by remember{mutableStateOf("")}
    var transferUser by remember{mutableStateOf<TransferTarget?>(null)}
    var departmentExpanded by remember{mutableStateOf(false)}
    var employeeExpanded by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()

    fun reload(force:Boolean=false){
        scope.launch{
            err=""
            runCatching{ApiClient.tasks(token,scopeSel,tab,force)}
                .onSuccess{result=it}
                .onFailure{err=it.message?:""}
        }
    }

    LaunchedEffect(scopeSel,tab){selectedIds.clear();selecting=false;reload(false)}

    fun openTransfer(){
        transferError="";transferDepartment="";transferUser=null;transferTargets=emptyList();transferOpen=true
        scope.launch{runCatching{ApiClient.transferTargets(token)}
            .onSuccess{transferTargets=it}
            .onFailure{transferError=it.message?:"Unable to load employees"}}
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF7FAF8))){
        if(boot.canViewTeamTasks) Segmented(listOf("MY" to "My","TEAM" to "Team"),scopeSel){scopeSel=it}
        TaskTabs(tab,result?.counts ?: emptyMap()){tab=it}
        if(result?.tasks?.any{it.canTransfer}==true || selectedIds.isNotEmpty()){
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={selecting=!selecting;if(!selecting)selectedIds.clear()}){
                    Text(if(selecting)"Cancel selection" else "Select tasks to transfer")
                }
                Spacer(Modifier.weight(1f))
                if(selecting){
                    Button(onClick={openTransfer()},enabled=selectedIds.isNotEmpty()){
                        Text("Transfer selected (${selectedIds.size})")
                    }
                }
            }
        }
        if(err.isNotBlank()) ErrorCard(err)
        val list=result?.tasks
        if(list==null){
            LinearProgressIndicator(Modifier.fillMaxWidth(),color=LpplGreen)
        } else if(list.isEmpty() && tab=="today" && scopeSel=="MY"){
            Box(Modifier.fillMaxSize().padding(18.dp),contentAlignment=Alignment.Center){
                Card(
                    colors=CardDefaults.cardColors(containerColor=Color(0xFFE9FFF0)),
                    border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF75DEA0)),
                    shape=RoundedCornerShape(22.dp)
                ){
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=34.dp),
                        horizontalAlignment=Alignment.CenterHorizontally
                    ){
                        Surface(shape=CircleShape,color=Color(0xFF2FC95A)){
                            Icon(Icons.Default.TaskAlt,null,tint=Color.White,modifier=Modifier.padding(14.dp).size(34.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("All tasks are completed",fontSize=24.sp,fontWeight=FontWeight.Black,color=Color(0xFF0B6D2B))
                        Spacer(Modifier.height(7.dp))
                        Text("Great work! You’re all caught up for today.",fontSize=13.sp,color=Color(0xFF4E725B))
                    }
                }
            }
        } else if(list.isEmpty()){
            EmptyState("No " + tab.replaceFirstChar{it.uppercase()} + " tasks")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                items(list.distinctBy{it.instanceId},key={it.instanceId}){task->
                    TaskRow(task,scopeSel=="TEAM",busyId==task.instanceId,
                        selecting=selecting,selected=selectedIds.contains(task.instanceId),onSelect={
                            if(selectedIds.contains(task.instanceId))selectedIds.remove(task.instanceId)
                            else {
                                val first=list.firstOrNull{selectedIds.contains(it.instanceId)}
                                if(first!=null && first.dueDate.take(10)!=task.dueDate.take(10))
                                    err="Select tasks from one date at a time"
                                else {err="";selectedIds.add(task.instanceId)}
                            }
                        },onTransfer={selectedIds.clear();selectedIds.add(task.instanceId);selecting=true;openTransfer()},onDone={
                        busyId=task.instanceId
                        scope.launch{
                            runCatching{ApiClient.completeTask(token,task.instanceId)}
                                .onSuccess{
                                    tab="today"
                                    result=result?.copy(tasks=result!!.tasks.filterNot{it.instanceId==task.instanceId})
                                    launch{ApiClient.prefetchTaskBundle(token)}
                                }
                                .onFailure{err=it.message?:"Unable to complete task"}
                            busyId=""
                        }
                    })
                }
            }
        }
    }
    if(transferOpen){
        val chosen=result?.tasks?.filter{selectedIds.contains(it.instanceId)}.orEmpty()
        val departments=transferTargets.map{it.department}.filter{it.isNotBlank()}.distinct().sorted()
        val employees=transferTargets.filter{it.department==transferDepartment && chosen.none{task->task.employeeId==it.employeeId}}
        AlertDialog(
            onDismissRequest={if(busyId.isBlank())transferOpen=false},
            title={Text("Select Department & User",fontWeight=FontWeight.Bold,fontSize=18.sp)},
            text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("${chosen.size} task(s) selected",fontSize=12.sp,color=TextMuted)
                if(transferError.isNotBlank())Text(transferError,color=Color.Red)
                if(transferTargets.isEmpty()&&transferError.isBlank())CircularProgressIndicator(Modifier.size(20.dp))
                Box{
                    OutlinedButton(onClick={departmentExpanded=true},modifier=Modifier.fillMaxWidth(),enabled=departments.isNotEmpty()){
                        Text(transferDepartment.ifBlank{"Choose a department"},modifier=Modifier.weight(1f));Text("▾")
                    }
                    DropdownMenu(expanded=departmentExpanded,onDismissRequest={departmentExpanded=false}){
                        departments.forEach{dept->DropdownMenuItem(text={Text(dept)},onClick={transferDepartment=dept;transferUser=null;departmentExpanded=false})}
                    }
                }
                Box{
                    OutlinedButton(onClick={employeeExpanded=true},modifier=Modifier.fillMaxWidth(),enabled=transferDepartment.isNotBlank()){
                        Text(transferUser?.let{it.employeeId+" · "+it.name}?:"Select employee",modifier=Modifier.weight(1f));Text("▾")
                    }
                    DropdownMenu(expanded=employeeExpanded,onDismissRequest={employeeExpanded=false}){
                        employees.forEach{target->DropdownMenuItem(text={Text(target.employeeId+" · "+target.name)},onClick={transferUser=target;employeeExpanded=false})}
                    }
                }
            }},
            confirmButton={Button(onClick={
                val target=transferUser?:return@Button
                busyId="transfer"
                scope.launch{runCatching{ApiClient.transferTasks(token,chosen,target.userId)}
                    .onSuccess{transferOpen=false;selectedIds.clear();selecting=false;result=null;reload(true)}
                    .onFailure{transferError=it.message?:"Transfer failed"}
                    busyId=""}
            },enabled=busyId.isBlank()&&transferUser!=null&&chosen.isNotEmpty()){Text("Submit")}},
            dismissButton={TextButton(onClick={transferOpen=false},enabled=busyId.isBlank()){Text("Cancel")}}
        )
    }
}

@Composable
private fun TaskTabs(selected:String,counts:Map<String,Int>,onSelect:(String)->Unit){
    fun countFor(key:String):Int = when(key){
        "notdone" -> counts["notdone"] ?: counts["notDone"] ?: 0
        "onleave" -> counts["onleave"] ?: counts["onLeave"] ?: 0
        else -> counts[key] ?: 0
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
        listOf(
            "today" to "Today","upcoming" to "Upcoming","overdue" to "Overdue",
            "notdone" to "Not Done","onleave" to "On Leave","completed" to "Completed"
        ).forEach{(k,l)->
            FilterChip(
                selected=selected==k,
                onClick={onSelect(k)},
                label={Text(l+" "+countFor(k),fontSize=10.sp,fontWeight=if(selected==k)FontWeight.Bold else FontWeight.Medium)},
                colors=FilterChipDefaults.filterChipColors(
                    selectedContainerColor=Color(0xFFE7F8EB),
                    selectedLabelColor=Color(0xFF0C7C35),
                    containerColor=Color.White,
                    labelColor=Color(0xFF53657D)
                ),
                border=FilterChipDefaults.filterChipBorder(
                    enabled=true,
                    selected=selected==k,
                    borderColor=Color(0xFFD8E2E9),
                    selectedBorderColor=Color(0xFF79D998)
                )
            )
        }
    }
}

@Composable
private fun TaskRow(t:TaskItem,isTeam:Boolean,busy:Boolean,selecting:Boolean,selected:Boolean,onSelect:()->Unit,onTransfer:()->Unit,onDone:()->Unit){
    Card(
        Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(12.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),
        border=androidx.compose.foundation.BorderStroke(
            1.dp,
            if(t.status.equals("Overdue",true)) Color(0xFFFFB2B2) else Color(0xFFDCE5EA)
        )
    ){
        Column(Modifier.padding(horizontal=12.dp,vertical=10.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Row(verticalAlignment=Alignment.CenterVertically){
                    if(selecting&&t.canTransfer) Checkbox(checked=selected,onCheckedChange={onSelect()},modifier=Modifier.size(36.dp))
                    Text(t.taskId,fontWeight=FontWeight.Black,fontSize=12.sp,color=Color(0xFF078A37))
                    if(t.frequency.isNotBlank()){
                        Spacer(Modifier.width(5.dp))
                        Surface(shape=RoundedCornerShape(4.dp),color=Color(0xFFEAF3FF),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFB8D4FF))){
                            Text(t.frequency,Modifier.padding(horizontal=5.dp,vertical=2.dp),fontSize=9.sp,color=Color(0xFF246BFD))
                        }
                    }
                    if(t.proofRequired){
                        Spacer(Modifier.width(5.dp))
                        Surface(shape=RoundedCornerShape(4.dp),color=Color(0xFFE9FAF0)){
                            Row(Modifier.padding(horizontal=5.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
                                Icon(Icons.Default.AttachFile,null,tint=Color(0xFF16813A),modifier=Modifier.size(10.dp))
                                Text("Proof",fontSize=8.sp,color=Color(0xFF16813A))
                            }
                        }
                    }
                }
                StatusChip(t.status)
            }
            Spacer(Modifier.height(7.dp))
            Text(t.title,fontWeight=FontWeight.Bold,fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=Color(0xFF0E1F31))
            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color=Color(0xFFE9EEF2))
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                val meta=listOf(t.category,if(isTeam)t.employeeName else "").filter{it.isNotBlank()}.joinToString(" • ")
                Text(meta.ifBlank{t.department},fontSize=12.sp,color=Color(0xFF60758B),modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                if(t.canTransfer&&!selecting){
                    TextButton(onClick=onTransfer,enabled=!busy){Text("Transfer",fontSize=11.sp)}
                }
                if(t.canComplete && !t.status.equals("completed",true)){
                    Button(
                        onClick=onDone,enabled=!busy,
                        contentPadding=PaddingValues(horizontal=10.dp,vertical=0.dp),
                        modifier=Modifier.height(30.dp),
                        shape=RoundedCornerShape(6.dp)
                    ){
                        if(busy) CircularProgressIndicator(Modifier.size(14.dp),strokeWidth=2.dp,color=Color.White)
                        else {
                            Icon(Icons.Default.CheckCircle,null,Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Mark Done",fontSize=10.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class TicketMedia(val uri:Uri,val mimeType:String,val fallbackName:String)

private fun newTicketMediaUri(context:Context,extension:String):Uri{
    val folder=File(context.cacheDir,"ticket_media").apply{mkdirs()}
    val file=File(folder,"ticket_${UUID.randomUUID()}.$extension")
    return FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
}

private fun ticketDisplayName(context:Context,media:TicketMedia):String = runCatching{
    context.contentResolver.query(media.uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
        if(cursor.moveToFirst())cursor.getString(0) else null
    }
}.getOrNull()?.takeIf{it.isNotBlank()} ?: media.fallbackName

private suspend fun readTicketUpload(context:Context,media:TicketMedia):TicketUpload=withContext(Dispatchers.IO){
    val maxBytes=10_000_000
    val name=runCatching{
        context.contentResolver.query(media.uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
            if(cursor.moveToFirst())cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf{it.isNotBlank()} ?: media.fallbackName
    val bytes=context.contentResolver.openInputStream(media.uri)?.use{input->
        val out=java.io.ByteArrayOutputStream()
        val buffer=ByteArray(8192)
        while(true){
            val read=input.read(buffer)
            if(read<0)break
            if(out.size()+read>maxBytes)throw ApiException("Each attachment must be under 10 MB. Record a shorter video.")
            out.write(buffer,0,read)
        }
        out.toByteArray()
    } ?: throw ApiException("Could not read the selected file")
    TicketUpload(bytes,name.take(150),media.mimeType)
}

@Composable
private fun TicketsScreen(token:String,boot:BootstrapData){
    var scopeSel by remember{mutableStateOf("MY")}
    var ownerSel by remember{mutableStateOf("CREATED")}
    var data by remember{mutableStateOf(ApiClient.cachedTickets())}
    var status by remember{mutableStateOf("ALL")}
    var filtersOpen by remember{mutableStateOf(false)}
    var department by remember{mutableStateOf("ALL")}
    var category by remember{mutableStateOf("ALL")}
    var urgency by remember{mutableStateOf("ALL")}
    var selectedTicket by remember{mutableStateOf<TicketItem?>(null)}
    var createOpen by remember{mutableStateOf(false)}
    var createBusy by remember{mutableStateOf(false)}
    var createError by remember{mutableStateOf("")}
    var newDepartment by remember{mutableStateOf("")}
    var newUser by remember{mutableStateOf<TicketUser?>(null)}
    var newCategory by remember{mutableStateOf("")}
    var newPriority by remember{mutableStateOf("")}
    var newDate by remember{mutableStateOf(LocalDate.now().toString())}
    var newDescription by remember{mutableStateOf("")}
    var newMachine by remember{mutableStateOf("")}
    var attachment by remember{mutableStateOf<TicketMedia?>(null)}
    var voiceNote by remember{mutableStateOf<TicketMedia?>(null)}
    var captureUri by remember{mutableStateOf<Uri?>(null)}
    var recorder by remember{mutableStateOf<MediaRecorder?>(null)}
    var recordingFile by remember{mutableStateOf<File?>(null)}
    var recording by remember{mutableStateOf(false)}
    var err by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    val files=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){
            val mime=context.contentResolver.getType(uri).orEmpty().substringBefore(';').lowercase()
            if(mime in listOf("image/jpeg","image/png","image/webp","application/pdf","video/mp4","video/3gpp"))
                attachment=TicketMedia(uri,mime,"ticket_attachment")
            else createError="Choose a JPG, PNG, WebP, PDF or MP4 video"
        }
    }
    val voiceFiles=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){
            val mime=context.contentResolver.getType(uri).orEmpty().substringBefore(';').lowercase()
            if(mime in listOf("audio/mp4","audio/x-m4a","audio/mpeg","audio/ogg","audio/wav","audio/x-wav","audio/aac","audio/3gpp","audio/3gpp2","audio/amr","audio/webm"))
                voiceNote=TicketMedia(uri,mime,"voice_note")
            else createError="Choose a supported audio file"
        }
    }
    val cameraPhoto=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->
        if(success)captureUri?.let{attachment=TicketMedia(it,"image/jpeg","camera_photo.jpg")}
        captureUri=null
    }
    val cameraVideo=rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()){success->
        if(success)captureUri?.let{attachment=TicketMedia(it,"video/mp4","camera_video.mp4")}
        captureUri=null
    }
    fun startRecording(){
        var next:MediaRecorder?=null
        try{
            val file=File(File(context.cacheDir,"ticket_media").apply{mkdirs()},"voice_${UUID.randomUUID()}.m4a")
            val active=MediaRecorder()
            next=active
            active.setAudioSource(MediaRecorder.AudioSource.MIC)
            active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            active.setAudioEncodingBitRate(64000)
            active.setAudioSamplingRate(44100)
            active.setMaxDuration(120000)
            active.setOutputFile(file.absolutePath)
            active.prepare();active.start()
            recordingFile=file;recorder=active;recording=true;createError=""
        }catch(e:Exception){
            next?.release();recorder=null;recording=false
            createError=e.message?:"Microphone could not start"
        }
    }
    fun stopRecording(save:Boolean){
        val active=recorder
        recorder=null;recording=false
        val file=recordingFile
        recordingFile=null
        if(active!=null){
            val stopped=runCatching{active.stop()}.isSuccess
            active.release()
            if(save&&stopped&&file!=null&&file.length()>0)
                voiceNote=TicketMedia(Uri.fromFile(file),"audio/mp4",file.name)
            else if(save)createError="Voice note was too short. Please record again."
        }
        if(!save)file?.delete()
    }
    val microphonePermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted)startRecording() else createError="Microphone permission is required to record a voice note"
    }
    DisposableEffect(Unit){onDispose{recorder?.run{runCatching{stop()};release()};recordingFile?.delete()}}

    LaunchedEffect(Unit){
        runCatching{ApiClient.tickets(token)}.onSuccess{data=it}.onFailure{err=it.message?:""}
    }

    val source=if(scopeSel=="TEAM") data?.team else data?.mine
    val departments=(boot.departments + (source?.map{it.department} ?: emptyList())).filter{it.isNotBlank()}.distinct().sorted()
    val categories=(boot.ticketCategories + (source?.map{it.category} ?: emptyList())).filter{it.isNotBlank()}.distinct().sorted()
    val urgencies=(boot.priorities + (source?.map{it.urgency} ?: emptyList())).filter{it.isNotBlank()}.distinct().sorted()
    val list=source?.filter{
        val ownerOk=if(scopeSel=="TEAM"){
            if(ownerSel=="CREATED") it.isCreatedByTeam else it.isAssignedToTeam
        }else{
            if(ownerSel=="CREATED") it.isCreatedByMe else it.isAssignedToMe
        }
        val s=it.status.uppercase()
        val statusOk=when(status){
            "OPEN" -> s in listOf("OPEN","ASSIGNED")
            "IN PROGRESS" -> s=="IN PROGRESS"
            "ARCHIVE" -> s in listOf("ARCHIVE","ARCHIVED")
            "CLOSED" -> s in listOf("RESOLVED","CLOSED")
            else -> true
        }
        val deptOk=department=="ALL" || it.department==department
        val catOk=category=="ALL" || it.category==category
        val urgOk=urgency=="ALL" || it.urgency==urgency
        ownerOk && statusOk && deptOk && catOk && urgOk
    }

    Box(Modifier.fillMaxSize().background(Color.White)){
        Column(Modifier.fillMaxSize()){
            if(boot.canViewTeamTickets){
                Segmented(listOf("MY" to "My","TEAM" to "Team"),scopeSel){
                    scopeSel=it;ownerSel="CREATED"
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=3.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically){
                FilterChip(
                    selected=ownerSel=="CREATED",
                    onClick={ownerSel="CREATED"},
                    label={Text(if(scopeSel=="TEAM")"Created by Team" else "Created by Me",fontSize=10.sp,maxLines=1)}
                )
                FilterChip(
                    selected=ownerSel=="ASSIGNED",
                    onClick={ownerSel="ASSIGNED"},
                    label={Text(if(scopeSel=="TEAM")"Assigned to Team" else "Assigned to Me",fontSize=10.sp,maxLines=1)}
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick={filtersOpen=!filtersOpen}){
                    BadgedBox(badge={
                        val active=listOf(department,category,urgency).count{it!="ALL"}
                        if(active>0) Badge{Text(active.toString())}
                    }){Icon(Icons.Default.Tune,"Filters")}
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("ALL","OPEN","IN PROGRESS","ARCHIVE","CLOSED").forEach{s->
                    FilterChip(
                        selected=status==s,
                        onClick={status=s},
                        label={Text(s.lowercase().replaceFirstChar{it.uppercase()},fontSize=10.sp)},
                        colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFFE9E0FF),selectedLabelColor=Color(0xFF44227A))
                    )
                }
            }

            if(filtersOpen){
                Card(
                    Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp),
                    colors=CardDefaults.cardColors(containerColor=Color.White),
                    border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFDCE5EA)),
                    shape=RoundedCornerShape(14.dp)
                ){
                    Column(Modifier.padding(12.dp)){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                            Text("Filters",fontWeight=FontWeight.Black,fontSize=15.sp,color=Color(0xFF182A3B))
                            TextButton(onClick={department="ALL";category="ALL";urgency="ALL"}){Text("Clear all")}
                        }
                        TicketFilterRow("Department",departments,department){department=it}
                        TicketFilterRow("Category",categories,category){category=it}
                        TicketFilterRow("Urgency",urgencies,urgency){urgency=it}
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                            TextButton(onClick={filtersOpen=false}){Text("Collapse")}
                        }
                    }
                }
            }

            if(err.isNotBlank()) ErrorCard(err)
            if(list==null){
                LinearProgressIndicator(Modifier.fillMaxWidth(),color=LpplGreen)
            } else if(list.isEmpty()){
                EmptyState("No tickets match these filters")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal=10.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp),
                    contentPadding=PaddingValues(bottom=90.dp)
                ){
                    items(list,key={it.ticketId}){TicketRow(it){selectedTicket=it}}
                }
            }
        }

        FloatingActionButton(
            onClick={createError="";createOpen=true},
            containerColor=Color(0xFF31B84B),
            contentColor=Color.White,
            modifier=Modifier.align(Alignment.BottomEnd).padding(16.dp),
            shape=RoundedCornerShape(16.dp)
        ){Icon(Icons.Default.Add,"New Ticket")}

        if(createOpen){
            Dialog(onDismissRequest={if(!createBusy){stopRecording(false);createOpen=false}},
                properties=DialogProperties(usePlatformDefaultWidth=false)){
                Surface(Modifier.fillMaxSize(),color=Color.White){
                    Column(Modifier.fillMaxSize()){
                        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                            IconButton(onClick={stopRecording(false);createOpen=false},enabled=!createBusy){Icon(Icons.Default.Close,"Close")}
                            Text("Raise Help Ticket",fontSize=20.sp,fontWeight=FontWeight.Bold,color=TextPrimary)
                        }
                        HorizontalDivider(color=Color(0xFFE2E8E3))
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                            Text("Select a doer and describe the issue.",fontSize=13.sp,color=TextMuted)
                            TicketSelect("Doer department *",newDepartment,boot.departments){newDepartment=it;newUser=null}
                            TicketSelect("Doer *",newUser?.let{it.employeeId+" · "+it.name}.orEmpty(),
                                data?.users?.filter{it.department==newDepartment}?.map{it.employeeId+" · "+it.name}.orEmpty()) { label ->
                                newUser=data?.users?.firstOrNull{it.department==newDepartment && it.employeeId+" · "+it.name==label}
                            }
                            TicketSelect("Category *",newCategory,boot.ticketCategories){newCategory=it}
                            TicketSelect("Urgency *",newPriority,boot.priorities){newPriority=it}
                            OutlinedButton(onClick={
                                val day=runCatching{LocalDate.parse(newDate)}.getOrDefault(LocalDate.now())
                                DatePickerDialog(context,{_,year,month,dayOfMonth->
                                    newDate=LocalDate.of(year,month+1,dayOfMonth).toString()
                                },day.year,day.monthValue-1,day.dayOfMonth).apply{
                                    datePicker.minDate=System.currentTimeMillis()-86400000L
                                }.show()
                            },modifier=Modifier.fillMaxWidth()){
                                Icon(Icons.Default.CalendarMonth,null)
                                Spacer(Modifier.width(8.dp))
                                Text("Due date *: $newDate",modifier=Modifier.weight(1f))
                            }
                            OutlinedTextField(newMachine,{newMachine=it},label={Text("Machine / Area (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(newDescription,{newDescription=it},label={Text("Description *")},minLines=3,modifier=Modifier.fillMaxWidth())
                            Text("Attachment (optional)",fontWeight=FontWeight.SemiBold)
                            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                                OutlinedButton(onClick={files.launch(arrayOf("image/*","application/pdf","video/*"))},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp)){
                                    Icon(Icons.Default.FolderOpen,null,Modifier.size(18.dp));Spacer(Modifier.width(3.dp));Text("Storage",fontSize=12.sp)
                                }
                                OutlinedButton(onClick={
                                    val uri=newTicketMediaUri(context,"jpg");captureUri=uri;cameraPhoto.launch(uri)
                                },modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp)){
                                    Icon(Icons.Default.PhotoCamera,null,Modifier.size(18.dp));Spacer(Modifier.width(3.dp));Text("Photo",fontSize=12.sp)
                                }
                                OutlinedButton(onClick={
                                    val uri=newTicketMediaUri(context,"mp4");captureUri=uri;cameraVideo.launch(uri)
                                },modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp)){
                                    Icon(Icons.Default.Videocam,null,Modifier.size(18.dp));Spacer(Modifier.width(3.dp));Text("Video",fontSize=12.sp)
                                }
                            }
                            attachment?.let{Row(verticalAlignment=Alignment.CenterVertically){
                                Text("Selected: "+ticketDisplayName(context,it),modifier=Modifier.weight(1f),fontSize=12.sp,maxLines=2)
                                IconButton(onClick={attachment=null}){Icon(Icons.Default.Close,"Remove attachment")}
                            }}
                            Text("Voice note (optional)",fontWeight=FontWeight.SemiBold)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                OutlinedButton(onClick={
                                    if(recording)stopRecording(true)
                                    else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startRecording()
                                    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                },modifier=Modifier.weight(1f)){
                                    Icon(if(recording)Icons.Default.Stop else Icons.Default.Mic,null,Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp));Text(if(recording)"Stop" else "Record")
                                }
                                OutlinedButton(onClick={voiceFiles.launch(arrayOf("audio/*"))},modifier=Modifier.weight(1f)){
                                    Text("Choose audio")
                                }
                            }
                            if(recording)Text("Recording… tap Stop when finished",color=Color(0xFFC52B2B),fontSize=12.sp)
                            voiceNote?.let{Row(verticalAlignment=Alignment.CenterVertically){
                                Text("Voice: "+ticketDisplayName(context,it),modifier=Modifier.weight(1f),fontSize=12.sp)
                                IconButton(onClick={voiceNote=null}){Icon(Icons.Default.Close,"Remove voice note")}
                            }}
                            Text("Each file must be under 10 MB. Record a short video.",fontSize=11.sp,color=TextMuted)
                            if(createError.isNotBlank())Text(createError,color=Color.Red,fontSize=12.sp)
                        }
                        HorizontalDivider(color=Color(0xFFE2E8E3))
                        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                            OutlinedButton(onClick={stopRecording(false);createOpen=false},enabled=!createBusy,modifier=Modifier.weight(1f)){Text("Cancel")}
                            Button(onClick={
                                val parsed=runCatching{LocalDate.parse(newDate)}.getOrNull()
                                when {
                                    newDepartment.isBlank()||newUser==null||newCategory.isBlank()||newPriority.isBlank()||newDescription.isBlank()->createError="Complete all required fields"
                                    parsed==null||parsed.isBefore(LocalDate.now())->createError="Choose a valid due date"
                                    recording->createError="Stop recording before submitting"
                                    else->{
                                        createBusy=true;createError=""
                                        scope.launch{
                                            runCatching{
                                                val selectedAttachment=attachment?.let{readTicketUpload(context,it)}
                                                val selectedVoice=voiceNote?.let{readTicketUpload(context,it)}
                                                ApiClient.createTicket(token,newDepartment,newUser!!.userId,newCategory,newPriority,newDate,newDescription.trim(),newMachine.trim(),selectedAttachment,selectedVoice)
                                            }.onSuccess{
                                                createOpen=false;newDescription="";newMachine="";attachment=null;voiceNote=null;data=null
                                                runCatching{ApiClient.tickets(token,true)}.onSuccess{data=it;scopeSel="MY";ownerSel="CREATED"}
                                            }.onFailure{createError=it.message?:"Could not create ticket"}
                                            createBusy=false
                                        }
                                    }
                                }
                            },enabled=!createBusy,modifier=Modifier.weight(1f)){Text(if(createBusy)"Submitting…" else "Submit Ticket")}
                        }
                    }
                }
            }
        }

        selectedTicket?.let{t->
            AlertDialog(
                onDismissRequest={selectedTicket=null},
                title={
                    Column{
                        Text(t.ticketId,fontWeight=FontWeight.Black,fontSize=15.sp,color=LpplDark)
                        StatusChip(if(t.status.equals("Resolved",true))"Closed" else t.status)
                    }
                },
                text={
                    Column(Modifier.verticalScroll(rememberScrollState())){
                        Text(t.description,fontWeight=FontWeight.SemiBold,fontSize=15.sp,color=Color(0xFF102033))
                        Spacer(Modifier.height(12.dp))
                        SettingLine("Department",t.department)
                        SettingLine("Category",t.category)
                        SettingLine("Urgency",t.urgency)
                        SettingLine("Created by",listOf(t.raisedByEmployeeId,t.raisedByName).filter{it.isNotBlank()}.joinToString(" · "))
                        SettingLine("Assigned to",listOf(t.assignedToEmployeeId,t.assignedToName).filter{it.isNotBlank()}.joinToString(" · ").ifBlank{"Unassigned"})
                        if(t.dueDate.isNotBlank()) SettingLine("Due date",t.dueDate)
                    }
                },
                confirmButton={TextButton(onClick={selectedTicket=null}){Text("Close")}}
            )
        }
    }
}

@Composable
private fun TicketSelect(label:String,value:String,options:List<String>,onSelect:(String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Box{
        OutlinedButton(onClick={expanded=true},enabled=options.isNotEmpty(),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.weight(1f)){
                Text(label,fontSize=11.sp,color=TextMuted)
                Text(value.ifBlank{"Select"},maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Text("▾")
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
            options.forEach{option->DropdownMenuItem(text={Text(option)},onClick={onSelect(option);expanded=false})}
        }
    }
}

@Composable
private fun TicketFilterRow(label:String,options:List<String>,selected:String,onSelect:(String)->Unit){
    Text(label.uppercase(),fontSize=9.sp,fontWeight=FontWeight.Black,color=Color(0xFF6E7D8F))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom=7.dp),
        horizontalArrangement=Arrangement.spacedBy(6.dp)
    ){
        FilterChip(selected=selected=="ALL",onClick={onSelect("ALL")},label={Text("All",fontSize=10.sp)})
        options.forEach{value->
            FilterChip(selected=selected==value,onClick={onSelect(value)},label={Text(value,fontSize=10.sp,maxLines=1)})
        }
    }
}

@Composable
private fun TicketRow(t:TicketItem,onClick:()->Unit){
    val shownStatus=if(t.status.equals("Resolved",true))"Closed" else t.status
    Card(
        Modifier.fillMaxWidth().clickable(onClick=onClick),
        shape=RoundedCornerShape(13.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),
        border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFDDE6EC))
    ){
        Column(Modifier.padding(11.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                StatusChip(shownStatus)
                Text(t.ticketId,fontSize=11.sp,fontWeight=FontWeight.Bold,color=TextMuted)
            }
            Spacer(Modifier.height(5.dp))
            Text(t.description,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(listOf(t.department,t.category,t.urgency).filter{it.isNotBlank()}.joinToString(" • "),fontSize=11.sp,color=TextMuted)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text("By "+t.raisedByName,fontSize=11.sp,color=TextMuted,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight,null,tint=Color(0xFF93A1B1),modifier=Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun AlertsScreen(token:String,onUnreadChange:(Int)->Unit){
    var rows by remember{mutableStateOf<List<NotificationItem>?>(null)}
    var err by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    fun load(){
        scope.launch{
            runCatching{ApiClient.notifications(token)}.onSuccess{
                rows=it
                runCatching{ApiClient.notificationCount(token)}.onSuccess(onUnreadChange)
            }.onFailure{err=it.message?:""}
        }
    }
    LaunchedEffect(Unit){load()}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.End){
            TextButton(onClick={scope.launch{runCatching{ApiClient.markAllNotificationsRead(token)}.onSuccess{load()}.onFailure{err=it.message?:""}}}){Text("Mark all read")}
        }
        if(err.isNotBlank())ErrorCard(err)
        if(rows==null) LinearProgressIndicator(Modifier.fillMaxWidth(),color=LpplGreen)
        else LazyColumn(Modifier.padding(horizontal=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(rows!!,key={it.id}){n->
                Card(modifier=Modifier.fillMaxWidth().clickable{
                    if(!n.isRead)scope.launch{runCatching{ApiClient.markNotificationRead(token,n.id)}.onSuccess{load()}.onFailure{err=it.message?:""}}
                },colors=CardDefaults.cardColors(containerColor=if(n.isRead)Color.White else Color(0xFFEAF7EC))){
                    Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
                        if(!n.isRead){Icon(Icons.Default.Circle,null,tint=LpplGreen,modifier=Modifier.size(7.dp));Spacer(Modifier.width(7.dp))}
                        Column(Modifier.weight(1f)){
                            Text(n.title,fontWeight=FontWeight.Bold,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(n.message,fontSize=11.sp,color=TextMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Text(n.createdOn.take(10),fontSize=9.sp,color=TextMuted,maxLines=1)
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
    var reloadKey by remember{mutableStateOf(0)}
    var editRow by remember{mutableStateOf<ShiftRow?>(null)}
    var selectedUserId by remember{mutableStateOf("")}
    var selectedShiftType by remember{mutableStateOf("")}
    var selectedShiftId by remember{mutableStateOf("")}
    var fromDate by remember{mutableStateOf("")}
    var toDate by remember{mutableStateOf("")}
    var note by remember{mutableStateOf("")}
    var saving by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()

    fun displayDate(value:String):String{
        val raw=value.take(10)
        return runCatching{LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))}.getOrElse{raw}
    }
    fun serverDate(value:String):String{
        return runCatching{LocalDate.parse(value,DateTimeFormatter.ofPattern("dd-MM-yyyy")).toString()}.getOrElse{value.take(10)}
    }

    LaunchedEffect(reloadKey){
        err=""
        runCatching{ApiClient.shiftRoster(token,boot.canManageTeamShifts)}
            .onSuccess{data=it}
            .onFailure{err=it.message?:"Unable to load shift roster"}
    }

    Box(Modifier.fillMaxSize()){
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
                        Icon(Icons.Default.CalendarMonth,null,tint=LpplGreen,modifier=Modifier.size(22.dp))
                        Spacer(Modifier.width(9.dp))
                        Column{
                            Text("LPPL Operational Shift Roster",fontSize=14.sp,fontWeight=FontWeight.Black,color=Color(0xFF0E6F31))
                            Text(if(boot.canManageTeamShifts)"Direct-report team shift schedule" else "Your current assigned shift",fontSize=11.sp,color=Color(0xFF65758B))
                        }
                    }
                }
            }

            item{
                Text(if(boot.canManageTeamShifts)"TEAM SHIFT ROSTER" else "MY ASSIGNED SHIFT",fontSize=12.sp,fontWeight=FontWeight.Black,color=Color(0xFF172A3A))
            }

            if(err.isNotBlank()){
                item{ErrorCard(err)}
            } else if(data==null){
                item{LinearProgressIndicator(Modifier.fillMaxWidth(),color=LpplGreen)}
            } else if(data!!.rows.isEmpty()){
                item{EmptyState("No active shift assignment found")}
            } else {
                items(data!!.rows,key={it.rosterId.ifBlank{it.employeeId+it.shiftCode}}){row->
                    Card(
                        colors=CardDefaults.cardColors(containerColor=Color.White),
                        border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFD9E2EA)),
                        shape=RoundedCornerShape(13.dp)
                    ){
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement=Arrangement.SpaceBetween,
                            verticalAlignment=Alignment.CenterVertically
                        ){
                            Column(Modifier.weight(1f)){
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Text(row.employeeName.ifBlank{boot.user.name},fontWeight=FontWeight.Black,fontSize=19.sp,color=Color(0xFF102033))
                                    if(row.employeeId.isNotBlank()){
                                        Spacer(Modifier.width(8.dp))
                                        Surface(shape=RoundedCornerShape(7.dp),color=LpplGreen){
                                            Text(row.employeeId,Modifier.padding(horizontal=10.dp,vertical=6.dp),fontSize=12.sp,fontWeight=FontWeight.Bold,color=Color.White)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(listOf(row.department,row.shiftType.ifBlank{row.shiftName}).filter{it.isNotBlank()}.joinToString(" • "),fontSize=12.sp,color=Color(0xFF52718C))
                                if(row.startTime.isNotBlank() || row.endTime.isNotBlank()){
                                    Spacer(Modifier.height(6.dp))
                                    Text(row.startTime+" - "+row.endTime+"  ("+row.shiftCode+")",fontSize=16.sp,fontWeight=FontWeight.Bold,color=Color(0xFF244C36))
                                }
                            }
                            if(boot.canManageTeamShifts){
                                Button(
                                    onClick={
                                        editRow=row
                                        selectedUserId=row.userId
                                        selectedShiftType=row.shiftType
                                        selectedShiftId=row.shiftId
                                        fromDate=displayDate(row.effectiveFrom)
                                        toDate=displayDate(row.effectiveTo)
                                        note=row.note
                                    },
                                    shape=RoundedCornerShape(8.dp),
                                    contentPadding=PaddingValues(horizontal=10.dp,vertical=5.dp)
                                ){
                                    Icon(Icons.Default.Edit,null,Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Change Shift",fontSize=10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        val row=editRow
        if(row!=null && data!=null){
            val employeeOptions=(data!!.employees.ifEmpty{
                data!!.rows.map{ShiftEmployee(it.userId,it.employeeId,it.employeeName,it.department,it.designation)}.distinctBy{it.userId}
            })
            val shiftTypes=data!!.shifts.map{it.shiftType}.filter{it.isNotBlank()}.distinct()
            val shiftOptions=data!!.shifts.filter{selectedShiftType.isBlank() || it.shiftType==selectedShiftType}
            AlertDialog(
                onDismissRequest={if(!saving)editRow=null},
                title={Text("Edit Shift Roster",fontWeight=FontWeight.Black,fontSize=20.sp)},
                text={
                    Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
                        SelectPopupField(
                            label="Employee *",
                            value=employeeOptions.find{it.userId==selectedUserId}?.let{it.employeeId+" · "+it.name+" · "+it.department}.orEmpty(),
                            options=employeeOptions.map{it.userId to (it.employeeId+" · "+it.name+" · "+it.department)},
                            onSelect={selectedUserId=it}
                        )
                        SelectPopupField(
                            label="Shift Type *",
                            value=selectedShiftType,
                            options=shiftTypes.map{it to it},
                            onSelect={
                                selectedShiftType=it
                                selectedShiftId=data!!.shifts.firstOrNull{sh->sh.shiftType==it}?.shiftId.orEmpty()
                            }
                        )
                        SelectPopupField(
                            label="Shift Format *",
                            value=shiftOptions.find{it.shiftId==selectedShiftId}?.let{it.shiftCode+" - ("+it.shiftName+")"}.orEmpty(),
                            options=shiftOptions.map{it.shiftId to (it.shiftCode+" - ("+it.shiftName+")")},
                            onSelect={selectedShiftId=it}
                        )
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            OutlinedTextField(fromDate,{fromDate=it},label={Text("Effective From *")},singleLine=true,modifier=Modifier.weight(1f))
                            OutlinedTextField(toDate,{toDate=it},label={Text("Effective To *")},singleLine=true,modifier=Modifier.weight(1f))
                        }
                        OutlinedTextField(note,{note=it},label={Text("Note")},placeholder={Text("Rotation / coverage note")},modifier=Modifier.fillMaxWidth())
                    }
                },
                confirmButton={
                    Button(
                        enabled=!saving && selectedUserId.isNotBlank() && selectedShiftId.isNotBlank() && fromDate.isNotBlank() && toDate.isNotBlank(),
                        onClick={
                            saving=true
                            scope.launch{
                                runCatching{
                                    ApiClient.saveShiftRoster(
                                        token,row.rosterId,selectedUserId,selectedShiftId,
                                        serverDate(fromDate),serverDate(toDate),note
                                    )
                                }.onSuccess{editRow=null;reloadKey++}
                                 .onFailure{err=it.message?:"Unable to change shift"}
                                saving=false
                            }
                        }
                    ){if(saving)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=Color.White) else Text("Save Shift")}
                },
                dismissButton={OutlinedButton(enabled=!saving,onClick={editRow=null}){Text("Cancel")}}
            )
        }
    }
}

@Composable
private fun SelectPopupField(label:String,value:String,options:List<Pair<String,String>>,onSelect:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    Column{
        Text(label,fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF243746))
        Box{
            OutlinedButton(
                onClick={open=true},
                modifier=Modifier.fillMaxWidth(),
                shape=RoundedCornerShape(7.dp),
                contentPadding=PaddingValues(horizontal=12.dp,vertical=11.dp)
            ){
                Text(value.ifBlank{"Select"},Modifier.weight(1f),color=Color(0xFF102033),maxLines=1,overflow=TextOverflow.Ellipsis)
                Icon(Icons.Default.ArrowDropDown,null)
            }
            DropdownMenu(expanded=open,onDismissRequest={open=false}){
                options.forEach{(key,text)->
                    DropdownMenuItem(text={Text(text,fontSize=12.sp)},onClick={onSelect(key);open=false})
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(boot:BootstrapData,profile:ProfileData?,onShiftRoster:()->Unit,onLogout:()->Unit){
    var profileOpen by remember{mutableStateOf(false)}
    var settingsOpen by remember{mutableStateOf(false)}
    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFFF7FAF8)).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)
    ){
        item{
            Card(
                Modifier.fillMaxWidth().clickable{profileOpen=!profileOpen},
                colors=CardDefaults.cardColors(containerColor=Color.White),
                border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFD9E2EA)),
                shape=RoundedCornerShape(15.dp)
            ){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                    ProfileAvatar(profile?.profilePhotoDataUri.orEmpty(),boot.user.name,54.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){
                        Text(profile?.name?.ifBlank{boot.user.name} ?: boot.user.name,fontWeight=FontWeight.Black,fontSize=16.sp,color=Color(0xFF14283A))
                        Text(boot.user.employeeId+" • "+(profile?.designation?.ifBlank{boot.user.designation} ?: boot.user.designation),fontSize=11.sp,color=Color(0xFF64748B))
                        Text(boot.user.department,fontSize=10.sp,color=Color(0xFF16813A))
                    }
                    Icon(if(profileOpen)Icons.Default.ExpandLess else Icons.Default.ChevronRight,null,tint=Color(0xFF91A0B1))
                }
            }
        }
        if(profileOpen){
            item{ProfileDetailsCard(boot,profile)}
        }
        item{
            InfoRow(Icons.Default.CalendarMonth,"Shift Roster",if(boot.canManageTeamShifts)"View and change direct-report team shifts" else "View my assigned shift",onClick=onShiftRoster)
        }
        item{
            InfoRow(Icons.Default.Settings,"Settings","Profile and app preferences",onClick={settingsOpen=!settingsOpen})
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
                        ProfileDetailsRows(boot,profile)
                        HorizontalDivider(Modifier.padding(vertical=8.dp))
                        SettingLine("App","LPPL PMS Native")
                        SettingLine("Version","1.2.0")
                    }
                }
            }
        }
        item{
            OutlinedButton(onClick=onLogout,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp)){
                Icon(Icons.Default.Logout,null);Spacer(Modifier.width(6.dp));Text("Log out")
            }
        }
    }
}

@Composable
private fun ProfileDetailsCard(boot:BootstrapData,profile:ProfileData?){
    Card(
        colors=CardDefaults.cardColors(containerColor=Color(0xFFEFFAF2)),
        border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFB9E8C6)),
        shape=RoundedCornerShape(13.dp)
    ){
        Column(Modifier.padding(14.dp)){
            Text("PROFILE DETAILS",fontSize=10.sp,fontWeight=FontWeight.Black,color=Color(0xFF16752A))
            Spacer(Modifier.height(9.dp))
            ProfileDetailsRows(boot,profile)
        }
    }
}

@Composable
private fun ProfileDetailsRows(boot:BootstrapData,profile:ProfileData?){
    SettingLine("Employee ID",profile?.employeeId?.ifBlank{boot.user.employeeId} ?: boot.user.employeeId)
    SettingLine("Name",profile?.name?.ifBlank{boot.user.name} ?: boot.user.name)
    SettingLine("Department",profile?.department?.ifBlank{boot.user.department} ?: boot.user.department)
    SettingLine("Designation",profile?.designation?.ifBlank{boot.user.designation} ?: boot.user.designation)
    SettingLine("Role",boot.user.effectiveRole)
    if(!profile?.email.isNullOrBlank()) SettingLine("Email",profile!!.email)
    if(!profile?.phone.isNullOrBlank()) SettingLine("Phone",profile!!.phone)
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
