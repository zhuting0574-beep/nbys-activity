let rosterCache = null;
const openSections = new Map();
let lastRosterInteractionAt = 0;
const ROSTER_REFRESH_MS = 3000;
const ROSTER_INTERACTION_PAUSE_MS = 12000;

function markRosterInteraction() {
  lastRosterInteractionAt = Date.now();
}

function isRosterInteractionActive() {
  const active = document.activeElement;
  const activeInRoster = active && active.closest && active.closest('#organizationBoard');
  return !!activeInRoster || (Date.now() - lastRosterInteractionAt < ROSTER_INTERACTION_PAUSE_MS);
}

function isSectionOpen(key, defaultOpen = true) {
  return openSections.has(key) ? openSections.get(key) : defaultOpen;
}

function escapeHTML(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function showNotice(message, isError = false) {
  const el = document.getElementById('pageNotice');
  if (!el) return;
  el.textContent = message;
  el.style.display = 'block';
  el.style.borderColor = isError ? 'rgba(239,68,68,.45)' : 'rgba(56,189,248,.35)';
  el.style.background = isError ? 'rgba(239,68,68,.12)' : 'rgba(56,189,248,.10)';
  el.style.color = isError ? '#fecaca' : '#bae6fd';
  clearTimeout(showNotice.timer);
  showNotice.timer = setTimeout(() => { el.style.display = 'none'; }, 3500);
}

async function postJSON(url, payload = {}) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  const data = await res.json().catch(() => ({ ok: false, message: '服务器返回异常。' }));
  if (!res.ok || !data.ok) throw new Error(data.message || '操作失败。');
  return data;
}

function optionHTML(value, label, selected) {
  return `<option value="${escapeHTML(value ?? '')}" ${selected ? 'selected' : ''}>${escapeHTML(label)}</option>`;
}

function getMyEnrollment(data) {
  return data.rows.find(row => row.user_id === data.current_user_id) || null;
}

function canEditRow(data, row) {
  if (data.is_admin) return true;
  return row.user_id === data.current_user_id && data.can_self_adjust;
}

function renderStatus(data) {
  const statusText = document.getElementById('statusText');
  statusText.textContent = data.activity.status_text;
  statusText.className = `status ${data.activity.status_class}`;
  document.getElementById('signupCount').textContent = data.activity.signup_count;
  document.getElementById('lockState').textContent = data.activity.list_locked ? '管理员已锁定' : '未锁定';
  const lockBtn = document.getElementById('toggleLockBtn');
  if (lockBtn) lockBtn.textContent = data.activity.list_locked ? '解除锁定' : '锁定人员列表';
}

function renderStats(data) {
  const el = document.getElementById('activityStats');
  if (!el) return;
  const rows = data.rows || [];
  const jobCounts = {};
  for (const job of data.activity.allowed_jobs || []) jobCounts[job] = 0;
  let unselected = 0;
  for (const row of rows) {
    if (row.job) jobCounts[row.job] = (jobCounts[row.job] || 0) + 1;
    else unselected += 1;
  }
  const jobItems = Object.entries(jobCounts).map(([job, count]) => `
    <div class="stat-pill"><span>${escapeHTML(job)}</span><strong>${count}</strong></div>
  `).join('');
  el.innerHTML = `
    <div class="stat-card">
      <h3>职业数量</h3>
      <div class="stat-pill-grid">
        ${jobItems || '<span class="muted">暂无开放职业</span>'}
        <div class="stat-pill"><span>未选择职业</span><strong>${unselected}</strong></div>
        <div class="stat-pill"><span>报名总人数</span><strong>${rows.length}</strong></div>
      </div>
    </div>
  `;
}

function renderMemberRow(data, row) {
  const editable = canEditRow(data, row);
  const isMe = row.user_id === data.current_user_id;
  const leaderBadge = row.is_squad_leader ? '<span class="leader-badge">队长</span>' : '';
  const jobOptions = [optionHTML('', '未选择职业', !row.job)];
  for (const job of data.activity.allowed_jobs) {
    jobOptions.push(optionHTML(job, job, row.job === job));
  }

  let campPart = '';
  let squadPart = '';
  if (data.is_admin) {
    const campOptions = [optionHTML('', '未分配阵营', !row.camp_no)];
    for (let i = 1; i <= data.activity.camp_count; i++) {
      campOptions.push(optionHTML(i, `阵营 ${i}`, row.camp_no === i));
    }
    const squadOptions = [optionHTML('', '未分配小队', !row.squad_no)];
    for (let i = 1; i <= data.activity.squad_count; i++) {
      squadOptions.push(optionHTML(i, `小队 ${i}`, row.squad_no === i));
    }
    campPart = `<select class="compact-select" title="调整阵营" data-field="camp_no" data-enrollment="${row.id}">${campOptions.join('')}</select>`;
    squadPart = `<select class="compact-select" title="调整小队" data-field="squad_no" data-enrollment="${row.id}">${squadOptions.join('')}</select>`;
  }

  const jobPart = editable
    ? `<select class="compact-select" title="选择职业" data-field="job" data-enrollment="${row.id}">${jobOptions.join('')}</select>`
    : `<span class="readonly-pill">${row.job ? escapeHTML(row.job) : '未选择职业'}</span>`;

  const controlsClass = data.is_admin ? 'admin-controls' : 'user-controls';
  return `
    <div class="member-row compact-member-row ${data.is_admin ? 'admin-member-row' : 'user-member-row'} ${isMe ? 'me-member' : ''} ${row.is_squad_leader ? 'leader-member' : ''}" data-enrollment-row="${row.id}">
      <div class="member-main compact-member-main">
        <strong>${leaderBadge}${escapeHTML(row.callsign)}${isMe ? ' · 我' : ''}</strong>
        <span class="member-meta" title="报名时间：${escapeHTML(row.created_at)}">报名</span>
      </div>
      <div class="member-inline-controls ${controlsClass}">
        ${squadPart}
        ${jobPart}
        ${campPart}
      </div>
    </div>
  `;
}

function participantOptions(rows, selectedUserId, placeholder = '未指定') {
  const opts = [optionHTML('', placeholder, !selectedUserId)];
  for (const row of rows) {
    opts.push(optionHTML(row.user_id, row.callsign, selectedUserId === row.user_id));
  }
  return opts.join('');
}

function renderSquadSettings(data, camp, squad, squadRows, current) {
  const key = `${camp}-${squad}`;
  const setting = data.squad_settings[key] || { name: `小队 ${squad}`, radio_channel: '', leader_user_id: null, locked: false, can_edit_settings: false };
  const readonly = !setting.can_edit_settings;
  const nameInput = `<input class="squad-setting-input squad-name-input" title="小队名称" data-squad-setting="name" data-camp="${camp}" data-squad="${squad}" value="${escapeHTML(setting.name)}" ${readonly ? 'readonly' : ''}>`;
  const radioInput = `<input class="squad-setting-input radio-input" title="无线电频道" data-squad-setting="radio_channel" data-camp="${camp}" data-squad="${squad}" value="${escapeHTML(setting.radio_channel)}" ${readonly ? 'readonly' : ''}>`;
  let leaderSelect = '';
  if (data.is_admin) {
    leaderSelect = `<select class="compact-select leader-select" title="指定小队队长" data-squad-leader data-camp="${camp}" data-squad="${squad}">${participantOptions(squadRows, setting.leader_user_id, '未指定队长')}</select>`;
  } else if (setting.leader_callsign) {
    leaderSelect = `<span class="readonly-pill leader-pill">队长：${escapeHTML(setting.leader_callsign)}</span>`;
  }
  return `
    <div class="squad-setting-line" data-stop-summary>
      ${nameInput}
      ${radioInput}
      ${leaderSelect}
    </div>
  `;
}

function renderOrganization(data) {
  const my = getMyEnrollment(data);
  const container = document.getElementById('organizationBoard');
  const canUserClick = !!my && (data.is_admin || data.can_self_adjust);
  const rows = data.rows || [];
  container.innerHTML = '';

  const assignedUserIds = new Set();

  for (let camp = 1; camp <= data.activity.camp_count; camp++) {
    const campRows = rows.filter(row => row.camp_no === camp);
    const campCount = data.camp_counts[String(camp)] || 0;
    const campFull = campCount >= data.activity.camp_limit && (!my || my.camp_no !== camp);
    const campSetting = data.camp_settings[String(camp)] || {};
    const commanderSelect = data.is_admin
      ? `<select class="compact-select commander-select ${campRows.length > 15 ? 'long-commander-select' : ''}" title="指定阵营指挥" data-camp-commander data-camp="${camp}">${participantOptions(campRows, campSetting.commander_user_id, '未指定指挥')}</select>`
      : (campSetting.commander_callsign ? `<span class="readonly-pill">指挥：${escapeHTML(campSetting.commander_callsign)}</span>` : '<span class="readonly-pill">未指定指挥</span>');

    let squadHtml = '';
    for (let squad = 1; squad <= data.activity.squad_count; squad++) {
      const squadRows = rows.filter(row => row.camp_no === camp && row.squad_no === squad);
      for (const row of squadRows) assignedUserIds.add(row.id);

      const countKey = `${camp}-${squad}`;
      const squadCount = data.squad_counts[countKey] || 0;
      const full = campFull || (squadCount >= data.activity.squad_limit && (!my || !(my.camp_no === camp && my.squad_no === squad)));
      const current = my && my.camp_no === camp && my.squad_no === squad;
      const setting = data.squad_settings[countKey] || {};
      const squadLocked = !!setting.locked;
      const disabled = !canUserClick || full || current || squadLocked;
      const btnText = current ? '当前小队' : (squadLocked ? '小队已锁定' : (full ? '人数已满' : '加入此小队'));
      const lockBadge = squadLocked ? '<span class="locked-badge">已锁定</span>' : '';
      const lockButton = data.is_admin ? `<button class="secondary small-btn squad-lock-btn ${squadLocked ? 'locked' : ''}" data-toggle-squad-lock data-camp="${camp}" data-squad="${squad}">${squadLocked ? '解锁小队' : '锁定小队'}</button>` : '';

      const squadKey = `squad-${camp}-${squad}`;
      squadHtml += `
        <details class="squad-card ${current ? 'current-squad' : ''} ${squadLocked ? 'squad-locked' : ''}" data-collapse-key="${squadKey}" ${isSectionOpen(squadKey, true) ? 'open' : ''}>
          <summary class="squad-head">
            <div class="squad-title-wrap">
              <span class="collapse-icon">▶</span>
              <strong>${escapeHTML(setting.name || `小队 ${squad}`)}${current ? ' · 已加入' : ''}</strong>
              ${lockBadge}
              <span class="meta">${squadCount}/${data.activity.squad_limit}</span>
            </div>
            <div class="squad-actions" data-stop-summary>
              <button class="${current ? 'secondary' : 'primary'} small-btn" data-join-squad="${squad}" data-join-camp="${camp}" ${disabled ? 'disabled' : ''}>${btnText}</button>
              ${lockButton}
            </div>
            ${renderSquadSettings(data, camp, squad, squadRows, current)}
          </summary>
          <div class="member-list">
            ${squadRows.length ? squadRows.map(row => renderMemberRow(data, row)).join('') : '<div class="empty-squad muted">暂无人员</div>'}
          </div>
        </details>
      `;
    }

    const unassignedInCamp = campRows.filter(row => !row.squad_no);
    for (const row of unassignedInCamp) assignedUserIds.add(row.id);
    if (unassignedInCamp.length) {
      const unassignedKey = `squad-${camp}-unassigned`;
      squadHtml += `
        <details class="squad-card unassigned-card" data-collapse-key="${unassignedKey}" ${isSectionOpen(unassignedKey, true) ? 'open' : ''}>
          <summary class="squad-head"><span class="squad-title-wrap"><span class="collapse-icon">▶</span><strong>阵营 ${camp} · 未分配小队</strong></span></summary>
          <div class="member-list">${unassignedInCamp.map(row => renderMemberRow(data, row)).join('')}</div>
        </details>
      `;
    }

    const campKey = `camp-${camp}`;
    container.insertAdjacentHTML('beforeend', `
      <details class="camp-board" data-collapse-key="${campKey}" ${isSectionOpen(campKey, true) ? 'open' : ''}>
        <summary class="camp-head">
          <span class="camp-title-wrap"><span class="collapse-icon">▶</span><h2>阵营 ${camp}</h2></span>
          <span class="status ${campFull ? 'not-open' : 'signup'}">${campCount}/${data.activity.camp_limit}</span>
          <span class="camp-commander-wrap" data-stop-summary>${commanderSelect}</span>
        </summary>
        <div class="squad-list-vertical">${squadHtml}</div>
      </details>
    `);
  }

  const unassigned = rows.filter(row => !row.camp_no || !row.squad_no).filter(row => !assignedUserIds.has(row.id));
  if (unassigned.length) {
    container.insertAdjacentHTML('beforeend', `
      <details class="camp-board unassigned-board" data-collapse-key="camp-unassigned" ${isSectionOpen('camp-unassigned', true) ? 'open' : ''}>
        <summary class="camp-head"><span class="camp-title-wrap"><span class="collapse-icon">▶</span><h2>未分配阵营 / 小队</h2></span></summary>
        <div class="member-list">${unassigned.map(row => renderMemberRow(data, row)).join('')}</div>
      </details>
    `);
  }
}

async function loadRoster() {
  try {
    const res = await fetch(`/api/activities/${window.ACTIVITY_ID}/roster`);
    const data = await res.json();
    if (!data.ok) throw new Error(data.message || '加载失败。');
    rosterCache = data;
    renderStatus(data);
    renderOrganization(data);
    renderStats(data);
  } catch (err) {
    showNotice(err.message, true);
  }
}

async function joinActivity() {
  try {
    await postJSON(`/api/activities/${window.ACTIVITY_ID}/join`, {});
    showNotice('报名成功。请点击目标小队加入阵营和小队。');
    window.location.reload();
  } catch (err) {
    showNotice(err.message, true);
  }
}

async function cancelJoin() {
  if (!confirm('确认要取消本次活动报名吗？确认后你会从当前加入的阵营和小队中移除。')) return;
  try {
    await postJSON(`/api/activities/${window.ACTIVITY_ID}/cancel`, {});
    showNotice('已取消报名。');
    window.location.reload();
  } catch (err) {
    showNotice(err.message, true);
  }
}

async function updateEnrollment(enrollmentId, payloadOrField, value) {
  try {
    let payload = {};
    if (typeof payloadOrField === 'object') {
      payload = payloadOrField;
    } else {
      payload[payloadOrField] = value;
    }
    markRosterInteraction();
    await postJSON(`/api/enrollments/${enrollmentId}/update`, payload);
    showNotice('已自动保存。');

    // 职业这类字段更新后，不立即重绘整个人员列表，避免手机端横向滑动位置被刷新重置。
    // 阵营 / 小队调整会影响人员所在位置，因此仍然刷新列表。
    const needReload = Object.prototype.hasOwnProperty.call(payload, 'camp_no') || Object.prototype.hasOwnProperty.call(payload, 'squad_no');
    if (needReload) {
      await loadRoster();
    } else if (rosterCache && Array.isArray(rosterCache.rows)) {
      const row = rosterCache.rows.find(item => String(item.id) === String(enrollmentId));
      if (row) Object.assign(row, payload);
      renderStats(rosterCache);
    }
  } catch (err) {
    showNotice(err.message, true);
    await loadRoster();
  }
}

async function updateSquadSetting(input) {
  const camp = input.getAttribute('data-camp');
  const squad = input.getAttribute('data-squad');
  const nameEl = document.querySelector(`[data-squad-setting="name"][data-camp="${camp}"][data-squad="${squad}"]`);
  const radioEl = document.querySelector(`[data-squad-setting="radio_channel"][data-camp="${camp}"][data-squad="${squad}"]`);
  try {
    await postJSON(`/api/activities/${window.ACTIVITY_ID}/squad/${camp}/${squad}/settings`, {
      name: nameEl ? nameEl.value : '',
      radio_channel: radioEl ? radioEl.value : ''
    });
    showNotice('小队信息已保存。');
    await loadRoster();
  } catch (err) {
    showNotice(err.message, true);
    await loadRoster();
  }
}

async function updateSquadLeader(select) {
  const camp = select.getAttribute('data-camp');
  const squad = select.getAttribute('data-squad');
  try {
    await postJSON(`/api/activities/${window.ACTIVITY_ID}/squad/${camp}/${squad}/leader`, {
      leader_user_id: select.value
    });
    showNotice('小队队长已更新。');
    await loadRoster();
  } catch (err) {
    showNotice(err.message, true);
    await loadRoster();
  }
}

async function toggleSquadLock(button) {
  const camp = button.getAttribute('data-camp');
  const squad = button.getAttribute('data-squad');
  try {
    const data = await postJSON(`/api/activities/${window.ACTIVITY_ID}/squad/${camp}/${squad}/toggle-lock`, {});
    showNotice(data.message || '小队锁定状态已更新。');
    await loadRoster();
  } catch (err) {
    showNotice(err.message, true);
    await loadRoster();
  }
}


async function updateCampCommander(select) {
  const camp = select.getAttribute('data-camp');
  try {
    await postJSON(`/api/activities/${window.ACTIVITY_ID}/camp/${camp}/commander`, {
      commander_user_id: select.value
    });
    showNotice('阵营指挥已更新。');
    await loadRoster();
  } catch (err) {
    showNotice(err.message, true);
    await loadRoster();
  }
}


async function updateActivityOrganization(form) {
  const formData = new FormData(form);
  const payload = {
    camp_count: formData.get('camp_count'),
    squad_count: formData.get('squad_count'),
    camp_limit: formData.get('camp_limit'),
    squad_limit: formData.get('squad_limit')
  };
  if (!confirm('确认修改阵营/小队设置吗？所有已报名人员都会重置为未分配，需要管理员重新分配。')) return;
  try {
    await postJSON(`/api/activities/${window.ACTIVITY_ID}/organization`, payload);
    showNotice('组织结构已更新，人员已重置为未分配。');
    const modal = document.getElementById('orgEditModal');
    if (modal) modal.setAttribute('aria-hidden', 'true');
    await loadRoster();
  } catch (err) {
    showNotice(err.message, true);
  }
}

document.addEventListener('toggle', (event) => {
  const detail = event.target.closest('details[data-collapse-key]');
  if (!detail) return;
  openSections.set(detail.getAttribute('data-collapse-key'), detail.open);
}, true);

document.addEventListener('pointerdown', (event) => {
  if (event.target.closest('#organizationBoard')) markRosterInteraction();
}, true);

document.addEventListener('focusin', (event) => {
  if (event.target.closest('#organizationBoard')) markRosterInteraction();
}, true);

document.addEventListener('touchmove', (event) => {
  if (event.target.closest('#organizationBoard')) markRosterInteraction();
}, { passive: true, capture: true });

document.addEventListener('click', async (event) => {
  if (event.target.closest('[data-stop-summary]')) {
    event.stopPropagation();
  }

  const signupDirect = event.target.closest('#signupDirectBtn');
  if (signupDirect) joinActivity();

  const cancelApiBtn = event.target.closest('#confirmCancelJoin');
  if (cancelApiBtn) cancelJoin();

  const squadBtn = event.target.closest('[data-join-squad][data-join-camp]');
  if (squadBtn && rosterCache) {
    event.preventDefault();
    event.stopPropagation();
    const my = getMyEnrollment(rosterCache);
    if (my) {
      updateEnrollment(my.id, {
        camp_no: squadBtn.getAttribute('data-join-camp'),
        squad_no: squadBtn.getAttribute('data-join-squad')
      });
    }
  }

  const squadLockBtn = event.target.closest('button[data-toggle-squad-lock]');
  if (squadLockBtn) {
    event.preventDefault();
    event.stopPropagation();
    await toggleSquadLock(squadLockBtn);
    return;
  }

  const lockBtn = event.target.closest('#toggleLockBtn');
  if (lockBtn) {
    try {
      await postJSON(`/api/activities/${window.ACTIVITY_ID}/toggle-lock`, {});
      showNotice('人员列表锁定状态已更新。');
      await loadRoster();
    } catch (err) {
      showNotice(err.message, true);
    }
  }
});

document.addEventListener('submit', async (event) => {
  const orgForm = event.target.closest('#orgEditForm');
  if (orgForm) {
    event.preventDefault();
    await updateActivityOrganization(orgForm);
    return;
  }

  const form = event.target.closest('#cancelSignupForm');
  if (!form) return;
  event.preventDefault();
  await cancelJoin();
});

document.addEventListener('change', async (event) => {
  if (event.target.closest('#organizationBoard')) markRosterInteraction();
  const select = event.target.closest('select[data-field][data-enrollment]');
  if (select) {
    const field = select.getAttribute('data-field');
    const enrollmentId = select.getAttribute('data-enrollment');
    if (field === 'camp_no' && select.value === '') {
      updateEnrollment(enrollmentId, { camp_no: '', squad_no: '' });
    } else {
      updateEnrollment(enrollmentId, field, select.value);
    }
    return;
  }
  const leaderSelect = event.target.closest('select[data-squad-leader]');
  if (leaderSelect) {
    updateSquadLeader(leaderSelect);
    return;
  }
  const commanderSelect = event.target.closest('select[data-camp-commander]');
  if (commanderSelect) {
    updateCampCommander(commanderSelect);
  }
});

document.addEventListener('blur', (event) => {
  const input = event.target.closest('input[data-squad-setting]');
  if (!input || input.readOnly) return;
  updateSquadSetting(input);
}, true);

document.addEventListener('keydown', (event) => {
  const input = event.target.closest('input[data-squad-setting]');
  if (!input || input.readOnly) return;
  if (event.key === 'Enter') {
    event.preventDefault();
    input.blur();
  }
});

loadRoster();
setInterval(() => {
  if (!isRosterInteractionActive()) loadRoster();
}, ROSTER_REFRESH_MS);
