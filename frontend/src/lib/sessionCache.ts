/**
 * 로그인한 사람에 따라 달라지는 화면 캐시(내 역할, 프로젝트 목록, 연동 상태)를 로그아웃 때 한꺼번에 비운다.
 *
 * 로그아웃은 새로고침 없이 /login으로 이동하므로 모듈에 들고 있던 값이 남는다. 같은 탭에서 다른 계정으로
 * 로그인하면 앞 계정의 역할로 버튼을 숨기거나, 앞 계정의 프로젝트 목록이 잠깐 보였다.
 * 캐시를 가진 모듈은 onSessionReset으로 비우는 함수를 등록한다.
 */
const resets = new Set<() => void>();

export function onSessionReset(reset: () => void): void {
  resets.add(reset);
}

export function resetSessionCaches(): void {
  resets.forEach((reset) => reset());
}
