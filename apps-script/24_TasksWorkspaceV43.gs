/**************************************************************
 * 24_TasksWorkspaceV43.gs — unified My / direct-report Team Tasks v4.7
 * Unique Tasks now means unique logical Task Masters assigned to
 * the user/team (one row per Recurring ID), not One-Time only.
 **************************************************************/

function api_getMyTaskWorkspaceV43(sessionToken) {
  return safeCall_('Tasks','api_getMyTaskWorkspaceV43',()=>{
    const me=requireSession_(sessionToken,null);
    ensureV42MaintenanceFresh_();
    const todayKey=dateKeyV42_(new Date());
    const myScope=getAccessScopeV40_(me);
    const masters=sheetToObjects_(SHEET_NAMES.TASK_MASTER);
    const masterById={}; masters.forEach(m=>masterById[String(m.MasterID||'')]=m);
    const result={today:[],upcoming:[],overdue:[],notdone:[],onleave:[],completed:[],unique:[],
      counts:{today:0,upcoming:0,overdue:0,notdone:0,onleave:0,completed:0,unique:0}};
    const historyCutoff=dateKeyV42_(new Date(Date.now()-TEAM_TASK_COMPLETED_LOOKBACK_DAYS*86400000));
    const seenInstances={};

    dedupeTaskInstancesByLogicalV48_(sheetToObjects_(SHEET_NAMES.TASK_INSTANCES),masterById)
      .filter(task=>recordBelongsToUser_(task,me,'AssignedToUserID','AssignedToEmail'))
      .forEach(task=>{
        const instanceKey=String(task.TaskID||'');
        if(instanceKey&&seenInstances[instanceKey])return;
        if(instanceKey)seenInstances[instanceKey]=true;
        const master=masterById[String(task.MasterID||'')]||{};
        const row=taskWorkspaceRowV43_(task,master,me,myScope);
        const bucket=myTaskBucketV42_(task,todayKey);
        if(bucket==='upcoming'&&!isTaskInUpcomingWindowV45_(task.DueDate))return;
        const historyKey=dateKeyV42_(task.CompletedOn||task.DueDate);
        const oldCompleted=bucket==='completed'&&historyKey&&historyKey<historyCutoff;
        if(!oldCompleted&&result[bucket]){result[bucket].push(row);result.counts[bucket]++;}
      });

    // Unique Tasks = one logical Task Master assigned to this employee,
    // regardless of Daily/Weekly/Monthly/One-Time frequency.
    const uniqueSeen={};
    masters.filter(master=>String(master.AssignedToUserID||'')===String(me.UserID||''))
      .forEach(master=>{
        const logical=String(master.RecurringTaskID||master.AssignmentGroupID||master.MasterID||'');
        if(!logical||uniqueSeen[logical])return;
        uniqueSeen[logical]=true;
        result.unique.push(uniqueTaskMasterRowV44_(master,me));
      });
    result.counts.unique=result.unique.length;

    ['today','upcoming','overdue','notdone','onleave','completed'].forEach(key=>{
      result[key].sort((a,b)=>{
        const av=new Date(key==='completed'?(a.CompletedOn||a.DueDate):a.DueDate).getTime()||0;
        const bv=new Date(key==='completed'?(b.CompletedOn||b.DueDate):b.DueDate).getTime()||0;
        return key==='completed'?bv-av:av-bv;
      });
    });
    result.unique.sort((a,b)=>String(a.TaskDescription||'').localeCompare(String(b.TaskDescription||'')));
    return result;
  });
}

function api_getTeamTaskWorkspaceV43(sessionToken,filters){
  return safeCall_('Tasks','api_getTeamTaskWorkspaceV43',()=>{
    const me=requireSession_(sessionToken,null);
    assertTeamTaskAccessV40_(me);
    ensureV42MaintenanceFresh_(); filters=filters||{};

    // v4.6: Team Tasks = direct reports only. The logged-in user's own tasks
    // live only under My Tasks and are never duplicated here.
    const users=getDirectReportUsersV46_(me,false), userById={},userIdSet={};
    users.forEach(u=>{const id=String(u.UserID||'');if(id){userById[id]=u;userIdSet[id]=true;}});
    const departments=directReportDepartmentNamesV46_(me);

    const requestedDepartment=cleanText_(filters.department,150);
    if(requestedDepartment&&departments.indexOf(requestedDepartment)===-1)throw new Error('Selected department is outside your reporting team.');
    const requestedUserId=cleanText_(filters.userId,60);
    if(requestedUserId&&!userIdSet[requestedUserId])throw new Error('Selected employee does not report to you.');

    const tab=normalizeTaskWorkspaceTabV43_(filters.tab,true), search=cleanText_(filters.search,120).toLowerCase();
    const pageSize=Math.max(20,Math.min(Number(filters.pageSize)||TEAM_TASK_PAGE_SIZE,TEAM_TASK_MAX_PAGE_SIZE));
    const page=Math.max(1,Number(filters.page)||1), todayKey=dateKeyV42_(new Date());
    const dateWindow=teamTaskDateWindowV42_(filters,tab==='unique'?'all':tab);
    const leaveSet=getActiveLeaveSetV42_(dateWindow.fromKey||'',dateWindow.toKey||'');
    const masters=sheetToObjects_(SHEET_NAMES.TASK_MASTER), masterById={};
    masters.forEach(m=>masterById[String(m.MasterID||'')]=m);
    const counts={today:0,upcoming:0,overdue:0,notdone:0,onleave:0,completed:0,unique:0,all:0};
    const instanceRows=[];
    const seenTeamInstances={};
    const instances=dedupeTaskInstancesByLogicalV48_(sheetToObjects_(SHEET_NAMES.TASK_INSTANCES),masterById);
    const todayLogical={};
    instances.forEach(task=>{
      const assignedId=String(task.AssignedToUserID||'');
      if(!userById[assignedId]||teamTaskBucketV42_(task,todayKey)!=='today')return;
      const master=masterById[String(task.MasterID||'')]||{};
      const logical=String(master.RecurringTaskID||master.AssignmentGroupID||master.MasterID||task.MasterID||'');
      if(logical)todayLogical[assignedId+'|'+logical]=true;
    });

    instances.forEach(task=>{
      const assignedId=String(task.AssignedToUserID||''), employee=userById[assignedId]; if(!employee)return;
      const instanceKey=String(task.TaskID||'');
      if(instanceKey&&seenTeamInstances[instanceKey])return;
      if(instanceKey)seenTeamInstances[instanceKey]=true;
      if(requestedDepartment&&String(employee.Department||'')!==requestedDepartment)return;
      if(requestedUserId&&assignedId!==requestedUserId)return;
      const master=masterById[String(task.MasterID||'')]||{};
      const logicalId=String(master.RecurringTaskID||master.AssignmentGroupID||master.MasterID||task.MasterID||'');
      const frequency=String(master.Frequency||'');
      if(search){const hay=[task.TaskID,logicalId,task.TaskDescription,task.Category,frequency,employee.EmployeeID,employee.Name,employee.Designation,employee.Department,task.ShiftCode].join(' ').toLowerCase();if(hay.indexOf(search)===-1)return;}
      const bucket=teamTaskBucketV42_(task,todayKey);
      // A recurring task visible Today should not also repeat under Upcoming.
      if(bucket==='upcoming'&&todayLogical[assignedId+'|'+logicalId])return;
      if(bucket==='upcoming'&&typeof isTaskInUpcomingWindowV45_==='function'&&!isTaskInUpcomingWindowV45_(task.DueDate))return;
      counts.all++; if(counts[bucket]!==undefined)counts[bucket]++;
      if(tab!=='unique'){
        if(tab!=='all'&&bucket!==tab)return;
        if(!teamTaskWithinDateWindowV42_(task,bucket,dateWindow))return;
        const row=teamTaskViewRowV42_(task,employee,me,leaveSet);
        row.RecurringTaskID=logicalId; row.Frequency=frequency; row.IsUniqueMaster=false;
        row.CanReopen=canReopenTaskRowV44_(task,me,employee,null);
        row.IsTransferable=row.IsTransferable&&canTransferTaskRowV44_(task,me);
        instanceRows.push(row);
      }
    });

    // Team Unique Tasks = one Task Master per direct-report employee + logical ID.
    const uniqueRows=[], uniqueSeen={};
    masters.forEach(master=>{
      const assignedId=String(master.AssignedToUserID||''), employee=userById[assignedId]; if(!employee)return;
      if(requestedDepartment&&String(employee.Department||'')!==requestedDepartment)return;
      if(requestedUserId&&assignedId!==requestedUserId)return;
      const logical=String(master.RecurringTaskID||master.AssignmentGroupID||master.MasterID||'');
      const key=assignedId+'|'+logical; if(!logical||uniqueSeen[key])return;
      if(search){const hay=[master.TaskID,logical,master.TaskDescription,master.Category,master.Frequency,employee.EmployeeID,employee.Name,employee.Designation,employee.Department].join(' ').toLowerCase();if(hay.indexOf(search)===-1)return;}
      uniqueSeen[key]=true; uniqueRows.push(uniqueTeamTaskMasterRowV44_(master,employee));
    });
    counts.unique=uniqueRows.length;

    let baseRows=tab==='unique'?uniqueRows:instanceRows;
    if(tab==='unique')baseRows.sort((a,b)=>String(a.TaskDescription||'').localeCompare(String(b.TaskDescription||'')));
    else {baseRows.sort((a,b)=>teamTaskSortValueV42_(a,tab)-teamTaskSortValueV42_(b,tab));if(tab==='completed')baseRows.reverse();}
    const total=baseRows.length,start=(page-1)*pageSize,rows=baseRows.slice(start,start+pageSize);
    const transferTargets=users.filter(u=>u.Status===ACTIVE_STATUS).map(u=>({UserID:u.UserID,EmployeeID:u.EmployeeID,Name:u.Name,Department:u.Department,Designation:u.Designation})).sort((a,b)=>String(a.Name).localeCompare(String(b.Name)));
    return {
      scope:{mode:normalizeRole_(me.Role)===ROLES.ADMIN?'COMPANY':'DIRECT_REPORTS',label:normalizeRole_(me.Role)===ROLES.ADMIN?'Company Team':'Direct Reports',departments:departments.slice()},
      departments:departments.slice(),
      employees:users.map(u=>({UserID:u.UserID,EmployeeID:u.EmployeeID,Name:u.Name,Department:u.Department,Designation:u.Designation,Status:u.Status})).sort((a,b)=>String(a.Name).localeCompare(String(b.Name))),
      transferTargets:transferTargets,counts:counts,rows:rows,
      pagination:{page:page,pageSize:pageSize,total:total,totalPages:Math.max(1,Math.ceil(total/pageSize)),hasPrevious:page>1,hasNext:start+pageSize<total},
      filters:{tab:tab,department:requestedDepartment,userId:requestedUserId,search:search,fromDate:dateWindow.autoDefault?'':(dateWindow.fromKey||''),toDate:dateWindow.autoDefault?'':(dateWindow.toKey||'')}
    };
  });
}

// Multiple Task Masters can share a RecurringTaskID. Their generated rows
// have distinct TaskIDs, so deduplicating only by TaskID leaves visible copies.
// Keep one row for each assignee, logical task and due date. Prefer a completed
// instance if one exists; this avoids showing a pending duplicate as work due.
function dedupeTaskInstancesByLogicalV48_(instances, masterById) {
  const byKey = {}, order = [];
  (instances || []).forEach(task => {
    const master = masterById[String(task.MasterID || '')] || {};
    const logical = String(master.RecurringTaskID || master.AssignmentGroupID || master.MasterID || task.MasterID || task.TaskID || '');
    const due = dateKeyV42_(task.DueDate) || String(task.TaskID || '');
    const assigned = String(task.AssignedToUserID || task.AssignedToEmail || '');
    const key = JSON.stringify([assigned, logical, due]);
    if (!Object.prototype.hasOwnProperty.call(byKey, key)) {
      byKey[key] = task;
      order.push(key);
    } else if (String(task.Status || '') === String(TASK_STATUS.COMPLETED) &&
               String(byKey[key].Status || '') !== String(TASK_STATUS.COMPLETED)) {
      byKey[key] = task;
    }
  });
  return order.map(key => byKey[key]);
}

function taskWorkspaceRowV43_(task,master,me,scope){
  const frequency=String(master.Frequency||''),status=String(task.Status||'');
  return {
    TaskID:task.TaskID,MasterID:task.MasterID,RecurringTaskID:String(master.RecurringTaskID||master.AssignmentGroupID||master.MasterID||task.MasterID||''),
    TaskDescription:task.TaskDescription,Category:task.Category,Department:task.Department,Frequency:frequency,DueDate:task.DueDate,Status:task.Status,
    CompletedOn:task.CompletedOn,Remark:task.Remark||'',OnTime:task.OnTime||'',DelayDays:Number(task.DelayDays)||0,ProofRequired:task.ProofRequired||'No',
    SubmissionResult:task.SubmissionResult||'',HasProof:!!task.ProofFileID,
    CanComplete:[TASK_STATUS.PENDING,TASK_STATUS.OVERDUE].indexOf(status)!==-1&&hasPmsRuleV44_(me,'TASK_COMPLETE_OWN'),
    CanReopen:canReopenTaskRowV44_(task,me,me,scope),
    CanTransfer:isTransferableTaskV42_(task)&&hasPmsRuleV44_(me,'TASK_TRANSFER_OWN'),
    TransferCount:Number(task.TransferCount)||0,IsOwnTask:true,IsUniqueMaster:false,
    ShiftID:task.ShiftID || '', ShiftCode:task.ShiftCode || '', ShiftName:task.ShiftName || '',
    TaskDeadline:task.TaskDeadline || task.SubmissionDeadline || '', DeadlineRule:task.DeadlineRule || '', GraceMinutes:Number(task.GraceMinutes)||0
  };
}

function uniqueTaskMasterRowV44_(master,user){
  return {IsUniqueMaster:true,TaskID:'',MasterTaskID:String(master.TaskID||''),MasterID:master.MasterID,RecurringTaskID:String(master.RecurringTaskID||master.AssignmentGroupID||master.MasterID||''),
    TaskDescription:master.TaskDescription,Category:master.Category,Department:master.Department||user.Department,Frequency:master.Frequency||'',
    StartDate:master.StartDate,EndDate:master.EndDate,ProofRequired:master.ProofRequired||'No',Status:master.Status||ACTIVE_STATUS,
    AssignedToUserID:user.UserID,EmployeeID:user.EmployeeID,EmployeeName:user.Name,CanComplete:false,CanReopen:false,HasProof:false,TransferCount:0};
}
function uniqueTeamTaskMasterRowV44_(master,employee){
  const row=uniqueTaskMasterRowV44_(master,employee); row.EmployeeUserID=employee.UserID;row.EmployeeID=employee.EmployeeID;row.EmployeeName=employee.Name;row.EmployeeDesignation=employee.Designation;return row;
}
function canTransferTaskRowV44_(task,me){
  const own=String(task.AssignedToUserID||'')===String(me.UserID||'');
  return hasPmsRuleV44_(me,own?'TASK_TRANSFER_OWN':'TASK_TRANSFER_TEAM');
}
function canReopenTaskRowV44_(task,me,employee,scope){
  const status=String(task.Status||'');
  const key=status===TASK_STATUS.ON_LEAVE?'TASK_REOPEN_ON_LEAVE':((status===TASK_STATUS.NOT_DONE||status===TASK_STATUS.MISSED)?'TASK_REOPEN_NOT_DONE':'');
  if(!key||!hasPmsRuleV44_(me,key))return false;
  if(String(task.AssignedToUserID||'')===String(me.UserID||''))return true;
  return !!(employee&&userIsDirectReportV46_(employee,me));
}
function normalizeTaskWorkspaceTabV43_(value,allowAll){
  const key=String(value||'today').toLowerCase();const allowed=['today','upcoming','overdue','onleave','notdone','completed','unique'];if(allowAll)allowed.push('all');return allowed.indexOf(key)!==-1?key:'today';
}
