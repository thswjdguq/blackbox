"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronRight } from "lucide-react";
import { projectSection } from "@/lib/projectNavigation";

export default function ProjectNav({ projectId }: { projectId: string }) {
  const pathname = usePathname();
  const base = `/projects/${projectId}`;
  const section = projectSection(pathname, projectId);
  const scheduling = pathname === base + "/schedule";
  const detail = section?.suffix && pathname !== base + section.suffix;
  const label = section?.label ?? (pathname.endsWith("/schedule") ? "일정 조율" : "프로젝트");
  return (
    <nav aria-label="현재 위치" className="flex flex-wrap items-center gap-2 mb-5 text-xs text-bb-text2">
      <Link href="/dashboard" className="hover:text-bb-primary">내 프로젝트</Link><ChevronRight size={12} aria-hidden="true" />
      {pathname === base ? <span aria-current="page">프로젝트 홈</span> : <>
        <Link href={base} className="hover:text-bb-primary">프로젝트 홈</Link><ChevronRight size={12} aria-hidden="true" />
        {detail && section ? <><Link href={base + section.suffix} className="hover:text-bb-primary">{label}</Link><ChevronRight size={12} aria-hidden="true" /><span aria-current="page">{scheduling ? "회의 시간 정하기" : section.key === "meetings" ? "회의 상세" : "최종 제출물 상세"}</span></> : <span aria-current="page">{label}</span>}
      </>}
    </nav>
  );
}
