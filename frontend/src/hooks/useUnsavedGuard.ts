"use client";

import { useEffect, useRef } from "react";

/** 링크를 눌렀을 때 실제로 이 화면을 떠나는지. 새 탭·내려받기·같은 주소·#이동은 떠나지 않는다 */
export function leavesPage(e: MouseEvent, here: { pathname: string; search: string }): boolean {
  if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return false;
  const a = e.target instanceof Element ? e.target.closest("a[href]") : null;
  if (!a || a.hasAttribute("download")) return false;
  const target = a.getAttribute("target");
  if (target && target !== "_self") return false;
  const raw = a.getAttribute("href") ?? "";
  if (raw.startsWith("#")) return false;
  try {
    const url = new URL(raw, window.location.href);
    // 같은 사이트의 /api/ 주소는 파일 내려받기라 화면을 떠나지 않는다
    if (url.origin === window.location.origin && url.pathname.startsWith("/api/")) return false;
    return !(url.origin === window.location.origin && url.pathname === here.pathname && url.search === here.search);
  } catch {
    return false;
  }
}

/**
 * 저장하지 않은 입력이 있는 동안 창 닫기·새로고침과 화면 안 링크 이동 전에 묻는다.
 * 회의록(#회의 상세)과 제출 기록(FinalSubmissionPanel)에 따로 있던 방식을 공용으로 뺀 것이다.
 * 버튼에서 router.push로 옮기는 곳과 브라우저 뒤로 가기는 막지 못한다.
 */
export function useUnsavedGuard(dirty: boolean, message = "저장하지 않은 내용이 있습니다. 이 화면을 떠날까요?") {
  const text = useRef(message);
  text.current = message;
  useEffect(() => {
    if (!dirty) return;
    const unload = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ""; };
    const click = (e: MouseEvent) => {
      if (!leavesPage(e, window.location)) return;
      if (!window.confirm(text.current)) { e.preventDefault(); e.stopPropagation(); }
    };
    window.addEventListener("beforeunload", unload);
    document.addEventListener("click", click, true);
    return () => { window.removeEventListener("beforeunload", unload); document.removeEventListener("click", click, true); };
  }, [dirty]);
}
