import { ROLE_LABEL, type MemberRole } from "@/types/member";

/** 서버 역할 값(LEADER·MEMBER·OBSERVER)을 화면 이름으로. 표는 types/member.ts의 ROLE_LABEL 하나만 쓴다. 모르는 값은 그대로 보여 준다 */
export function roleLabel(role: string): string {
  // "constructor" 같은 객체 기본 속성 이름이 와도 그대로 보여 주도록 자기 키만 본다
  return Object.prototype.hasOwnProperty.call(ROLE_LABEL, role) ? ROLE_LABEL[role as MemberRole] : role;
}
