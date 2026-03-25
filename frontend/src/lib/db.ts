/**
 * DB Client Placeholder
 *
 * DB가 확정되면 아래 주석 중 해당 항목의 주석을 해제하고
 * `db` export를 실제 클라이언트로 교체하세요.
 *
 * 환경변수:
 *   NEXT_PUBLIC_DB_URL  — DB 엔드포인트 또는 연결 URL
 *   NEXT_PUBLIC_DB_KEY  — API 키 또는 공개 접근 토큰
 *
 * ── 후보 클라이언트 ────────────────────────────────────────
 *
 * [Supabase]
 *   import { createClient } from "@supabase/supabase-js";
 *   export const db = createClient(DB_URL, DB_KEY);
 *
 * [Prisma]
 *   import { PrismaClient } from "@prisma/client";
 *   export const db = new PrismaClient();
 *
 * [Drizzle + Postgres]
 *   import { drizzle } from "drizzle-orm/postgres-js";
 *   import postgres from "postgres";
 *   export const db = drizzle(postgres(DB_URL));
 *
 * [Firebase Firestore]
 *   import { initializeApp } from "firebase/app";
 *   import { getFirestore } from "firebase/firestore";
 *   export const db = getFirestore(initializeApp({ ... }));
 *
 * ──────────────────────────────────────────────────────────
 */

const DB_URL = process.env.NEXT_PUBLIC_DB_URL;
const DB_KEY = process.env.NEXT_PUBLIC_DB_KEY;

if (!DB_URL || !DB_KEY) {
  console.warn("[db] NEXT_PUBLIC_DB_URL 또는 NEXT_PUBLIC_DB_KEY가 설정되지 않았습니다.");
}

// TODO: DB 확정 후 실제 클라이언트로 교체
export const db = {
  url: DB_URL,
  key: DB_KEY,
} as const;
