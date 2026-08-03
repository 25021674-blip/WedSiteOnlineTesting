(() => {
    "use strict";

    const COMPLETION_STORAGE_KEY = "onlineTestingExamCompletion";
    const DEFAULT_HOME_URL = "/student-exam/StudentExamDemo.html?demo=true";
    const EXAM_TYPES = new Set(["MULTIPLE_CHOICE", "ESSAY", "MIXED"]);

    const elements = {
        brandLink: document.getElementById("brand-link"),
        previewBadge: document.getElementById("preview-badge"),
        completionEyebrow: document.getElementById("completion-eyebrow"),
        completionTitle: document.getElementById("completion-title"),
        examTitle: document.getElementById("exam-title"),
        resultPanel: document.getElementById("result-panel"),
        resultStatusIcon: document.getElementById("result-status-icon"),
        resultStatusTitle: document.getElementById("result-status-title"),
        resultMessage: document.getElementById("result-message"),
        scorePanel: document.getElementById("score-panel"),
        scoreValue: document.getElementById("score-value"),
        scoreMaximum: document.getElementById("score-maximum"),
        answeredValue: document.getElementById("answered-value"),
        submittedTime: document.getElementById("submitted-time"),
        homeLink: document.getElementById("home-link")
    };

    initializeResultPage();

    function initializeResultPage() {
        const completion = readCompletionData();
        const homeUrl = resolveSafeHomeUrl(completion.homeUrl);

        elements.brandLink.href = homeUrl;
        elements.homeLink.href = homeUrl;
        elements.homeLink.addEventListener("click", clearCompletionData);
        elements.brandLink.addEventListener("click", clearCompletionData);

        renderCompletion(completion);
        elements.completionTitle.focus({ preventScroll: true });
    }

    function renderCompletion(completion) {
        const wasAutomatic = completion.wasAutomatic
            || completion.status === "AUTO_SUBMITTED";
        const hasScore = Number.isFinite(completion.score);
        const needsManualGrading = completion.examType === "ESSAY"
            || completion.examType === "MIXED";

        elements.previewBadge.hidden = !completion.isDemo;
        elements.completionEyebrow.textContent = wasAutomatic
            ? "Hết thời gian làm bài"
            : "Hoàn tất bài kiểm tra";
        elements.completionTitle.textContent = wasAutomatic
            ? "Bài kiểm tra đã được tự động nộp"
            : "Bạn đã hoàn thành bài kiểm tra";
        elements.examTitle.textContent = completion.examTitle
            || "Bài làm của bạn đã được hệ thống ghi nhận.";

        elements.resultPanel.className = "result-panel";
        elements.scorePanel.hidden = !hasScore;

        if (hasScore) {
            renderScore(completion);
        } else if (needsManualGrading) {
            renderPendingManualGrade();
        } else {
            renderPendingAutomaticGrade();
        }

        elements.answeredValue.textContent = completion.totalQuestions > 0
            ? `${completion.answeredCount}/${completion.totalQuestions}`
            : "Đã ghi nhận";
        elements.submittedTime.textContent = formatSubmittedTime(completion.submittedAt);
        document.title = `${elements.completionTitle.textContent} | Online Testing`;
    }

    function renderScore(completion) {
        elements.resultPanel.classList.add("is-scored");
        elements.resultStatusIcon.textContent = "✓";
        elements.resultStatusTitle.textContent = "Đã có kết quả bài kiểm tra";
        elements.resultMessage.textContent = completion.examType === "MIXED"
            ? "Điểm hiện tại đã được hệ thống ghi nhận. Kết quả có thể được cập nhật sau khi giáo viên chấm phần tự luận."
            : "Hệ thống đã hoàn tất chấm bài và ghi nhận kết quả của bạn.";
        elements.scoreValue.textContent = formatScore(completion.score);

        if (Number.isFinite(completion.maxScore)) {
            elements.scoreMaximum.hidden = false;
            elements.scoreMaximum.textContent = `/ ${formatScore(completion.maxScore)}`;
        } else {
            elements.scoreMaximum.hidden = true;
        }
    }

    function renderPendingManualGrade() {
        elements.resultPanel.classList.add("is-pending");
        elements.resultStatusIcon.textContent = "◷";
        elements.resultStatusTitle.textContent = "Đang chờ giáo viên chấm điểm";
        elements.resultMessage.textContent = "Bài kiểm tra có câu tự luận nên điểm chưa thể hiển thị ngay. Kết quả sẽ được cập nhật sau khi giáo viên hoàn tất chấm bài.";
    }

    function renderPendingAutomaticGrade() {
        elements.resultPanel.classList.add("is-pending");
        elements.resultStatusIcon.textContent = "◷";
        elements.resultStatusTitle.textContent = "Đang xử lý kết quả";
        elements.resultMessage.textContent = "Bài làm đã được ghi nhận. Điểm số sẽ hiển thị khi hệ thống hoàn tất quá trình chấm bài.";
    }

    function readCompletionData() {
        const queryCompletion = readQueryCompletion();
        const storedCompletion = readStoredCompletion();
        const selectedCompletion = shouldUseStoredCompletion(storedCompletion, queryCompletion)
            ? storedCompletion
            : queryCompletion;

        return normalizeCompletion(selectedCompletion || createPreviewCompletion());
    }

    function readStoredCompletion() {
        try {
            const rawCompletion = sessionStorage.getItem(COMPLETION_STORAGE_KEY);
            return rawCompletion ? JSON.parse(rawCompletion) : null;
        } catch {
            return null;
        }
    }

    function readQueryCompletion() {
        const searchParams = new URLSearchParams(window.location.search);
        const hasCompletionData = [
            "attemptId",
            "status",
            "examType",
            "answeredCount",
            "totalQuestions",
            "submittedAt",
            "score"
        ].some((name) => searchParams.has(name));

        if (!hasCompletionData) {
            return null;
        }

        return {
            attemptId: searchParams.get("attemptId"),
            status: searchParams.get("status"),
            examTitle: searchParams.get("examTitle"),
            examType: searchParams.get("examType"),
            answeredCount: searchParams.get("answeredCount"),
            totalQuestions: searchParams.get("totalQuestions"),
            submittedAt: searchParams.get("submittedAt"),
            score: searchParams.get("score"),
            maxScore: searchParams.get("maxScore"),
            wasAutomatic: searchParams.get("automatic") === "true",
            isDemo: searchParams.get("demo") === "true",
            homeUrl: searchParams.get("homeUrl")
        };
    }

    function shouldUseStoredCompletion(storedCompletion, queryCompletion) {
        if (!storedCompletion) {
            return false;
        }
        if (!queryCompletion?.attemptId) {
            return true;
        }
        return String(storedCompletion.attemptId) === String(queryCompletion.attemptId);
    }

    function normalizeCompletion(completion) {
        const totalQuestions = toNonNegativeInteger(completion.totalQuestions);
        const answeredCount = Math.min(
            toNonNegativeInteger(completion.answeredCount),
            totalQuestions || Number.MAX_SAFE_INTEGER
        );
        const examType = EXAM_TYPES.has(completion.examType)
            ? completion.examType
            : "MIXED";

        return {
            attemptId: completion.attemptId ?? null,
            status: completion.status || "SUBMITTED",
            examTitle: cleanText(completion.examTitle),
            examType,
            answeredCount,
            totalQuestions,
            submittedAt: completion.submittedAt || new Date().toISOString(),
            score: toFiniteNumber(completion.score),
            maxScore: toFiniteNumber(completion.maxScore),
            wasAutomatic: completion.wasAutomatic === true,
            isDemo: completion.isDemo === true,
            homeUrl: cleanText(completion.homeUrl)
        };
    }

    function createPreviewCompletion() {
        return {
            attemptId: "preview",
            status: "SUBMITTED",
            examTitle: "Kiểm tra 45 phút • Sinh học 10",
            examType: "MIXED",
            answeredCount: 8,
            totalQuestions: 10,
            submittedAt: new Date().toISOString(),
            score: null,
            maxScore: null,
            wasAutomatic: false,
            isDemo: true,
            homeUrl: DEFAULT_HOME_URL
        };
    }

    function resolveSafeHomeUrl(requestedUrl) {
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

    function clearCompletionData() {
        try {
            sessionStorage.removeItem(COMPLETION_STORAGE_KEY);
        } catch {
            return;
        }
    }

    function toNonNegativeInteger(value) {
        const number = Number(value);
        return Number.isFinite(number) && number >= 0 ? Math.floor(number) : 0;
    }

    function toFiniteNumber(value) {
        if (value === null || value === undefined || value === "") {
            return null;
        }
        const number = Number(value);
        return Number.isFinite(number) ? number : null;
    }

    function cleanText(value) {
        return typeof value === "string" ? value.trim() : "";
    }

    function formatScore(value) {
        return new Intl.NumberFormat("vi-VN", {
            maximumFractionDigits: 2
        }).format(value);
    }

    function formatSubmittedTime(value) {
        const submittedDate = new Date(value);
        if (Number.isNaN(submittedDate.getTime())) {
            return "Vừa hoàn tất";
        }

        return new Intl.DateTimeFormat("vi-VN", {
            hour: "2-digit",
            minute: "2-digit",
            day: "2-digit",
            month: "2-digit",
            year: "numeric"
        }).format(submittedDate);
    }
})();
