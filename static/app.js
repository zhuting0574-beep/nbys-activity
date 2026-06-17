document.addEventListener('click', (event) => {
  const openBtn = event.target.closest('[data-open-modal]');
  if (openBtn) {
    const id = openBtn.getAttribute('data-open-modal');
    const modal = document.getElementById(id);
    if (modal) modal.setAttribute('aria-hidden', 'false');
  }
  if (event.target.closest('[data-close-modal]')) {
    const modal = event.target.closest('.modal');
    if (modal) modal.setAttribute('aria-hidden', 'true');
  }
});

// V78: restore the page viewport after the soft keyboard closes in Android WeChat.
(function () {
  const root = document.documentElement;
  const editableSelector = 'input:not([type="checkbox"]):not([type="radio"]), textarea, select, [contenteditable="true"]';
  let stableHeight = 0;
  let focusScrollY = 0;
  let settleTimer = null;

  function viewportHeight() {
    const visualHeight = window.visualViewport?.height || 0;
    return Math.round(Math.max(visualHeight, window.innerHeight || 0, root.clientHeight || 0));
  }

  function syncViewport(force = false) {
    const active = document.activeElement;
    const editing = active && active.matches && active.matches(editableSelector);
    const height = viewportHeight();
    if (!height) return;
    if (!editing || force) stableHeight = Math.max(stableHeight, height);
    if (stableHeight) root.style.setProperty('--app-height', `${stableHeight}px`);
  }

  function settleAfterKeyboard() {
    clearTimeout(settleTimer);
    const startedAt = Date.now();
    const restore = () => {
      syncViewport(true);
      const maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
      window.scrollTo(0, Math.min(focusScrollY, maxScroll));
      if (Date.now() - startedAt < 700) settleTimer = setTimeout(restore, 120);
    };
    settleTimer = setTimeout(restore, 80);
  }

  syncViewport(true);
  window.addEventListener('load', () => syncViewport(true));
  window.addEventListener('pageshow', () => syncViewport(true));
  window.addEventListener('orientationchange', () => {
    stableHeight = 0;
    setTimeout(() => syncViewport(true), 250);
  });
  window.addEventListener('resize', () => syncViewport(false));
  window.visualViewport?.addEventListener('resize', () => syncViewport(false));

  document.addEventListener('focusin', (event) => {
    if (event.target.matches?.(editableSelector)) focusScrollY = window.scrollY;
  });
  document.addEventListener('focusout', (event) => {
    if (event.target.matches?.(editableSelector)) settleAfterKeyboard();
  });
})();

// V8: 活动策划日期选择框，点击 + 新增日期，点击 - 删除日期。
document.addEventListener('click', (event) => {
  const addBtn = event.target.closest('[data-add-date]');
  if (addBtn) {
    const wrap = addBtn.closest('[data-date-list]');
    const items = wrap?.querySelector('[data-date-list-items]');
    if (!items) return;
    const row = document.createElement('div');
    row.className = 'date-input-row';
    row.innerHTML = '<input type="date" name="dates" required><input type="text" name="date_notes" maxlength="80" placeholder="备注，可选" class="date-note-input" autocomplete="off"><button type="button" class="button danger small" data-remove-date>-</button>';
    items.appendChild(row);
  }
  const removeBtn = event.target.closest('[data-remove-date]');
  if (removeBtn) {
    const row = removeBtn.closest('.date-input-row');
    if (row) row.remove();
  }
});


// V17: mobile navigation toggle for phone layout.
document.addEventListener('click', (event) => {
  const menuBtn = event.target.closest('[data-mobile-menu]');
  if (menuBtn) {
    const nav = document.querySelector('[data-mobile-nav]');
    if (!nav) return;
    const isOpen = nav.classList.toggle('is-open');
    menuBtn.textContent = isOpen ? '收起' : '菜单';
    menuBtn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
  }
});

// V26: launcher rental photo preview.
document.addEventListener('click', (event) => {
  const previewBtn = event.target.closest('[data-preview-img]');
  if (!previewBtn) return;
  const modal = document.getElementById('imagePreviewModal');
  const img = document.getElementById('imagePreviewImg');
  const title = document.getElementById('imagePreviewTitle');
  if (!modal || !img) return;
  img.src = previewBtn.getAttribute('data-preview-img') || '';
  img.alt = previewBtn.getAttribute('data-preview-title') || '照片预览';
  if (title) title.textContent = previewBtn.getAttribute('data-preview-title') || '照片预览';
  modal.setAttribute('aria-hidden', 'false');
});


// V32: preserve scroll position for user-management actions such as toggling regular member.
(function () {
  const key = 'yongshi_preserve_scroll_y';
  document.addEventListener('submit', (event) => {
    const form = event.target.closest('form[data-preserve-scroll]');
    if (!form) return;
    try { sessionStorage.setItem(key, String(window.scrollY || 0)); } catch (e) {}
  });
  window.addEventListener('load', () => {
    let saved = null;
    try {
      saved = sessionStorage.getItem(key);
      sessionStorage.removeItem(key);
    } catch (e) {}
    if (saved !== null && saved !== '') {
      const y = parseInt(saved, 10);
      if (!Number.isNaN(y)) setTimeout(() => window.scrollTo(0, y), 0);
    }
  });
})();


// V36: attendance table toggle is saved asynchronously to avoid jumping back to top.
(function () {
  document.addEventListener('submit', async (event) => {
    const form = event.target.closest('form[data-attendance-toggle]');
    if (!form) return;
    event.preventDefault();

    const button = form.querySelector('.dot-button');
    if (!button || button.disabled) return;
    const userId = form.getAttribute('data-user-id');
    const oldPresent = button.classList.contains('present');
    const url = new URL(form.action, window.location.href);
    const params = new URLSearchParams(window.location.search);
    if (params.get('year')) url.searchParams.set('year', params.get('year'));
    if (params.get('region')) url.searchParams.set('region', params.get('region'));

    button.disabled = true;
    try {
      const response = await fetch(url.toString(), {
        method: 'POST',
        headers: {
          'X-Requested-With': 'XMLHttpRequest',
          'Accept': 'application/json'
        },
        credentials: 'same-origin'
      });
      if (!response.ok) throw new Error('保存失败');
      const data = await response.json();
      button.classList.toggle('present', !!data.present);
      const countEl = document.querySelector(`[data-attendance-count="${userId}"]`);
      if (countEl && typeof data.count !== 'undefined') countEl.textContent = String(data.count);
      const notice = document.querySelector('[data-floating-notice]');
      if (notice) {
        notice.textContent = '已保存';
        notice.classList.add('show');
        clearTimeout(window.__attendanceNoticeTimer);
        window.__attendanceNoticeTimer = setTimeout(() => notice.classList.remove('show'), 1200);
      }
    } catch (error) {
      button.classList.toggle('present', oldPresent);
      alert('出勤记录保存失败，请刷新后重试。');
    } finally {
      button.disabled = false;
    }
  });
})();


// V38: When opening or refreshing the attendance table, default to the latest activities on the right.
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.attendance-table-wrap').forEach((wrap) => {
    requestAnimationFrame(() => {
      wrap.scrollLeft = wrap.scrollWidth;
    });
  });
});

// V42: searchable invitee picker. Only non-regular users are rendered by templates.
(function () {
  function updateInviteeCount(scope) {
    const root = scope || document;
    root.querySelectorAll('form').forEach((form) => {
      const countEls = form.querySelectorAll('[data-invitee-count]');
      if (!countEls.length) return;
      const checked = form.querySelectorAll('[data-invitee-checkbox]:checked').length;
      countEls.forEach((el) => { el.textContent = `已选择 ${checked} 人`; });
    });
  }

  document.addEventListener('input', (event) => {
    const search = event.target.closest('[data-invitee-search]');
    if (!search) return;
    const modal = search.closest('.modal');
    const list = modal?.querySelector('[data-invitee-list]');
    if (!list) return;
    const keyword = (search.value || '').trim().toLowerCase();
    let visible = 0;
    list.querySelectorAll('[data-invitee-item]').forEach((item) => {
      const text = (item.getAttribute('data-search-text') || '').toLowerCase();
      const show = !keyword || text.includes(keyword);
      item.style.display = show ? '' : 'none';
      if (show) visible += 1;
    });
    let empty = list.querySelector('[data-invitee-empty]');
    if (!visible) {
      if (!empty) {
        empty = document.createElement('div');
        empty.className = 'invitee-empty';
        empty.setAttribute('data-invitee-empty', 'true');
        empty.textContent = '没有匹配的非正式队员';
        list.appendChild(empty);
      }
      empty.style.display = '';
    } else if (empty) {
      empty.style.display = 'none';
    }
  });

  document.addEventListener('change', (event) => {
    if (event.target.closest('[data-invitee-checkbox]')) updateInviteeCount(document);
  });

  document.addEventListener('DOMContentLoaded', () => updateInviteeCount(document));
})();

// V51: 逃离西撇镇仓库拖拽摆放。支持鼠标拖拽；手机端可点选物品后点目标格子。
(function () {
  let draggedItem = null;
  let selectedItem = null;
  let lastHoverCell = null;
  let dragAnchor = { rowOffset: 0, colOffset: 0 };

  function notice(message, ok = true) {
    const el = document.querySelector('[data-extraction-notice]') || document.querySelector('[data-floating-notice]');
    if (!el) {
      if (!ok) alert(message);
      return;
    }
    el.textContent = message;
    el.classList.toggle('error', !ok);
    el.classList.add('show');
    clearTimeout(window.__extractionNoticeTimer);
    window.__extractionNoticeTimer = setTimeout(() => el.classList.remove('show'), 1600);
  }

  function gridCellFromPoint(grid, clientX, clientY) {
    const rect = grid.getBoundingClientRect();
    const cols = parseInt(grid.dataset.cols || '1', 10);
    const rows = parseInt(grid.dataset.rows || '1', 10);
    const cellW = rect.width / cols;
    const cellH = rect.height / rows;
    const col = Math.floor((clientX - rect.left) / cellW) + 1;
    const row = Math.floor((clientY - rect.top) / cellH) + 1;
    if (row < 1 || col < 1 || row > rows || col > cols) return null;
    return { row, col };
  }

  function findCell(grid, row, col) {
    return grid.querySelector(`[data-drop-cell][data-row="${row}"][data-col="${col}"]`);
  }

  function topLeftFromPointerCell(item, cell) {
    return {
      row: cell.row - (dragAnchor.rowOffset || 0),
      col: cell.col - (dragAnchor.colOffset || 0)
    };
  }

  function clearHover() {
    if (lastHoverCell) {
      lastHoverCell.classList.remove('drop-hover', 'drop-deny');
      lastHoverCell = null;
    }
  }

  function isStackTarget(item, other, row, col) {
    return item.dataset.category === '钱币'
      && other.dataset.category === '钱币'
      && item.dataset.itemDefId === other.dataset.itemDefId
      && parseInt(other.dataset.row || '1', 10) === row
      && parseInt(other.dataset.col || '1', 10) === col;
  }

  function hoveredMoneyStackTarget(event, item, grid) {
    const target = event.target.closest('[data-inventory-item]');
    if (!target || target === item || target.closest('[data-warehouse-grid]') !== grid) return null;
    if (item.dataset.category !== '钱币' || target.dataset.category !== '钱币') return null;
    if (item.dataset.itemDefId !== target.dataset.itemDefId) return null;
    return {
      row: parseInt(target.dataset.row || '1', 10),
      col: parseInt(target.dataset.col || '1', 10)
    };
  }

  function canClientPlace(item, grid, row, col) {
    const rows = parseInt(grid.dataset.rows || '1', 10);
    const cols = parseInt(grid.dataset.cols || '1', 10);
    const w = parseInt(item.dataset.width || '1', 10);
    const h = parseInt(item.dataset.height || '1', 10);
    if (row < 1 || col < 1 || row + h - 1 > rows || col + w - 1 > cols) return false;
    return true;
    // 粗略前端碰撞检测，最终以后端为准。
    const itemId = item.dataset.itemId;
    const targetLocation = grid.dataset.location;
    const placed = grid.querySelectorAll('[data-inventory-item]');
    const targetCells = new Set();
    for (let rr = row; rr < row + h; rr++) for (let cc = col; cc < col + w; cc++) targetCells.add(`${rr},${cc}`);
    for (const other of placed) {
      if (other.dataset.itemId === itemId) continue;
      if (other.dataset.location !== targetLocation) continue;
      const orow = parseInt(other.dataset.row || '1', 10);
      const ocol = parseInt(other.dataset.col || '1', 10);
      if (isStackTarget(item, other, row, col)) return true;
      const ow = parseInt(other.dataset.width || '1', 10);
      const oh = parseInt(other.dataset.height || '1', 10);
      for (let rr = orow; rr < orow + oh; rr++) {
        for (let cc = ocol; cc < ocol + ow; cc++) {
          if (targetCells.has(`${rr},${cc}`)) return false;
        }
      }
    }
    return true;
  }

  async function moveItem(item, grid, row, col) {
    const id = item.dataset.itemId;
    const oldParent = item.parentElement;
    const oldStyle = { row: item.style.gridRow, col: item.style.gridColumn, location: item.dataset.location, dataRow: item.dataset.row, dataCol: item.dataset.col };
    item.style.gridRow = `${row} / span ${item.dataset.height || 1}`;
    item.style.gridColumn = `${col} / span ${item.dataset.width || 1}`;
    grid.appendChild(item);
    item.dataset.location = grid.dataset.location;
    item.dataset.row = String(row);
    item.dataset.col = String(col);
    try {
      const res = await fetch(`/extraction/inventory/${id}/place`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
        credentials: 'same-origin',
        body: JSON.stringify({ location: grid.dataset.location, row, col })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || !data.ok) throw new Error(data.message || '移动失败');
      if (data.stacked) {
        const target = document.querySelector(`[data-inventory-item][data-item-id="${data.target_id}"]`);
        if (target) {
          target.dataset.quantity = String(data.quantity || 1);
          const badge = target.querySelector('.item-quantity-badge') || document.createElement('span');
          badge.className = 'item-quantity-badge';
          badge.textContent = `×${data.quantity || 1}`;
          if (!badge.parentElement) target.appendChild(badge);
          const hoverQty = target.querySelector('[data-hover-quantity]');
          if (hoverQty) hoverQty.textContent = String(data.quantity || 1);
        }
        if (data.remaining_id && data.remaining_quantity > 0) {
          item.style.gridRow = oldStyle.row;
          item.style.gridColumn = oldStyle.col;
          item.dataset.location = oldStyle.location;
          item.dataset.row = oldStyle.dataRow;
          item.dataset.col = oldStyle.dataCol;
          oldParent.appendChild(item);
          item.dataset.quantity = String(data.remaining_quantity);
          const badge = item.querySelector('.item-quantity-badge') || document.createElement('span');
          badge.className = 'item-quantity-badge';
          badge.textContent = `×${data.remaining_quantity}`;
          if (!badge.parentElement) item.appendChild(badge);
          const hoverQty = item.querySelector('[data-hover-quantity]');
          if (hoverQty) hoverQty.textContent = String(data.remaining_quantity);
        } else {
          item.remove();
        }
      } else {
        const newRow = parseInt(data.row || row, 10);
        const newCol = parseInt(data.col || col, 10);
        item.style.gridRow = `${newRow} / span ${item.dataset.height || 1}`;
        item.style.gridColumn = `${newCol} / span ${item.dataset.width || 1}`;
        item.dataset.row = String(newRow);
        item.dataset.col = String(newCol);
      }
      notice(data.message || '已移动');
    } catch (err) {
      item.style.gridRow = oldStyle.row;
      item.style.gridColumn = oldStyle.col;
      item.dataset.location = oldStyle.location;
      item.dataset.row = oldStyle.dataRow;
      item.dataset.col = oldStyle.dataCol;
      oldParent.appendChild(item);
      notice(err.message || '移动失败', false);
    }
  }

  document.addEventListener('dragstart', (event) => {
    const item = event.target.closest('[data-inventory-item]');
    if (!item) return;
    draggedItem = item;
    const rect = item.getBoundingClientRect();
    const w = Math.max(1, parseInt(item.dataset.width || '1', 10));
    const h = Math.max(1, parseInt(item.dataset.height || '1', 10));
    const relX = Math.min(Math.max(event.clientX - rect.left, 0), Math.max(rect.width - 1, 0));
    const relY = Math.min(Math.max(event.clientY - rect.top, 0), Math.max(rect.height - 1, 0));
    dragAnchor = {
      colOffset: Math.min(w - 1, Math.max(0, Math.floor(relX / (rect.width / w || 1)))),
      rowOffset: Math.min(h - 1, Math.max(0, Math.floor(relY / (rect.height / h || 1))))
    };
    item.classList.add('is-dragging');
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', item.dataset.itemId || '');
  });

  document.addEventListener('dragend', () => {
    if (draggedItem) draggedItem.classList.remove('is-dragging');
    draggedItem = null;
    clearHover();
  });

  document.addEventListener('dragover', (event) => {
    if (!draggedItem) return;
    const grid = event.target.closest('[data-warehouse-grid]');
    if (!grid) return;
    event.preventDefault();
    const cell = gridCellFromPoint(grid, event.clientX, event.clientY);
    clearHover();
    if (!cell) return;
    const stackTarget = hoveredMoneyStackTarget(event, draggedItem, grid);
    const target = stackTarget || topLeftFromPointerCell(draggedItem, cell);
    const cellEl = findCell(grid, cell.row, cell.col);
    if (cellEl) {
      const ok = canClientPlace(draggedItem, grid, target.row, target.col) && !(draggedItem.dataset.location === 'storage' && grid.dataset.location === 'buffer');
      cellEl.classList.add(ok ? 'drop-hover' : 'drop-deny');
      lastHoverCell = cellEl;
    }
  });

  document.addEventListener('drop', (event) => {
    if (!draggedItem) return;
    const grid = event.target.closest('[data-warehouse-grid]');
    if (!grid) return;
    event.preventDefault();
    const cell = gridCellFromPoint(grid, event.clientX, event.clientY);
    clearHover();
    if (!cell) return;
    const stackTarget = hoveredMoneyStackTarget(event, draggedItem, grid);
    const target = stackTarget || topLeftFromPointerCell(draggedItem, cell);
    if (draggedItem.dataset.location === 'storage' && grid.dataset.location === 'buffer') {
      notice('个人仓库物品不能放回缓冲区。', false);
      return;
    }
    if (!canClientPlace(draggedItem, grid, target.row, target.col)) {
      notice('目标位置空间不足或发生重叠。', false);
      return;
    }
    moveItem(draggedItem, grid, target.row, target.col);
  });

  document.addEventListener('click', (event) => {
    if (event.target.closest('button, a, input, select, textarea, form')) return;
    const item = event.target.closest('[data-inventory-item]');
    if (item) {
      if (selectedItem) selectedItem.classList.remove('is-selected');
      selectedItem = item;
      selectedItem.classList.add('is-selected');
      notice('已选择物品，请点击目标格子移动。');
      return;
    }
    const cell = event.target.closest('[data-drop-cell]');
    if (!cell || !selectedItem) return;
    const grid = cell.closest('[data-warehouse-grid]');
    const row = parseInt(cell.dataset.row || '1', 10);
    const col = parseInt(cell.dataset.col || '1', 10);
    if (selectedItem.dataset.location === 'storage' && grid.dataset.location === 'buffer') {
      notice('个人仓库物品不能放回缓冲区。', false);
      return;
    }
    if (!canClientPlace(selectedItem, grid, row, col)) {
      notice('目标位置空间不足或发生重叠。', false);
      return;
    }
    const moving = selectedItem;
    selectedItem.classList.remove('is-selected');
    selectedItem = null;
    moveItem(moving, grid, row, col);
  });
})();

// V52: 逃离西撇镇出售箱。支持把物品拖入出售箱后批量出售。
(function () {
  const selected = new Map();
  function refreshSellBox() {
    const form = document.querySelector('[data-sell-box-form]');
    if (!form) return;
    const hidden = form.querySelector('[data-sell-hidden]');
    const list = form.querySelector('[data-sell-list]');
    const count = form.querySelector('[data-sell-count]');
    if (hidden) {
      hidden.innerHTML = '';
      selected.forEach((name, id) => {
        const input = document.createElement('input');
        input.type = 'hidden'; input.name = 'item_ids'; input.value = id;
        hidden.appendChild(input);
      });
    }
    if (list) {
      list.innerHTML = '';
      selected.forEach((name, id) => {
        const tag = document.createElement('span');
        tag.className = 'sell-tag';
        tag.textContent = name;
        tag.title = '点击移出出售箱';
        tag.addEventListener('click', () => { selected.delete(id); refreshSellBox(); });
        list.appendChild(tag);
      });
    }
    if (count) count.textContent = String(selected.size);
  }
  function addItem(item) {
    if (!item || !item.dataset.itemId) return;
    const name = item.querySelector('.grid-item-label strong')?.textContent || `物品${item.dataset.itemId}`;
    selected.set(item.dataset.itemId, name);
    refreshSellBox();
    const notice = document.querySelector('[data-extraction-notice]') || document.querySelector('[data-floating-notice]');
    if (notice) {
      notice.textContent = '已加入出售箱';
      notice.classList.add('show');
      clearTimeout(window.__sellNoticeTimer);
      window.__sellNoticeTimer = setTimeout(() => notice.classList.remove('show'), 1200);
    }
  }
  document.addEventListener('dragover', (event) => {
    const box = event.target.closest('[data-sell-drop]');
    if (!box) return;
    event.preventDefault();
    box.classList.add('sell-hover');
  });
  document.addEventListener('dragleave', (event) => {
    const box = event.target.closest('[data-sell-drop]');
    if (box) box.classList.remove('sell-hover');
  });
  document.addEventListener('drop', (event) => {
    const box = event.target.closest('[data-sell-drop]');
    if (!box) return;
    event.preventDefault();
    box.classList.remove('sell-hover');
    const id = event.dataTransfer?.getData('text/plain');
    const item = id ? document.querySelector(`[data-inventory-item][data-item-id="${id}"]`) : null;
    addItem(item);
  });
  document.addEventListener('dblclick', (event) => {
    const item = event.target.closest('[data-inventory-item]');
    if (!item) return;
    event.preventDefault();
    addItem(item);
  });
  document.addEventListener('click', (event) => {
    const clear = event.target.closest('[data-clear-sell-box]');
    if (clear) { selected.clear(); refreshSellBox(); return; }
    const box = event.target.closest('[data-sell-drop]');
    if (box) {
      const item = document.querySelector('[data-inventory-item].is-selected');
      if (item) addItem(item);
    }
  });
})();
