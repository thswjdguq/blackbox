"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import api from "@/lib/api";
import { useAuthStore } from "@/lib/store/authStore";
import { afterLogin, loginHref } from "@/lib/loginRedirect";

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

        // 로그인된 채 로그인·가입 화면에 오면 next(원래 가려던 주소)로, 없으면 대시보드로
        if (isPublicPath) {
          router.replace(afterLogin());
        }
      } catch {
        useAuthStore.getState().clearTokens();
        // 원래 주소를 next로 들고 가서 로그인 뒤 돌아온다(이메일 알림 링크 등)
        if (!isPublicPath) {
          router.replace(loginHref());
        }
      }
    };

    void verifyAuth();
  }, [pathname, router]);

  return <>{children}</>;
}