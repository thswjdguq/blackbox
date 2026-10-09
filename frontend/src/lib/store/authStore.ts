import { create } from "zustand";
import { resetSessionCaches } from "@/lib/sessionCache";

interface AuthState {
  isAuthenticated: boolean;
  setTokens: () => void;
  clearTokens: () => void;
  initFromStorage: () => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  isAuthenticated: false,

  setTokens: () => {
    set({ isAuthenticated: true });
  },

  // 로그아웃·세션 만료 모두 여기를 지난다. 앞 계정의 화면 캐시(역할·프로젝트 목록)도 함께 비운다
  clearTokens: () => {
    set({ isAuthenticated: false });
    resetSessionCaches();
  },

  initFromStorage: () => {
    return get().isAuthenticated;
  },
}));
