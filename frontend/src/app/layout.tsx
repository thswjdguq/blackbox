import type { Metadata } from "next";
import "./globals.css";

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
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
