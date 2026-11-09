"use client";

import { useState, useRef, useEffect, useLayoutEffect } from "react";
import { createPortal } from "react-dom";
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
  required?: boolean;
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
  required = false,
}: DatePickerProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState({ top: 0, left: 0, maxHeight: 0 });
  const yearRef  = useRef<HTMLInputElement>(null);
  const monthRef = useRef<HTMLInputElement>(null);
  const dayRef   = useRef<HTMLInputElement>(null);

  // Parse value into parts
  const parsed = value ? parse(value, "yyyy-MM-dd", new Date()) : null;
  const selected = parsed && isValid(parsed) ? parsed : undefined;

  const [year,  setYear]  = useState(value ? value.slice(0, 4) : "");
  const [month, setMonth] = useState(value ? value.slice(5, 7) : "");
  const [day,   setDay]   = useState(value ? value.slice(8, 10) : "");

  useEffect(() => {
    const candidate = `${year}-${month}-${day}`;
    const date = parse(candidate, "yyyy-MM-dd", new Date());
    const empty = !year && !month && !day;
    const valid = /^\d{4}-\d{2}-\d{2}$/.test(candidate)
      && Number(year) >= 1 && isValid(date) && format(date, "yyyy-MM-dd") === candidate;
    yearRef.current?.setCustomValidity(empty && !required || valid ? "" : "올바른 날짜를 모두 입력해 주세요.");
  }, [year, month, day, required]);

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
      if (containerRef.current && !containerRef.current.contains(e.target as Node)
          && !popoverRef.current?.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  useLayoutEffect(() => {
    if (!open) return;
    const updatePosition = () => {
      const anchor = containerRef.current?.getBoundingClientRect();
      const popup = popoverRef.current;
      if (!anchor || !popup) return;
      const viewport = window.visualViewport;
      const topEdge = (viewport?.offsetTop ?? 0) + 8;
      const leftEdge = (viewport?.offsetLeft ?? 0) + 8;
      const bottomEdge = (viewport?.offsetTop ?? 0) + (viewport?.height ?? window.innerHeight) - 8;
      const rightEdge = (viewport?.offsetLeft ?? 0) + (viewport?.width ?? window.innerWidth) - 8;
      const below = Math.max(0, bottomEdge - anchor.bottom - 4);
      const above = Math.max(0, anchor.top - topEdge - 4);
      const height = popup.scrollHeight;
      const openAbove = below < height && above > below;
      const maxHeight = Math.max(1, openAbove ? above : below);
      const visibleHeight = Math.min(height, maxHeight);
      setPosition({
        top: Math.max(topEdge, Math.min(openAbove ? anchor.top - 4 - visibleHeight : anchor.bottom + 4, bottomEdge - visibleHeight)),
        left: Math.max(leftEdge, Math.min(anchor.left, rightEdge - popup.getBoundingClientRect().width)),
        maxHeight,
      });
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.stopPropagation(); setOpen(false); }
    };
    updatePosition();
    const observer = new ResizeObserver(updatePosition);
    if (popoverRef.current) observer.observe(popoverRef.current);
    window.addEventListener("resize", updatePosition);
    window.addEventListener("scroll", updatePosition, true);
    window.visualViewport?.addEventListener("resize", updatePosition);
    window.visualViewport?.addEventListener("scroll", updatePosition);
    document.addEventListener("keydown", onKeyDown, true);
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", updatePosition);
      window.removeEventListener("scroll", updatePosition, true);
      window.visualViewport?.removeEventListener("resize", updatePosition);
      window.visualViewport?.removeEventListener("scroll", updatePosition);
      document.removeEventListener("keydown", onKeyDown, true);
    };
  }, [open]);

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
        onClick={(e) => { if (e.target === e.currentTarget) yearRef.current?.focus(); }}
      >
        <input
          ref={yearRef}
          aria-label="연도"
          required={required}
          inputMode="numeric"
          value={year}
          onChange={handleYearChange}
          placeholder="YYYY"
          maxLength={4}
          className={`${inputBase} w-10`}
        />
        <span className="text-bb-text2 text-sm select-none">-</span>
        <input
          ref={monthRef}
          aria-label="월"
          required={required}
          inputMode="numeric"
          value={month}
          onChange={handleMonthChange}
          placeholder="MM"
          maxLength={2}
          className={`${inputBase} w-6`}
        />
        <span className="text-bb-text2 text-sm select-none">-</span>
        <input
          ref={dayRef}
          aria-label="일"
          required={required}
          inputMode="numeric"
          value={day}
          onChange={handleDayChange}
          placeholder="DD"
          maxLength={2}
          className={`${inputBase} w-6`}
        />
        {/* Calendar icon button */}
        <button
          type="button"
          aria-label="날짜 선택 달력"
          aria-expanded={open}
          onClick={(e) => { e.stopPropagation(); setOpen((v) => !v); }}
          className="ml-auto text-bb-text2 hover:text-bb-primary transition-colors"
        >
          <Calendar size={14} />
        </button>
      </div>

      {/* Calendar Popover */}
      {open && createPortal(
        <div
          ref={popoverRef}
          role="dialog"
          aria-label="날짜 선택"
          style={{ top: position.top, left: position.left, maxHeight: position.maxHeight || undefined,
            maxWidth: "calc(100vw - 16px)", visibility: position.maxHeight ? "visible" : "hidden" }}
          className="fixed z-[100] overflow-auto overscroll-contain
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
        </div>, document.body
      )}
    </div>
  );
}
