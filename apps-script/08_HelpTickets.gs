/**************************************************************
 * 08_HelpTickets.gs — LPPL PMS v4.2 scope-aware help tickets + notifications
 **************************************************************/

function api_createTicket(sessionToken, ticket, fileData, voiceData) {
  return safeCall_('HelpTickets', 'api_createTicket', () => {
    const me = requireSession_(sessionToken, null);
    ticket = ticket || {};
    const clean = {
      Department: requireText_(ticket.Department, 'Doer department', 100),
      AssignedToUserID: requireText_(ticket.AssignedToUserID, 'Doer', 50),
      Category: requireText_(ticket.Category, 'Category', 100),
      Priority: requireText_(ticket.Priority, 'Urgency', 30),
      DueDate: requireText_(ticket.DueDate, 'Due date', 10),
      Description: requireText_(ticket.Description, 'Description', 3000),
      MachineArea: cleanText_(ticket.MachineArea, 200)
    };

    const dept = sheetToObjects_(SHEET_NAMES.DEPARTMENTS)
      .find(row => row.Status === ACTIVE_STATUS && String(row.DepartmentName) === clean.Department);
    if (!dept) throw new Error('Select an active doer department.');
    if (getActiveCategories_('Ticket').indexOf(clean.Category) === -1) throw new Error('Invalid ticket category.');
    if (splitSetting_('Priorities').indexOf(clean.Priority) === -1) throw new Error('Invalid urgency.');

    const assigned = getUserById_(clean.AssignedToUserID);
    if (!assigned || assigned.Status !== ACTIVE_STATUS) throw new Error('Select an active doer.');
    const departmentHodId = getRecordUserId_(dept, 'HODUserID', 'HODEmail');
    const isDepartmentHod = String(departmentHodId) === String(assigned.UserID);
    if (String(assigned.Department) !== clean.Department && !isDepartmentHod) {
      throw new Error('The selected doer is not part of the selected department.');
    }

    const dueDate = parseTicketDueDate_(clean.DueDate);
    const manager = getTicketReportingManager_(assigned, dept);
    const ticketId = nextSequenceId_('SEQ_TICKET', 'HELP-', 7);
    let attachment = null;
    let voiceNote = null;

    if (fileData && fileData.base64) {
      attachment = uploadPrivateFile_('Help Tickets', ticketId, fileData, [
        'image/jpeg', 'image/png', 'image/webp', 'application/pdf', 'video/mp4', 'video/3gpp'
      ]);
    }
    if (voiceData && voiceData.base64) {
      voiceData.mimeType = cleanText_(voiceData.mimeType, 100).split(';')[0].toLowerCase();
      voiceNote = uploadPrivateFile_('Help Tickets', ticketId + '_VOICE', voiceData, [
        'audio/webm', 'audio/mp4', 'audio/x-m4a', 'audio/mpeg', 'audio/ogg', 'audio/wav', 'audio/x-wav',
        'audio/aac', 'audio/3gpp', 'audio/3gpp2', 'audio/amr'
      ]);
    }

    appendRowFromObject_(SHEET_NAMES.HELP_TICKETS, {
      TicketID: ticketId,
      CreatedByEmail: normalizeEmail_(me.Email),
      CreatedByUserID: me.UserID,
      Department: clean.Department,
      Category: clean.Category,
      Priority: clean.Priority,
      Description: clean.Description,
      MachineArea: clean.MachineArea,
      DueDate: dueDate,
      PhotoURL: attachment ? attachment.getUrl() : '',
      PhotoFileID: attachment ? attachment.getId() : '',
      VoiceNoteURL: voiceNote ? voiceNote.getUrl() : '',
      VoiceNoteFileID: voiceNote ? voiceNote.getId() : '',
      Status: TICKET_STATUS.ASSIGNED,
      AssignedToEmail: normalizeEmail_(assigned.Email),
      AssignedToUserID: assigned.UserID,
      ReportingManagerUserID: manager ? manager.UserID : '',
      ReportingManagerEmail: manager ? normalizeEmail_(manager.Email) : '',
      ReportingManagerName: manager ? manager.Name : '',
      CreatedOn: new Date(),
      ResolvedOn: '',
      Escalated: 'No'
    });

    appendTicketSystemMessage_(ticketId, me,
      'Ticket created for ' + assigned.EmployeeID + ' · ' + assigned.Name +
      (manager ? '; reporting manager: ' + manager.EmployeeID + ' · ' + manager.Name + '.' : '; reporting manager is not mapped.'));
    logAudit_('HelpTickets', 'Create', ticketId, me);
    createNotificationsForUsersV42_([assigned.UserID, manager ? manager.UserID : ''],
      'HELP_TICKET', 'New help ticket assigned',
      ticketId + ' · ' + clean.Department + ' · ' + clean.Category, 'TICKET', ticketId, me.UserID);
    return ticketId;
  });
}

function api_getMyTickets(sessionToken) {
  return safeCall_('HelpTickets', 'api_getMyTickets', () => {
    const me = requireSession_(sessionToken, null);
    const scope = getAccessScopeV40_(me);
    const newestFirst = list => list.sort((a, b) => new Date(b.CreatedOn) - new Date(a.CreatedOn));
    const all = sheetToObjects_(SHEET_NAMES.HELP_TICKETS);

    const isMine = ticket =>
      recordBelongsToUser_(ticket, me, 'CreatedByUserID', 'CreatedByEmail') ||
      recordBelongsToUser_(ticket, me, 'AssignedToUserID', 'AssignedToEmail');

    const myTickets = all.filter(isMine);
    const teamTickets = scope.canViewTeamTickets
      ? all.filter(ticket => ticketIsInsideTeamScopeV41_(ticket, scope) && !isMine(ticket))
      : [];

    const decorate = ticket => decorateTicketForClient_(ticket, me, scope);
    return {
      mine: newestFirst(myTickets.map(decorate)),
      team: newestFirst(teamTickets.map(decorate)),
      canViewTeam: scope.canViewTeamTickets,
      scopeDepartments: scope.canViewTeamTickets ? (scope.departmentNames || []).slice() : [],
      ticketUsers: buildTicketUserDirectory_()
    };
  });
}

function api_updateTicketStatus(sessionToken, ticketId, status) {
  return safeCall_('HelpTickets', 'api_updateTicketStatus', () => {
    const me = requireSession_(sessionToken, null);
    const ticket = sheetToObjects_(SHEET_NAMES.HELP_TICKETS).find(row => row.TicketID === ticketId);
    if (!ticket) throw new Error('Ticket not found.');
    const allowed = allowedTicketStatusesFor_(me, ticket);
    if (allowed.indexOf(status) === -1) throw new Error('You cannot apply that status to this ticket.');

    const fields = { Status: status };
    fields.ResolvedOn = [TICKET_STATUS.RESOLVED, TICKET_STATUS.CLOSED].indexOf(status) !== -1
      ? (ticket.ResolvedOn || new Date()) : '';
    updateRowFields_(SHEET_NAMES.HELP_TICKETS, ticket._row, fields);
    appendTicketSystemMessage_(ticketId, me, 'Status changed from ' + ticket.Status + ' to ' + status + '.');
    logAudit_('HelpTickets', 'StatusChange', ticketId + ' -> ' + status, me);
    createNotificationsForUsersV42_(ticketParticipantUserIdsV42_(ticket),
      'HELP_TICKET_STATUS', ticketId + ' status changed',
      'Status changed from ' + ticket.Status + ' to ' + status + ' by ' + me.Name + '.',
      'TICKET', ticketId, me.UserID);
    return true;
  });
}

function api_assignTicket(sessionToken, ticketId, assignedUserId) {
  return safeCall_('HelpTickets', 'api_assignTicket', () => {
    const me = requireSession_(sessionToken, null);
    const scope = getAccessScopeV40_(me);
    const ticket = requireTicketAccess_(me, ticketId);

    if (!canManageTicketDepartmentV41_(me, ticket, scope)) {
      throw new Error('You can transfer tickets only inside your managed departments.');
    }
    if ([TICKET_STATUS.RESOLVED, TICKET_STATUS.CLOSED].indexOf(ticket.Status) !== -1) {
      throw new Error('Resolved or closed tickets cannot be transferred. Reopen the ticket first.');
    }

    let assigned = null;
    let manager = null;
    if (assignedUserId) {
      assigned = getUserById_(assignedUserId);
      if (!assigned || assigned.Status !== ACTIVE_STATUS) throw new Error('Select an active employee.');

      const dept = sheetToObjects_(SHEET_NAMES.DEPARTMENTS)
        .find(row => row.Status === ACTIVE_STATUS && String(row.DepartmentName) === String(ticket.Department));
      const departmentHodId = dept ? getRecordUserId_(dept, 'HODUserID', 'HODEmail') : '';
      const isDepartmentHod = String(departmentHodId) === String(assigned.UserID);
      if (String(assigned.Department) !== String(ticket.Department) && !isDepartmentHod) {
        throw new Error('The selected employee is not part of the ticket department.');
      }
      manager = getTicketReportingManager_(assigned, dept);
    }

    updateRowFields_(SHEET_NAMES.HELP_TICKETS, ticket._row, {
      AssignedToUserID: assigned ? assigned.UserID : '',
      AssignedToEmail: assigned ? normalizeEmail_(assigned.Email) : '',
      ReportingManagerUserID: manager ? manager.UserID : '',
      ReportingManagerEmail: manager ? normalizeEmail_(manager.Email) : '',
      ReportingManagerName: manager ? manager.Name : '',
      Status: assigned ? TICKET_STATUS.ASSIGNED : TICKET_STATUS.OPEN
    });

    appendTicketSystemMessage_(ticketId, me, assigned
      ? 'Ticket assigned to ' + assigned.EmployeeID + ' · ' + assigned.Name + '.'
      : 'Ticket moved to the unassigned queue.');
    logAudit_('HelpTickets', 'Assignment', ticketId + ' -> ' + (assigned ? assigned.EmployeeID : 'Unassigned'), me);
    createNotificationsForUsersV42_(ticketParticipantUserIdsV42_(ticket, [
        assigned ? assigned.UserID : '', manager ? manager.UserID : ''
      ]),
      'HELP_TICKET_ASSIGNMENT', ticketId + ' assignment updated',
      assigned ? ('Assigned to ' + assigned.EmployeeID + ' · ' + assigned.Name + ' by ' + me.Name + '.')
        : ('Moved to unassigned queue by ' + me.Name + '.'),
      'TICKET', ticketId, me.UserID);
    return true;
  });
}

function allowedTicketStatusesFor_(me, ticket, accessScope) {
  const all = Object.keys(TICKET_STATUS).map(key => TICKET_STATUS[key]);
  const scope = accessScope || getAccessScopeV40_(me);
  if (scope.mode === 'COMPANY') return all;

  const assigned = recordBelongsToUser_(ticket, me, 'AssignedToUserID', 'AssignedToEmail');
  if (canManageTicketDepartmentV41_(me, ticket, scope)) {
    return [TICKET_STATUS.ASSIGNED, TICKET_STATUS.IN_PROGRESS, TICKET_STATUS.WAITING,
      TICKET_STATUS.RESOLVED, TICKET_STATUS.CLOSED];
  }
  if (assigned) {
    return [TICKET_STATUS.IN_PROGRESS, TICKET_STATUS.WAITING, TICKET_STATUS.RESOLVED];
  }
  const creator = recordBelongsToUser_(ticket, me, 'CreatedByUserID', 'CreatedByEmail');
  if (creator && ticket.Status === TICKET_STATUS.RESOLVED) return [TICKET_STATUS.CLOSED];
  return [];
}

function api_getTicketMessages(sessionToken, ticketId) {
  return safeCall_('HelpTickets', 'api_getTicketMessages', () => {
    const me = requireSession_(sessionToken, null);
    requireTicketAccess_(me, ticketId);
    return sheetToObjects_(SHEET_NAMES.TICKET_MESSAGES)
      .filter(message => message.TicketID === ticketId)
      .map(message => {
        const copy = Object.assign({}, message);
        const sender = getUserById_(getRecordUserId_(message, 'SenderUserID', 'SenderEmail'));
        copy.SenderName = sender ? sender.Name : (message.SenderEmail || 'System');
        copy.SenderEmployeeID = sender ? sender.EmployeeID : '';
        return copy;
      })
      .sort((a, b) => new Date(a.Timestamp) - new Date(b.Timestamp));
  });
}

function api_addTicketMessage(sessionToken, ticketId, message) {
  return safeCall_('HelpTickets', 'api_addTicketMessage', () => {
    const me = requireSession_(sessionToken, null);
    requireTicketAccess_(me, ticketId);
    appendRowFromObject_(SHEET_NAMES.TICKET_MESSAGES, {
      MessageID: Utilities.getUuid(),
      TicketID: ticketId,
      SenderEmail: normalizeEmail_(me.Email),
      SenderUserID: me.UserID,
      Message: requireText_(message, 'Comment', 2000),
      Timestamp: new Date()
    });
    logAudit_('HelpTickets', 'Comment', ticketId, me);
    const ticket = sheetToObjects_(SHEET_NAMES.HELP_TICKETS).find(row => row.TicketID === ticketId);
    if (ticket) createNotificationsForUsersV42_(ticketParticipantUserIdsV42_(ticket),
      'HELP_TICKET_MESSAGE', 'New message on ' + ticketId,
      me.Name + ' added a message: ' + cleanText_(message, 180), 'TICKET', ticketId, me.UserID);
    return true;
  });
}

function api_getTicketAttachment(sessionToken, ticketId) {
  return safeCall_('HelpTickets', 'api_getTicketAttachment', () => {
    const me = requireSession_(sessionToken, null);
    const ticket = requireTicketAccess_(me, ticketId);
    return getTicketFileData_(ticket.PhotoFileID, 'This ticket has no attachment.');
  });
}

function api_getTicketVoiceNote(sessionToken, ticketId) {
  return safeCall_('HelpTickets', 'api_getTicketVoiceNote', () => {
    const me = requireSession_(sessionToken, null);
    const ticket = requireTicketAccess_(me, ticketId);
    return getTicketFileData_(ticket.VoiceNoteFileID, 'This ticket has no voice note.');
  });
}

function getTicketFileData_(fileId, missingMessage) {
  if (!fileId) throw new Error(missingMessage);
  const blob = DriveApp.getFileById(fileId).getBlob();
  return {
    fileName: blob.getName(),
    mimeType: blob.getContentType(),
    base64: Utilities.base64Encode(blob.getBytes())
  };
}

function requireTicketAccess_(me, ticketId) {
  const ticket = sheetToObjects_(SHEET_NAMES.HELP_TICKETS).find(row => row.TicketID === ticketId);
  if (!ticket) throw new Error('Ticket not found.');

  const participant =
    recordBelongsToUser_(ticket, me, 'CreatedByUserID', 'CreatedByEmail') ||
    recordBelongsToUser_(ticket, me, 'AssignedToUserID', 'AssignedToEmail');
  const scope = getAccessScopeV40_(me);
  const manager = scope.canViewTeamTickets && ticketIsInsideTeamScopeV41_(ticket, scope);
  if (!participant && !manager) throw new Error('You do not have access to this ticket.');
  return ticket;
}

function decorateTicketForClient_(ticket, me, accessScope) {
  const copy = Object.assign({}, ticket);
  const scope = accessScope || getAccessScopeV40_(me);
  const creator = getUserById_(getRecordUserId_(ticket, 'CreatedByUserID', 'CreatedByEmail'));
  const assignee = getUserById_(getRecordUserId_(ticket, 'AssignedToUserID', 'AssignedToEmail'));
  const storedManager = getUserById_(ticket.ReportingManagerUserID) || getUserByEmail_(ticket.ReportingManagerEmail);
  const manager = storedManager || (assignee ? getTicketReportingManager_(assignee, null) : null);

  copy.CreatedByUserID = creator ? creator.UserID : (ticket.CreatedByUserID || '');
  copy.CreatedByEmployeeID = creator ? creator.EmployeeID : '';
  copy.CreatedByName = creator ? creator.Name : (ticket.CreatedByEmail || '');
  copy.AssignedToUserID = assignee ? assignee.UserID : (ticket.AssignedToUserID || '');
  copy.AssignedEmployeeID = assignee ? assignee.EmployeeID : '';
  copy.AssignedToName = assignee ? assignee.Name : '';
  copy.ReportingManagerUserID = manager ? manager.UserID : (ticket.ReportingManagerUserID || '');
  copy.ReportingManagerEmployeeID = manager ? manager.EmployeeID : '';
  copy.ReportingManagerName = manager ? manager.Name : (ticket.ReportingManagerName || '');
  copy.IsCreatedByMe = recordBelongsToUser_(ticket, me, 'CreatedByUserID', 'CreatedByEmail');
  copy.IsAssignedToMe = recordBelongsToUser_(ticket, me, 'AssignedToUserID', 'AssignedToEmail');
  copy.IsUnassigned = !copy.AssignedToUserID;
  copy.AllowedStatuses = allowedTicketStatusesFor_(me, ticket, scope);
  copy.CanUpdateStatus = copy.AllowedStatuses.length > 0;
  copy.CanAssign = canManageTicketDepartmentV41_(me, ticket, scope);
  copy.IsOverdue = !!ticket.DueDate &&
    Utilities.formatDate(new Date(ticket.DueDate), APP_TIME_ZONE, 'yyyy-MM-dd') < Utilities.formatDate(new Date(), APP_TIME_ZONE, 'yyyy-MM-dd') &&
    [TICKET_STATUS.RESOLVED, TICKET_STATUS.CLOSED].indexOf(ticket.Status) === -1;
  return copy;
}

function buildTicketUserDirectory_() {
  const departments = sheetToObjects_(SHEET_NAMES.DEPARTMENTS).filter(row => row.Status === ACTIVE_STATUS);
  const users = getUsersCached_().filter(user => user.Status === ACTIVE_STATUS);
  return users.map(user => {
    const dept = departments.find(row => String(row.DepartmentName) === String(user.Department)) || null;
    const manager = getTicketReportingManager_(user, dept);
    const directManager = (user.ManagerUserID ? getUserById_(user.ManagerUserID) : null) ||
      (user.ManagerEmail ? getUserByEmail_(user.ManagerEmail) : null);
    return {
      UserID: user.UserID,
      EmployeeID: user.EmployeeID,
      Name: user.Name,
      Department: user.Department,
      Designation: user.Designation,
      Role: normalizeRole_(user.Role),
      ManagerUserID: manager ? manager.UserID : '',
      ManagerEmployeeID: manager ? manager.EmployeeID : '',
      ManagerName: manager ? manager.Name : '',
      DirectManagerUserID: directManager && directManager.Status === ACTIVE_STATUS ? directManager.UserID : '',
      DirectManagerEmployeeID: directManager && directManager.Status === ACTIVE_STATUS ? directManager.EmployeeID : '',
      DirectManagerName: directManager && directManager.Status === ACTIVE_STATUS ? directManager.Name : ''
    };
  }).sort((a, b) => String(a.EmployeeID).localeCompare(String(b.EmployeeID)));
}

function getTicketReportingManager_(user, dept) {
  if (!user) return null;

  // Employee master mapping is the first source of truth. v4.1 checked only
  // ManagerEmail, which is why employees mapped by ManagerUserID could show
  // "Not mapped" in Help Tickets.
  let direct = user.ManagerUserID ? getUserById_(user.ManagerUserID) : null;
  if (!direct && user.ManagerEmail) direct = getUserByEmail_(user.ManagerEmail);
  if (direct && direct.Status === ACTIVE_STATUS && String(direct.UserID) !== String(user.UserID)) return direct;

  // Fallback: active HOD of the employee/doer department.
  if (!dept) {
    dept = sheetToObjects_(SHEET_NAMES.DEPARTMENTS)
      .find(row => row.Status === ACTIVE_STATUS && String(row.DepartmentName) === String(user.Department));
  }
  const hodId = dept ? String(dept.HODUserID || '') : '';
  let hod = hodId ? getUserById_(hodId) : null;
  if (!hod && dept && dept.HODEmail) hod = getUserByEmail_(dept.HODEmail);
  if (hod && hod.Status === ACTIVE_STATUS && String(hod.UserID) !== String(user.UserID)) return hod;

  // Last fallback: if Department master has not yet been mapped, use the
  // active HOD-role employee from the same department. This covers existing
  // LPPL data where the employee master is correct before Departments.HODUserID
  // is populated.
  const sameDepartmentHods = getUsersCached_().filter(candidate =>
    candidate.Status === ACTIVE_STATUS &&
    normalizeRole_(candidate.Role) === ROLES.HOD &&
    String(candidate.Department || '') === String(user.Department || '') &&
    String(candidate.UserID || '') !== String(user.UserID || '')
  );
  return sameDepartmentHods.length === 1 ? sameDepartmentHods[0] : null;
}

function parseTicketDueDate_(value) {
  const text = cleanText_(value, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) throw new Error('Select a valid due date.');
  const today = Utilities.formatDate(new Date(), APP_TIME_ZONE, 'yyyy-MM-dd');
  if (text < today) throw new Error('Due date cannot be earlier than today.');
  const parsed = Utilities.parseDate(text, APP_TIME_ZONE, 'yyyy-MM-dd');
  if (Utilities.formatDate(parsed, APP_TIME_ZONE, 'yyyy-MM-dd') !== text) throw new Error('Select a valid due date.');
  return parsed;
}

function appendTicketSystemMessage_(ticketId, actor, message) {
  appendRowFromObject_(SHEET_NAMES.TICKET_MESSAGES, {
    MessageID: Utilities.getUuid(),
    TicketID: ticketId,
    SenderEmail: normalizeEmail_(actor && actor.Email),
    SenderUserID: actor ? actor.UserID : 'SYSTEM',
    Message: '[System] ' + cleanText_(message, 1900),
    Timestamp: new Date()
  });
}
