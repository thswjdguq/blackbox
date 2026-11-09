/** 프로젝트 메뉴와 현재 위치 표시에 같은 이름·경로를 사용한다. */
export const projectNavigation = [
  { key: "home", label: "프로젝트 홈", suffix: "", primary: true },
  { key: "deliverables", label: "최종 제출물", suffix: "/deliverables", primary: true },
  { key: "board", label: "팀 업무", suffix: "/board", primary: true },
  { key: "feedback", label: "피드백", suffix: "/feedback", primary: true },
  { key: "meetings", label: "회의", suffix: "/meetings", primary: true },
  { key: "vault", label: "파일 모아보기", suffix: "/vault", primary: false },
  { key: "analytics", label: "활동·기여 기록", suffix: "/analytics", primary: false },
  { key: "settings", label: "팀 설정", suffix: "/settings", primary: false },
] as const;

export function projectSection(pathname: string, projectId: string) {
  const base = `/projects/${projectId}`;
  if (pathname === base + "/schedule") return projectNavigation.find(item => item.key === "meetings");
  return projectNavigation.find(item => item.suffix
    ? pathname === base + item.suffix || pathname.startsWith(base + item.suffix + "/")
    : pathname === base);
}
