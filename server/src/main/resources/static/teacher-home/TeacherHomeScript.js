(() => {
    "use strict";

    const API_BASE_URL = (window.TEACHER_HOME_CONFIG?.apiBaseUrl || "/api").replace(/\/+$/, "");
    const SESSION_STORAGE_KEY = "onlineTestingAuthSession";
    const REQUEST_TIMEOUT_MILLIS = 12000;
    const VALID_ROUTES = ["home", "exams", "create-exam", "create-questions"];
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
        publish: `<svg viewBox="0 0 24 24"><path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/></svg>`,
        refresh: `<svg viewBox="0 0 24 24"><path d="M20 6v5h-5M4 18v-5h5"/><path d="M6.1 9A7 7 0 0 1 18 6l2 5M3.9 13l2 5a7 7 0 0 0 11.9-3"/></svg>`,
        save: `<svg viewBox="0 0 24 24"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z"/><path d="M17 21v-8H7v8M7 3v5h8"/></svg>`,
        students: `<svg viewBox="0 0 24 24"><circle cx="9" cy="8" r="4"/><path d="M2 21a7 7 0 0 1 14 0M16 3.5a4 4 0 0 1 0 7.5M18 14a6 6 0 0 1 4 5.7"/></svg>`,
        trash: `<svg viewBox="0 0 24 24"><path d="M3 6h18M8 6V4h8v2M19 6l-1 15H6L5 6M10 11v6M14 11v6"/></svg>`,
        upload: `<svg viewBox="0 0 24 24"><path d="M12 16V3M7 8l5-5 5 5"/><path d="M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6"/></svg>`
    };

    const state = {
        session: readSession(),
        summaries: [...DEMO_EXAM_SUMMARIES],
        loading: false,
        createFlow: createEmptyFlow()
    };

    class ApiError extends Error {
        constructor(status, message, body) {
            super(message);
            this.name = "ApiError";
            this.status = status;
            this.body = body;
        }
    }

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

    function createEmptyFlow() {
        return {
            examId: null,
            creatingExam: false,
            savingQuestion: false,
            nextQuestionClientId: 1,
            examDraft: {
                title: "",
                durationMinutes: "",
                startTime: "",
                deadline: "",
                maxScore: ""
            }
        };
    }

    function resetCreateFlow() {
        state.createFlow = createEmptyFlow();
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
        return VALID_ROUTES.includes(rawRoute()) ? rawRoute() : "home";
    }

    function normalizeRoute() {
        if (!VALID_ROUTES.includes(rawRoute())) {
            window.location.hash = "home";
        }
    }

    function navigate(route) {
        if (!VALID_ROUTES.includes(route)) return;
        if (window.location.hash === `#${route}`) render();
        else window.location.hash = route;
    }

    function questionBuilderExamId() {
        const query = window.location.hash.split("?")[1] || "";
        const examId = new URLSearchParams(query).get("examId") || "";
        return /^[1-9]\d*$/.test(examId) ? examId : null;
    }

    function openQuestionBuilder(examId) {
        const normalizedId = String(examId || "");
        if (!/^[1-9]\d*$/.test(normalizedId)) {
            throw new Error("Server không trả về mã bài kiểm tra hợp lệ.");
        }
        window.location.replace(`#create-questions?examId=${encodeURIComponent(normalizedId)}`);
    }

    async function requestJson(path, options = {}) {
        const controller = new AbortController();
        const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);
        const headers = new Headers(options.headers || {});

        headers.set("Accept", "application/json");
        if (options.body) {
            headers.set("Content-Type", "application/json");
        }
        if (state.session?.token) {
            headers.set("Authorization", `${state.session.tokenType || "Bearer"} ${state.session.token}`);
        }

        try {
            const response = await fetch(`${API_BASE_URL}${path}`, {
                ...options,
                headers,
                signal: controller.signal
            });
            const responseText = await response.text();
            let payload = null;

            if (responseText) {
                try {
                    payload = JSON.parse(responseText);
                } catch {
                    payload = null;
                }
            }

            if (!response.ok) {
                const validationMessage = Object.values(payload?.errors || {})
                    .find(message => typeof message === "string" && message);
                const defaultMessage = response.status === 401
                    ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
                    : `Máy chủ trả về lỗi ${response.status}.`;
                throw new ApiError(
                    response.status,
                    validationMessage || payload?.message || defaultMessage,
                    payload
                );
            }

            return payload;
        } catch (error) {
            if (error.name === "AbortError") {
                throw new Error("Máy chủ phản hồi quá lâu. Vui lòng thử lại.");
            }
            if (error instanceof TypeError) {
                throw new Error("Không thể kết nối tới server. Hãy kiểm tra Spring Boot đang chạy.");
            }
            throw error;
        } finally {
            window.clearTimeout(timeoutId);
        }
    }

    async function fetchExamSummaries() {
        const payload = await requestJson("/teacher/exams");
        return Array.isArray(payload) ? payload : [];
    }

    async function loadExamSummaries() {
        state.loading = true;
        if (getRoute() === "exams") render();
        try {
            const summaries = await fetchExamSummaries();
            state.summaries = summaries.length ? summaries : [...DEMO_EXAM_SUMMARIES];
        } catch {
            state.summaries = [...DEMO_EXAM_SUMMARIES];
        } finally {
            state.loading = false;
            if (getRoute() === "exams") render();
        }
    }

    function requireAccessToken() {
        if (!state.session?.token) {
            throw new Error("Bạn cần đăng nhập bằng tài khoản giáo viên trước khi lưu dữ liệu.");
        }
    }

    function setFormStatus(form, message, type = "error") {
        const status = form.querySelector(".form-status");
        if (!status) return;
        status.textContent = message;
        status.className = `form-status form-status--${type}`;
        status.setAttribute("role", type === "error" ? "alert" : "status");
        status.hidden = false;
    }

    function clearFormStatus(form) {
        const status = form.querySelector(".form-status");
        if (!status) return;
        status.textContent = "";
        status.className = "form-status";
        status.setAttribute("role", "status");
        status.hidden = true;
    }

    function updateQuestionOptionVisibility(typeSelect) {
        const options = typeSelect?.closest(".question-editor-card")?.querySelector(".answer-options");
        if (!options) return;

        const isEssay = typeSelect.value === "ESSAY";
        options.hidden = isEssay;
        options.querySelectorAll("input").forEach(input => {
            input.disabled = isEssay;
        });
    }

    function setFormBusy(form, busy, pendingLabel, idleLabel) {
        form.setAttribute("aria-busy", String(busy));
        form.querySelectorAll("input, select, textarea, button").forEach(control => {
            control.disabled = busy;
        });
        form.closest(".create-exam-scene, .question-builder-scene")
            ?.querySelectorAll("button[data-route]")
            .forEach(button => {
                button.disabled = busy;
            });

        const buttonLabel = form.querySelector("[data-button-label]");
        if (buttonLabel) {
            buttonLabel.textContent = busy ? pendingLabel : idleLabel;
        }

        if (!busy && form.matches("[data-question-form]")) {
            updateQuestionOptionVisibility(form.elements.questionType);
        }
    }

    function setQuestionBuilderSaveControlsBusy(busy) {
        const canSave = Boolean(questionBuilderExamId());
        document.querySelectorAll(
            ".question-builder-scene [data-question-form] button[type='submit'], " +
            ".question-builder-scene [data-action='add-question']"
        ).forEach(button => {
            button.disabled = busy || !canSave;
        });
    }

    function readExamDraft(form) {
        const formData = new FormData(form);
        return {
            title: String(formData.get("title") || "").trim(),
            durationMinutes: String(formData.get("durationMinutes") || ""),
            startTime: String(formData.get("startTime") || ""),
            deadline: String(formData.get("deadline") || ""),
            maxScore: String(formData.get("maxScore") || "")
        };
    }

    function examPayload(draft, includeType) {
        const payload = {
            title: draft.title,
            description: null,
            startTime: draft.startTime,
            deadline: draft.deadline,
            durationMinutes: Number(draft.durationMinutes),
            maxScore: Number(draft.maxScore)
        };
        if (includeType) {
            payload.type = "MIXED";
        }
        return payload;
    }

    async function handleCreateExamSubmit(form) {
        const flow = state.createFlow;
        if (flow.creatingExam) return;
        clearFormStatus(form);

        const draft = readExamDraft(form);
        flow.examDraft = draft;

        if (draft.deadline <= draft.startTime) {
            setFormStatus(form, "Ngày đóng phải sau ngày mở.");
            form.elements.deadline.focus();
            return;
        }

        try {
            requireAccessToken();
        } catch (error) {
            setFormStatus(form, error.message);
            return;
        }

        const existingExamId = flow.examId;
        const isUpdate = Boolean(existingExamId);
        flow.creatingExam = true;
        setFormStatus(
            form,
            isUpdate ? "Đang cập nhật bài kiểm tra..." : "Đang tạo bài kiểm tra nháp...",
            "loading"
        );
        setFormBusy(form, true, "Đang xử lý...", "Next");

        try {
            const response = await requestJson(isUpdate ? `/exams/${existingExamId}` : "/exams", {
                method: isUpdate ? "PUT" : "POST",
                body: JSON.stringify(examPayload(draft, !isUpdate))
            });
            if (state.createFlow !== flow || getRoute() !== "create-exam") return;

            const examId = String(response?.id || existingExamId || "");
            if (!/^[1-9]\d*$/.test(examId)) {
                throw new Error("Server không trả về mã bài kiểm tra hợp lệ.");
            }

            flow.examId = examId;
            openQuestionBuilder(examId);
        } catch (error) {
            if (state.createFlow === flow && getRoute() === "create-exam") {
                setFormStatus(form, error.message || "Không thể tạo bài kiểm tra.");
            }
        } finally {
            flow.creatingExam = false;
            if (form.isConnected) {
                setFormBusy(form, false, "Đang xử lý...", "Next");
            }
        }
    }

    function readQuestionPayload(form) {
        const formData = new FormData(form);
        const questionType = String(formData.get("questionType") || "");
        const content = String(formData.get("content") || "").trim();
        const points = Number(formData.get("points"));
        let options = [];

        if (questionType === "MULTIPLE_CHOICE") {
            const correctIndex = String(
                form.querySelector("[data-correct-option]:checked")?.value || ""
            );
            const optionNames = ["optionA", "optionB", "optionC", "optionD"];
            if (!/^[0-3]$/.test(correctIndex)) {
                throw new Error("Vui lòng chọn một đáp án đúng.");
            }
            options = optionNames.map((name, index) => ({
                content: String(formData.get(name) || "").trim(),
                correct: String(index) === correctIndex
            }));
        }

        return { questionType, content, points, options };
    }

    async function handleQuestionSubmit(form) {
        const flow = state.createFlow;
        if (flow.savingQuestion) return;
        clearFormStatus(form);

        const examId = questionBuilderExamId();
        if (!examId || !/^[1-9]\d*$/.test(examId)) {
            setFormStatus(form, "Không tìm thấy bài kiểm tra. Hãy quay lại trang chủ và tạo bài kiểm tra mới.");
            return;
        }

        try {
            requireAccessToken();
        } catch (error) {
            setFormStatus(form, error.message);
            return;
        }

        const previousUnsavedForm = Array.from(
            document.querySelectorAll("[data-question-form]")
        ).find(candidate => {
            if (candidate === form) return false;
            const appearsBeforeCurrent = Boolean(
                candidate.compareDocumentPosition(form) & Node.DOCUMENT_POSITION_FOLLOWING
            );
            return !candidate.dataset.questionId && appearsBeforeCurrent;
        });
        if (previousUnsavedForm) {
            const previousNumber = previousUnsavedForm.dataset.questionNumber || "trước";
            setFormStatus(form, `Hãy lưu Câu ${previousNumber} trước để giữ đúng thứ tự câu hỏi.`);
            previousUnsavedForm.querySelector("[name='content']")?.focus();
            return;
        }

        let payload;
        try {
            payload = readQuestionPayload(form);
        } catch (error) {
            setFormStatus(form, error.message);
            return;
        }

        const questionId = form.dataset.questionId || null;
        if (questionId && !/^[1-9]\d*$/.test(questionId)) {
            setFormStatus(form, "Mã câu hỏi trên giao diện không hợp lệ. Hãy tải lại trang.");
            return;
        }
        const isUpdate = Boolean(questionId);
        const clientKey = form.dataset.clientKey;
        flow.savingQuestion = true;
        setFormStatus(
            form,
            isUpdate ? "Đang cập nhật câu hỏi..." : "Đang lưu câu hỏi vào database...",
            "loading"
        );
        setFormBusy(
            form,
            true,
            "Đang lưu...",
            isUpdate ? "Cập nhật câu hỏi" : "Lưu câu hỏi"
        );
        setQuestionBuilderSaveControlsBusy(true);

        try {
            const response = await requestJson(
                isUpdate ? `/questions/${questionId}` : `/exams/${examId}/questions`,
                {
                    method: isUpdate ? "PUT" : "POST",
                    body: JSON.stringify(payload)
                }
            );
            if (
                state.createFlow !== flow ||
                getRoute() !== "create-questions" ||
                questionBuilderExamId() !== examId ||
                !form.isConnected ||
                form.dataset.clientKey !== clientKey
            ) return;

            const savedQuestionId = String(response?.id || questionId || "");
            if (!/^[1-9]\d*$/.test(savedQuestionId)) {
                throw new Error("Server không trả về mã câu hỏi hợp lệ.");
            }
            if (String(response?.examId || "") !== examId) {
                throw new Error("Câu hỏi được trả về không thuộc bài kiểm tra hiện tại.");
            }
            if (isUpdate && savedQuestionId !== questionId) {
                throw new Error("Server trả về mã câu hỏi không khớp với câu đang cập nhật.");
            }

            form.dataset.questionId = savedQuestionId;
            setFormStatus(
                form,
                isUpdate ? "Đã cập nhật câu hỏi trong database." : "Đã lưu câu hỏi vào database.",
                "success"
            );
        } catch (error) {
            if (
                state.createFlow === flow &&
                getRoute() === "create-questions" &&
                questionBuilderExamId() === examId &&
                form.isConnected &&
                form.dataset.clientKey === clientKey
            ) {
                setFormStatus(form, error.message || "Không thể lưu câu hỏi.");
            }
        } finally {
            flow.savingQuestion = false;
            const idleLabel = form.dataset.questionId ? "Cập nhật câu hỏi" : "Lưu câu hỏi";
            if (form.isConnected) {
                setFormBusy(form, false, "Đang lưu...", idleLabel);
            }
            if (
                state.createFlow === flow &&
                getRoute() === "create-questions"
            ) {
                setQuestionBuilderSaveControlsBusy(false);
            }
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

                    <button class="home-action home-action--create" type="button" data-route="create-exam" data-new-exam>
                        <span class="action-icon">${icon("create")}</span>
                        <span class="action-copy">
                            <strong>Tạo bài kiểm tra</strong>
                            <small>Nhập thông tin để bắt đầu tạo một bài kiểm tra mới.</small>
                        </span>
                        <span class="action-arrow">${icon("arrow")}</span>
                    </button>
                </div>
            </div>
        `;
    }

    function createExamScene() {
        const draft = state.createFlow.examDraft;
        return `
            <div class="create-exam-scene">
                <div class="create-exam-scene__navigation">
                    <button class="button button--secondary" type="button" data-route="home">
                        ${icon("back")} Trở về trang chủ
                    </button>
                </div>

                <section class="create-card create-exam-card" aria-labelledby="create-exam-title">
                    <header class="create-exam-card__header">
                        <p class="eyebrow">Tạo bài kiểm tra</p>
                        <h1 id="create-exam-title">Thông tin bài kiểm tra</h1>
                    </header>

                    <form class="create-form create-form--single" id="create-exam-form">
                        <div class="field">
                            <label for="create-title">Tên bài kiểm tra</label>
                            <input id="create-title" name="title" type="text" maxlength="200" value="${escapeHtml(draft.title)}" required>
                        </div>

                        <div class="field">
                            <label for="create-duration">Thời gian làm bài (phút)</label>
                            <input id="create-duration" name="durationMinutes" type="number" min="1" step="1" value="${escapeHtml(draft.durationMinutes)}" required>
                        </div>

                        <div class="field">
                            <label for="create-start-time">Ngày mở</label>
                            <input id="create-start-time" name="startTime" type="datetime-local" value="${escapeHtml(draft.startTime)}" required>
                        </div>

                        <div class="field">
                            <label for="create-deadline">Ngày đóng</label>
                            <input id="create-deadline" name="deadline" type="datetime-local" value="${escapeHtml(draft.deadline)}" required>
                        </div>

                        <div class="field">
                            <label for="create-max-score">Điểm tối đa</label>
                            <input id="create-max-score" name="maxScore" type="number" min="0.01" max="99999999.99" step="0.01" value="${escapeHtml(draft.maxScore)}" required>
                        </div>

                        <p class="form-status" id="create-exam-status" role="status" hidden></p>

                        <div class="form-actions">
                            <button class="button" type="submit">
                                <span data-button-label>Next</span> ${icon("arrow")}
                            </button>
                        </div>
                    </form>
                </section>
            </div>
        `;
    }

    function questionEditorCard(questionNumber) {
        const examId = questionBuilderExamId();
        const clientId = state.createFlow.nextQuestionClientId++;
        const key = `question-${clientId}`;
        const hasExam = Boolean(examId);
        const canSave = hasExam && !state.createFlow.savingQuestion;
        return `
            <article class="question-editor-card" data-question-card data-question-number="${questionNumber}" aria-labelledby="${key}-title">
                <header class="question-editor-card__header">
                    <div>
                        <p class="eyebrow">Câu hỏi</p>
                        <h2 id="${key}-title">Câu ${questionNumber}</h2>
                    </div>
                    <button class="button button--danger" type="button" aria-label="Xóa câu ${questionNumber}">
                        ${icon("trash")} Xóa
                    </button>
                </header>

                <form class="question-editor-form" id="${key}-form" data-question-form data-client-key="${key}" data-question-number="${questionNumber}">
                    <div class="field">
                        <label for="${key}-type">Loại câu hỏi</label>
                        <select id="${key}-type" name="questionType" data-question-type>
                            <option value="MULTIPLE_CHOICE">Trắc nghiệm</option>
                            <option value="ESSAY">Tự luận</option>
                        </select>
                    </div>

                    <div class="field question-points-field">
                        <label for="${key}-points">Điểm câu hỏi</label>
                        <input id="${key}-points" name="points" type="number" min="0.01" max="999.99" step="0.01" placeholder="1.00" required>
                    </div>

                    <div class="field question-content-field">
                        <label for="${key}-content">Nội dung câu hỏi</label>
                        <textarea id="${key}-content" name="content" maxlength="2000" placeholder="Nhập nội dung câu hỏi..." required></textarea>
                    </div>

                    <fieldset class="answer-options">
                        <legend>Các phương án trả lời</legend>
                        <p>Chọn vòng tròn bên cạnh phương án đúng.</p>

                        <div class="answer-option">
                            <input type="radio" name="${key}-correct" value="0" data-correct-option aria-label="Chọn đáp án A là đáp án đúng" required>
                            <span class="answer-option__letter">A</span>
                            <input type="text" name="optionA" maxlength="1000" placeholder="Nhập phương án A" aria-label="Nội dung phương án A" required>
                        </div>

                        <div class="answer-option">
                            <input type="radio" name="${key}-correct" value="1" data-correct-option aria-label="Chọn đáp án B là đáp án đúng">
                            <span class="answer-option__letter">B</span>
                            <input type="text" name="optionB" maxlength="1000" placeholder="Nhập phương án B" aria-label="Nội dung phương án B" required>
                        </div>

                        <div class="answer-option">
                            <input type="radio" name="${key}-correct" value="2" data-correct-option aria-label="Chọn đáp án C là đáp án đúng">
                            <span class="answer-option__letter">C</span>
                            <input type="text" name="optionC" maxlength="1000" placeholder="Nhập phương án C" aria-label="Nội dung phương án C" required>
                        </div>

                        <div class="answer-option">
                            <input type="radio" name="${key}-correct" value="3" data-correct-option aria-label="Chọn đáp án D là đáp án đúng">
                            <span class="answer-option__letter">D</span>
                            <input type="text" name="optionD" maxlength="1000" placeholder="Nhập phương án D" aria-label="Nội dung phương án D" required>
                        </div>
                    </fieldset>

                    <p class="form-status${hasExam ? "" : " form-status--error"}" id="${key}-status" role="${hasExam ? "status" : "alert"}"${hasExam ? " hidden" : ""}>${hasExam ? "" : "Không tìm thấy bài kiểm tra. Hãy tạo bài kiểm tra bằng nút Next trước."}</p>

                    <div class="question-editor-card__actions">
                        <button class="button" type="submit"${canSave ? "" : " disabled"}>
                            ${icon("save")} <span data-button-label>Lưu câu hỏi</span>
                        </button>
                    </div>
                </form>
            </article>
        `;
    }

    function addQuestionEditor() {
        const list = document.querySelector("#question-editor-list");
        if (!list || state.createFlow.savingQuestion || !questionBuilderExamId()) return;

        const questionNumber = list.querySelectorAll("[data-question-card]").length + 1;
        list.insertAdjacentHTML("beforeend", questionEditorCard(questionNumber));

        const newCard = list.lastElementChild;
        newCard?.scrollIntoView({ behavior: "smooth", block: "start" });
        newCard?.querySelector("[name='content']")?.focus({ preventScroll: true });
    }

    function createQuestionsScene() {
        return `
            <div class="question-builder-scene">
                <section class="question-builder-board" aria-labelledby="question-builder-title">
                    <header class="question-builder-toolbar">
                        <button class="button button--secondary" type="button" data-route="home">
                            ${icon("back")} Quay lại Trang chủ
                        </button>
                        <button class="button button--secondary" type="button">
                            ${icon("upload")} Upload file
                        </button>
                    </header>

                    <div class="question-builder-heading">
                        <p class="eyebrow">Tạo bài kiểm tra</p>
                        <h1 id="question-builder-title">Soạn câu hỏi</h1>
                    </div>

                    <div class="question-editor-list" id="question-editor-list">
                        ${questionEditorCard(1)}
                    </div>

                    <button class="add-question-button" type="button" data-action="add-question" aria-controls="question-editor-list"${questionBuilderExamId() && !state.createFlow.savingQuestion ? "" : " disabled"}>
                        ${icon("create")}
                        <span>Thêm câu hỏi</span>
                    </button>

                    <footer class="question-builder-footer">
                        <button class="button" type="button">
                            ${icon("publish")} Xuất bản
                        </button>
                    </footer>
                </section>
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
        if (route === "exams") {
            document.title = "Bài kiểm tra của học sinh | Online Testing";
            scene.innerHTML = examsScene();
            return;
        }

        if (route === "create-exam") {
            document.title = "Tạo bài kiểm tra | Online Testing";
            scene.innerHTML = createExamScene();
            return;
        }

        if (route === "create-questions") {
            document.title = "Soạn câu hỏi | Online Testing";
            scene.innerHTML = createQuestionsScene();
            return;
        }

        document.title = "Trang chủ giáo viên | Online Testing";
        scene.innerHTML = homeScene();
    }

    function bindEvents() {
        window.addEventListener("hashchange", () => {
            normalizeRoute();
            render();
        });

        document.addEventListener("click", event => {
            const routeButton = event.target.closest("[data-route]");
            if (routeButton) {
                if (routeButton.hasAttribute("data-new-exam")) {
                    resetCreateFlow();
                }
                navigate(routeButton.dataset.route);
                return;
            }

            const publishButton = event.target.closest(".question-builder-footer .button");
            if (publishButton) {
                const configurationPage = new URL("./ExamConfigurationDemo.html", window.location.href);
                const examId = questionBuilderExamId();
                if (examId) configurationPage.searchParams.set("examId", examId);
                window.location.assign(configurationPage.href);
                return;
            }

            const actionButton = event.target.closest("[data-action]");
            if (actionButton?.dataset.action === "reload") {
                loadExamSummaries();
                return;
            }
            if (actionButton?.dataset.action === "add-question") {
                addQuestionEditor();
            }
        });

        document.addEventListener("submit", event => {
            const form = event.target;
            if (form.id === "create-exam-form") {
                event.preventDefault();
                handleCreateExamSubmit(form);
                return;
            }
            if (form.matches("[data-question-form]")) {
                event.preventDefault();
                handleQuestionSubmit(form);
            }
        });

        document.addEventListener("input", event => {
            const form = event.target.closest("#create-exam-form, [data-question-form]");
            if (form?.matches("[data-question-form]") && !questionBuilderExamId()) return;
            if (form) clearFormStatus(form);
        });

        document.addEventListener("change", event => {
            const typeSelect = event.target.closest("[data-question-type]");
            if (!typeSelect) return;
            clearFormStatus(typeSelect.form);
            updateQuestionOptionVisibility(typeSelect);
        });
    }
})();
