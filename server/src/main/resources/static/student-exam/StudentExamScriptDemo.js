(() => {
    "use strict";

    const SESSION_STORAGE_KEY = "onlineTestingAuthSession";
    const COMPLETION_STORAGE_KEY = "onlineTestingExamCompletion";
    const DEFAULT_HOME_URL = "/student-exam/StudentExamDemo.html?demo=true";
    const AUTOSAVE_DELAY_MILLIS = 750;
    const REQUEST_TIMEOUT_MILLIS = 12000;
    const MAX_ESSAY_LENGTH = 10000;

    const elements = {
        loadingState: document.getElementById("loading-state"),
        errorState: document.getElementById("error-state"),
        errorMessage: document.getElementById("error-message"),
        retryButton: document.getElementById("retry-button"),
        examApp: document.getElementById("exam-app"),
        previewBadge: document.getElementById("preview-badge"),
        examType: document.getElementById("exam-type"),
        examTitle: document.getElementById("exam-title"),
        studentName: document.getElementById("student-name"),
        studentAvatar: document.getElementById("student-avatar"),
        saveStatus: document.getElementById("save-status"),
        saveStatusText: document.getElementById("save-status-text"),
        questionPanel: document.getElementById("question-panel"),
        questionPosition: document.getElementById("question-position"),
        questionType: document.getElementById("question-type"),
        questionProgressText: document.getElementById("question-progress-text"),
        questionTitle: document.getElementById("question-title"),
        reviewButton: document.getElementById("review-button"),
        reviewIcon: document.querySelector(".review-button__icon"),
        choiceFieldset: document.getElementById("choice-fieldset"),
        choiceOptions: document.getElementById("choice-options"),
        essayField: document.getElementById("essay-field"),
        essayAnswer: document.getElementById("essay-answer"),
        essayCount: document.getElementById("essay-count"),
        answerForm: document.getElementById("answer-form"),
        previousButton: document.getElementById("previous-button"),
        nextButton: document.getElementById("next-button"),
        answeredRatio: document.getElementById("answered-ratio"),
        answeredSummary: document.getElementById("answered-summary"),
        progressBar: document.getElementById("progress-bar"),
        progressBarValue: document.getElementById("progress-bar-value"),
        questionNavigation: document.getElementById("question-navigation"),
        timerCard: document.getElementById("timer-card"),
        timerValue: document.getElementById("timer-value"),
        mobileTimerValue: document.getElementById("mobile-timer-value"),
        deadlineText: document.getElementById("deadline-text"),
        screenExitCount: document.getElementById("screen-exit-count"),
        submitButton: document.getElementById("submit-button"),
        toast: document.getElementById("toast"),
        toastIcon: document.getElementById("toast-icon"),
        toastMessage: document.getElementById("toast-message"),
        submitDialog: document.getElementById("submit-dialog"),
        submitDialogSummary: document.getElementById("submit-dialog-summary"),
        unansweredWarning: document.getElementById("unanswered-warning"),
        unansweredWarningText: document.getElementById("unanswered-warning-text"),
        confirmSubmitButton: document.getElementById("confirm-submit-button")
    };

    const state = {
        isDemo: true,
        isSubmitting: false,
        autoSubmitStarted: false,
        session: null,
        examId: null,
        exam: null,
        attemptId: null,
        status: "IN_PROGRESS",
        questions: [],
        currentIndex: 0,
        markedQuestionIds: new Set(),
        screenExitCount: 0,
        serverOffsetMillis: 0,
        deadlineMillis: 0,
        timerId: null,
        toastTimerId: null,
        visibilityExitPending: false,
        apiBaseUrl: "",
        homeUrl: DEFAULT_HOME_URL
    };

    class ApiError extends Error {
        constructor(status, message, body) {
            super(message);
            this.name = "ApiError";
            this.status = status;
            this.body = body;
        }
    }

    initializePage();

    function initializePage() {
        elements.answerForm.addEventListener("submit", (event) => event.preventDefault());
        elements.choiceOptions.addEventListener("change", handleChoiceChange);
        elements.essayAnswer.addEventListener("input", handleEssayInput);
        elements.previousButton.addEventListener("click", () => navigateBy(-1));
        elements.nextButton.addEventListener("click", () => navigateBy(1));
        elements.questionNavigation.addEventListener("click", handleQuestionNavigation);
        elements.reviewButton.addEventListener("click", toggleReviewMark);
        elements.submitButton.addEventListener("click", openSubmitDialog);
        elements.confirmSubmitButton.addEventListener("click", () => submitExam(false));
        elements.retryButton.addEventListener("click", loadExam);
        document.addEventListener("keydown", handleKeyboardNavigation);
        document.addEventListener("visibilitychange", handleVisibilityChange);
        document.querySelector(".brand")?.addEventListener("click", (event) => event.preventDefault());

        loadExam();
    }

    async function loadExam() {
        resetRuntimeState();
        showPageState("loading");

        const searchParams = new URLSearchParams(window.location.search);
        const examId = Number(searchParams.get("examId"));
        const forceDemo = searchParams.get("demo") === "true";

        state.examId = Number.isInteger(examId) && examId > 0 ? examId : null;
        state.isDemo = forceDemo || state.examId === null;
        state.session = readStoredSession();
        state.apiBaseUrl = resolveApiBaseUrl(searchParams);
        state.homeUrl = resolveHomeUrl(searchParams.get("homeUrl"));

        try {
            let examScreen;

            if (state.isDemo) {
                await delay(260);
                examScreen = createDemoExamScreen();
            } else {
                validateStudentSession(state.session);
                examScreen = await requestJson(
                    `/api/student/exams/${state.examId}/attempts/start`,
                    { method: "POST" }
                );
            }

            applyExamScreen(examScreen);
            showPageState("exam");
        } catch (error) {
            showLoadError(getErrorMessage(error));
        }
    }

    function resetRuntimeState() {
        if (state.timerId) {
            window.clearInterval(state.timerId);
        }

        state.questions.forEach((question) => {
            if (question.saveTimerId) {
                window.clearTimeout(question.saveTimerId);
            }
        });

        state.isSubmitting = false;
        state.autoSubmitStarted = false;
        state.exam = null;
        state.questions = [];
        state.currentIndex = 0;
        state.markedQuestionIds = new Set();
        state.timerId = null;
        state.visibilityExitPending = false;
    }

    function applyExamScreen(examScreen) {
        if (!examScreen?.exam || !Array.isArray(examScreen.questions) || examScreen.questions.length === 0) {
            throw new Error("Bài kiểm tra chưa có câu hỏi để hiển thị.");
        }

        const serverTimeMillis = Date.parse(examScreen.serverTime);
        const parsedDeadlineMillis = Date.parse(examScreen.deadlineAt);
        const fallbackRemainingSeconds = Number(examScreen.remainingSeconds) || 0;

        state.attemptId = examScreen.attemptId;
        state.examId = examScreen.exam.id ?? state.examId;
        state.exam = examScreen.exam;
        state.status = examScreen.status || "IN_PROGRESS";
        state.screenExitCount = Math.max(0, Number(examScreen.screenExitCount) || 0);
        state.serverOffsetMillis = Number.isFinite(serverTimeMillis)
            ? serverTimeMillis - Date.now()
            : 0;
        state.deadlineMillis = Number.isFinite(parsedDeadlineMillis)
            ? parsedDeadlineMillis
            : Date.now() + state.serverOffsetMillis + fallbackRemainingSeconds * 1000;

        state.questions = examScreen.questions
            .map(normalizeQuestion)
            .sort((first, second) => first.number - second.number);

        const firstUnansweredIndex = state.questions.findIndex((question) => !isQuestionAnswered(question));
        state.currentIndex = firstUnansweredIndex >= 0 ? firstUnansweredIndex : 0;

        renderExamHeader(examScreen.exam);
        renderCurrentQuestion(false);
        renderProgress();
        renderScreenExitCount();
        updateSaveStatus();
        updateTimer();

        state.timerId = window.setInterval(updateTimer, 500);
        document.title = `${examScreen.exam.title} | Online Testing`;
    }

    function normalizeQuestion(question, index) {
        const answer = question.answer || {};
        const normalizedAnswer = {
            selectedOptionId: answer.selectedOptionId ?? null,
            essayContent: answer.essayContent ?? "",
            clientRevision: Math.max(0, Number(answer.clientRevision) || 0)
        };

        return {
            id: question.id,
            number: Number(question.number) || index + 1,
            type: question.type,
            content: String(question.content || ""),
            options: Array.isArray(question.options) ? question.options : [],
            answer: normalizedAnswer,
            revision: normalizedAnswer.clientRevision,
            lastSavedSnapshot: serializeAnswer(normalizedAnswer),
            dirty: false,
            saveError: null,
            saveTimerId: null,
            savingPromise: null
        };
    }

    function renderExamHeader(exam) {
        elements.examTitle.textContent = exam.title || "Bài kiểm tra trực tuyến";
        elements.examType.textContent = getExamTypeLabel(exam.type);
        elements.previewBadge.hidden = !state.isDemo;

        const fullName = state.isDemo
            ? "Nguyễn Minh Anh"
            : state.session?.user?.fullName || "Học sinh";

        elements.studentName.textContent = fullName;
        elements.studentAvatar.textContent = getInitials(fullName);
    }

    function renderCurrentQuestion(shouldFocus) {
        const question = getCurrentQuestion();
        if (!question) {
            return;
        }

        const questionCount = state.questions.length;
        const isMultipleChoice = question.type === "MULTIPLE_CHOICE";
        const isActive = state.status === "IN_PROGRESS" && !state.isSubmitting;
        const isMarked = state.markedQuestionIds.has(question.id);

        elements.questionPosition.textContent = `Câu ${question.number} / ${questionCount}`;
        elements.questionProgressText.textContent = `Câu hỏi ${state.currentIndex + 1} trong ${questionCount}`;
        elements.questionType.textContent = isMultipleChoice ? "Trắc nghiệm" : "Tự luận";
        elements.questionTitle.textContent = question.content;
        elements.reviewButton.setAttribute("aria-pressed", String(isMarked));
        elements.reviewIcon.textContent = isMarked ? "★" : "☆";
        elements.reviewButton.disabled = !isActive;
        elements.choiceFieldset.hidden = !isMultipleChoice;
        elements.essayField.hidden = isMultipleChoice;
        elements.previousButton.disabled = state.currentIndex === 0;
        elements.nextButton.disabled = state.currentIndex === questionCount - 1;

        if (isMultipleChoice) {
            renderChoiceOptions(question, isActive);
        } else {
            elements.essayAnswer.value = question.answer.essayContent || "";
            elements.essayAnswer.disabled = !isActive;
            renderEssayCount();
        }

        renderProgress();

        if (shouldFocus) {
            elements.questionPanel.focus({ preventScroll: true });
            elements.questionPanel.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    }

    function renderChoiceOptions(question, isActive) {
        const fragment = document.createDocumentFragment();
        elements.choiceOptions.replaceChildren();

        question.options.forEach((option, index) => {
            const label = document.createElement("label");
            const letter = document.createElement("span");
            const content = document.createElement("span");
            const input = document.createElement("input");
            const optionId = String(option.id);

            label.className = "choice-option";
            letter.className = "choice-option__letter";
            content.className = "choice-option__content";
            letter.textContent = getOptionLetter(index);
            content.textContent = option.content || "";

            input.type = "radio";
            input.name = `question-${question.id}`;
            input.value = optionId;
            input.dataset.optionId = optionId;
            input.checked = String(question.answer.selectedOptionId) === optionId;
            input.disabled = !isActive;
            input.setAttribute("aria-label", `Phương án ${letter.textContent}: ${option.content || ""}`);

            label.append(letter, content, input);
            fragment.append(label);
        });

        elements.choiceOptions.append(fragment);
    }

    function handleChoiceChange(event) {
        const input = event.target.closest("input[type='radio'][data-option-id]");
        const question = getCurrentQuestion();
        if (!input || !question || question.type !== "MULTIPLE_CHOICE") {
            return;
        }

        question.answer.selectedOptionId = parseIdentifier(input.dataset.optionId);
        question.answer.essayContent = "";
        markQuestionDirty(question, 120);
        renderProgress();
    }

    function handleEssayInput() {
        const question = getCurrentQuestion();
        if (!question || question.type !== "ESSAY") {
            return;
        }

        const wasAnswered = isQuestionAnswered(question);
        question.answer.selectedOptionId = null;
        question.answer.essayContent = elements.essayAnswer.value;
        renderEssayCount();
        markQuestionDirty(question, AUTOSAVE_DELAY_MILLIS);

        if (wasAnswered !== isQuestionAnswered(question)) {
            renderProgress();
        }
    }

    function renderEssayCount() {
        const count = elements.essayAnswer.value.length;
        elements.essayCount.textContent = `${formatNumber(count)} / ${formatNumber(MAX_ESSAY_LENGTH)} ký tự`;
    }

    function markQuestionDirty(question, delayMillis) {
        if (state.status !== "IN_PROGRESS" || state.isSubmitting) {
            return;
        }

        const currentSnapshot = serializeAnswer(question.answer);
        question.dirty = currentSnapshot !== question.lastSavedSnapshot;
        question.saveError = null;

        if (question.saveTimerId) {
            window.clearTimeout(question.saveTimerId);
            question.saveTimerId = null;
        }

        if (question.dirty) {
            question.saveTimerId = window.setTimeout(() => {
                question.saveTimerId = null;
                void persistQuestion(question).catch(() => undefined);
            }, delayMillis);
        }

        updateSaveStatus();
    }

    async function persistQuestion(question) {
        if (question.savingPromise) {
            return question.savingPromise;
        }

        if (!question.dirty || state.status !== "IN_PROGRESS") {
            return true;
        }

        if (question.saveTimerId) {
            window.clearTimeout(question.saveTimerId);
            question.saveTimerId = null;
        }

        const snapshot = serializeAnswer(question.answer);
        const snapshotAnswer = JSON.parse(snapshot);
        const nextRevision = question.revision + 1;

        question.dirty = false;
        question.saveError = null;
        updateSaveStatus();

        question.savingPromise = (async () => {
            try {
                let response;

                if (state.isDemo) {
                    await delay(260);
                    response = { clientRevision: nextRevision };
                } else {
                    response = await requestJson(
                        `/api/student/exam-attempts/${state.attemptId}/questions/${question.id}/answer`,
                        {
                            method: "PUT",
                            body: JSON.stringify({
                                selectedOptionId: snapshotAnswer.selectedOptionId,
                                essayContent: question.type === "ESSAY" ? snapshotAnswer.essayContent : null,
                                clientRevision: nextRevision
                            })
                        }
                    );
                }

                question.revision = Math.max(nextRevision, Number(response?.clientRevision) || 0);
                question.answer.clientRevision = question.revision;
                question.lastSavedSnapshot = snapshot;
                question.dirty = serializeAnswer(question.answer) !== question.lastSavedSnapshot;
                question.saveError = null;
                return true;
            } catch (error) {
                question.dirty = true;
                question.saveError = error;
                throw error;
            } finally {
                question.savingPromise = null;
                updateSaveStatus();

                if (question.dirty && !question.saveError && !state.isSubmitting) {
                    markQuestionDirty(question, 80);
                }
            }
        })();

        return question.savingPromise;
    }

    async function flushPendingAnswers() {
        state.questions.forEach((question) => {
            if (question.saveTimerId) {
                window.clearTimeout(question.saveTimerId);
                question.saveTimerId = null;
            }
        });

        for (let pass = 0; pass < 4; pass += 1) {
            const pendingQuestions = state.questions.filter(
                (question) => question.dirty || question.savingPromise
            );

            if (pendingQuestions.length === 0) {
                return;
            }

            await Promise.all(pendingQuestions.map((question) => persistQuestion(question)));
        }

        if (state.questions.some((question) => question.dirty || question.savingPromise)) {
            throw new Error("Một số câu trả lời vẫn chưa được lưu.");
        }
    }

    function updateSaveStatus() {
        const hasError = state.questions.some((question) => question.saveError);
        const isSaving = state.questions.some((question) => question.savingPromise);
        const hasPending = state.questions.some((question) => question.dirty || question.saveTimerId);

        elements.saveStatus.className = "save-status";

        if (hasError) {
            elements.saveStatus.classList.add("is-error");
            elements.saveStatusText.textContent = "Lưu thất bại";
            return;
        }

        if (isSaving) {
            elements.saveStatus.classList.add("is-saving");
            elements.saveStatusText.textContent = "Đang lưu...";
            return;
        }

        if (hasPending) {
            elements.saveStatus.classList.add("is-pending");
            elements.saveStatusText.textContent = "Chờ tự động lưu";
            return;
        }

        elements.saveStatusText.textContent = state.isDemo ? "Đã lưu bản nháp" : "Đã lưu";
    }

    function navigateBy(offset) {
        navigateToQuestion(state.currentIndex + offset);
    }

    function navigateToQuestion(index) {
        if (index < 0 || index >= state.questions.length || index === state.currentIndex) {
            return;
        }

        state.currentIndex = index;
        renderCurrentQuestion(true);
    }

    function handleQuestionNavigation(event) {
        const button = event.target.closest("button[data-question-index]");
        if (!button) {
            return;
        }

        navigateToQuestion(Number(button.dataset.questionIndex));
    }

    function handleKeyboardNavigation(event) {
        const target = event.target;
        const isTyping = target instanceof HTMLInputElement
            || target instanceof HTMLTextAreaElement
            || target instanceof HTMLSelectElement
            || target?.isContentEditable;
        const hasOpenModal = elements.submitDialog.open;

        if (isTyping || hasOpenModal || event.altKey || event.ctrlKey || event.metaKey) {
            return;
        }

        if (event.key === "ArrowLeft") {
            event.preventDefault();
            navigateBy(-1);
        }

        if (event.key === "ArrowRight") {
            event.preventDefault();
            navigateBy(1);
        }
    }

    function toggleReviewMark() {
        const question = getCurrentQuestion();
        if (!question) {
            return;
        }

        if (state.markedQuestionIds.has(question.id)) {
            state.markedQuestionIds.delete(question.id);
            showToast("Đã bỏ đánh dấu câu hỏi.", "success");
        } else {
            state.markedQuestionIds.add(question.id);
            showToast("Đã đánh dấu câu này để xem lại.", "warning");
        }

        renderCurrentQuestion(false);
    }

    function renderProgress() {
        const answeredQuestions = state.questions.filter(isQuestionAnswered);
        const answeredCount = answeredQuestions.length;
        const totalQuestions = state.questions.length;
        const percentage = totalQuestions === 0 ? 0 : Math.round((answeredCount / totalQuestions) * 100);

        elements.answeredRatio.textContent = `${answeredCount}/${totalQuestions}`;
        elements.answeredSummary.textContent = getAnsweredSummary(answeredCount, totalQuestions);
        elements.progressBar.setAttribute("aria-valuemax", String(totalQuestions));
        elements.progressBar.setAttribute("aria-valuenow", String(answeredCount));
        elements.progressBarValue.style.width = `${percentage}%`;

        const fragment = document.createDocumentFragment();
        state.questions.forEach((question, index) => {
            const button = document.createElement("button");
            const isCurrent = index === state.currentIndex;
            const isAnswered = isQuestionAnswered(question);
            const isMarked = state.markedQuestionIds.has(question.id);
            const statusParts = [
                `Câu ${question.number}`,
                isAnswered ? "đã trả lời" : "chưa trả lời"
            ];

            if (isMarked) {
                statusParts.push("đã đánh dấu xem lại");
            }

            button.type = "button";
            button.textContent = question.number;
            button.dataset.questionIndex = String(index);
            button.classList.toggle("is-answered", isAnswered);
            button.classList.toggle("is-current", isCurrent);
            button.classList.toggle("is-marked", isMarked);
            button.setAttribute("aria-label", statusParts.join(", "));

            if (isCurrent) {
                button.setAttribute("aria-current", "step");
            }

            fragment.append(button);
        });

        elements.questionNavigation.replaceChildren(fragment);
    }

    function updateTimer() {
        const nowOnServer = Date.now() + state.serverOffsetMillis;
        const remainingMillis = Math.max(0, state.deadlineMillis - nowOnServer);
        const remainingSeconds = Math.ceil(remainingMillis / 1000);
        const formattedTime = formatDuration(remainingSeconds);

        elements.timerValue.textContent = formattedTime;
        elements.mobileTimerValue.textContent = formattedTime;
        elements.timerCard.classList.toggle("is-warning", remainingSeconds <= 600 && remainingSeconds > 60);
        elements.timerCard.classList.toggle("is-danger", remainingSeconds <= 60);

        if (state.deadlineMillis > 0) {
            elements.deadlineText.textContent = `Tự động nộp lúc ${formatClockTime(state.deadlineMillis)}`;
        }

        if (remainingSeconds <= 0 && state.status === "IN_PROGRESS" && !state.autoSubmitStarted) {
            state.autoSubmitStarted = true;
            void submitExam(true);
        }
    }

    function handleVisibilityChange() {
        if (state.status !== "IN_PROGRESS" || elements.examApp.hidden) {
            return;
        }

        if (document.hidden) {
            state.visibilityExitPending = true;
            state.screenExitCount += 1;
            renderScreenExitCount();
            return;
        }

        if (state.visibilityExitPending) {
            state.visibilityExitPending = false;
            showToast(`Hệ thống đã ghi nhận lần rời màn hình thứ ${state.screenExitCount}.`, "warning");
        }
    }

    function renderScreenExitCount() {
        elements.screenExitCount.textContent = String(state.screenExitCount);
    }

    function openSubmitDialog() {
        if (state.status !== "IN_PROGRESS" || state.isSubmitting) {
            return;
        }

        const unansweredQuestions = state.questions.filter((question) => !isQuestionAnswered(question));
        const answeredCount = state.questions.length - unansweredQuestions.length;
        const unansweredNumbers = unansweredQuestions.map((question) => question.number);

        elements.submitDialogSummary.textContent = `Bạn đã trả lời ${answeredCount}/${state.questions.length} câu.`;
        elements.unansweredWarning.hidden = unansweredQuestions.length === 0;

        if (unansweredQuestions.length > 0) {
            const visibleNumbers = unansweredNumbers.slice(0, 8).join(", ");
            const remainingCount = unansweredNumbers.length - 8;
            const suffix = remainingCount > 0 ? ` và ${remainingCount} câu khác` : "";
            elements.unansweredWarningText.textContent = `Còn câu ${visibleNumbers}${suffix} chưa được trả lời.`;
        }

        openDialog(elements.submitDialog);
    }

    async function submitExam(isAutomatic) {
        if (state.isSubmitting || state.status !== "IN_PROGRESS") {
            return;
        }

        state.isSubmitting = true;
        elements.submitButton.disabled = true;
        elements.confirmSubmitButton.disabled = true;
        elements.confirmSubmitButton.textContent = isAutomatic ? "Đang tự động nộp..." : "Đang nộp bài...";
        renderCurrentQuestion(false);

        try {
            try {
                await flushPendingAnswers();
            } catch (saveError) {
                if (!isAutomatic) {
                    throw saveError;
                }
            }

            let response;
            if (state.isDemo) {
                await delay(650);
                response = {
                    attemptId: state.attemptId,
                    status: isAutomatic ? "AUTO_SUBMITTED" : "SUBMITTED",
                    submittedAt: new Date().toISOString(),
                    serverTime: new Date().toISOString()
                };
            } else {
                response = await requestJson(
                    `/api/student/exams/${state.examId}/attempts/${state.attemptId}/submit`,
                    { method: "POST" }
                );
            }

            state.status = response.status || (isAutomatic ? "AUTO_SUBMITTED" : "SUBMITTED");
            if (state.timerId) {
                window.clearInterval(state.timerId);
                state.timerId = null;
            }

            if (elements.submitDialog.open) {
                elements.submitDialog.close();
            }

            renderCurrentQuestion(false);
            navigateToResultPage(response, isAutomatic || state.status === "AUTO_SUBMITTED");
        } catch (error) {
            state.isSubmitting = false;
            state.autoSubmitStarted = false;
            elements.submitButton.disabled = false;
            elements.confirmSubmitButton.disabled = false;
            elements.confirmSubmitButton.textContent = "Nộp bài ngay";
            renderCurrentQuestion(false);
            showToast(`Chưa thể nộp bài: ${getErrorMessage(error)}`, "error", 6000);
        }
    }

    function navigateToResultPage(response, wasAutomatic) {
        const answeredCount = state.questions.filter(isQuestionAnswered).length;
        const submittedAt = response.submittedAt
            || response.serverTime
            || new Date().toISOString();
        const completion = {
            attemptId: response.attemptId ?? state.attemptId,
            status: response.status || state.status,
            examId: state.examId,
            examTitle: state.exam?.title || "Bài kiểm tra trực tuyến",
            examType: state.exam?.type || "MIXED",
            answeredCount,
            totalQuestions: state.questions.length,
            submittedAt,
            score: response.score ?? null,
            maxScore: response.maxScore ?? state.exam?.maxScore ?? null,
            wasAutomatic,
            isDemo: state.isDemo,
            homeUrl: state.homeUrl,
            storedAt: new Date().toISOString()
        };

        try {
            sessionStorage.setItem(COMPLETION_STORAGE_KEY, JSON.stringify(completion));
        } catch {
            // Query string bên dưới vẫn giúp trang kết quả hoạt động khi storage bị chặn.
        }

        const searchParams = createResultSearchParams(completion);
        window.location.replace(`./StudentExamResultDemo.html?${searchParams.toString()}`);
    }

    function createResultSearchParams(completion) {
        const searchParams = new URLSearchParams({
            attemptId: String(completion.attemptId ?? ""),
            status: completion.status,
            examTitle: completion.examTitle,
            examType: completion.examType,
            answeredCount: String(completion.answeredCount),
            totalQuestions: String(completion.totalQuestions),
            submittedAt: completion.submittedAt,
            automatic: String(completion.wasAutomatic),
            demo: String(completion.isDemo),
            homeUrl: completion.homeUrl
        });

        if (completion.score !== null) {
            searchParams.set("score", String(completion.score));
        }
        if (completion.maxScore !== null) {
            searchParams.set("maxScore", String(completion.maxScore));
        }

        return searchParams;
    }

    function showPageState(stateName) {
        elements.loadingState.hidden = stateName !== "loading";
        elements.errorState.hidden = stateName !== "error";
        elements.examApp.hidden = stateName !== "exam";
    }

    function showLoadError(message) {
        elements.errorMessage.textContent = message;
        showPageState("error");
    }

    function showToast(message, type = "success", durationMillis = 3600) {
        if (state.toastTimerId) {
            window.clearTimeout(state.toastTimerId);
        }

        elements.toast.className = "toast";
        elements.toast.classList.toggle("is-error", type === "error");
        elements.toast.classList.toggle("is-warning", type === "warning");
        elements.toastIcon.textContent = type === "error" || type === "warning" ? "!" : "✓";
        elements.toastMessage.textContent = message;
        elements.toast.hidden = false;

        state.toastTimerId = window.setTimeout(() => {
            elements.toast.hidden = true;
        }, durationMillis);
    }

    function openDialog(dialog) {
        if (typeof dialog.showModal === "function") {
            if (!dialog.open) {
                dialog.showModal();
            }
            return;
        }

        dialog.setAttribute("open", "");
    }

    async function requestJson(path, options = {}) {
        const controller = new AbortController();
        const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);
        const headers = new Headers(options.headers || {});
        const tokenType = state.session?.tokenType || "Bearer";

        headers.set("Accept", "application/json");
        if (options.body) {
            headers.set("Content-Type", "application/json");
        }
        if (state.session?.token) {
            headers.set("Authorization", `${tokenType} ${state.session.token}`);
        }

        try {
            const response = await fetch(`${state.apiBaseUrl}${path}`, {
                ...options,
                headers,
                signal: controller.signal
            });
            const responseText = await response.text();
            let responseBody = null;

            if (responseText) {
                try {
                    responseBody = JSON.parse(responseText);
                } catch {
                    responseBody = null;
                }
            }

            if (!response.ok) {
                throw new ApiError(
                    response.status,
                    responseBody?.message || `Máy chủ trả về lỗi ${response.status}.`,
                    responseBody
                );
            }

            return responseBody;
        } catch (error) {
            if (error.name === "AbortError") {
                throw new Error("Máy chủ phản hồi quá lâu. Vui lòng thử lại.");
            }
            if (error instanceof TypeError) {
                throw new Error("Không thể kết nối tới máy chủ. Hãy kiểm tra backend và cấu hình cùng origin/CORS.");
            }
            throw error;
        } finally {
            window.clearTimeout(timeoutId);
        }
    }

    function readStoredSession() {
        try {
            const rawSession = sessionStorage.getItem(SESSION_STORAGE_KEY);
            if (!rawSession) {
                return null;
            }

            const session = JSON.parse(rawSession);
            if (typeof session?.token !== "string" || !session.token || !session.user) {
                return null;
            }
            return session;
        } catch {
            return null;
        }
    }

    function validateStudentSession(session) {
        if (!session) {
            throw new Error("Không tìm thấy phiên đăng nhập. Hãy đăng nhập bằng tài khoản học sinh trước khi mở bài thi.");
        }

        if (session.user?.role && session.user.role !== "STUDENT") {
            throw new Error("Chỉ tài khoản học sinh mới có thể mở màn hình làm bài.");
        }
    }

    function resolveApiBaseUrl(searchParams) {
        const configuredBaseUrl = searchParams.get("apiBaseUrl")?.trim();
        if (configuredBaseUrl) {
            return configuredBaseUrl.replace(/\/$/, "");
        }

        return window.location.protocol === "file:" ? "http://localhost:8080" : "";
    }

    function resolveHomeUrl(requestedUrl) {
        if (!requestedUrl) {
            return DEFAULT_HOME_URL;
        }

        try {
            const resolvedUrl = new URL(requestedUrl, window.location.origin);
            if (resolvedUrl.origin !== window.location.origin) {
                return DEFAULT_HOME_URL;
            }
            return `${resolvedUrl.pathname}${resolvedUrl.search}${resolvedUrl.hash}`;
        } catch {
            return DEFAULT_HOME_URL;
        }
    }

    function getCurrentQuestion() {
        return state.questions[state.currentIndex] || null;
    }

    function isQuestionAnswered(question) {
        if (question.type === "MULTIPLE_CHOICE") {
            return question.answer.selectedOptionId !== null
                && question.answer.selectedOptionId !== undefined;
        }

        return Boolean(question.answer.essayContent?.trim());
    }

    function serializeAnswer(answer) {
        return JSON.stringify({
            selectedOptionId: answer.selectedOptionId ?? null,
            essayContent: answer.essayContent ?? ""
        });
    }

    function getAnsweredSummary(answeredCount, totalQuestions) {
        if (answeredCount === 0) {
            return "Bạn chưa trả lời câu nào.";
        }
        if (answeredCount === totalQuestions) {
            return "Bạn đã trả lời tất cả câu hỏi.";
        }
        return `Đã hoàn thành ${answeredCount} trên ${totalQuestions} câu.`;
    }

    function getExamTypeLabel(type) {
        const labels = {
            MULTIPLE_CHOICE: "Bài kiểm tra trắc nghiệm",
            ESSAY: "Bài kiểm tra tự luận",
            MIXED: "Bài kiểm tra hỗn hợp"
        };
        return labels[type] || "Bài kiểm tra trực tuyến";
    }

    function getInitials(fullName) {
        const words = fullName.trim().split(/\s+/).filter(Boolean);
        if (words.length === 0) {
            return "HS";
        }

        return words
            .slice(-2)
            .map((word) => word.charAt(0).toLocaleUpperCase("vi"))
            .join("");
    }

    function getOptionLetter(index) {
        return String.fromCharCode(65 + index);
    }

    function parseIdentifier(value) {
        const numericValue = Number(value);
        return Number.isSafeInteger(numericValue) ? numericValue : value;
    }

    function formatDuration(totalSeconds) {
        const safeSeconds = Math.max(0, Math.floor(totalSeconds));
        const hours = Math.floor(safeSeconds / 3600);
        const minutes = Math.floor((safeSeconds % 3600) / 60);
        const seconds = safeSeconds % 60;

        return [hours, minutes, seconds]
            .map((value) => String(value).padStart(2, "0"))
            .join(":");
    }

    function formatClockTime(timestamp) {
        return new Intl.DateTimeFormat("vi-VN", {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit"
        }).format(new Date(timestamp));
    }

    function formatNumber(value) {
        return new Intl.NumberFormat("vi-VN").format(value);
    }

    function getErrorMessage(error) {
        if (error instanceof ApiError) {
            if (error.status === 401) {
                return "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại.";
            }
            if (error.status === 403) {
                return "Tài khoản này không có quyền làm bài kiểm tra.";
            }
            if (error.status === 404) {
                return "Không tìm thấy bài kiểm tra được yêu cầu.";
            }
            if (error.status === 409 || error.status === 410) {
                return error.message;
            }
        }

        return error?.message || "Đã xảy ra lỗi không xác định. Vui lòng thử lại.";
    }

    function delay(milliseconds) {
        return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
    }

    function createDemoExamScreen() {
        const now = new Date();
        const deadline = new Date(now.getTime() + 45 * 60 * 1000);

        return {
            attemptId: 9001,
            exam: {
                id: 101,
                title: "Kiểm tra 45 phút • Sinh học 10",
                description: "Bài kiểm tra kiến thức chương Chuyển hóa vật chất và năng lượng.",
                type: "MIXED"
            },
            status: "IN_PROGRESS",
            serverTime: now.toISOString(),
            startedAt: now.toISOString(),
            deadlineAt: deadline.toISOString(),
            remainingSeconds: 2700,
            screenExitCount: 0,
            progress: {
                answeredCount: 2,
                totalQuestions: 10,
                answeredQuestionIds: [102, 105]
            },
            questions: [
                {
                    id: 101,
                    number: 1,
                    type: "MULTIPLE_CHOICE",
                    content: "Bào quan nào trong tế bào thực vật là nơi diễn ra quá trình quang hợp?",
                    options: [
                        { id: 1001, content: "Ti thể" },
                        { id: 1002, content: "Lục lạp" },
                        { id: 1003, content: "Ribosome" },
                        { id: 1004, content: "Bộ máy Golgi" }
                    ],
                    answer: null
                },
                {
                    id: 102,
                    number: 2,
                    type: "MULTIPLE_CHOICE",
                    content: "Sản phẩm trực tiếp của pha sáng trong quang hợp gồm những chất nào?",
                    options: [
                        { id: 2001, content: "ATP, NADPH và O₂" },
                        { id: 2002, content: "Glucose, ATP và CO₂" },
                        { id: 2003, content: "NADH, CO₂ và H₂O" },
                        { id: 2004, content: "Glucose và O₂" }
                    ],
                    answer: { selectedOptionId: 2001, essayContent: null, clientRevision: 1 }
                },
                {
                    id: 103,
                    number: 3,
                    type: "MULTIPLE_CHOICE",
                    content: "Yếu tố nào sau đây không ảnh hưởng trực tiếp đến cường độ quang hợp?",
                    options: [
                        { id: 3001, content: "Cường độ ánh sáng" },
                        { id: 3002, content: "Nồng độ CO₂" },
                        { id: 3003, content: "Nhiệt độ" },
                        { id: 3004, content: "Màu sắc của rễ" }
                    ],
                    answer: null
                },
                {
                    id: 104,
                    number: 4,
                    type: "MULTIPLE_CHOICE",
                    content: "Pha tối của quang hợp diễn ra chủ yếu ở đâu trong lục lạp?",
                    options: [
                        { id: 4001, content: "Màng ngoài" },
                        { id: 4002, content: "Màng tilacoit" },
                        { id: 4003, content: "Chất nền (stroma)" },
                        { id: 4004, content: "Khoang gian màng" }
                    ],
                    answer: null
                },
                {
                    id: 105,
                    number: 5,
                    type: "ESSAY",
                    content: "Hãy giải thích ngắn gọn vai trò của diệp lục đối với quá trình quang hợp ở thực vật.",
                    options: [],
                    answer: {
                        selectedOptionId: null,
                        essayContent: "Diệp lục hấp thụ năng lượng ánh sáng và chuyển hóa thành năng lượng hóa học để phục vụ quá trình tổng hợp chất hữu cơ.",
                        clientRevision: 2
                    }
                },
                {
                    id: 106,
                    number: 6,
                    type: "MULTIPLE_CHOICE",
                    content: "Trong hô hấp tế bào, giai đoạn đường phân diễn ra ở vị trí nào?",
                    options: [
                        { id: 6001, content: "Tế bào chất" },
                        { id: 6002, content: "Chất nền ti thể" },
                        { id: 6003, content: "Màng trong ti thể" },
                        { id: 6004, content: "Nhân tế bào" }
                    ],
                    answer: null
                },
                {
                    id: 107,
                    number: 7,
                    type: "ESSAY",
                    content: "So sánh điểm giống và khác nhau cơ bản giữa quang hợp và hô hấp tế bào.",
                    options: [],
                    answer: null
                },
                {
                    id: 108,
                    number: 8,
                    type: "MULTIPLE_CHOICE",
                    content: "Một phân tử glucose qua đường phân tạo ra bao nhiêu phân tử pyruvate?",
                    options: [
                        { id: 8001, content: "1" },
                        { id: 8002, content: "2" },
                        { id: 8003, content: "4" },
                        { id: 8004, content: "6" }
                    ],
                    answer: null
                },
                {
                    id: 109,
                    number: 9,
                    type: "MULTIPLE_CHOICE",
                    content: "Chất nhận electron cuối cùng trong chuỗi chuyền electron hô hấp hiếu khí là gì?",
                    options: [
                        { id: 9001, content: "CO₂" },
                        { id: 9002, content: "O₂" },
                        { id: 9003, content: "NAD⁺" },
                        { id: 9004, content: "Pyruvate" }
                    ],
                    answer: null
                },
                {
                    id: 110,
                    number: 10,
                    type: "ESSAY",
                    content: "Từ kiến thức đã học, hãy đề xuất hai biện pháp giúp cây trồng quang hợp hiệu quả hơn trong nhà kính.",
                    options: [],
                    answer: null
                }
            ]
        };
    }

})();
