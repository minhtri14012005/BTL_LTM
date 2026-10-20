import { h, heading, link, button, notice } from "/js/shared/dom.js";
import { emptyState } from "/js/shared/ui.js";
import { kickPanel } from "/js/features/admin/kick.js";
import { sanctionControl, orphanControl } from "/js/features/admin/sanctions.js";
import { icon } from "/js/shared/icons.js";

const sections = { games: { title: "Trận đang chơi", path: "games", columns: ["Trận / Room", "Tên trận", "Giai đoạn", "Tiến trình", "Host"] }, users: { title: "Tài khoản", path: "users", columns: ["ID", "Tên đăng nhập", "Tên hiển thị", "Role", "Trạng thái / Chế tài"] }, orphans: { title: "Phòng thiếu Host", path: "orphan-rooms", columns: ["Room", "Tên phòng", "Host", "Lý do", "Trạng thái", "Thao tác"] } };
export async function adminPage(app, tab = "games", page = 0, game = null) {
  if (app.user?.role !== "ADMIN") return notice("Cần quyền ADMIN để xem trang này.", "error");
  if (!Object.hasOwn(sections, tab)) tab = "games";
  const section = sections[tab], data = await app.api.request("GET", `/api/admin/${section.path}?page=${page}&size=20`);
  const orphanRow = row => { const status = h("span", { className: "admin-room-status", id: `admin-room-status-${row.id}` }, row.status); return [`#${row.id}`, row.name, `#${row.hostUserId}`, row.reason === "HOST_BANNED" ? "Host đang bị ban" : row.reason === "HOST_DELETED" ? "Host đã bị xóa" : "Không tìm thấy Host", status, orphanControl(app, row, () => { status.textContent = "CLOSED"; })]; };
  const rows = data.items.map(row => tab === "users" ? [row.id, row.username, row.displayName, row.role, sanctionControl(app, row)] :
    tab === "games" ? [h("div", {}, `#${row.id} · Room #${row.roomId}`, h("p", {}, link("Quản lý Player", `admin?tab=games&game=${row.id}`, "button secondary"))), row.title || `Trận nhiều màn #${row.id}`, row.phase, `${row.questionIndex}/${row.questionCount}`, `#${row.hostUserId}`] :
      orphanRow(row));
  return h("section", { id: "admin-page", className: "admin-page", "data-tab": tab },
    heading("Quản trị", "Kiểm soát trận đấu, tài khoản và phòng thiếu Host.", h("span", { className: "badge admin-access" }, icon("warning"), "ADMIN")),
    tab === "games" && game > 0 ? await kickPanel(app, game) : null,
    h("div", { className: "card-top" }, h("h2", {}, section.title), button("Cập nhật", () => app.render(), { className: "secondary", id: "refresh-admin" })),
    h("p", { className: "muted", role: "status" }, `${data.totalElements} mục · trang ${page + 1}`),
    tab === "orphans" ? notice("Phòng WAITING có Host đang bị ban/đã xóa/không còn tồn tại và không có Game ACTIVE. Mất kết nối không được coi là thiếu Host.") : null,
    rows.length ? h("section", { className: "card admin-data" }, h("div", { className: "admin-table-scroll", tabIndex: 0, "aria-label": section.title },
      h("table", { className: "admin-table", role: "table", "aria-label": section.title }, h("thead", { role: "rowgroup" }, h("tr", { role: "row" }, section.columns.map(name => h("th", { scope: "col", role: "columnheader" }, name)))), h("tbody", { role: "rowgroup" }, rows.map((row, index) => h("tr", { role: "row", "data-record-id": data.items[index].id }, row.map((value, column) => h("td", { role: "cell", "data-label": section.columns[column] }, value)))))))) :
      emptyState("Chưa có mục phù hợp trong dữ liệu Server.", "Danh sách trống"),
    h("nav", { className: "pagination", "aria-label": "Trang dữ liệu quản trị" }, page > 0 ? link("Trang trước", `admin?tab=${tab}&page=${page - 1}`, "button secondary") : null,
      h("span", {}, `Trang ${page + 1}`), (page + 1) * data.size < data.totalElements ? link("Trang sau", `admin?tab=${tab}&page=${page + 1}`, "button secondary") : null));
}
