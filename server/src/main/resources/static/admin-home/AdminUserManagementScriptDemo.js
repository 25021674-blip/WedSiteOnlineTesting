(() => {
    "use strict";

    const SESSION_STORAGE_KEY = "onlineTestingAuthSession";
    const AUTH_PAGE_URL = "/auth/Auth.html";
    const USERS_API_URL = "/api/admin/users";
    const REQUEST_TIMEOUT_MILLIS = 12000;
    const AVATAR_TONES = ["violet", "blue", "green", "amber", "rose", "cyan", "indigo", "orange"];
    const session = readSession();
    let currentUsers = [];
    let deleteRequestInProgress = false;

    if (!hasValidSession(session)) {
        removeInvalidSession();
        window.location.replace(AUTH_PAGE_URL);
        return;
    }

    if (session.user.role !== "ADMIN") {
        window.location.replace(AUTH_PAGE_URL);
        return;
    }

    window.addEventListener("DOMContentLoaded", initializePage, { once: true });

    function initializePage() {
        applyAdminIdentity(session.user);
        document.querySelector(".logout-button")?.addEventListener("click", logout);
        document.querySelector("#user-table tbody")?.addEventListener("click", handleUserTableClick);
        void loadUsers();
    }

    function readSession() {
        try {
            return JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY) || "null");
        } catch {
            return null;
        }
    }

    function hasValidSession(value) {
        return Boolean(
            value
            && typeof value.token === "string"
            && value.token
            && value.user
            && typeof value.user === "object"
            && typeof value.user.role === "string"
        );
    }

    function applyAdminIdentity(user) {
        const fullName = user.fullName || "Quản trị viên";
        const email = user.email || "Tài khoản quản trị";

        const nameElement = document.querySelector(".admin-profile__copy strong");
        const emailElement = document.querySelector(".admin-profile__copy small");
        const avatarElement = document.querySelector(".admin-profile .avatar");

        if (nameElement) {
            nameElement.textContent = fullName;
        }
        if (emailElement) {
            emailElement.textContent = email;
        }
        if (avatarElement) {
            avatarElement.textContent = getInitials(fullName);
        }
    }

    async function loadUsers() {
        const tableBody = document.querySelector("#user-table tbody");
        const userCard = document.querySelector("#user-table");
        if (!tableBody) {
            return;
        }

        userCard?.setAttribute("aria-busy", "true");
        renderTableMessage(tableBody, "Đang tải danh sách người dùng...", "loading");
        updateSummary(null, "loading");

        const controller = new AbortController();
        const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);

        try {
            const response = await fetch(USERS_API_URL, {
                method: "GET",
                headers: {
                    "Accept": "application/json",
                    "Authorization": `${session.tokenType || "Bearer"} ${session.token}`
                },
                signal: controller.signal
            });

            if (response.status === 401 || response.status === 403) {
                removeInvalidSession();
                window.location.replace(AUTH_PAGE_URL);
                return;
            }

            const responseBody = await readResponseBody(response);
            if (!response.ok) {
                throw new Error(getResponseMessage(responseBody, response.status));
            }
            if (!Array.isArray(responseBody)) {
                throw new Error("Máy chủ trả về danh sách người dùng không đúng định dạng.");
            }

            currentUsers = responseBody;
            renderUsers(tableBody, responseBody);
            updateSummary(responseBody);
            return true;
        } catch (error) {
            const message = error.name === "AbortError"
                ? "Máy chủ phản hồi quá lâu. Vui lòng tải lại trang."
                : error instanceof TypeError
                    ? "Không thể kết nối tới máy chủ. Vui lòng kiểm tra Spring Boot đang chạy."
                    : error.message || "Không thể tải danh sách người dùng.";

            renderTableMessage(tableBody, message, "error");
            updateSummary(null, "error");
            return false;
        } finally {
            window.clearTimeout(timeoutId);
            userCard?.removeAttribute("aria-busy");
        }
    }

    async function readResponseBody(response) {
        const responseText = await response.text();
        if (!responseText) {
            return null;
        }

        try {
            return JSON.parse(responseText);
        } catch {
            throw new Error("Máy chủ trả về dữ liệu không đúng định dạng.");
        }
    }

    function getResponseMessage(responseBody, status) {
        if (responseBody && typeof responseBody === "object") {
            return responseBody.message
                || responseBody.detail
                || responseBody.error
                || `Không thể tải danh sách người dùng (${status}).`;
        }
        return `Không thể tải danh sách người dùng (${status}).`;
    }

    function renderUsers(tableBody, users) {
        tableBody.replaceChildren();

        if (!users.length) {
            renderTableMessage(tableBody, "Chưa có người dùng nào trong hệ thống.", "empty");
            return;
        }

        users.forEach((user, index) => {
            tableBody.append(createUserRow(user, index));
        });
    }

    function createUserRow(user, index) {
        const userId = user?.userId ?? "";
        const fullName = String(user?.fullName || "Chưa cập nhật");
        const email = String(user?.email || "");
        const role = String(user?.role || "");
        const isCurrentUser = String(userId) === String(session.user.id);

        const row = document.createElement("tr");
        row.dataset.userId = String(userId);
        row.dataset.fullName = fullName;
        row.append(createNumberCell(index));
        row.append(createIdCell(userId));
        row.append(createIdentityCell(fullName, index));
        row.append(createEmailCell(email));
        row.append(createRoleCell(role));
        row.append(createActionCell(fullName, isCurrentUser));

        const deleteButton = row.querySelector(".delete-button");
        if (!isCurrentUser && userId !== "" && deleteButton) {
            deleteButton.disabled = false;
            deleteButton.title = `Xóa người dùng ${fullName}`;
            deleteButton.setAttribute("aria-label", `Xóa người dùng ${fullName}`);
        }
        if (!isCurrentUser && userId === "" && deleteButton) {
            deleteButton.textContent = "Thiếu ID";
            deleteButton.title = "Không thể xóa vì dữ liệu người dùng không có ID";
            deleteButton.setAttribute("aria-label", deleteButton.title);
        }
        return row;
    }

    function createNumberCell(index) {
        const cell = createCell("Số thứ tự");
        const number = document.createElement("span");
        number.className = "row-number";
        number.textContent = String(index + 1).padStart(2, "0");
        cell.append(number);
        return cell;
    }

    function createIdCell(userId) {
        const cell = createCell("ID người dùng");
        const code = document.createElement("code");
        code.textContent = userId === "" ? "—" : String(userId);
        cell.append(code);
        return cell;
    }

    function createIdentityCell(fullName, index) {
        const cell = createCell("Tên người dùng");
        const identity = document.createElement("div");
        identity.className = "user-identity";

        const avatar = document.createElement("span");
        avatar.className = `user-avatar user-avatar--${AVATAR_TONES[index % AVATAR_TONES.length]}`;
        avatar.setAttribute("aria-hidden", "true");
        avatar.textContent = getInitials(fullName);

        const name = document.createElement("strong");
        name.textContent = fullName;
        identity.append(avatar, name);
        cell.append(identity);
        return cell;
    }

    function createEmailCell(email) {
        const cell = createCell("Email");

        if (!email) {
            cell.textContent = "Chưa cập nhật";
            return cell;
        }

        const link = document.createElement("a");
        link.className = "email-link";
        link.href = `mailto:${email}`;
        link.textContent = email;
        cell.append(link);
        return cell;
    }

    function createRoleCell(role) {
        const normalizedRole = role.toUpperCase();
        const roleNames = {
            ADMIN: "Quản trị viên",
            TEACHER: "Giáo viên",
            STUDENT: "Học sinh"
        };
        const roleClasses = {
            ADMIN: "admin",
            TEACHER: "teacher",
            STUDENT: "student"
        };

        const cell = createCell("Vai trò");
        const badge = document.createElement("span");
        badge.className = `role role--${roleClasses[normalizedRole] || "unknown"}`;
        badge.textContent = roleNames[normalizedRole] || role || "Chưa xác định";
        cell.append(badge);
        return cell;
    }

    function createActionCell(fullName, isCurrentUser) {
        const cell = createCell("Thao tác");
        cell.classList.add("action-cell");

        const button = document.createElement("button");
        button.className = "delete-button";
        button.type = "button";
        button.disabled = true;
        button.textContent = isCurrentUser ? "Tài khoản hiện tại" : "Xóa";
        button.setAttribute(
            "aria-label",
            isCurrentUser
                ? `Không thể xóa người dùng ${fullName} vì đây là tài khoản hiện tại`
                : `Chức năng xóa người dùng ${fullName} chưa được triển khai`
        );
        if (!isCurrentUser) {
            button.title = "Chức năng xóa sẽ được bổ sung sau";
        }

        cell.append(button);
        return cell;
    }

    function handleUserTableClick(event) {
        const button = event.target?.closest?.(".delete-button");
        if (!button || button.disabled || deleteRequestInProgress) {
            return;
        }

        const row = button.closest("tr");
        const userId = row?.dataset.userId;
        const fullName = row?.dataset.fullName || "người dùng này";
        if (!userId) {
            showUserActionStatus("Không thể xác định ID người dùng cần xóa.", "error");
            return;
        }

        const confirmed = window.confirm(
            `Bạn có chắc muốn xóa người dùng “${fullName}”?\n\nHành động này không thể hoàn tác.`
        );
        if (confirmed) {
            void deleteUser(userId, fullName, button, row);
        }
    }

    async function deleteUser(userId, fullName, button, row) {
        deleteRequestInProgress = true;
        const originalButtonText = button.textContent;
        const enabledDeleteButtons = Array.from(
            document.querySelectorAll(".delete-button:not(:disabled)")
        );
        let shouldRestoreButton = true;
        let responseStatus = null;

        enabledDeleteButtons.forEach(deleteButton => {
            deleteButton.disabled = true;
        });
        button.disabled = true;
        button.classList.add("is-loading");
        button.textContent = "Đang xóa...";
        row?.setAttribute("aria-busy", "true");
        showUserActionStatus(`Đang xóa người dùng ${fullName}...`, "loading");

        const controller = new AbortController();
        const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MILLIS);

        try {
            const response = await fetch(`${USERS_API_URL}/${encodeURIComponent(userId)}`, {
                method: "DELETE",
                headers: {
                    "Accept": "application/json",
                    "Authorization": `${session.tokenType || "Bearer"} ${session.token}`
                },
                signal: controller.signal
            });
            responseStatus = response.status;

            if (response.status === 401 || response.status === 403) {
                shouldRestoreButton = false;
                removeInvalidSession();
                window.location.replace(AUTH_PAGE_URL);
                return;
            }

            const responseBody = await readResponseBody(response);
            if (!response.ok) {
                throw new Error(getDeleteResponseMessage(responseBody, response.status));
            }

            shouldRestoreButton = false;
            currentUsers = currentUsers.filter(
                user => String(user?.userId) !== String(userId)
            );

            const tableBody = document.querySelector("#user-table tbody");
            if (tableBody) {
                renderUsers(tableBody, currentUsers);
            }
            updateSummary(currentUsers);
            showUserActionStatus(`Đã xóa người dùng ${fullName}.`, "success");
            document.querySelector("#user-action-status")?.focus();
        } catch (error) {
            const message = error.name === "AbortError"
                ? "Yêu cầu xóa mất quá nhiều thời gian. Vui lòng thử lại."
                : error instanceof TypeError
                    ? "Không thể kết nối tới máy chủ để xóa người dùng."
                    : error.message || "Không thể xóa người dùng.";

            showUserActionStatus(message, "error");
            if (error.name === "AbortError" || error instanceof TypeError || responseStatus === 404) {
                const synchronized = await loadUsers();
                if (synchronized) {
                    const synchronizedMessage = responseStatus === 404
                        ? "Người dùng này không còn tồn tại. Danh sách đã được cập nhật."
                        : "Không xác định được yêu cầu xóa đã hoàn tất hay chưa. Danh sách đã được cập nhật.";
                    showUserActionStatus(synchronizedMessage, "error");
                }
            }
        } finally {
            window.clearTimeout(timeoutId);
            deleteRequestInProgress = false;
            row?.removeAttribute("aria-busy");
            button.classList.remove("is-loading");

            if (shouldRestoreButton) {
                enabledDeleteButtons.forEach(deleteButton => {
                    if (deleteButton.isConnected) {
                        deleteButton.disabled = false;
                    }
                });
                button.disabled = false;
                button.textContent = originalButtonText;
            }
        }
    }

    function getDeleteResponseMessage(responseBody, status) {
        if (responseBody && typeof responseBody === "object") {
            return responseBody.message
                || responseBody.detail
                || responseBody.error
                || `Không thể xóa người dùng (${status}).`;
        }
        return `Không thể xóa người dùng (${status}).`;
    }

    function showUserActionStatus(message, tone) {
        const status = document.querySelector("#user-action-status");
        if (!status) {
            return;
        }

        status.hidden = false;
        status.tabIndex = -1;
        status.className = `user-action-status user-action-status--${tone}`;
        status.setAttribute("role", tone === "error" ? "alert" : "status");
        status.setAttribute("aria-live", tone === "error" ? "assertive" : "polite");
        status.textContent = message;
    }

    function createCell(label) {
        const cell = document.createElement("td");
        cell.dataset.label = label;
        return cell;
    }

    function renderTableMessage(tableBody, message, tone) {
        const row = document.createElement("tr");
        row.className = "table-message-row";

        const cell = document.createElement("td");
        cell.className = "table-message-cell";
        cell.colSpan = 6;

        const status = document.createElement("div");
        status.className = `table-message table-message--${tone}`;
        status.setAttribute("role", tone === "error" ? "alert" : "status");
        status.textContent = message;

        cell.append(status);
        row.append(cell);
        tableBody.replaceChildren(row);
    }

    function updateSummary(users, state) {
        const values = document.querySelectorAll(".role-summary strong");
        const footerText = document.querySelector(".table-footer p");

        if (!Array.isArray(users)) {
            if (values[0]) {
                values[0].textContent = "—";
            }
            if (values[1]) {
                values[1].textContent = "—";
            }
            if (footerText) {
                footerText.textContent = state === "error"
                    ? "Không thể tải danh sách người dùng"
                    : "Đang cập nhật danh sách người dùng";
            }
            return;
        }

        const roleCount = new Set(users.map(user => user?.role).filter(Boolean)).size;
        if (values[0]) {
            values[0].textContent = String(users.length).padStart(2, "0");
        }
        if (values[1]) {
            values[1].textContent = String(roleCount).padStart(2, "0");
        }

        if (footerText) {
            footerText.textContent = users.length
                ? `Hiển thị 1–${users.length} trong ${users.length} người dùng`
                : "Không có người dùng để hiển thị";
        }
    }

    function getInitials(fullName) {
        const words = String(fullName).trim().split(/\s+/).filter(Boolean);
        if (!words.length) {
            return "AD";
        }

        const firstInitial = words[0][0] || "";
        const lastInitial = words.length > 1
            ? words[words.length - 1][0] || ""
            : words[0][1] || "";
        return `${firstInitial}${lastInitial}`.toUpperCase();
    }

    function logout() {
        try {
            sessionStorage.removeItem(SESSION_STORAGE_KEY);
        } catch {
            // Phiên đăng nhập phía server vẫn được bảo vệ bằng JWT.
        }
        window.location.replace(AUTH_PAGE_URL);
    }

    function removeInvalidSession() {
        try {
            sessionStorage.removeItem(SESSION_STORAGE_KEY);
        } catch {
            // Không cần xử lý thêm nếu trình duyệt chặn sessionStorage.
        }
    }
})();
