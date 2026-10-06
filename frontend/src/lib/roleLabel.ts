/** 서버 역할 값(LEADER·MEMBER·OBSERVER)을 화면 이름으로. 모르는 값은 그대로 보여 준다 */
export function roleLabel(role: string): string {
  switch (role) {
    case "LEADER":
      return "팀장";
    case "MEMBER":
      return "팀원";
    case "OBSERVER":
      return "관찰자";
    default:
      return role;
  }
}
