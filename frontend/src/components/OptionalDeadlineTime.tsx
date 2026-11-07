"use client";

export default function OptionalDeadlineTime({ value, onChange, name }: {
  value: string | null | undefined;
  onChange: (value: string | null) => void;
  name?: string;
}) {
  // Empty string opens the input without choosing a default time.
  const expanded = value != null;
  return <div className="mt-2 text-sm">
    {expanded ? <div className="flex flex-wrap items-center gap-2">
      <label className="flex items-center gap-2 text-bb-text">
        마감 시간
        <input aria-label="마감 시간" name={name} type="time" step={60} required value={value}
          onChange={e => onChange(e.target.value)}
          className="rounded-lg border border-bb-border bg-bb-bg px-3 py-2 text-bb-text" />
      </label>
      <span className="text-xs text-bb-text2">한국 시간</span>
      <button type="button" onClick={() => onChange(null)} className="text-xs text-bb-text2 underline">시간 삭제</button>
    </div> : <button type="button" onClick={() => onChange("")} className="text-sm text-bb-primary">+ 마감 시간 추가</button>}
    {!expanded && <p className="mt-1 text-xs text-bb-text2">시간 미지정</p>}
  </div>;
}
