"use client";

import { usePathname } from "next/navigation";

export default function SiteFooter() {
  const pathname = usePathname();
  const isPublicPage = pathname === "/login" || pathname === "/signup" || pathname === "/";

  return (
    <footer className={`border-t border-bb-border bg-bb-bg px-6 py-6 text-bb-text2 sm:px-10 ${isPublicPage ? "" : "md:ml-64"}`}>
      <div className="mx-auto flex max-w-7xl flex-col gap-3 sm:flex-row sm:items-center sm:justify-between sm:gap-6">
        <div>
          <p className="text-sm font-semibold text-bb-text">Team Blackbox</p>
          <p className="mt-1 break-keep text-xs leading-5">함께한 팀플, 과정부터 최종 제출까지.</p>
        </div>
        <p className="text-xs leading-5">© {new Date().getFullYear()} Team Blackbox</p>
      </div>
    </footer>
  );
}
