import axios, { InternalAxiosRequestConfig } from "axios";
import { useAuthStore } from "@/lib/store/authStore";

const api = axios.create({
  baseURL: "/api",
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
});

type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean };

// 응답 인터셉터: 401 시 refresh 시도
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config as RetryConfig;
    if (error.response?.status === 401 && !original?._retry && original?.url !== "/auth/refresh") {
      original._retry = true;
      try {
        await api.post("/auth/refresh");
        useAuthStore.getState().setTokens();
        return api(original);
      } catch {
        useAuthStore.getState().clearTokens();
        if (window.location.pathname !== "/login") {
          window.location.href = "/login";
        }
      }
    } else if (error.response?.status === 401 && original?.url === "/auth/refresh") {
      useAuthStore.getState().clearTokens();
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);

export default api;
