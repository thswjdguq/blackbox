"use client";

import { useState, useRef, useEffect } from "react";
import { DayPicker } from "react-day-picker";
import { format, parse, isValid } from "date-fns";
import { ko } from "date-fns/locale";
import { Calendar } from "lucide-react";
import "react-day-picker/dist/style.css";

interface DatePickerProps {
  value: string;           // "YYYY-MM-DD" or ""
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
}

/**
 * DatePicker — Calendar popover + 년/월/일 수동 입력 지원
 * - 달력 팝오버로 날짜 선택
 * - 직접 입력 시 년(4) → 월(2) → 일(2) 자동 포커스 이동
 */
export default function DatePicker({
  value,
  onChange,
  placeholder = "날짜 선택",
  className = "",
}: DatePickerProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const yearRef  = useRef<HTMLInputElement>(null);
  const monthRef = useRef<HTMLInputElement>(null);
  const dayRef   = useRef<HTMLInputElement>(null);

  // Parse value into parts
  const parsed = value ? parse(value, "yyyy-MM-dd", new Date()) : null;
  const selected = parsed && isValid(parsed) ? parsed : undefined;

  const [year,  setYear]  = useState(value ? value.slice(0, 4) : "");
  const [month, setMonth] = useState(value ? value.slice(5, 7) : "");
  const [day,   setDay]   = useState(value ? value.slice(8, 10) : "");

  // Sync parts → parent when all 3 are valid
  const commitDate = (y: string, m: string, d: string) => {
    if (y.length === 4 && m.length === 2 && d.length === 2) {
      const candidate = `${y}-${m}-${d}`;
      const parsed = parse(candidate, "yyyy-MM-dd", new Date());
      if (isValid(parsed)) onChange(candidate);
    } else if (!y && !m && !d) {
      onChange("");
    }
  };

  // Sync parent value → parts when changed externally
  useEffect(() => {
    setYear(value ? value.slice(0, 4) : "");
    setMonth(value ? value.slice(5, 7) : "");
    setDay(value ? value.slice(8, 10) : "");
  }, [value]);

  // Close on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const handleDaySelect = (day: Date | undefined) => {
    if (day && isValid(day)) {
      const formatted = format(day, "yyyy-MM-dd");
      onChange(formatted);
    } else {
      onChange("");
    }
    setOpen(false);
  };

  // Auto-tab handlers
  const handleYearChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value.replace(/\D/g, "").slice(0, 4);
    setYear(v);
    commitDate(v, month, day);
    if (v.length === 4) monthRef.current?.focus();
  };

  const handleMonthChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value.replace(/\D/g, "").slice(0, 2);
    setMonth(v);
    commitDate(year, v, day);
    if (v.length === 2) dayRef.current?.focus();
  };

  const handleDayChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value.replace(/\D/g, "").slice(0, 2);
    setDay(v);
    commitDate(year, month, v);
  };

  const inputBase =
    "bg-transparent text-center text-sm text-bb-text focus:outline-none placeholder-bb-text2";

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      {/* Input row */}
      <div
        className="flex items-center gap-1 w-full bg-bb-surface border border-bb-border rounded-lg px-3 py-2
                   focus-within:border-bb-primary transition-colors cursor-text"
        onClick={() => yearRef.current?.focus()}
      >
        <input
          ref={yearRef}
          value={year}
          onChange={handleYearChange}
          placeholder="YYYY"
          maxLength={4}
          className={`${inputBase} w-10`}
        />
        <span className="text-bb-text2 text-sm select-none">-</span>
        <input
          ref={monthRef}
          value={month}
          onChange={handleMonthChange}
          placeholder="MM"
          maxLength={2}
          className={`${inputBase} w-6`}
        />
        <span className="text-bb-text2 text-sm select-none">-</span>
        <input
          ref={dayRef}
          value={day}
          onChange={handleDayChange}
          placeholder="DD"
          maxLength={2}
          className={`${inputBase} w-6`}
        />
        {/* Calendar icon button */}
        <button
          type="button"
          onClick={(e) => { e.stopPropagation(); setOpen((v) => !v); }}
          className="ml-auto text-bb-text2 hover:text-bb-primary transition-colors"
        >
          <Calendar size={14} />
        </button>
      </div>

      {/* Calendar Popover */}
      {open && (
        <div
          className="absolute top-full left-0 mt-1 z-50
                     bg-bb-surface border border-bb-border rounded-xl shadow-xl p-3"
          onClick={(e) => e.stopPropagation()}
        >
          <DayPicker
            mode="single"
            selected={selected}
            onSelect={handleDaySelect}
            locale={ko}
            captionLayout="dropdown-buttons"
            fromYear={2020}
            toYear={2035}
            showOutsideDays
          />
        </div>
      )}
    </div>
  );
}
