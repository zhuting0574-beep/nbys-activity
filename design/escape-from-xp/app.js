const modeButtons = document.querySelectorAll(".mode-btn");
const prototypes = {
  h5: document.querySelector("#h5Prototype"),
  admin: document.querySelector("#adminPrototype")
};

modeButtons.forEach((button) => {
  button.addEventListener("click", () => {
    modeButtons.forEach((item) => item.classList.toggle("active", item === button));
    Object.entries(prototypes).forEach(([key, node]) => node.classList.toggle("active", key === button.dataset.mode));
  });
});

document.querySelectorAll(".nav-item").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("active", item === button));
    document.querySelectorAll(".mobile-view").forEach((view) => view.classList.toggle("active", view.dataset.mobileView === button.dataset.view));
  });
});

document.querySelectorAll("[data-open-dialog]").forEach((button) => {
  button.addEventListener("click", () => document.querySelector(`#${button.dataset.openDialog}`).showModal());
});

document.querySelectorAll(".loot").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelector("#selectedItemName").textContent = button.dataset.item;
    document.querySelectorAll(".loot").forEach((item) => item.style.filter = item === button ? "brightness(1.25)" : "");
  });
});

const adminTitles = {
  overview: "运营概览",
  items: "物品配置",
  season: "赛季与规则",
  classes: "职业与武器",
  assets: "用户资产"
};

document.querySelectorAll("[data-admin-view]").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelectorAll("[data-admin-view]").forEach((item) => item.classList.toggle("active", item === button));
    document.querySelectorAll(".admin-view").forEach((page) => page.classList.toggle("active", page.dataset.adminPage === button.dataset.adminView));
    document.querySelector("#adminPageTitle").textContent = adminTitles[button.dataset.adminView];
  });
});
