export function apiError(error: unknown, fallback = "저장에 실패했습니다. 다시 시도해주세요."): string {
  const data = (error as { response?: { data?: { detail?: string; errors?: Record<string, string> } } })?.response?.data;
  if (data?.detail) return data.detail;
  if (data?.errors) return Object.values(data.errors).join(" / ");
  return fallback;
}

/**
 * responseType: "blob" 요청용. 오류 응답의 ProblemDetail JSON 도 Blob 으로 오므로 읽어서 해석한다.
 * JSON 이 아니거나 읽지 못하면 fallback.
 */
export async function blobApiError(error: unknown, fallback: string): Promise<string> {
  const data = (error as { response?: { data?: unknown } })?.response?.data;
  if (data instanceof Blob) {
    try {
      return apiError({ response: { data: JSON.parse(await data.text()) } }, fallback);
    } catch {
      return fallback;
    }
  }
  return apiError(error, fallback);
}
