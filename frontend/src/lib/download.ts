import api from "@/lib/api";

/**
 * 파일을 내려받는다(main C-34 #109).
 *
 * 주소를 새 창이나 링크로 열면 api 인터셉터를 거치지 않아, 로그인 30분이 지나 액세스 쿠키가 사라진 뒤에는
 * 갱신 없이 "HTTP ERROR 401" 창이 뜬다. 그래서 api로 받아 401이면 갱신하고 다시 받는다.
 * 저장 이름은 응답 머리 대신 화면이 아는 파일 이름을 쓴다(한글 이름이 깨지지 않게).
 */
export async function downloadFile(fileId: string, fileName: string): Promise<void> {
  const { data } = await api.get<Blob>(`/files/${fileId}/download`, { responseType: "blob" });
  const url = URL.createObjectURL(data);
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  // 클릭이 내려받기를 시작한 뒤에 푼다
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
