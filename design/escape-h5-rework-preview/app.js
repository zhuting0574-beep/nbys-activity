const tabs = [...document.querySelectorAll("[data-view]")];
const pages = [...document.querySelectorAll("[data-page]")];
const roleButtons = [...document.querySelectorAll("[data-role]")];
const managerTab = document.querySelector("[data-manager-tab]");
const topTabs = document.querySelector(".top-tabs");
const permissionCopy = document.querySelector("#permissionCopy");
const managerIdentity = document.querySelector("#managerIdentity");
const toast = document.querySelector(".toast");
let activeView = "profile";
let toastTimer;

function showToast(message) {
  window.clearTimeout(toastTimer);
  toast.textContent = message;
  toast.classList.add("show");
  toastTimer = window.setTimeout(() => toast.classList.remove("show"), 1800);
}

function switchView(view) {
  activeView = view;
  tabs.forEach((tab) => tab.classList.toggle("active", tab.dataset.view === view));
  pages.forEach((page) => page.classList.toggle("active", page.dataset.page === view));
  document.querySelector(".app-content").scrollTop = 0;
}

tabs.forEach((tab) => tab.addEventListener("click", () => switchView(tab.dataset.view)));

roleButtons.forEach((button) => {
  button.addEventListener("click", () => {
    const role = button.dataset.role;
    const canManage = role !== "member";
    roleButtons.forEach((item) => item.classList.toggle("active", item === button));
    managerTab.hidden = !canManage;
    topTabs.classList.toggle("two-tabs", !canManage);
    if (!canManage && activeView === "matches") switchView("profile");
    if (role === "creator") {
      permissionCopy.textContent = "当前以活动发起人身份预览，对局管理入口可见。";
      managerIdentity.textContent = "当前身份：活动发起人";
    } else if (role === "superadmin") {
      permissionCopy.textContent = "当前以超级管理员身份预览，对局管理入口可见。";
      managerIdentity.textContent = "当前身份：超级管理员";
    } else {
      permissionCopy.textContent = "当前以普通队员身份预览，对局管理入口已隐藏。";
    }
  });
});

const warehouseConfig = {
  buffer: { rows: 16, cols: 48 },
  personal: { rows: 8, cols: 12 }
};

const inventory = [
  { id: "intel", name: "机密情报箱", rarity: "red", width: 3, height: 2, location: "buffer", row: 1, col: 2, price: "8,860", image: "./assets/item-red.png" },
  { id: "night", name: "夜视瞄具", rarity: "orange", width: 2, height: 3, location: "buffer", row: 1, col: 7, price: "4,600", image: "./assets/item-orange.png" },
  { id: "chip", name: "加密芯片", rarity: "purple", width: 2, height: 1, location: "buffer", row: 5, col: 2, price: "2,280", symbol: "CHIP" },
  { id: "med", name: "医疗包", rarity: "blue", width: 2, height: 2, location: "buffer", row: 7, col: 5, price: "780", symbol: "MED" },
  { id: "battery", name: "军用电池", rarity: "blue", width: 1, height: 2, location: "buffer", row: 4, col: 11, price: "650", symbol: "BAT" },
  { id: "coin", name: "西撇镇钱币", rarity: "orange", width: 2, height: 1, location: "buffer", row: 10, col: 2, price: "1,200", symbol: "XP" },
  { id: "thermal", name: "热成像瞄具", rarity: "orange", width: 2, height: 3, location: "personal", row: 1, col: 1, price: "4,950", image: "./assets/item-orange.png" },
  { id: "suppressor", name: "特制消音器", rarity: "purple", width: 3, height: 1, location: "personal", row: 1, col: 5, price: "2,560", symbol: "SUP" },
  { id: "key", name: "战术钥匙", rarity: "blue", width: 1, height: 1, location: "personal", row: 4, col: 6, price: "920", symbol: "KEY" }
];

let selectedItemId = "intel";
let pointerState = null;

function overlaps(first, second) {
  return first.col < second.col + second.width && first.col + first.width > second.col && first.row < second.row + second.height && first.row + first.height > second.row;
}

function canPlace(item, location, row, col) {
  const config = warehouseConfig[location];
  if (!config || row < 1 || col < 1 || row + item.height - 1 > config.rows || col + item.width - 1 > config.cols) return false;
  const candidate = { row, col, width: item.width, height: item.height };
  return !inventory.some((other) => other.id !== item.id && other.location === location && overlaps(candidate, other));
}

function updateSelectedItem() {
  const item = inventory.find((entry) => entry.id === selectedItemId);
  if (!item) return;
  document.querySelector("#selectedItem").textContent = item.name;
  document.querySelector("#selectedPrice").textContent = `¥${item.price}`;
  document.querySelector("#selectedRarity").textContent = `已选中 · ${item.width} × ${item.height}`;
}

function usage(location) {
  return inventory.filter((item) => item.location === location).reduce((total, item) => total + item.width * item.height, 0);
}

function clearDropPreview() {
  document.querySelectorAll(".storage-cell.drop-valid, .storage-cell.drop-invalid").forEach((cell) => cell.classList.remove("drop-valid", "drop-invalid"));
}

function paintDropPreview(grid, item, row, col, valid) {
  clearDropPreview();
  for (let currentRow = row; currentRow < row + item.height; currentRow += 1) {
    for (let currentCol = col; currentCol < col + item.width; currentCol += 1) {
      const cell = grid.querySelector(`[data-row="${currentRow}"][data-col="${currentCol}"]`);
      if (cell) cell.classList.add(valid ? "drop-valid" : "drop-invalid");
    }
  }
}

function gridPosition(grid, clientX, clientY) {
  const rect = grid.getBoundingClientRect();
  const cellSize = Number.parseFloat(getComputedStyle(grid).getPropertyValue("--cell"));
  return {
    row: Math.floor((clientY - rect.top) / cellSize) + 1,
    col: Math.floor((clientX - rect.left) / cellSize) + 1
  };
}

function placeItem(itemId, location, row, col) {
  const item = inventory.find((entry) => entry.id === itemId);
  if (!item || !canPlace(item, location, row, col)) {
    showToast("目标位置空间不足或与其他物品重叠");
    return false;
  }
  const source = item.location === "buffer" ? "缓冲区" : "个人仓库";
  const target = location === "buffer" ? "缓冲区" : "个人仓库";
  item.location = location;
  item.row = row;
  item.col = col;
  selectedItemId = item.id;
  renderWarehouses();
  showToast(source === target ? `${item.name} 已重新放置` : `${item.name} 已移入${target}`);
  return true;
}

function createStorageItem(item) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = `storage-item ${item.rarity}${item.id === selectedItemId ? " selected" : ""}`;
  button.dataset.itemId = item.id;
  button.style.gridRow = `${item.row} / span ${item.height}`;
  button.style.gridColumn = `${item.col} / span ${item.width}`;
  button.setAttribute("aria-label", `${item.name}，尺寸 ${item.width} 乘 ${item.height}`);
  if (item.image) {
    const image = document.createElement("img");
    image.src = item.image;
    image.alt = "";
    button.appendChild(image);
  } else {
    const symbol = document.createElement("span");
    symbol.textContent = item.symbol;
    button.appendChild(symbol);
  }
  const size = document.createElement("em");
  size.textContent = `${item.width}×${item.height}`;
  button.appendChild(size);
  const label = document.createElement("small");
  label.textContent = item.name;
  button.appendChild(label);
  button.addEventListener("pointerdown", beginPointerDrag);
  return button;
}

function renderWarehouses() {
  document.querySelectorAll(".storage-grid").forEach((grid) => {
    const location = grid.dataset.storage;
    const config = warehouseConfig[location];
    grid.style.setProperty("--rows", config.rows);
    grid.style.setProperty("--cols", config.cols);
    grid.replaceChildren();
    for (let row = 1; row <= config.rows; row += 1) {
      for (let col = 1; col <= config.cols; col += 1) {
        const cell = document.createElement("div");
        cell.className = "storage-cell";
        cell.dataset.row = row;
        cell.dataset.col = col;
        cell.style.gridRow = row;
        cell.style.gridColumn = col;
        grid.appendChild(cell);
      }
    }
    inventory.filter((item) => item.location === location).forEach((item) => grid.appendChild(createStorageItem(item)));
    grid.onclick = (event) => {
      if (event.target.closest(".storage-item") || !selectedItemId) return;
      const position = gridPosition(grid, event.clientX, event.clientY);
      placeItem(selectedItemId, location, position.row, position.col);
    };
  });
  document.querySelector("#bufferUsage").textContent = `${usage("buffer")} / 768 格`;
  document.querySelector("#personalUsage").textContent = `${usage("personal")} / 96 格`;
  updateSelectedItem();
}

function beginPointerDrag(event) {
  const item = inventory.find((entry) => entry.id === event.currentTarget.dataset.itemId);
  if (!item) return;
  selectedItemId = item.id;
  updateSelectedItem();
  document.querySelectorAll(".storage-item").forEach((entry) => entry.classList.toggle("selected", entry.dataset.itemId === item.id));
  const source = event.currentTarget;
  source.setPointerCapture(event.pointerId);
  pointerState = {
    item,
    source,
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    dragging: false,
    ghost: null,
    target: null
  };
  source.addEventListener("pointermove", movePointerDrag);
  source.addEventListener("pointerup", endPointerDrag);
  source.addEventListener("pointercancel", cancelPointerDrag);
}

function startPointerDrag(event) {
  if (!pointerState || pointerState.dragging) return;
  pointerState.dragging = true;
  pointerState.source.classList.add("dragging");
  const ghost = pointerState.source.cloneNode(true);
  ghost.classList.add("storage-drag-ghost");
  ghost.classList.remove("selected", "dragging");
  const sourceRect = pointerState.source.getBoundingClientRect();
  ghost.style.width = `${sourceRect.width}px`;
  ghost.style.height = `${sourceRect.height}px`;
  document.body.appendChild(ghost);
  pointerState.ghost = ghost;
  positionGhost(event);
}

function positionGhost(event) {
  if (!pointerState?.ghost) return;
  pointerState.ghost.style.left = `${event.clientX}px`;
  pointerState.ghost.style.top = `${event.clientY}px`;
}

function autoScrollAtPointer(event) {
  const element = document.elementFromPoint(event.clientX, event.clientY);
  const scroll = element?.closest(".storage-scroll");
  if (scroll) {
    const rect = scroll.getBoundingClientRect();
    if (event.clientX > rect.right - 30) scroll.scrollLeft += 18;
    if (event.clientX < rect.left + 30) scroll.scrollLeft -= 18;
    if (event.clientY > rect.bottom - 24) scroll.scrollTop += 14;
    if (event.clientY < rect.top + 24) scroll.scrollTop -= 14;
  }
  const content = document.querySelector(".app-content");
  const contentRect = content.getBoundingClientRect();
  if (event.clientY > contentRect.bottom - 34) content.scrollTop += 16;
  if (event.clientY < contentRect.top + 34) content.scrollTop -= 16;
}

function updatePointerTarget(event) {
  const element = document.elementFromPoint(event.clientX, event.clientY);
  const grid = element?.closest(".storage-grid");
  if (!grid) {
    pointerState.target = null;
    clearDropPreview();
    return;
  }
  const position = gridPosition(grid, event.clientX, event.clientY);
  const location = grid.dataset.storage;
  const valid = canPlace(pointerState.item, location, position.row, position.col);
  pointerState.target = { location, row: position.row, col: position.col, valid };
  paintDropPreview(grid, pointerState.item, position.row, position.col, valid);
}

function movePointerDrag(event) {
  if (!pointerState || event.pointerId !== pointerState.pointerId) return;
  const distance = Math.hypot(event.clientX - pointerState.startX, event.clientY - pointerState.startY);
  if (!pointerState.dragging && distance > 6) startPointerDrag(event);
  if (!pointerState.dragging) return;
  event.preventDefault();
  positionGhost(event);
  autoScrollAtPointer(event);
  updatePointerTarget(event);
}

function finishPointerListeners() {
  if (!pointerState) return;
  pointerState.source.removeEventListener("pointermove", movePointerDrag);
  pointerState.source.removeEventListener("pointerup", endPointerDrag);
  pointerState.source.removeEventListener("pointercancel", cancelPointerDrag);
  pointerState.ghost?.remove();
  pointerState.source.classList.remove("dragging");
  clearDropPreview();
}

function endPointerDrag(event) {
  if (!pointerState || event.pointerId !== pointerState.pointerId) return;
  const target = pointerState.target;
  const itemId = pointerState.item.id;
  const wasDragging = pointerState.dragging;
  finishPointerListeners();
  pointerState = null;
  if (wasDragging && target?.valid) placeItem(itemId, target.location, target.row, target.col);
  else if (wasDragging) showToast("请把物品放到绿色可用格子内");
  else renderWarehouses();
}

function cancelPointerDrag() {
  finishPointerListeners();
  pointerState = null;
  renderWarehouses();
}

renderWarehouses();

document.querySelectorAll(".filter-tabs button, .match-filter button").forEach((button) => {
  button.addEventListener("click", () => {
    [...button.parentElement.children].forEach((item) => item.classList.toggle("active", item === button));
  });
});

document.querySelectorAll("[data-open]").forEach((button) => {
  button.addEventListener("click", () => document.querySelector(`#${button.dataset.open}`).showModal());
});

document.querySelectorAll("[data-action]").forEach((button) => {
  button.addEventListener("click", () => {
    const messages = {
      sell: "预览：出售前会展示价格与二次确认",
      buy: "预览：购买后物品进入缓冲区",
      created: "预览：战局已创建",
      settled: "预览：最终结算完成"
    };
    showToast(messages[button.dataset.action] || "操作已完成");
  });
});
