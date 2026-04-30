"use client";

import { useEffect, useRef, useState } from "react";

interface Props {
  value: string;
  onChange: (v: string) => void;
  withTime?: boolean;   // true → value/onChange 형식: "YYYY-MM-DDTHH:MM"
  className?: string;
  placeholder?: string;
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

  const monthRef = useRef<HTMLInputElement>(null);
  const dayRef   = useRef<HTMLInputElement>(null);
  const hourRef  = useRef<HTMLInputElement>(null);
  const minRef   = useRef<HTMLInputElement>(null);

  // 외부 value → 내부 state 파싱
  useEffect(() => {
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
    if (!dateFull) { if (!y && !mo && !d) onChange(""); return; }
    const dateVal = `${y}-${mo.padStart(2, "0")}-${d.padStart(2, "0")}`;
    if (withTime) {
      if (h.length >= 1 && mn.length >= 1) {
        onChange(`${dateVal}T${h.padStart(2, "0")}:${mn.padStart(2, "0")}`);
      }
    } else {
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
        onKeyDown={(e) => {
          if (e.key === "Backspace" && month === "") monthRef.current?.blur();
        }}
        className={`${base} w-10`}
      />
      {sep}
      {/* 일 */}
      <input
        ref={dayRef}
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
        className={`${base} w-10`}
      />

      {/* 시간 (withTime=true 일 때만) */}
      {withTime && (
        <>
          <span className="text-bb-text2 select-none ml-1">·</span>
          <input
            ref={hourRef}
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
            className={`${base} w-10`}
          />
          <span className="text-bb-text2 select-none">:</span>
          <input
            ref={minRef}
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
            className={`${base} w-10`}
          />
        </>
      )}
    </div>
  );
}
