import type { Metadata } from "next";
import "./globals.css";
import AuthProvider from "@/components/AuthProvider";
import PageProgress from "@/components/PageProgress";

export const metadata: Metadata = {
  title: "Team Blackbox",
  description: "팀 프로젝트 기여도 자동 증빙 플랫폼",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <head>
        {/* 테마 초기화 — 깜빡임 방지 (blocking script) */}
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){try{
              var t=localStorage.getItem('theme')||'dark';
              document.documentElement.classList.toggle('dark',t==='dark');
            }catch(e){}})()`,
          }}
        />
      </head>
      <body className="bg-bb-bg text-bb-text">
        <PageProgress />
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
