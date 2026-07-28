export const API_BASE_URL = (
    window.EDU_PORTAL_CONFIG?.apiBaseUrl
    || localStorage.getItem("apiBaseUrl")
    || "http://localhost:8080/api"
).replace(/\/+$/, "");

export const AUTH_STORAGE_KEYS = [
    "auth",
    "authData",
    "authToken",
    "accessToken",
    "jwtToken",
    "token"
];
