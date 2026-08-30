(() => {
    "use strict";

    const API_ENDPOINTS = {
        login: "/api/auth/login",
        registerByRole: {
            STUDENT: "/api/auth/student/register",
            TEACHER: "/api/auth/teacher/register"
        }
    };

    const SESSION_STORAGE_KEY = "onlineTestingAuthSession";
    const REQUEST_TIMEOUT_MILLIS = 12000;
    const ADMIN_HOME_URL = "/admin-home/AdminUserManagementDemo.html";

    const tabs = Array.from(document.querySelectorAll(".auth-tab[data-mode]"));
    const forms = {
        login: document.getElementById("login-form"),
        register: document.getElementById("register-form")
    };
    const authContent = document.getElementById("auth-content");
    const authTitle = document.getElementById("auth-title");
    const authDescription = document.getElementById("auth-description");
    const formStatus = document.getElementById("form-status");
    const sessionPanel = document.getElementById("session-panel");
    const sessionTitle = document.getElementById("session-title");
    const sessionName = document.getElementById("session-name");
    const sessionMessage = document.getElementById("session-message");
    const sessionEmail = document.getElementById("session-email");
    const sessionRole = document.getElementById("session-role");
    const logoutButton = document.getElementById("logout-button");

    let activeMode = "login";
    let isSubmitting = false;

    initializePage();

    function initializePage() {
        tabs.forEach((tab) => {
            tab.addEventListener("click", () => setMode(tab.dataset.mode));
            tab.addEventListener("keydown", handleTabKeydown);
        });

        document.querySelectorAll("[data-switch-mode]").forEach((button) => {
            button.addEventListener("click", () => {
                setMode(button.dataset.switchMode, { focusFirstField: true });
            });
        });

        document.querySelectorAll("[data-password-toggle]").forEach((button) => {
            button.addEventListener("click", () => togglePasswordVisibility(button));
        });

        Object.values(forms).forEach((form) => {
            form.addEventListener("submit", handleSubmit);
            form.addEventListener("input", handleFieldInput);
            form.addEventListener("change", handleFieldInput);
        });

        logoutButton.addEventListener("click", clearSession);

        const storedSession = readStoredSession();
        if (storedSession) {
            if (storedSession.user?.role === "ADMIN") {
                window.location.replace(ADMIN_HOME_URL);
                return;
            }

            showAuthenticatedSession(storedSession, "stored");
            return;
        }

        setMode("login");
    }

    function setMode(mode, options = {}) {
        if (!forms[mode] || isSubmitting) {
            return;
        }

        const previousMode = activeMode;
        const previousEmail = forms[previousMode]?.elements.email?.value.trim() || "";
        activeMode = mode;

        tabs.forEach((tab) => {
            const isSelected = tab.dataset.mode === mode;
            tab.classList.toggle("is-active", isSelected);
            tab.setAttribute("aria-selected", String(isSelected));
            tab.tabIndex = isSelected ? 0 : -1;
        });

        Object.entries(forms).forEach(([formMode, form]) => {
            form.hidden = formMode !== mode;
        });

        if (previousEmail && !forms[mode].elements.email.value) {
            forms[mode].elements.email.value = previousEmail;
        }

        clearStatus();
        clearFormErrors(forms.login);
        clearFormErrors(forms.register);
        updateHeader(mode);

        if (options.focusFirstField) {
            forms[mode].querySelector("input")?.focus();
        }
    }

    function updateHeader(mode) {
        const isLogin = mode === "login";
        authTitle.textContent = isLogin ? "Đăng nhập tài khoản" : "Tạo tài khoản mới";
        authDescription.textContent = isLogin
            ? "Tiếp tục hành trình học tập và kiểm tra của bạn."
            : "Bắt đầu sử dụng hệ thống kiểm tra trực tuyến ngay hôm nay.";
        document.title = isLogin
            ? "Đăng nhập | Online Testing"
            : "Đăng ký | Online Testing";
    }

    function handleTabKeydown(event) {
        const currentIndex = tabs.indexOf(event.currentTarget);
        let nextIndex = currentIndex;

        if (event.key === "ArrowRight") {
            nextIndex = (currentIndex + 1) % tabs.length;
        } else if (event.key === "ArrowLeft") {
            nextIndex = (currentIndex - 1 + tabs.length) % tabs.length;
        } else if (event.key === "Home") {
            nextIndex = 0;
        } else if (event.key === "End") {
            nextIndex = tabs.length - 1;
        } else {
            return;
        }

        event.preventDefault();
        const nextTab = tabs[nextIndex];
        setMode(nextTab.dataset.mode);
        nextTab.focus();
    }

    function togglePasswordVisibility(button) {
        const input = document.getElementById(button.dataset.passwordToggle);
        if (!input) {
            return;
        }

        const shouldShowPassword = input.type === "password";
        input.type = shouldShowPassword ? "text" : "password";
        button.textContent = shouldShowPassword ? "Ẩn" : "Hiện";
        button.setAttribute("aria-pressed", String(shouldShowPassword));
        button.setAttribute(
            "aria-label",
            shouldShowPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"
        );
    }

    function handleFieldInput(event) {
        if (
            !(event.target instanceof HTMLInputElement) &&
            !(event.target instanceof HTMLSelectElement)
        ) {
            return;
        }

        clearFieldError(event.target);
        clearStatus();

        if (
            event.currentTarget === forms.register &&
            (event.target.name === "password" || event.target.name === "confirmPassword")
        ) {
            const password = forms.register.elements.password.value;
            const confirmation = forms.register.elements.confirmPassword.value;
            if (confirmation && password === confirmation) {
                clearFieldError(forms.register.elements.confirmPassword);
            }
        }
    }

    async function handleSubmit(event) {
        event.preventDefault();

        if (isSubmitting) {
            return;
        }

        const form = event.currentTarget;
        const mode = form.dataset.mode;
        const validation = validateForm(form, mode);

        if (!validation.isValid) {
            form.querySelector("[aria-invalid='true']")?.focus();
            return;
        }

        setSubmitting(form, true);
        clearStatus();

        const controller = new AbortController();
        const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);

        try {
            const endpoint = getApiEndpoint(mode, validation.role);
            const response = await fetch(endpoint, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                },
                body: JSON.stringify(validation.payload),
                signal: controller.signal
            });

            const responseBody = await readResponseBody(response);

            if (!response.ok) {
                const hasFieldErrors = applyServerErrors(form, responseBody.errors);
                showStatus(
                    responseBody.message || "Yêu cầu chưa thể hoàn thành. Vui lòng kiểm tra lại.",
                    "error"
                );

                if (hasFieldErrors) {
                    form.querySelector("[aria-invalid='true']")?.focus();
                } else {
                    formStatus.focus();
                }
                return;
            }

            const session = createSession(responseBody);
            storeSession(session);

            if (session.user.role === "ADMIN") {
                window.location.assign(ADMIN_HOME_URL);
                return;
            }

            if (session.user.role === "STUDENT") {
                window.location.assign("/index.html#home");
                return;
            }

            if (session.user.role === "TEACHER") {
                window.location.assign("/teacher-home/TeacherHomeDemo.html");
                return;
            }

            showAuthenticatedSession(session, mode);
            storeSession(session);
            showAuthenticatedSession(session, mode);
        } catch (error) {
            if (error.name === "AbortError") {
                showStatus("Máy chủ phản hồi quá lâu. Vui lòng thử lại.", "error");
            } else if (error instanceof TypeError) {
                showStatus(
                    "Không thể kết nối tới máy chủ. Hãy kiểm tra Spring Boot đang chạy và thử lại.",
                    "error"
                );
            } else {
                showStatus(error.message || "Đã xảy ra lỗi. Vui lòng thử lại.", "error");
            }
            formStatus.focus();
        } finally {
            window.clearTimeout(timeoutId);
            setSubmitting(form, false);
        }
    }

    function validateForm(form, mode) {
        clearFormErrors(form);

        const emailInput = form.elements.email;
        const passwordInput = form.elements.password;
        const email = emailInput.value.trim();
        const password = passwordInput.value;
        let isValid = true;

        emailInput.value = email;

        if (!email) {
            setFieldError(emailInput, "Email không được để trống.");
            isValid = false;
        } else if (!isValidEmail(email)) {
            setFieldError(emailInput, "Email không đúng định dạng.");
            isValid = false;
        }

        if (!password) {
            setFieldError(passwordInput, "Mật khẩu không được để trống.");
            isValid = false;
        }

        if (mode === "login") {
            return {
                isValid,
                payload: { email, password }
            };
        }

        const fullNameInput = form.elements.fullName;
        const confirmPasswordInput = form.elements.confirmPassword;
        const roleSelect = form.elements.role;
        const fullName = fullNameInput.value.trim();
        const confirmPassword = confirmPasswordInput.value;
        const role = roleSelect.value;

        fullNameInput.value = fullName;

        if (!fullName) {
            setFieldError(fullNameInput, "Họ và tên không được để trống.");
            isValid = false;
        } else if (fullName.length > 100) {
            setFieldError(fullNameInput, "Họ và tên không được vượt quá 100 ký tự.");
            isValid = false;
        }

        if (password && password.length < 8) {
            setFieldError(passwordInput, "Mật khẩu phải có ít nhất 8 ký tự.");
            isValid = false;
        } else if (password.length > 72) {
            setFieldError(passwordInput, "Mật khẩu không được vượt quá 72 ký tự.");
            isValid = false;
        }

        if (!confirmPassword) {
            setFieldError(confirmPasswordInput, "Vui lòng nhập lại mật khẩu.");
            isValid = false;
        } else if (password !== confirmPassword) {
            setFieldError(confirmPasswordInput, "Mật khẩu xác nhận chưa khớp.");
            isValid = false;
        }

        if (!API_ENDPOINTS.registerByRole[role]) {
            setFieldError(roleSelect, "Vui lòng chọn học sinh hoặc giáo viên.");
            isValid = false;
        }

        return {
            isValid,
            payload: { fullName, email, password },
            role
        };
    }

    function getApiEndpoint(mode, role) {
        if (mode === "login") {
            return API_ENDPOINTS.login;
        }

        return API_ENDPOINTS.registerByRole[role];
    }

    function isValidEmail(email) {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    }

    function setFieldError(input, message) {
        input.setAttribute("aria-invalid", "true");
        const errorElement = document.getElementById(`${input.id}-error`);
        if (errorElement) {
            errorElement.textContent = message;
        }
    }

    function clearFieldError(input) {
        input.removeAttribute("aria-invalid");
        const errorElement = document.getElementById(`${input.id}-error`);
        if (errorElement) {
            errorElement.textContent = "";
        }
    }

    function clearFormErrors(form) {
        form.querySelectorAll("input, select").forEach(clearFieldError);
    }

    function applyServerErrors(form, errors) {
        if (!errors || typeof errors !== "object") {
            return false;
        }

        let hasFieldErrors = false;
        Object.entries(errors).forEach(([fieldName, message]) => {
            const input = form.elements.namedItem(fieldName);
            if (
                (input instanceof HTMLInputElement || input instanceof HTMLSelectElement) &&
                typeof message === "string"
            ) {
                setFieldError(input, message);
                hasFieldErrors = true;
            }
        });

        return hasFieldErrors;
    }

    async function readResponseBody(response) {
        const text = await response.text();
        if (!text) {
            return {};
        }

        try {
            return JSON.parse(text);
        } catch {
            return { message: "Máy chủ trả về dữ liệu không đúng định dạng." };
        }
    }

    function createSession(responseBody) {
        if (!responseBody || typeof responseBody.token !== "string" || !responseBody.token) {
            throw new Error("Phản hồi đăng nhập không chứa mã truy cập.");
        }

        return {
            token: responseBody.token,
            tokenType: responseBody.tokenType || "Bearer",
            user: {
                id: responseBody.userId,
                fullName: responseBody.fullName || "Bạn",
                email: responseBody.email || "",
                role: responseBody.role || "STUDENT"
            }
        };
    }

    function storeSession(session) {
        try {
            sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
        } catch {
            // Phiên vẫn dùng được trên trang hiện tại nếu trình duyệt chặn sessionStorage.
        }
    }

    function readStoredSession() {
        try {
            const rawSession = sessionStorage.getItem(SESSION_STORAGE_KEY);
            if (!rawSession) {
                return null;
            }

            const session = JSON.parse(rawSession);
            if (
                typeof session?.token !== "string" ||
                !session.token ||
                !session.user ||
                typeof session.user !== "object"
            ) {
                removeStoredSession();
                return null;
            }
            return session;
        } catch {
            return null;
        }
    }

    function showAuthenticatedSession(session, source) {
        authContent.hidden = true;
        sessionPanel.hidden = false;

        sessionName.textContent = session.user.fullName || "bạn";
        sessionEmail.textContent = session.user.email || "Chưa có thông tin";
        sessionRole.textContent = getRoleLabel(session.user.role);
        sessionMessage.textContent = source === "register"
            ? "Tài khoản đã được tạo và đăng nhập thành công."
            : source === "stored"
                ? "Phiên đăng nhập demo đang được lưu trong tab trình duyệt này."
                : "Bạn đã đăng nhập thành công vào hệ thống.";

        document.title = "Đăng nhập thành công | Online Testing";
        clearStatus();
        sessionTitle.focus();
    }

    function getRoleLabel(role) {
        const roleLabels = {
            ADMIN: "Quản trị viên",
            STUDENT: "Học sinh",
            TEACHER: "Giáo viên"
        };
        return roleLabels[role] || role || "Người dùng";
    }

    function clearSession() {
        removeStoredSession();

        Object.values(forms).forEach((form) => form.reset());
        document.querySelectorAll("[data-password-toggle]").forEach((button) => {
            const input = document.getElementById(button.dataset.passwordToggle);
            if (input) {
                input.type = "password";
            }
            button.textContent = "Hiện";
            button.setAttribute("aria-pressed", "false");
            button.setAttribute("aria-label", "Hiện mật khẩu");
        });

        sessionPanel.hidden = true;
        authContent.hidden = false;
        setMode("login", { focusFirstField: true });
    }

    function removeStoredSession() {
        try {
            sessionStorage.removeItem(SESSION_STORAGE_KEY);
        } catch {
            // Không cần xử lý thêm nếu trình duyệt không cho phép truy cập storage.
        }
    }

    function showStatus(message, type) {
        formStatus.textContent = message;
        formStatus.className = `status-message is-${type}`;
        formStatus.setAttribute("role", type === "error" ? "alert" : "status");
        formStatus.hidden = false;
    }

    function clearStatus() {
        formStatus.textContent = "";
        formStatus.className = "status-message";
        formStatus.setAttribute("role", "status");
        formStatus.hidden = true;
    }

    function setSubmitting(form, submitting) {
        isSubmitting = submitting;
        form.setAttribute("aria-busy", String(submitting));

        form.querySelectorAll("input, select, button").forEach((control) => {
            control.disabled = submitting;
        });
        tabs.forEach((tab) => {
            tab.disabled = submitting;
        });

        const submitButton = form.querySelector(".submit-button");
        const buttonLabel = submitButton.querySelector(".button-label");
        submitButton.classList.toggle("is-loading", submitting);
        buttonLabel.textContent = submitting
            ? form.dataset.mode === "login" ? "Đang đăng nhập..." : "Đang tạo tài khoản..."
            : form.dataset.mode === "login" ? "Đăng nhập" : "Tạo tài khoản";
    }
})();
