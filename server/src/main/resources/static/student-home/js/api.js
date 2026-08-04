import { API_BASE_URL, AUTH_STORAGE_KEYS } from "./config.js";

export class ApiError extends Error {
    constructor(message, status = 0, payload = null) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.payload = payload;
    }
}

function readJson(value) {
    if (!value) return null;
    try {
        return JSON.parse(value);
    } catch {
        return null;
    }
}

function tokenFromValue(value) {
    if (!value) return "";
    const parsed = readJson(value);
    if (parsed && typeof parsed === "object") {
        return parsed.token || parsed.accessToken || parsed.jwtToken || "";
    }
    return value.includes(".") ? value : "";
}

export function getAuthToken() {
    for (const key of AUTH_STORAGE_KEYS) {
        const token = tokenFromValue(localStorage.getItem(key))
            || tokenFromValue(sessionStorage.getItem(key));
        if (token) return token.replace(/^Bearer\s+/i, "");
    }
    return "";
}

export function getStoredUser() {
    const candidateKeys = [
        "onlineTestingAuthSession",
        "auth",
        "authData",
        "currentUser",
        "user"
    ];
    for (const key of candidateKeys) {
        const value = readJson(localStorage.getItem(key)) || readJson(sessionStorage.getItem(key));
        if (value && typeof value === "object") {
            return value.user && typeof value.user === "object" ? value.user : value;
        }
    }
    return {};
}

export function readJwtPayload(token = getAuthToken()) {
    if (!token) return {};
    try {
        const encoded = token.split(".")[1];
        const normalized = encoded.replace(/-/g, "+").replace(/_/g, "/");
        const json = decodeURIComponent(
            atob(normalized)
                .split("")
                .map(character => `%${character.charCodeAt(0).toString(16).padStart(2, "0")}`)
                .join("")
        );
        return JSON.parse(json);
    } catch {
        return {};
    }
}

export function clearAuthSession() {
    AUTH_STORAGE_KEYS.concat(["currentUser", "user"]).forEach(key => {
        localStorage.removeItem(key);
        sessionStorage.removeItem(key);
    });
}

async function request(path, options = {}) {
    const token = getAuthToken();
    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");
    if (token) headers.set("Authorization", `Bearer ${token}`);

    let response;
    try {
        response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
    } catch {
        throw new ApiError(
            `Không thể kết nối tới máy chủ tại ${API_BASE_URL}. Hãy kiểm tra backend và cấu hình CORS.`,
            0
        );
    }

    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json")
        ? await response.json().catch(() => null)
        : await response.text().catch(() => "");

    if (!response.ok) {
        const message = payload?.message
            || payload?.detail
            || (response.status === 401 ? "Phiên đăng nhập không hợp lệ hoặc đã hết hạn." : "Không thể tải dữ liệu.");
        throw new ApiError(message, response.status, payload);
    }

    return payload;
}

export async function getExams() {
    const exams = await request("/exams");
    return Array.isArray(exams) ? exams : [];
}

async function getMyExamResult(exam) {
    const path = exam.type === "ESSAY"
        ? `/exams/${exam.id}/essay-submissions/me`
        : `/exams/${exam.id}/quiz-submissions/me`;
    try {
        return await request(path);
    } catch (error) {
        if (error instanceof ApiError && [404, 409].includes(error.status)) return null;
        throw error;
    }
}

export async function getMyResults(exams) {
    const entries = await Promise.all(exams.map(async exam => {
        try {
            return [String(exam.id), await getMyExamResult(exam)];
        } catch (error) {
            if (error instanceof ApiError && error.status !== 401) return [String(exam.id), null];
            throw error;
        }
    }));
    return Object.fromEntries(entries);
}
