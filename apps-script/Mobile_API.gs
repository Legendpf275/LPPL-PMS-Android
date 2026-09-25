/**************************************************************
 * Mobile_API.gs — LPPL PMS native Android JSON bridge
 * Reuses existing LPPL PMS auth/session/business functions.
 * SAME PMS_DATABASE. No second auth system. No second database.
 **************************************************************/

function doPost(e) {
  try {
    const body = e && e.postData && e.postData.contents ? JSON.parse(e.postData.contents) : {};
    const action = String(body.action || '').trim();
    if (!action) return mobileJson_({ success:false, error:'Missing action.' });

    let out;
    switch (action) {
      case 'login': out = mobileLogin_(body); break;
      case 'logout': out = mobileUnwrap_(api_logout(body.token)); break;
      case 'bootstrap': out = mobileBootstrap_(body.token); break;
      case 'dashboard': out = mobileData_(api_getDashboard(body.token, body.filters || {})); break;
      case 'get_tasks': out = mobileTasks_(body); break;
      case 'get_task_bundle': out = mobileTaskBundle_(body.token); break;
      case 'complete_task': out = mobileCompleteTask_(body); break;
      case 'get_transfer_targets': out = mobileTransferTargets_(body.token); break;
      case 'transfer_task': out = mobileTransferTask_(body); break;
      case 'transfer_tasks': out = mobileTransferTasks_(body); break;
      case 'get_tickets': out = mobileTickets_(body.token); break;
      case 'create_ticket': out = mobileCreateTicket_(body); break;
      case 'ticket_messages': out = mobileData_(api_getTicketMessages(body.token, body.ticketId)); break;
      case 'add_ticket_message': out = mobileData_(api_addTicketMessage(body.token, body.ticketId, body.message)); break;
      case 'update_ticket_status': out = mobileData_(api_updateTicketStatus(body.token, body.ticketId, body.status)); break;
      case 'get_notifications': out = mobileNotifications_(body.token); break;
      case 'get_notification_summary': out = mobileData_(api_getNotificationSummary(body.token)); break;
      case 'mark_notification_read': out = mobileData_(api_markNotificationRead(body.token, body.notificationId)); break;
      case 'get_profile': out = mobileProfile_(body.token); break;
      case 'mark_all_notifications_read': out = mobileData_(api_markAllNotificationsRead(body.token)); break;
      case 'get_shift_roster': out = mobileData_(api_getShiftRosterV46(body.token, body.filters || {})); break;
      case 'get_my_shift': out = mobileMyShift_(body.token); break;
      case 'save_shift_roster': out = mobileData_(api_saveShiftRosterV46(body.token, body.input || {})); break;
      default: out = { success:false, error:'Unknown mobile action: ' + action };
    }
    return mobileJson_(out);
  } catch (err) {
    return mobileJson_({ success:false, error:String(err && err.message ? err.message : err) });
  }
}

function mobileJson_(obj) {
  return ContentService.createTextOutput(JSON.stringify(makeClientSafe_(obj)))
    .setMimeType(ContentService.MimeType.JSON);
}

function mobileUnwrap_(safeResult) {
  if (!safeResult || safeResult.ok === false) {
    return { success:false, error:safeResult && safeResult.error ? safeResult.error : 'Request failed.' };
  }
  return { success:true, data:safeResult.data };
}

function mobileData_(safeResult) { return mobileUnwrap_(safeResult); }

function mobileLogin_(body) {
  const r = api_login(body.employeeId, body.password, body.userAgent || 'LPPL PMS Native Android');
  if (!r || r.ok === false) return { success:false, error:r && r.error ? r.error : 'Login failed.' };
  const d = r.data || {};
  return { success:true, token:d.token, user:mobileUser_(d.me), expiresInDays:d.expiresInDays || 0 };
}

function mobileBootstrap_(token) {
  const r = api_bootstrap(token);
  if (!r || r.ok === false) return { success:false, error:r && r.error ? r.error : 'Unable to load PMS.' };
  const d = r.data || {};
  return { success:true, data:{
    user:mobileUser_(d.me),
    canViewTeamTasks:!!(d.permissions && d.permissions.canViewTeamTasks),
    canViewTeamTickets:!!(d.permissions && d.permissions.canViewTeamTickets),
    canManageTeamShifts:!!(d.permissions && d.permissions.canManageTeamShifts),
    departments:(d.departments || []).map(x => String(x.DepartmentName || '')).filter(Boolean),
    ticketCategories:d.ticketCategories || [],
    taskCategories:d.taskCategories || [],
    priorities:d.priorities || [],
    unreadCount:Number(d.unreadNotificationCount || 0),
    companyName:d.companyName || 'Legend Polyfoams Pvt. Ltd.',
    appName:d.appName || APP_NAME,
    version:d.version || APP_VERSION,
    taskBundle:(function(){
      try {
        const bundle = mobileTaskBundle_(token);
        return bundle && bundle.success ? bundle.data : null;
      } catch (ignored) {
        return null;
      }
    })()
  }};
}

function mobileUser_(u) {
  u = u || {};
  const rawRole = normalizeRole_(u.Role || 'Employee');
  const manager = String(u.IsManager || '').toLowerCase() === 'yes';
  return {
    userId:String(u.UserID || ''), employeeId:String(u.EmployeeID || ''), name:String(u.Name || ''),
    department:String(u.Department || ''), designation:String(u.Designation || ''), rawRole:rawRole,
    isManager:manager,
    effectiveRole:rawRole === ROLES.ADMIN ? 'Admin' : (rawRole === ROLES.MD ? 'MD' : (manager ? 'Manager' : 'Employee')),
    profilePhotoUrl:String(u.ProfilePhotoURL || '')
  };
}

function mobileTasks_(body) {
  const token = body.token;
  const scope = String(body.scope || 'MY').toUpperCase();
  const tab = normalizeTaskWorkspaceTabV43_(body.tab || 'today', true);
  let r, rows, counts;

  if (scope === 'TEAM') {
    r = api_getTeamTaskWorkspaceV43(token, { tab:tab, page:1, pageSize:200 });
    if (!r || r.ok === false) return { success:false, error:r && r.error ? r.error : 'Unable to load team tasks.' };
    rows = (r.data && r.data.rows) || [];
    counts = (r.data && r.data.counts) || {};
  } else {
    r = api_getMyTaskWorkspaceV43(token);
    if (!r || r.ok === false) return { success:false, error:r && r.error ? r.error : 'Unable to load tasks.' };
    rows = (r.data && r.data[tab]) || [];
    counts = (r.data && r.data.counts) || {};
  }

  const masters = {};
  sheetToObjects_(SHEET_NAMES.TASK_MASTER).forEach(m => masters[String(m.MasterID || '')] = m);
  const seen = {};
  return { success:true, data:{ tasks:rows.filter(row => {
    const id=String(row.TaskID || '');
    if (!id || seen[id]) return false;
    seen[id]=true; return true;
  }).map(row => mobileTask_(row, masters)), counts:counts } };
}

function mobileTaskBundle_(token) {
  const r = api_getMyTaskWorkspaceV43(token);
  if (!r || r.ok === false) return { success:false, error:r && r.error ? r.error : 'Unable to load tasks.' };
  const d = r.data || {};
  const masters = {};
  sheetToObjects_(SHEET_NAMES.TASK_MASTER).forEach(m => masters[String(m.MasterID || '')] = m);
  const tabs = {};
  ['today','upcoming','overdue','notdone','onleave','completed'].forEach(key => {
    const seen={};
    tabs[key] = (d[key] || []).filter(row => {
      const id=String(row.TaskID || '');
      if (!id || seen[id]) return false;
      seen[id]=true; return true;
    }).map(row => mobileTask_(row, masters));
  });
  return { success:true, data:{ tabs:tabs, counts:d.counts || {} } };
}

function mobileTask_(row, masterMap) {
  row = row || {};
  const master = (masterMap || {})[String(row.MasterID || '')] || {};
  // One user-facing logical Task ID: prefer manually entered Task_Master.TaskID.
  const logicalTaskId = String(master.TaskID || row.MasterTaskID || row.RecurringTaskID || master.RecurringTaskID || row.MasterID || '');
  const userId = String(row.EmployeeUserID || row.AssignedToUserID || '');
  const emp = userId ? getUserById_(userId) : null;
  return {
    taskId:logicalTaskId,
    logicalId:String(row.RecurringTaskID || master.RecurringTaskID || master.AssignmentGroupID || master.MasterID || row.MasterID || logicalTaskId),
    instanceId:String(row.TaskID || ''),
    title:String(row.TaskDescription || ''),
    category:String(row.Category || master.Category || ''),
    department:String(row.Department || (emp && emp.Department) || master.Department || ''),
    employeeName:String(row.EmployeeName || (emp && emp.Name) || ''),
    employeeId:String(row.EmployeeID || (emp && emp.EmployeeID) || ''),
    dueDate:row.DueDate || '',
    frequency:String(row.Frequency || master.Frequency || ''),
    status:String(row.Status || 'Pending'),
    proofRequired:String(row.ProofRequired || 'No') === 'Yes',
    canComplete:row.CanComplete !== false,
    canTransfer:!!(row.CanTransfer || row.IsTransferable)
  };
}

function mobileTransferTargets_(token) {
  const me=requireSession_(token,null);
  assertTeamTaskAccessV40_(me);
  const rows=getDirectReportUsersV46_(me,false).filter(u=>u.Status===ACTIVE_STATUS)
    .map(u=>({userId:String(u.UserID||''),employeeId:String(u.EmployeeID||''),name:String(u.Name||''),department:String(u.Department||'')}));
  return {success:true,data:{rows:rows}};
}

function mobileTransferTask_(body) {
  const me=requireSession_(body.token,null);
  const taskId=String(body.taskId||'');
  const task=sheetToObjects_(SHEET_NAMES.TASK_INSTANCES).find(t=>String(t.TaskID||'')===taskId);
  if (!task) return {success:false,error:'Task not found.'};
  const own=String(task.AssignedToUserID||'')===String(me.UserID||'');
  if (!hasPmsRuleV44_(me,own?'TASK_TRANSFER_OWN':'TASK_TRANSFER_TEAM'))
    return {success:false,error:'Task transfer is not allowed for your role.'};
  return mobileData_(api_transferTeamTasksV42(body.token,{
    mode:'assignments',date:String(body.date||''),
    assignments:[{taskId:taskId,targetUserId:String(body.targetUserId||'')}]
  }));
}

function mobileTransferTasks_(body) {
  const me=requireSession_(body.token,null);
  const ids=Array.isArray(body.taskIds)?body.taskIds.map(String):[];
  if(!ids.length||ids.length>100||new Set(ids).size!==ids.length)
    return {success:false,error:'Select between 1 and 100 different tasks.'};
  const tasks=sheetToObjects_(SHEET_NAMES.TASK_INSTANCES);
  const byId={}; tasks.forEach(task=>{byId[String(task.TaskID||'')]=task;});
  const assignments=[];
  ids.forEach(id=>{
    const task=byId[id];
    if(!task)throw new Error('Selected task was not found.');
    const own=String(task.AssignedToUserID||'')===String(me.UserID||'');
    if(!hasPmsRuleV44_(me,own?'TASK_TRANSFER_OWN':'TASK_TRANSFER_TEAM'))
      throw new Error('Task transfer is not allowed for your role.');
    assignments.push({taskId:id,targetUserId:String(body.targetUserId||'')});
  });
  return mobileData_(api_transferTeamTasksV42(body.token,{mode:'assignments',assignments:assignments}));
}

function mobileCompleteTask_(body) {
  let fileData = null;
  if (body.proofBase64) {
    fileData = {
      base64:String(body.proofBase64),
      fileName:String(body.proofFileName || 'task_proof.jpg'),
      mimeType:String(body.proofMimeType || 'image/jpeg')
    };
  }
  const r = api_completeTask(body.token, String(body.instanceId || ''), String(body.remark || ''), fileData);
  if (!r || r.ok === false) return { success:false, error:r && r.error ? r.error : 'Unable to complete task.' };
  return { success:true, data:{ completed:true, stayOnTab:'today' } };
}

function mobileTickets_(token) {
  const r = api_getMyTickets(token);
  if (!r || r.ok === false) return { success:false, error:r && r.error ? r.error : 'Unable to load help tickets.' };
  const d = r.data || {};
  const me = requireSession_(token, null);
  const scope = getAccessScopeV40_(me);
  const scopedUsers = getScopedActiveUsersV40_(me, scope);
  const teamIds = {};
  scopedUsers.forEach(u => {
    const id = String(u.UserID || '');
    if (id && id !== String(me.UserID || '')) teamIds[id] = true;
  });
  return { success:true, data:{
    mine:(d.mine || []).map(t => mobileTicket_(t, teamIds)),
    team:(d.team || []).map(t => mobileTicket_(t, teamIds)),
    canViewTeam:!!d.canViewTeam,
    ticketUsers:d.ticketUsers || []
  }};
}


function mobileTicket_(t, teamIds) {
  t = t || {};
  teamIds = teamIds || {};
  const creatorId = String(t.CreatedByUserID || '');
  const assigneeId = String(t.AssignedToUserID || '');
  return {
    ticketId:String(t.TicketID || ''),
    description:String(t.Description || ''),
    department:String(t.Department || ''),
    category:String(t.Category || ''),
    urgency:String(t.Priority || ''),
    status:String(t.Status || ''),
    raisedByName:String(t.CreatedByName || ''),
    raisedByEmployeeId:String(t.CreatedByEmployeeID || ''),
    assignedToName:String(t.AssignedToName || ''),
    assignedToEmployeeId:String(t.AssignedEmployeeID || ''),
    createdOn:t.CreatedOn || '',
    dueDate:t.DueDate || '',
    isCreatedByMe:!!t.IsCreatedByMe,
    isAssignedToMe:!!t.IsAssignedToMe,
    isCreatedByTeam:!!teamIds[creatorId],
    isAssignedToTeam:!!teamIds[assigneeId],
    isOverdue:!!t.IsOverdue
  };
}

function mobileCreateTicket_(body) {
  const ticket = body.ticket || {};
  const photo = body.photoBase64 ? {
    base64:String(body.photoBase64),
    fileName:String(body.photoFileName || 'ticket_attachment.jpg'),
    mimeType:String(body.photoMimeType || 'image/jpeg')
  } : null;
  const voice = body.voiceBase64 ? {
    base64:String(body.voiceBase64),
    fileName:String(body.voiceFileName || 'voice_note.m4a'),
    mimeType:String(body.voiceMimeType || 'audio/mp4')
  } : null;
  const r = api_createTicket(body.token, ticket, photo, voice);
  if (!r || r.ok === false) return { success:false, error:r && r.error ? r.error : 'Unable to create ticket.' };
  return { success:true, data:{ ticketId:r.data } };
}

function mobileNotifications_(token) {
  const r = api_getNotifications(token, 50);
  if (!r || r.ok === false) return { success:false, error:r && r.error ? r.error : 'Unable to load notifications.' };
  const d = r.data || {};
  return { success:true, data:{
    unreadCount:Number(d.unreadCount || 0),
    rows:(d.rows || []).map(n => ({
      id:String(n.NotificationID || ''),
      type:String(n.Type || ''),
      title:String(n.Title || ''),
      message:String(n.Message || ''),
      createdOn:n.CreatedOn || '',
      isRead:String(n.IsRead || '').toLowerCase() === 'yes'
    }))
  }};
}


function mobileMyShift_(token) {
  const me = requireSession_(token, null);
  const today = dateKeyV42_(new Date());
  const shiftMap = {};
  getActiveShiftMasterV46_().forEach(s => shiftMap[String(s.ShiftID || '')] = s);

  const rows = sheetToObjects_(SHEET_NAMES.SHIFT_ROSTER)
    .filter(row => {
      if (String(row.Status || '') !== ACTIVE_STATUS) return false;
      if (String(row.UserID || '') !== String(me.UserID || '')) return false;
      const fromKey = dateKeyV42_(row.EffectiveFrom);
      const toKey = dateKeyV42_(row.EffectiveTo);
      return fromKey && toKey && fromKey <= today && toKey >= today;
    })
    .map(row => {
      const shift = shiftMap[String(row.ShiftID || '')] || {};
      return {
        RosterID:row.RosterID, UserID:me.UserID, EmployeeID:me.EmployeeID, EmployeeName:me.Name,
        Department:me.Department, Designation:me.Designation,
        ShiftID:row.ShiftID, ShiftCode:row.ShiftCode, ShiftName:shift.ShiftName || row.ShiftCode || '',
        ShiftType:shift.ShiftType || shiftTypeFromNameV47_(shift.ShiftName),
        StartTime:cleanShiftTimeV47_(shift,'start'), EndTime:cleanShiftTimeV47_(shift,'end'),
        EffectiveFrom:row.EffectiveFrom, EffectiveTo:row.EffectiveTo, Status:row.Status
      };
    });

  return { success:true, data:{ rows:rows } };
}


function mobileProfile_(token) {
  const r = api_getMyProfile(token);
  if (!r || r.ok === false) return { success:false, error:r && r.error ? r.error : 'Unable to load profile.' };
  const p = r.data || {};
  return { success:true, data:{
    userId:String(p.UserID || ''),
    employeeId:String(p.EmployeeID || ''),
    name:String(p.Name || ''),
    email:String(p.Email || ''),
    phone:String(p.PhoneNumber || p.Phone || ''),
    department:String(p.Department || ''),
    designation:String(p.Designation || ''),
    role:String(p.Role || ''),
    isManager:String(p.IsManager || '').toLowerCase() === 'yes',
    profilePhotoDataUri:String(p.ProfilePhotoDataUri || '')
  }};
}
