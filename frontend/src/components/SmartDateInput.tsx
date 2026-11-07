"use client";

import { useEffect, useRef, useState } from "react";

interface Props {
  value: string;
  onChange: (v: string) => void;
  withTime?: boolean;   // true → value/onChange 형식: "YYYY-MM-DDTHH:MM"
  className?: string;
  placeholder?: string;
}

export function dateInputError(y: string, mo: string, d: string, h = "", mn = "", withTime = false): string {
  if (![y, mo, d, ...(withTime ? [h, mn] : [])].some(Boolean)) return "";
  if (y.length !== 4 || Number(y) < 1) return "연도를 네 자리로 입력해 주세요. 예: 2026";
  if (!mo || !d) return "월과 일을 모두 입력해 주세요.";
  const month = Number(mo), day = Number(d), year = Number(y);
  if (month < 1 || month > 12) return "월은 1~12 사이로 입력해 주세요.";
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const lastDay = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1];
  if (day < 1 || day > lastDay) return `${month}월은 1~${lastDay}일 사이로 입력해 주세요.`;
  if (withTime && (!h || !mn || Number(h) > 23 || Number(mn) > 59)) return "시간은 0~23시, 분은 0~59분으로 입력해 주세요.";
  return "";
}

export default function SmartDateInput({
  value,
  onChange,
  withTime = false,
  className = "",
}: Props) {
  const [year,  setYear]  = useState("");
  const [month, setMonth] = useState("");
  const [day,   setDay]   = useState("");
  const [hour,  setHour]  = useState("");
  const [min,   setMin]   = useState("");

  const yearRef = useRef<HTMLInputElement>(null);
  const monthRef = useRef<HTMLInputElement>(null);
  const dayRef   = useRef<HTMLInputElement>(null);
  const hourRef  = useRef<HTMLInputElement>(null);
  const minRef   = useRef<HTMLInputElement>(null);
  const lastEmitted = useRef("");
  const error = dateInputError(year, month, day, hour, min, withTime);
  useEffect(() => { yearRef.current?.setCustomValidity(error); }, [error]);

  // 외부 value → 내부 state 파싱
  useEffect(() => {
    if (value === lastEmitted.current) return;   // 내가 보낸 echo면 덮어쓰지 않음
    if (!value) {
      setYear(""); setMonth(""); setDay(""); setHour(""); setMin("");
      return;
    }
    // YYYY-MM-DDTHH:MM 또는 YYYY-MM-DD
    const dateStr = value.split("T")[0];
    const timeStr = value.includes("T") ? value.split("T")[1]?.slice(0, 5) : "";
    if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
      const [y, m, d] = dateStr.split("-");
      setYear(y); setMonth(m); setDay(d);
    }
    if (withTime && timeStr && /^\d{2}:\d{2}$/.test(timeStr)) {
      const [h, mn] = timeStr.split(":");
      setHour(h); setMin(mn);
    }
  }, [value, withTime]);

  const emit = (y: string, mo: string, d: string, h: string, mn: string) => {
    const dateFull = y.length === 4 && mo.length >= 1 && d.length >= 1;
    if (!dateFull) {
      if (!y && !mo && !d) { lastEmitted.current = ""; onChange(""); }
      return;
    }
    if (dateInputError(y, mo, d, h, mn, withTime)) return;
    const dateVal = `${y}-${mo.padStart(2, "0")}-${d.padStart(2, "0")}`;
    if (withTime) {
      if (h.length >= 1 && mn.length >= 1) {
        const out = `${dateVal}T${h.padStart(2, "0")}:${mn.padStart(2, "0")}`;
        lastEmitted.current = out;
        onChange(out);
      }
    } else {
      lastEmitted.current = dateVal;
      onChange(dateVal);
    }
  };

  const base =
    "bg-bb-bg border border-bb-border rounded text-sm text-bb-text placeholder-slate-500 " +
    "text-center focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-blue-500/30 transition-all px-1 py-2";

  const sep = <span className="text-bb-text2 select-none">-</span>;

  return (
    <div className={`flex items-center gap-1 flex-wrap ${className}`}>
      {/* 년도 */}
      <input
        ref={yearRef}
        aria-label="연도"
        aria-invalid={!!error}
        type="text"
        inputMode="numeric"
        maxLength={4}
        placeholder="YYYY"
        value={year}
        onChange={(e) => {
          const v = e.target.value.replace(/\D/g, "").slice(0, 4);
          setYear(v);
          if (v.length === 4) monthRef.current?.focus();
          emit(v, month, day, hour, min);
        }}
        className={`${base} w-14`}
      />
      {sep}
      {/* 월 */}
      <input
        ref={monthRef}
        aria-label="월"
        type="text"
        inputMode="numeric"
        maxLength={2}
        placeholder="MM"
        value={month}
        onChange={(e) => {
          const v = e.target.value.replace(/\D/g, "").slice(0, 2);
          setMonth(v);
          if (v.length === 2) dayRef.current?.focus();
          emit(year, v, day, hour, min);
        }}
        onBlur={() => setMonth((m) => (m.length === 1 ? m.padStart(2, "0") : m))}
        onKeyDown={(e) => {
          if (e.key === "Backspace" && month === "") monthRef.current?.blur();
        }}
        className={`${base} w-10`}
      />
      {sep}
      {/* 일 */}
      <input
        ref={dayRef}
        aria-label="일"
        type="text"
        inputMode="numeric"
        maxLength={2}
        placeholder="DD"
        value={day}
        onChange={(e) => {
          const v = e.target.value.replace(/\D/g, "").slice(0, 2);
          setDay(v);
          if (withTime && v.length === 2) hourRef.current?.focus();
          emit(year, month, v, hour, min);
        }}
        onBlur={() => setDay((d) => (d.length === 1 ? d.padStart(2, "0") : d))}
        className={`${base} w-10`}
      />

      {/* 시간 (withTime=true 일 때만) */}
      {withTime && (
        <>
          <span className="text-bb-text2 select-none ml-1">·</span>
          <input
            ref={hourRef}
            aria-label="시"
            type="text"
            inputMode="numeric"
            maxLength={2}
            placeholder="HH"
            value={hour}
            onChange={(e) => {
              const v = e.target.value.replace(/\D/g, "").slice(0, 2);
              setHour(v);
              if (v.length === 2) minRef.current?.focus();
              emit(year, month, day, v, min);
            }}
            onBlur={() => setHour((h) => (h.length === 1 ? h.padStart(2, "0") : h))}
            className={`${base} w-10`}
          />
          <span className="text-bb-text2 select-none">:</span>
          <input
            ref={minRef}
            aria-label="분"
            type="text"
            inputMode="numeric"
            maxLength={2}
            placeholder="MM"
            value={min}
            onChange={(e) => {
              const v = e.target.value.replace(/\D/g, "").slice(0, 2);
              setMin(v);
              emit(year, month, day, hour, v);
            }}
            onBlur={() => setMin((mn) => (mn.length === 1 ? mn.padStart(2, "0") : mn))}
            className={`${base} w-10`}
          />
        </>
      )}
      {error && <p role="alert" className="w-full text-xs text-red-600 dark:text-red-400">{error}</p>}
    </div>
  );
}
