"use client";

import { useEffect, useId, useRef, useState } from "react";
import Link from "next/link";
import { Check, ChevronsUpDown, FolderKanban } from "lucide-react";
import api from "@/lib/api";

interface ProjectItem {
  id: string;
  name: string;
  courseName: string | null;
}

/**
 * 사이드바가 화면마다 다시 그려져도 목록이 깜빡이지 않게 마지막 결과를 들고 있다가
 * 바로 보여 주고 뒤에서 다시 불러온다 (useIntegrationStatus와 같은 방식).
 */
let cache: ProjectItem[] | null = null;

/** 다른 프로젝트로 옮길 때 같은 메뉴로 간다. 상세 화면(제출물·회의록 하나)은 목록으로 */
function targetPath(pathname: string, projectId: string): string {
  const m = pathname.match(/^\/projects\/[^/]+\/([^/]+)/);
  return m ? `/projects/${projectId}/${m[1]}` : `/projects/${projectId}`;
}

/** 사이드바 맨 위 프로젝트 전환 (C-50 1번, Flow의 왼쪽 프로젝트 목록 참고) */
export default function ProjectSwitcher({ currentProjectId, pathname, onNavigate }: {
  currentProjectId: string | null;
  pathname: string;
  /** 고른 뒤 작은 화면 메뉴를 닫는다 */
  onNavigate?: () => void;
}) {
  const [projects, setProjects] = useState<ProjectItem[] | null>(cache);
  const [failed, setFailed] = useState(false);
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const listId = useId();

  useEffect(() => {
    let alive = true;
    api.get<ProjectItem[]>("/projects")
      .then(({ data }) => { cache = data; if (alive) { setProjects(data); setFailed(false); } })
      .catch(() => { if (alive) setFailed(true); });
    return () => { alive = false; };
  }, [currentProjectId]);

  // 바깥을 누르거나 Escape면 닫고 Escape일 때는 버튼으로 초점을 돌려준다
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => { if (!rootRef.current?.contains(e.target as Node)) setOpen(false); };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") { e.stopPropagation(); setOpen(false); buttonRef.current?.focus(); }
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey, true);
    return () => { document.removeEventListener("mousedown", onDown); document.removeEventListener("keydown", onKey, true); };
  }, [open]);

  const current = projects?.find((p) => p.id === currentProjectId) ?? null;
  const label = current?.name ?? (currentProjectId ? "프로젝트" : "프로젝트 선택");

  return (
    <div ref={rootRef} className="relative px-3 pt-3">
      <button
        ref={buttonRef}
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="true"
        aria-expanded={open}
        aria-controls={listId}
        aria-label={`프로젝트 전환: ${label}`}
        className="w-full flex items-center gap-2.5 rounded-lg border border-bb-border bg-bb-surface px-3 py-2 text-left hover:bg-bb-surface2"
      >
        <FolderKanban size={15} className="shrink-0 text-bb-primary" />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium text-bb-text">{label}</span>
          {current?.courseName && <span className="block truncate text-[11px] text-bb-text2">{current.courseName}</span>}
        </span>
        <ChevronsUpDown size={14} className="shrink-0 text-bb-text2" />
      </button>

      {open && (
        <div id={listId} className="absolute left-3 right-3 top-full z-50 mt-1 max-h-72 overflow-y-auto rounded-xl border border-bb-border bg-bb-surface shadow-xl">
          {projects === null ? (
            <p className="px-3 py-3 text-xs text-bb-text2">{failed ? "프로젝트 목록을 불러오지 못했습니다." : "불러오는 중…"}</p>
          ) : projects.length === 0 ? (
            <p className="px-3 py-3 text-xs text-bb-text2">참여 중인 프로젝트가 없습니다.</p>
          ) : (
            <ul className="py-1">
              {projects.map((p) => {
                const selected = p.id === currentProjectId;
                return (
                  <li key={p.id}>
                    <Link
                      href={targetPath(pathname, p.id)}
                      aria-current={selected ? "page" : undefined}
                      onClick={() => { setOpen(false); onNavigate?.(); }}
                      className={`flex items-center gap-2 px-3 py-2 text-sm hover:bg-bb-surface2 ${selected ? "text-bb-primary font-medium" : "text-bb-text"}`}
                    >
                      <span className="min-w-0 flex-1 truncate">{p.name}</span>
                      {selected && <Check size={14} className="shrink-0" />}
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
          {failed && projects !== null && (
            <p className="border-t border-bb-border px-3 py-2 text-[11px] text-amber-500">목록을 새로 불러오지 못해 이전 목록입니다.</p>
          )}
          <Link
            href="/dashboard"
            onClick={() => { setOpen(false); onNavigate?.(); }}
            className="block border-t border-bb-border px-3 py-2 text-xs text-bb-text2 hover:text-bb-text"
          >
            모든 프로젝트 보기 · 새 프로젝트
          </Link>
        </div>
      )}
    </div>
  );
}
