import { create } from "zustand";

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

  clearTokens: () => {
    set({ isAuthenticated: false });
  },

  initFromStorage: () => {
    return get().isAuthenticated;
  },
}));
