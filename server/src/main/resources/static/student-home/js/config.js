export const API_BASE_URL = (
    window.EDU_PORTAL_CONFIG?.apiBaseUrl
    || localStorage.getItem("apiBaseUrl")
    || `${window.location.origin}/api`
).replace(/\/+$/, "");

export const AUTH_STORAGE_KEYS = [
    "onlineTestingAuthSession",
    "auth",
    "authData",
    "authToken",
    "accessToken",
    "jwtToken",
    "token"
];
