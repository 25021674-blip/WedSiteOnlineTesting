(() => {
    "use strict";

    const API_BASE_URL = (window.TEACHER_HOME_CONFIG?.apiBaseUrl || "/api").replace(/\/+$/, "");
    const SESSION_STORAGE_KEY = "onlineTestingAuthSession";
    const REQUEST_TIMEOUT_MILLIS = 12000;
    const publishState = {
        configurationLoaded: false,
        recipientCandidatesLoaded: false,
        examStatus: null,
        publishing: false,
        published: false
    };

    initialize();
    initializeRecipientCandidates();
    bindPublishEvent();

    async function initialize() {
        const examId = readExamId();

        if (!examId) {
            showLoadError("Không tìm thấy mã bài kiểm tra hợp lệ trên đường dẫn.");
            return;
        }

        try {
            const configuration = await fetchConfiguration(examId);
            applyConfiguration(configuration);
            publishState.configurationLoaded = true;
            publishState.examStatus = configuration.status;
            publishState.published = configuration.status === "PUBLISHED";
            updatePublishButton();
        } catch (error) {
            showLoadError(error.message);
        }
    }

    function readExamId() {
        const currentPage = new URL(window.location.href);
        const examId = currentPage.searchParams.get("examId") || "";
        return /^[1-9]\d*$/.test(examId) ? examId : null;
    }

    function readSession() {
        try {
            const session = JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY) || "null");
            return session && typeof session === "object" ? session : null;
        } catch {
            return null;
        }
    }

    async function fetchConfiguration(examId) {
        const session = readSession();

        if (!session?.token) {
            throw new Error("Không tìm thấy phiên đăng nhập. Vui lòng đăng nhập lại.");
        }

        const controller = new AbortController();
        const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);

        try {
            const response = await fetch(
                `${API_BASE_URL}/teacher/exams/${encodeURIComponent(examId)}/configuration`,
                {
                    method: "GET",
                    headers: {
                        Accept: "application/json",
                        Authorization: `${session.tokenType || "Bearer"} ${session.token}`
                    },
                    signal: controller.signal
                }
            );
            const payload = await readResponsePayload(response);

            if (!response.ok) {
                throw new Error(resolveErrorMessage(response.status, payload));
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

    async function readResponsePayload(response) {
        const responseText = await response.text();

        if (!responseText) {
            return null;
        }

        try {
            return JSON.parse(responseText);
        } catch {
            return null;
        }
    }

    function resolveErrorMessage(status, payload) {
        if (typeof payload?.message === "string" && payload.message) {
            return payload.message;
        }
        if (status === 401) {
            return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
        }
        if (status === 403) {
            return "Bạn không có quyền xem cấu hình bài kiểm tra này.";
        }
        if (status === 404) {
            return "Không tìm thấy bài kiểm tra.";
        }
        return `Máy chủ trả về lỗi ${status}.`;
    }

    function applyConfiguration(configuration) {
        if (!configuration || typeof configuration !== "object") {
            throw new Error("Server không trả về cấu hình bài kiểm tra hợp lệ.");
        }

        setChecked("show-correct-answers-after-submit", configuration.showCorrectAnswersAfterSubmit);
        setChecked("time-limit-enabled", configuration.timeLimitEnabled);
        setChecked("show-score-after-submit", configuration.showScoreAfterSubmit);
        setChecked("require-fullscreen", configuration.requireFullscreen);
        setChecked("track-tab-switches", configuration.trackTabSwitches);

        const maxAttempts = Number(configuration.maxAttempts);
        const retakeCount = Number.isInteger(maxAttempts) && maxAttempts >= 1
            ? maxAttempts - 1
            : 0;
        document.querySelector("#retake-count").value = String(retakeCount);
    }

    function setChecked(elementId, checked) {
        document.getElementById(elementId).checked = Boolean(checked);
    }

    function showLoadError(message) {
        window.alert(message || "Không thể tải cấu hình bài kiểm tra.");
    }

    async function initializeRecipientCandidates() {
        const examId = readExamId();

        if (!examId) {
            return;
        }

        renderRecipientMessage("Đang tải danh sách học sinh...");

        try {
            const candidates = await fetchRecipientCandidates(examId);
            renderRecipientCandidates(candidates);
            publishState.recipientCandidatesLoaded = true;
            updatePublishButton();
        } catch (error) {
            renderRecipientMessage(
                error.message || "Không thể tải danh sách học sinh.",
                true
            );
        }
    }

    async function fetchRecipientCandidates(examId) {
        const session = readSession();

        if (!session?.token) {
            throw new Error("Không tìm thấy phiên đăng nhập. Vui lòng đăng nhập lại.");
        }

        const controller = new AbortController();
        const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);
        const searchParams = new URLSearchParams();
        searchParams.set("query", "");

        try {
            const response = await fetch(
                `${API_BASE_URL}/teacher/exams/${encodeURIComponent(examId)}/recipient-candidates?${searchParams.toString()}`,
                {
                    method: "GET",
                    headers: {
                        Accept: "application/json",
                        Authorization: `${session.tokenType || "Bearer"} ${session.token}`
                    },
                    signal: controller.signal
                }
            );
            const payload = await readResponsePayload(response);

            if (!response.ok) {
                throw new Error(resolveErrorMessage(response.status, payload));
            }
            if (!Array.isArray(payload)) {
                throw new Error("Server không trả về danh sách học sinh hợp lệ.");
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

    function renderRecipientCandidates(candidates) {
        const recipientList = document.querySelector(".recipient-list");
        const recipientCount = document.querySelector(".recipient-selector summary small");
        const validCandidates = candidates.filter(candidate => candidate?.studentId != null);

        recipientList.replaceChildren();
        recipientCount.textContent = `${validCandidates.length} học sinh`;

        if (!validCandidates.length) {
            renderRecipientMessage("Chưa có học sinh nào trong hệ thống.");
            recipientCount.textContent = "0 học sinh";
            return;
        }

        validCandidates.forEach(candidate => {
            const option = document.createElement("label");
            const checkbox = document.createElement("input");
            const identity = document.createElement("span");
            const fullName = document.createElement("strong");

            option.className = "recipient-option";
            checkbox.type = "checkbox";
            checkbox.name = "recipientStudentIds";
            checkbox.value = String(candidate.studentId);
            checkbox.checked = Boolean(candidate.selected);
            identity.className = "recipient-identity";
            fullName.textContent = candidate.fullName || `Học sinh #${candidate.studentId}`;

            identity.append(fullName);

            if (candidate.email) {
                const email = document.createElement("small");
                email.textContent = candidate.email;
                identity.append(email);
            }

            option.append(checkbox, identity);
            recipientList.append(option);
        });
    }

    function renderRecipientMessage(message, isError = false) {
        const recipientList = document.querySelector(".recipient-list");
        const recipientCount = document.querySelector(".recipient-selector summary small");
        const status = document.createElement("p");

        status.className = `recipient-message${isError ? " is-error" : ""}`;
        status.textContent = message;
        recipientList.replaceChildren(status);
        recipientCount.textContent = isError ? "Không tải được" : "Đang tải...";
    }

    function bindPublishEvent() {
        const publishButton = document.querySelector("[data-action='publish-exam']");

        publishButton.addEventListener("click", handlePublish);
        updatePublishButton();
    }

    async function handlePublish() {
        if (publishState.publishing || publishState.published) {
            return;
        }
        if (!publishState.configurationLoaded || !publishState.recipientCandidatesLoaded) {
            showLoadError("Vui lòng chờ tải xong cấu hình và danh sách học sinh.");
            return;
        }

        const examId = readExamId();

        if (!examId) {
            showLoadError("Không tìm thấy mã bài kiểm tra hợp lệ trên đường dẫn.");
            return;
        }

        let requestBody;

        try {
            requestBody = createPublishRequest();
        } catch (error) {
            showLoadError(error.message);
            return;
        }

        publishState.publishing = true;
        updatePublishButton();

        try {
            const publishedConfiguration = await publishExam(examId, requestBody);
            applyConfiguration(publishedConfiguration);
            publishState.publishing = false;
            publishState.published = true;
            publishState.examStatus = publishedConfiguration.status;
            updatePublishButton();
            window.alert("Xuất bản bài kiểm tra thành công.");
        } catch (error) {
            publishState.publishing = false;
            updatePublishButton();
            showLoadError(error.message || "Không thể xuất bản bài kiểm tra.");
        }
    }

    function createPublishRequest() {
        const retakeInput = document.querySelector("#retake-count");
        const retakeValue = retakeInput.value.trim();
        const retakeCount = Number(retakeValue);

        if (!retakeValue || !Number.isSafeInteger(retakeCount) || retakeCount < 0 || retakeCount > 2147483646) {
            retakeInput.focus();
            throw new Error("Số lần làm lại phải là số nguyên từ 0 trở lên.");
        }

        const selectedStudentIds = Array.from(
            document.querySelectorAll("input[name='recipientStudentIds']:checked")
        )
            .map(checkbox => Number(checkbox.value))
            .filter(studentId => Number.isSafeInteger(studentId) && studentId > 0);

        return {
            showCorrectAnswersAfterSubmit: document.getElementById("show-correct-answers-after-submit").checked,
            showScoreAfterSubmit: document.getElementById("show-score-after-submit").checked,
            maxAttempts: retakeCount + 1,
            timeLimitEnabled: document.getElementById("time-limit-enabled").checked,
            requireFullscreen: document.getElementById("require-fullscreen").checked,
            trackTabSwitches: document.getElementById("track-tab-switches").checked,
            recipientStudentIds: [...new Set(selectedStudentIds)]
        };
    }

    async function publishExam(examId, requestBody) {
        const session = readSession();

        if (!session?.token) {
            throw new Error("Không tìm thấy phiên đăng nhập. Vui lòng đăng nhập lại.");
        }

        const controller = new AbortController();
        const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);

        try {
            const response = await fetch(
                `${API_BASE_URL}/teacher/exams/${encodeURIComponent(examId)}/publish`,
                {
                    method: "POST",
                    headers: {
                        Accept: "application/json",
                        "Content-Type": "application/json",
                        Authorization: `${session.tokenType || "Bearer"} ${session.token}`
                    },
                    body: JSON.stringify(requestBody),
                    signal: controller.signal
                }
            );
            const payload = await readResponsePayload(response);

            if (!response.ok) {
                throw new Error(resolvePublishErrorMessage(response.status, payload));
            }
            if (!payload || typeof payload !== "object" || payload.status !== "PUBLISHED") {
                throw new Error("Server không trả về kết quả xuất bản hợp lệ.");
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

    function resolvePublishErrorMessage(status, payload) {
        const validationMessage = Object.values(payload?.errors || {})
            .find(message => typeof message === "string" && message);

        return validationMessage
            || payload?.detail
            || resolveErrorMessage(status, payload);
    }

    function updatePublishButton() {
        const publishButton = document.querySelector("[data-action='publish-exam']");
        const dataReady = publishState.configurationLoaded
            && publishState.recipientCandidatesLoaded;
        const canPublish = dataReady && publishState.examStatus === "DRAFT";

        publishButton.disabled = publishState.publishing
            || publishState.published
            || !canPublish;

        if (publishState.published) {
            publishButton.textContent = "Đã xuất bản";
        } else if (publishState.publishing) {
            publishButton.textContent = "Đang xuất bản...";
        } else if (publishState.examStatus && publishState.examStatus !== "DRAFT") {
            publishButton.textContent = "Không thể xuất bản";
        } else {
            publishButton.textContent = "Xuất bản";
        }
    }
})();
