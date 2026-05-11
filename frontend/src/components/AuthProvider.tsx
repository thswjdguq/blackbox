"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import api from "@/lib/api";
import { useAuthStore } from "@/lib/store/authStore";

type AuthProviderProps = {
  children: React.ReactNode;
};

export default function AuthProvider({ children }: AuthProviderProps) {
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    const verifyAuth = async () => {
      const hasToken = useAuthStore.getState().initFromStorage();

      if (!hasToken) {
        return;
      }

      try {
        await api.get("/auth/profile");
      } catch {
        useAuthStore.getState().clearTokens();
        router.replace("/login");
      }
    };

    void verifyAuth();
  }, [pathname, router]);

  return <>{children}</>;
}