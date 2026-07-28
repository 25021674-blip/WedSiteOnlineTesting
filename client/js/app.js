import {
    ApiError,
    clearAuthSession,
    getAuthToken,
    getExams,
    getMyResults,
    getStoredUser,
    readJwtPayload
} from "./api.js";

const icons = {
    home: `<svg viewBox="0 0 24 24"><path d="m3 11 9-8 9 8"/><path d="M5 10v10h14V10M9 20v-6h6v6"/></svg>`,
    exam: `<svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M8 13h8M8 17h6"/></svg>`,
    score: `<svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="5"/><path d="m8.5 12-1.2 9 4.7-2.8 4.7 2.8-1.2-9"/></svg>`,
    logout: `<svg viewBox="0 0 24 24"><path d="M10 17l5-5-5-5M15 12H3"/><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/></svg>`,
    menu: `<svg viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h16"/></svg>`,
    close: `<svg viewBox="0 0 24 24"><path d="m6 6 12 12M18 6 6 18"/></svg>`,
    check: `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/></svg>`,
    clock: `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>`,
    calendar: `<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/></svg>`,
    user: `<svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>`,
    search: `<svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>`,
    refresh: `<svg viewBox="0 0 24 24"><path d="M20 6v5h-5M4 18v-5h5"/><path d="M6.1 9A7 7 0 0 1 18 6l2 5M3.9 13l2 5a7 7 0 0 0 11.9-3"/></svg>`,
    arrow: `<svg viewBox="0 0 24 24"><path d="M5 12h14M13 6l6 6-6 6"/></svg>`,
    file: `<svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/></svg>`,
    empty: `<svg viewBox="0 0 24 24"><path d="M4 7h16v13H4zM8 4h8M8 11h8M8 15h5"/></svg>`
};

const pageConfig = {
    home: { title: "Trang chủ" },
    exams: { title: "Bài kiểm tra" },
    scores: { title: "Điểm của tôi" }
};

const dateFormatter = new Intl.DateTimeFormat("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
});

const state = {
    exams: [],
    results: {},
    loading: true,
    error: "",
    query: "",
    filter: "ALL"
};

const scene = document.querySelector("#scene");
const sidebar = document.querySelector("#sidebar");
const backdrop = document.querySelector("#sidebar-backdrop");
const dialog = document.querySelector("#exam-dialog");
let toastTimer;

function icon(name) {
    return icons[name] || icons.home;
}

function escapeHtml(value = "") {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function getRoute() {
    const route = window.location.hash.slice(1);
    return pageConfig[route] ? route : "home";
}

function getIdentity() {
    const stored = getStoredUser();
    const claims = readJwtPayload();
    const email = stored.email || claims.sub || "";
    const name = stored.fullName || stored.name || (email ? email.split("@")[0] : "Học sinh");
    const words = name.trim().split(/\s+/).filter(Boolean);
    const initials = words.length
        ? `${words[0][0] || ""}${words.length > 1 ? words.at(-1)[0] : words[0][1] || ""}`.toUpperCase()
        : "HS";
    return { name, email, initials };
}

function applyIdentity() {
    const user = getIdentity();
    document.querySelector("#sidebar-name").textContent = user.name;
    document.querySelector("#sidebar-email").textContent = user.email || "Chưa đăng nhập";
    document.querySelector("#sidebar-avatar").textContent = user.initials;
    document.querySelector("#topbar-name").textContent = user.name;
    document.querySelector("#topbar-avatar").textContent = user.initials;
}

function parseDate(value) {
    if (!value) return null;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
}

function formatDate(value) {
    const date = parseDate(value);
    return date ? dateFormatter.format(date) : "Chưa cập nhật";
}

function formatType(type) {
    return type === "ESSAY" ? "Tự luận PDF" : "Trắc nghiệm";
}

function examState(exam) {
    const result = state.results[String(exam.id)];
    if (result) {
        if (exam.type === "ESSAY" && result.score == null) {
            return { key: "WAITING", label: "Chờ chấm", className: "waiting" };
        }
        return { key: "SUBMITTED", label: "Đã có điểm", className: "submitted" };
    }

    const now = Date.now();
    const start = parseDate(exam.startTime)?.getTime();
    const deadline = parseDate(exam.deadline)?.getTime();
    if (start && now < start) return { key: "UPCOMING", label: "Sắp diễn ra", className: "upcoming" };
    if (deadline && now >= deadline) return { key: "CLOSED", label: "Đã kết thúc", className: "closed" };
    return { key: "OPEN", label: "Đang mở", className: "open" };
}

function getCounts() {
    const statuses = state.exams.map(examState);
    return {
        open: statuses.filter(status => status.key === "OPEN").length,
        upcoming: statuses.filter(status => status.key === "UPCOMING").length,
        submitted: statuses.filter(status => ["SUBMITTED", "WAITING"].includes(status.key)).length,
        graded: statuses.filter(status => status.key === "SUBMITTED").length
    };
}

function nextExam() {
    return [...state.exams]
        .filter(exam => ["OPEN", "UPCOMING"].includes(examState(exam).key))
        .sort((a, b) => {
            const aStatus = examState(a).key === "OPEN" ? 0 : 1;
            const bStatus = examState(b).key === "OPEN" ? 0 : 1;
            return aStatus - bStatus || (parseDate(a.deadline) - parseDate(b.deadline));
        })[0];
}

function statCard(iconName, tone, value, label) {
    return `
        <article class="stat-card">
            <div class="stat-card__top">
                <span class="stat-icon stat-icon--${tone}">${icon(iconName)}</span>
            </div>
            <div class="stat-card__value">${value}</div>
            <p class="stat-card__label">${label}</p>
        </article>
    `;
}

function errorState() {
    const isUnauthorized = !getAuthToken() || state.errorStatus === 401;
    return `
        <div class="message-state message-state--error">
            <h3>${isUnauthorized ? "Chưa có phiên đăng nhập học sinh" : "Chưa tải được dữ liệu"}</h3>
            <p>${escapeHtml(state.error)}</p>
            <button class="button" type="button" data-action="reload">${icon("refresh")} Thử tải lại</button>
        </div>
    `;
}

function loadingState(count = 3) {
    return `
        <div class="loading-grid" aria-label="Đang tải dữ liệu">
            ${Array.from({ length: count }, () => `<div class="loading-card skeleton"></div>`).join("")}
        </div>
    `;
}

function emptyState(title, message) {
    return `
        <div class="empty-state">
            <span class="empty-state__icon">${icon("empty")}</span>
            <h3>${escapeHtml(title)}</h3>
            <p>${escapeHtml(message)}</p>
        </div>
    `;
}

function activityRow(exam) {
    const status = examState(exam);
    const timeLabel = status.key === "UPCOMING" ? "Mở lúc" : "Hạn đến";
    return `
        <article class="activity-row">
            <div>
                <span class="type-label">${formatType(exam.type)}</span>
                <h4>${escapeHtml(exam.title)}</h4>
                <p>${escapeHtml(exam.createdByName || "Giáo viên")} · ${timeLabel} ${formatDate(status.key === "UPCOMING" ? exam.startTime : exam.deadline)}</p>
            </div>
            <div class="activity-row__aside">
                <span class="status status--${status.className}">${status.label}</span>
                <small>${exam.durationMinutes ? `${exam.durationMinutes} phút` : "Nộp file PDF"}</small>
            </div>
        </article>
    `;
}

function homeScene() {
    if (state.loading) return loadingState(4);
    if (state.error) return errorState();

    const user = getIdentity();
    const counts = getCounts();
    const spotlight = nextExam();
    const attentionExams = [...state.exams]
        .filter(exam => ["OPEN", "UPCOMING", "WAITING"].includes(examState(exam).key))
        .sort((a, b) => (parseDate(a.deadline) || 0) - (parseDate(b.deadline) || 0))
        .slice(0, 4);

    return `
        <section class="hero">
            <div class="hero__content">
                <span class="hero__tag">Giao diện học sinh</span>
                <h2>Chào ${escapeHtml(user.name)}!</h2>
                <p>Theo dõi bài kiểm tra và điểm của bạn tại một nơi. Dữ liệu được cập nhật trực tiếp từ hệ thống.</p>
                <div class="hero__chips">
                    <span class="hero-chip">${counts.open} bài đang mở</span>
                    <span class="hero-chip">${counts.upcoming} bài sắp diễn ra</span>
                </div>
            </div>
            <div class="hero__aside">
                <div class="hero-spotlight">
                    <small>${spotlight ? "Cần chú ý" : "Hôm nay"}</small>
                    <strong>${spotlight ? escapeHtml(spotlight.title) : "Không có bài sắp tới"}</strong>
                    <span>${spotlight ? `${examState(spotlight).label} · ${formatDate(spotlight.deadline)}` : "Bạn đã theo dõi hết các bài kiểm tra hiện có."}</span>
                </div>
            </div>
        </section>

        <div class="stats-grid">
            ${statCard("exam", "blue", counts.open, "Bài kiểm tra đang mở")}
            ${statCard("clock", "amber", counts.upcoming, "Bài sắp diễn ra")}
            ${statCard("check", "violet", counts.submitted, "Bài đã nộp")}
            ${statCard("score", "green", counts.graded, "Bài đã có điểm")}
        </div>

        <div class="content-grid">
            <section class="section-card">
                <div class="section-card__header">
                    <h3 class="section-title"><span class="section-icon">${icon("exam")}</span>Bài cần chú ý</h3>
                    <span class="section-meta">${attentionExams.length} BÀI</span>
                </div>
                ${attentionExams.length
                    ? `<div class="activity-list">${attentionExams.map(activityRow).join("")}</div>`
                    : emptyState("Chưa có bài cần chú ý", "Bài kiểm tra mới từ giáo viên sẽ xuất hiện tại đây.")}
            </section>

            <aside class="section-card">
                <div class="section-card__header">
                    <h3 class="section-title"><span class="section-icon">${icon("arrow")}</span>Truy cập nhanh</h3>
                </div>
                <div class="quick-links">
                    <a class="quick-link" href="#exams">
                        <span class="stat-icon stat-icon--blue">${icon("exam")}</span>
                        <span><strong>Xem bài kiểm tra</strong><small>Thời gian và trạng thái bài</small></span>
                    </a>
                    <a class="quick-link" href="#scores">
                        <span class="stat-icon stat-icon--green">${icon("score")}</span>
                        <span><strong>Điểm của tôi</strong><small>Xem điểm và phản hồi</small></span>
                    </a>
                </div>
            </aside>
        </div>
    `;
}

function examCard(exam) {
    const status = examState(exam);
    return `
        <article class="exam-card" data-search="${escapeHtml(`${exam.title} ${exam.description || ""}`.toLocaleLowerCase("vi"))}" data-status="${status.key}">
            <div class="exam-card__top">
                <span class="type-label">${formatType(exam.type)}</span>
                <span class="status status--${status.className}">${status.label}</span>
            </div>
            <h3>${escapeHtml(exam.title)}</h3>
            <p class="exam-card__description">${escapeHtml(exam.description || "Bài kiểm tra chưa có mô tả.")}</p>
            <div class="exam-card__meta">
                <span class="exam-meta">${icon("calendar")} Mở: ${formatDate(exam.startTime)}</span>
                <span class="exam-meta">${icon("clock")} Hạn: ${formatDate(exam.deadline)}</span>
                <span class="exam-meta">${icon("user")} ${escapeHtml(exam.createdByName || "Giáo viên")}</span>
            </div>
            <div class="exam-card__footer">
                <small>${exam.durationMinutes ? `${exam.durationMinutes} phút` : "Bài nộp PDF"}</small>
                <button class="button button--soft" type="button" data-action="details" data-exam-id="${exam.id}">Xem thông tin</button>
            </div>
        </article>
    `;
}

function filteredExams() {
    const query = state.query.trim().toLocaleLowerCase("vi");
    return state.exams.filter(exam => {
        const text = `${exam.title} ${exam.description || ""} ${exam.createdByName || ""}`.toLocaleLowerCase("vi");
        const matchesQuery = !query || text.includes(query);
        const currentStatus = examState(exam).key;
        const matchesFilter = state.filter === "ALL"
            || currentStatus === state.filter
            || (state.filter === "SUBMITTED" && currentStatus === "WAITING");
        return matchesQuery && matchesFilter;
    });
}

function examsScene() {
    if (state.loading) return loadingState();
    if (state.error) return errorState();
    const exams = filteredExams();
    return `
        <div class="page-toolbar">
            <div>
                <h2>Danh sách bài kiểm tra</h2>
                <p>${state.exams.length} bài được lấy từ hệ thống</p>
            </div>
            <div class="toolbar-actions">
                <label class="search-box">
                    ${icon("search")}
                    <input id="exam-search" type="search" value="${escapeHtml(state.query)}" placeholder="Tìm bài kiểm tra..." aria-label="Tìm bài kiểm tra">
                </label>
                <button class="button button--soft" type="button" data-action="reload">${icon("refresh")} Làm mới</button>
            </div>
        </div>
        <div class="filters" aria-label="Lọc bài kiểm tra">
            ${[
                ["ALL", "Tất cả"],
                ["OPEN", "Đang mở"],
                ["UPCOMING", "Sắp diễn ra"],
                ["SUBMITTED", "Đã nộp"],
                ["CLOSED", "Đã kết thúc"]
            ].map(([value, label]) => `
                <button class="filter-button ${state.filter === value ? "is-active" : ""}" type="button" data-filter="${value}">${label}</button>
            `).join("")}
        </div>
        <div id="exam-results">
            ${exams.length
                ? `<div class="exam-grid">${exams.map(examCard).join("")}</div>`
                : emptyState("Không tìm thấy bài kiểm tra", state.exams.length ? "Hãy thử từ khóa hoặc bộ lọc khác." : "Khi giáo viên công khai bài, bài kiểm tra sẽ xuất hiện tại đây.")}
        </div>
    `;
}

function scoreText(exam, result) {
    if (exam.type === "MULTIPLE_CHOICE") {
        const score = Number(result.score || 0);
        const total = Number(result.totalPoints || 0);
        return {
            main: total ? `${score.toLocaleString("vi-VN")} / ${total.toLocaleString("vi-VN")}` : score.toLocaleString("vi-VN"),
            note: total ? `${Math.round(score / total * 100)}% tổng điểm` : "Điểm trắc nghiệm"
        };
    }
    return result.score == null
        ? { main: "Chờ chấm", note: "Giáo viên chưa nhập điểm" }
        : { main: Number(result.score).toLocaleString("vi-VN"), note: "Điểm tự luận" };
}

function scoreCard(exam, result) {
    const score = scoreText(exam, result);
    const submittedAt = result.submittedAt || exam.deadline;
    return `
        <article class="score-card">
            <div>
                <div class="score-card__head">
                    <span class="type-label">${formatType(exam.type)}</span>
                    <span class="status status--${result.score == null ? "waiting" : "submitted"}">${result.score == null ? "Chờ chấm" : "Đã chấm"}</span>
                </div>
                <h3>${escapeHtml(exam.title)}</h3>
                <p>Nộp lúc ${formatDate(submittedAt)}${result.feedback ? ` · Phản hồi: ${escapeHtml(result.feedback)}` : ""}</p>
            </div>
            <div class="score-value">
                <strong>${score.main}</strong>
                <small>${score.note}</small>
            </div>
        </article>
    `;
}

function scoresScene() {
    if (state.loading) return loadingState();
    if (state.error) return errorState();
    const completed = state.exams
        .map(exam => [exam, state.results[String(exam.id)]])
        .filter(([, result]) => result)
        .sort(([, a], [, b]) => (parseDate(b.submittedAt) || 0) - (parseDate(a.submittedAt) || 0));

    return `
        <div class="page-toolbar">
            <div>
                <h2>Kết quả học tập</h2>
                <p>Điểm và phản hồi từ các bài bạn đã nộp</p>
            </div>
            <button class="button button--soft" type="button" data-action="reload">${icon("refresh")} Làm mới</button>
        </div>
        ${completed.length
            ? `<div class="score-list">${completed.map(([exam, result]) => scoreCard(exam, result)).join("")}</div>`
            : emptyState("Chưa có kết quả", "Điểm sẽ xuất hiện tại đây sau khi bạn nộp bài và hệ thống hoặc giáo viên chấm xong.")}
    `;
}

function render() {
    const route = getRoute();
    document.querySelector("#page-title").textContent = pageConfig[route].title;
    document.title = `${pageConfig[route].title} — EduPortal`;
    document.querySelectorAll("[data-route]").forEach(link => {
        link.classList.toggle("is-active", link.dataset.route === route);
    });

    scene.innerHTML = route === "home"
        ? homeScene()
        : route === "exams" ? examsScene() : scoresScene();
    bindSceneEvents();
    closeMobileMenu();
}

async function loadData() {
    state.loading = true;
    state.error = "";
    state.errorStatus = 0;
    render();

    if (!getAuthToken()) {
        state.loading = false;
        state.error = "Giao diện đang chờ token do trang đăng nhập lưu vào localStorage hoặc sessionStorage.";
        state.errorStatus = 401;
        render();
        return;
    }

    try {
        const exams = await getExams();
        state.exams = exams;
        state.results = await getMyResults(exams);
    } catch (error) {
        state.exams = [];
        state.results = {};
        state.error = error instanceof ApiError ? error.message : "Đã xảy ra lỗi khi tải dữ liệu.";
        state.errorStatus = error instanceof ApiError ? error.status : 0;
    } finally {
        state.loading = false;
        render();
    }
}

function renderExamResults() {
    const container = document.querySelector("#exam-results");
    if (!container) return;
    const exams = filteredExams();
    container.innerHTML = exams.length
        ? `<div class="exam-grid">${exams.map(examCard).join("")}</div>`
        : emptyState("Không tìm thấy bài kiểm tra", state.exams.length ? "Hãy thử từ khóa hoặc bộ lọc khác." : "Khi giáo viên công khai bài, bài kiểm tra sẽ xuất hiện tại đây.");
    bindDetailButtons();
}

function bindDetailButtons() {
    document.querySelectorAll('[data-action="details"]').forEach(button => {
        button.addEventListener("click", () => openExamDialog(button.dataset.examId));
    });
}

function bindSceneEvents() {
    document.querySelector('[data-action="reload"]')?.addEventListener("click", loadData);
    bindDetailButtons();

    document.querySelector("#exam-search")?.addEventListener("input", event => {
        state.query = event.target.value;
        renderExamResults();
    });

    document.querySelectorAll("[data-filter]").forEach(button => {
        button.addEventListener("click", () => {
            state.filter = button.dataset.filter;
            document.querySelectorAll("[data-filter]").forEach(item => {
                item.classList.toggle("is-active", item === button);
            });
            renderExamResults();
        });
    });
}

function openExamDialog(examId) {
    const exam = state.exams.find(item => String(item.id) === String(examId));
    if (!exam) return;
    const status = examState(exam);
    document.querySelector("#dialog-content").innerHTML = `
        <div class="dialog-body">
            <span class="status status--${status.className}">${status.label}</span>
            <h2 id="dialog-title">${escapeHtml(exam.title)}</h2>
            <p>${escapeHtml(exam.description || "Bài kiểm tra chưa có mô tả.")}</p>
            <div class="detail-list">
                <div class="detail-item"><small>Loại bài</small><strong>${formatType(exam.type)}</strong></div>
                <div class="detail-item"><small>Thời lượng</small><strong>${exam.durationMinutes ? `${exam.durationMinutes} phút` : "Nộp một file PDF"}</strong></div>
                <div class="detail-item"><small>Thời gian mở</small><strong>${formatDate(exam.startTime)}</strong></div>
                <div class="detail-item"><small>Hạn kết thúc</small><strong>${formatDate(exam.deadline)}</strong></div>
                <div class="detail-item"><small>Giáo viên</small><strong>${escapeHtml(exam.createdByName || "Chưa cập nhật")}</strong></div>
                <div class="detail-item"><small>Tệp đề</small><strong>${escapeHtml(exam.assignmentFile?.originalFileName || (exam.type === "ESSAY" ? "Chưa cập nhật" : "Không áp dụng"))}</strong></div>
            </div>
            <div class="dialog-note">Phạm vi giao diện hiện tại chỉ hiển thị thông tin bài kiểm tra. Màn hình làm và nộp bài sẽ được ghép ở phần chức năng riêng.</div>
        </div>
    `;
    dialog.showModal();
}

function showToast(message) {
    document.querySelector("#toast-message").textContent = message;
    const toast = document.querySelector("#toast");
    toast.classList.add("is-visible");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove("is-visible"), 2500);
}

function openMobileMenu() {
    sidebar.classList.add("is-open");
    backdrop.classList.add("is-visible");
    document.body.style.overflow = "hidden";
}

function closeMobileMenu() {
    sidebar.classList.remove("is-open");
    backdrop.classList.remove("is-visible");
    document.body.style.overflow = "";
}

document.querySelector("#mobile-menu").addEventListener("click", openMobileMenu);
backdrop.addEventListener("click", closeMobileMenu);
document.querySelector("#dialog-close").addEventListener("click", () => dialog.close());
dialog.addEventListener("click", event => {
    if (event.target === dialog) dialog.close();
});
document.querySelector("#logout-button").addEventListener("click", () => {
    clearAuthSession();
    applyIdentity();
    window.dispatchEvent(new CustomEvent("eduportal:logout"));
    showToast("Đã xóa phiên đăng nhập trên trình duyệt");
    loadData();
});
window.addEventListener("hashchange", render);
window.addEventListener("keydown", event => {
    if (event.key === "Escape") closeMobileMenu();
});
window.addEventListener("resize", () => {
    if (window.innerWidth > 900) closeMobileMenu();
});

document.querySelectorAll("[data-icon]").forEach(element => {
    element.innerHTML = icon(element.dataset.icon);
});

if (!window.location.hash || !pageConfig[window.location.hash.slice(1)]) {
    window.location.hash = "home";
}

applyIdentity();
render();
loadData();
