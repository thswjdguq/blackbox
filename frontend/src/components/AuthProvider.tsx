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
    const isPublicPath = pathname === "/login" || pathname === "/signup";

    const verifyAuth = async () => {
      try {
        await api.get("/auth/profile");
        useAuthStore.getState().setTokens();

        if (isPublicPath) {
          router.replace("/dashboard");
        }
      } catch {
        useAuthStore.getState().clearTokens();
        if (!isPublicPath) {
          router.replace("/login");
        }
      }
    };

    void verifyAuth();
  }, [pathname, router]);

  return <>{children}</>;
}