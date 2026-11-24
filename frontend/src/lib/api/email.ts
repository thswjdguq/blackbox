import api from "@/lib/api";

/** K-41 0.5 8장. 본인의 업무 이메일 알림 설정(GET·PUT 같은 모양) */
export interface EmailSettings {
  /** 수신 의향. 켜도 주소 인증 전에는 업무 메일을 보내지 않는다 */
  enabled: boolean;
  /** 현재 계정 주소의 인증 여부(A가 보장) */
  emailVerified: boolean;
  /** enabled && !emailVerified */
  verificationRequired: boolean;
  /** 지금 확인 메일을 요청할 수 있는가(메일 기능 꺼짐 등이면 false) */
  verificationAvailable: boolean;
  /** 요청할 수 없을 때 사용자에게 보일 서버 문장. 가능하면 null */
  verificationUnavailableReason: string | null;
  updatedAt: string | null;
}

export const getEmailSettings = () => api.get<EmailSettings>("/notifications/email-settings");

export const putEmailSettings = (enabled: boolean) =>
  api.put<EmailSettings>("/notifications/email-settings", { enabled });

/** K-41 0.5 9장. 202 발송 요청(`{ message }` 서버 문장), 204 이미 인증, 429(+Retry-After)·503은 서버 detail */
export const requestEmailVerification = () => api.post<{ message?: string } | "">("/auth/email-verification/request");

/** 204 인증 완료, 400(잘못된·다른 계정 토큰)·410(만료)·409(주소 변경)은 서버 detail */
export const confirmEmailVerification = (token: string) =>
  api.post("/auth/email-verification/confirm", { token });
