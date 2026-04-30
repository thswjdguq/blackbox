"use client";

import { useEffect, useRef, useState } from "react";

interface Props {
  value: string;          // YYYY-MM-DD
  onChange: (v: string) => void;
  className?: string;
  placeholder?: string;
}

export default function SmartDateInput({ value, onChange, className = "", placeholder }: Props) {
  const [year,  setYear]  = useState("");
  const [month, setMonth] = useState("");
  const [day,   setDay]   = useState("");

  const monthRef = useRef<HTMLInputElement>(null);
  const dayRef   = useRef<HTMLInputElement>(null);

  // value → 파싱
  useEffect(() => {
    if (value && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
      const [y, m, d] = value.split("-");
      setYear(y); setMonth(m); setDay(d);
    } else if (!value) {
      setYear(""); setMonth(""); setDay("");
    }
  }, [value]);

  const emit = (y: string, m: string, d: string) => {
    if (y.length === 4 && m.length >= 1 && d.length >= 1) {
      onChange(`${y}-${m.padStart(2, "0")}-${d.padStart(2, "0")}`);
    } else if (!y && !m && !d) {
      onChange("");
    }
  };

  const base =
    "bg-bb-bg border border-bb-border rounded text-sm text-bb-text placeholder-slate-500 " +
    "text-center focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-blue-500/30 transition-all px-1 py-2";

  return (
    <div className={`flex items-center gap-1 ${className}`}>
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
          emit(v, month, day);
        }}
        className={`${base} w-14`}
      />
      <span className="text-bb-text2 select-none">-</span>
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
          emit(year, v, day);
        }}
        className={`${base} w-10`}
      />
      <span className="text-bb-text2 select-none">-</span>
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
          emit(year, month, v);
        }}
        className={`${base} w-10`}
      />
    </div>
  );
}
