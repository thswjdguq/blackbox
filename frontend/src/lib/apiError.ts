export function apiError(error: unknown, fallback = "저장에 실패했습니다. 다시 시도해주세요."): string {
  const data = (error as { response?: { data?: { detail?: string; errors?: Record<string, string> } } })?.response?.data;
  if (data?.detail) return data.detail;
  if (data?.errors) return Object.values(data.errors).join(" / ");
  return fallback;
}
