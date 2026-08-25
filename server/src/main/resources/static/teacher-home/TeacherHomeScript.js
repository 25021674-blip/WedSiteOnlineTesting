(() => {
    "use strict";

    const API_BASE_URL = (window.TEACHER_HOME_CONFIG?.apiBaseUrl || "/api").replace(/\/+$/, "");
    const SESSION_STORAGE_KEY = "onlineTestingAuthSession";
    const REQUEST_TIMEOUT_MILLIS = 12000;
    const DEMO_EXAM_SUMMARIES = [
        {
            examId: 1001,
            title: "Kiểm tra Toán giữa kỳ",
            completedStudentCount: 28
        },
        {
            examId: 1002,
            title: "Kiểm tra Ngữ văn chương 1",
            completedStudentCount: 24
        }
    ];

    const icons = {
        arrow: `<svg viewBox="0 0 24 24"><path d="M5 12h14M13 6l6 6-6 6"/></svg>`,
        back: `<svg viewBox="0 0 24 24"><path d="m15 18-6-6 6-6"/></svg>`,
        create: `<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/><rect x="3" y="3" width="18" height="18" rx="4"/></svg>`,
        exam: `<svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M8 13h8M8 17h6"/></svg>`,
        logout: `<svg viewBox="0 0 24 24"><path d="M10 17l5-5-5-5M15 12H3"/><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/></svg>`,
        refresh: `<svg viewBox="0 0 24 24"><path d="M20 6v5h-5M4 18v-5h5"/><path d="M6.1 9A7 7 0 0 1 18 6l2 5M3.9 13l2 5a7 7 0 0 0 11.9-3"/></svg>`,
        students: `<svg viewBox="0 0 24 24"><circle cx="9" cy="8" r="4"/><path d="M2 21a7 7 0 0 1 14 0M16 3.5a4 4 0 0 1 0 7.5M18 14a6 6 0 0 1 4 5.7"/></svg>`
    };

    const state = {
        session: readSession(),
        summaries: [...DEMO_EXAM_SUMMARIES],
        loading: false
    };

    const scene = document.querySelector("#scene");

    initialize();

    function initialize() {
        document.querySelectorAll("[data-icon]").forEach(element => {
            element.innerHTML = icon(element.dataset.icon);
        });
        applyIdentity();
        bindEvents();
        normalizeRoute();
        render();
        loadExamSummaries();
    }

    function icon(name) {
        return icons[name] || icons.exam;
    }

    function escapeHtml(value = "") {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function readSession() {
        try {
            const session = JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY) || "null");
            return session && typeof session === "object" ? session : null;
        } catch {
            return null;
        }
    }

    function currentUser() {
        return state.session?.user || {};
    }

    function initials(name) {
        const words = String(name || "Giáo viên").trim().split(/\s+/).filter(Boolean);
        if (!words.length) return "GV";
        return `${words[0][0] || ""}${words.length > 1 ? words.at(-1)[0] || "" : words[0][1] || ""}`.toUpperCase();
    }

    function applyIdentity() {
        const user = currentUser();
        document.querySelector("#teacher-name").textContent = user.fullName || "Giáo viên";
        document.querySelector("#teacher-email").textContent = user.email || "Không gian giáo viên";
        document.querySelector("#teacher-avatar").textContent = initials(user.fullName);
        document.querySelector("#logout-label").textContent = "Đăng xuất";
    }

    function rawRoute() {
        return window.location.hash.slice(1).split("?")[0];
    }

    function getRoute() {
        return ["home", "exams"].includes(rawRoute()) ? rawRoute() : "home";
    }

    function normalizeRoute() {
        if (!["home", "exams"].includes(rawRoute())) {
            window.location.hash = "home";
        }
    }

    function navigate(route) {
        if (!["home", "exams"].includes(route)) return;
        if (window.location.hash === `#${route}`) render();
        else window.location.hash = route;
    }

    async function fetchExamSummaries() {
        const controller = new AbortController();
        const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);
        const headers = new Headers({ Accept: "application/json" });
        if (state.session?.token) {
            headers.set("Authorization", `${state.session.tokenType || "Bearer"} ${state.session.token}`);
        }

        try {
            const response = await fetch(`${API_BASE_URL}/teacher/exams`, {
                method: "GET",
                headers,
                signal: controller.signal
            });
            const payload = await response.json().catch(() => null);
            if (!response.ok) {
                throw new Error(payload?.message || `API trả về mã ${response.status}`);
            }
            return Array.isArray(payload) ? payload : [];
        } finally {
            window.clearTimeout(timeoutId);
        }
    }

    async function loadExamSummaries() {
        state.loading = true;
        render();
        try {
            const summaries = await fetchExamSummaries();
            state.summaries = summaries.length ? summaries : [...DEMO_EXAM_SUMMARIES];
        } catch {
            state.summaries = [...DEMO_EXAM_SUMMARIES];
        } finally {
            state.loading = false;
            render();
        }
    }

    function homeScene() {
        const name = currentUser().fullName || "Giáo viên";
        return `
            <div class="home-scene">
                <header class="home-intro">
                    <h1>Xin chào, ${escapeHtml(name)}</h1>
                    <p>Chọn công việc bạn muốn thực hiện.</p>
                </header>

                <div class="home-actions">
                    <button class="home-action" type="button" data-route="exams">
                        <span class="action-icon">${icon("students")}</span>
                        <span class="action-copy">
                            <strong>Bài kiểm tra của học sinh</strong>
                            <small>Xem tên bài kiểm tra và số học sinh đã hoàn thành.</small>
                        </span>
                        <span class="action-arrow">${icon("arrow")}</span>
                    </button>

                    <button class="home-action home-action--create home-action--inactive" type="button" aria-disabled="true">
                        <span class="action-icon">${icon("create")}</span>
                        <span class="action-copy">
                            <strong>Tạo bài kiểm tra</strong>
                            <small>Chức năng tạo bài kiểm tra sẽ được bổ sung sau.</small>
                            <span class="action-meta">Tạm thời chưa xử lý</span>
                        </span>
                    </button>
                </div>
            </div>
        `;
    }

    function pageToolbar() {
        return `
            <header class="page-toolbar">
                <div class="page-heading">
                    <p class="eyebrow">Không gian giáo viên</p>
                    <h1>Bài kiểm tra của học sinh</h1>
                </div>
                <div class="toolbar-actions">
                    <button class="button button--secondary" type="button" data-route="home">${icon("back")} Trang chủ</button>
                    <button class="button button--secondary" type="button" data-action="reload">${icon("refresh")} Làm mới</button>
                </div>
            </header>
        `;
    }

    function examCard(exam) {
        return `
            <article class="teacher-exam-card">
                <span class="teacher-exam-card__icon">${icon("exam")}</span>
                <div class="teacher-exam-card__content">
                    <h2>${escapeHtml(exam.title || "Bài kiểm tra chưa có tên")}</h2>
                </div>
                <div class="teacher-exam-card__completed">
                    <strong>${Number(exam.completedStudentCount || 0)}</strong>
                    <span>Số học sinh đã hoàn thành</span>
                </div>
            </article>
        `;
    }

    function examsScene() {
        return `
            ${pageToolbar()}
            <section class="exam-scene-board">
                <div class="exam-scene-board__header">
                    <div>
                        <p class="eyebrow">Tổng quan bài kiểm tra</p>
                        <h2>${state.summaries.length} bài kiểm tra</h2>
                    </div>
                </div>
                <div class="teacher-exam-grid">
                    ${state.summaries.map(examCard).join("")}
                </div>
            </section>
        `;
    }

    function render() {
        const route = getRoute();
        document.title = route === "home"
            ? "Trang chủ giáo viên | Online Testing"
            : "Bài kiểm tra của học sinh | Online Testing";
        scene.innerHTML = route === "exams" ? examsScene() : homeScene();
    }

    function bindEvents() {
        window.addEventListener("hashchange", () => {
            normalizeRoute();
            render();
        });

        document.addEventListener("click", event => {
            const routeButton = event.target.closest("[data-route]");
            if (routeButton) {
                navigate(routeButton.dataset.route);
                return;
            }

            const actionButton = event.target.closest("[data-action]");
            if (actionButton?.dataset.action === "reload") {
                loadExamSummaries();
            }
        });
    }
})();
