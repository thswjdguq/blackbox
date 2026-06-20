# Task 73 — fix: SmartDateInput 일/시/분 한 자리 입력 갇힘 버그

## 1. 한 줄 요약
`SmartDateInput`에서 한 자리 숫자 입력 시 패딩된 값("01")이 부모 `value`를 거쳐 즉시 역류해 입력칸을 덮어써 두 자리를 못 넣는 버그를, "입력 중 raw 유지 + blur 시 보정"으로 고친다.

## 2. 변경 범위 (HARD LIMIT)

> 실행자는 이 섹션 밖의 파일·동작을 절대 건드리지 않는다.

- **파일 (이외 금지):**
  - `frontend/src/components/SmartDateInput.tsx`
- **변경 지점 (이 4곳만):**
  1. `lastEmitted` ref 1개 추가
  2. `emit` 함수: `onChange` 호출 직전에 `lastEmitted.current`에 보낼 값 기록
  3. 파싱용 `useEffect` 맨 위에 self-echo 가드 1줄 추가
  4. 월·일·시·분 4개 `<input>`에 `onBlur` 한 자리 패딩 추가
- **예상 변경 라인 수:** ~15줄
- **예상 변경 파일 수:** 1개

## 3. 절대 건드리지 말 것 (Out of Scope)

- `DatePicker.tsx` — **다른 컴포넌트이고 이 버그 없음**(`commitDate`가 `d.length===2`일 때만 emit). 건드리지 말 것.
- `SmartDateInput`의 자동 포커스 이동(`monthRef`/`dayRef`/`hourRef`/`minRef.focus()`) 로직 — 그대로 유지.
- `maxLength`, `inputMode`, className, placeholder, 마크업 구조 — 변경 금지.
- emit의 패딩(`padStart`) 자체 — 유지(부모에 보내는 값은 계속 2자리 정규형이어야 함).
- 시간(withTime) 분기 구조 — 유지.

## 4. 컨텍스트 / 의도

**증상:** 일(日) 칸에 한 자리(예: `1`)를 치면 `01`로 바뀌고 두 번째 자리(`5`)를 못 쳐서 `15`를 입력할 수 없다. 월은 멀쩡(일 칸이 비어있을 땐 emit이 안 불려 역류 없음).

**원인(역류):** 일 칸에 `1` 입력 → `emit`이 `d.padStart(2,"0")`로 `"...-01"`을 만들어 `onChange` → 부모 `value`가 `"...-01"`로 바뀜 → 파싱 `useEffect`가 정규식 매칭해 `setDay("01")`로 **입력칸을 덮어씀** → maxLength=2라 더는 못 침.

**해결:** 부모로 보내는 값은 계속 패딩된 정규형으로 두되, **내가 방금 emit한 값이 그대로 되돌아온 경우(self-echo)에는 `useEffect`가 파싱·덮어쓰기를 건너뛴다.** 그러면 입력 중에는 raw(`1`→`15`)가 유지된다. 한 자리 자동 보정은 **칸을 떠날 때(blur)** 수행해 두 동작을 공존시킨다.

- 관련 INV: 해당 없음
- 관련 Drift ID: 해당 없음 (테스트 중 발견된 기존 UX 버그, 리팩토링과 무관)
- 관련 PRINCIPLES 섹션: §4(동작 변경은 의도된 fix로 분리 — 본 Task는 `fix:`)

## 4-1. 의존 관계

- **선행 Task:** 없음.
- **후행 Task:** 없음. (`DatePicker`의 한 자리 미보정은 별개 — 본 Task 범위 밖.)
- **API surface 변경 여부:** No (프론트 컴포넌트 내부 동작만). 문서 갱신 면제.

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §6 (실행자 협업 규칙):

- 추측 금지. 변경 범위 작업지시서 외 확장 금지.
- 아래 §6에 박힌 코드 그대로 적용. 마크업·focus 로직은 손대지 않는다.

## 6. 작업 절차 (정확한 코드)

### (1) `lastEmitted` ref + `emit` 수정
현재 `emit`(현 49~60행 부근)을 아래로 교체한다. `useRef`는 이미 import돼 있다(현 3행).

**Before:**
```tsx
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
```

**After:**
```tsx
  const lastEmitted = useRef("");

  const emit = (y: string, mo: string, d: string, h: string, mn: string) => {
    const dateFull = y.length === 4 && mo.length >= 1 && d.length >= 1;
    if (!dateFull) {
      if (!y && !mo && !d) { lastEmitted.current = ""; onChange(""); }
      return;
    }
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
```

### (2) 파싱 `useEffect`에 self-echo 가드 추가
파싱 `useEffect`(현 31행 `useEffect(() => {` 다음 줄, `if (!value) {` 바로 위)에 **한 줄** 추가:

```tsx
  useEffect(() => {
    if (value === lastEmitted.current) return;   // ← 추가: 내가 보낸 echo면 덮어쓰지 않음
    if (!value) {
      // ...기존 본문 그대로...
```
나머지 useEffect 본문은 변경하지 않는다.

### (3) 월·일·시·분 input에 blur 한 자리 보정
각 `<input>`에 `onBlur`를 추가한다(월=`month`/`setMonth`, 일=`day`/`setDay`, 시=`hour`/`setHour`, 분=`min`/`setMin`). 예(월):
```tsx
        onBlur={() => { if (month.length === 1) setMonth(month.padStart(2, "0")); }}
```
- 월 input(ref `monthRef`)에 위 줄 추가.
- 일 input(ref `dayRef`): `onBlur={() => { if (day.length === 1) setDay(day.padStart(2, "0")); }}`
- 시 input(ref `hourRef`): `onBlur={() => { if (hour.length === 1) setHour(hour.padStart(2, "0")); }}`
- 분 input(ref `minRef`): `onBlur={() => { if (min.length === 1) setMin(min.padStart(2, "0")); }}`
- 년(year, maxLength 4) input에는 **추가하지 않는다.**

### (4) 타입체크
`cd frontend && npm run type-check` 통과 확인.

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — §6에 before→after·추가 줄이 코드 단위로 박혀 있고 1 파일·~15줄. focus/마크업 무변경.
- [ ] **Required**

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `lastEmitted` ref 추가, `emit`이 `onChange` 직전 `lastEmitted.current` 기록(빈값 클리어 포함).
- [ ] 파싱 `useEffect` 첫 줄에 `if (value === lastEmitted.current) return;` 추가, 나머지 본문 0줄 변경.
- [ ] 월·일·시·분 4개 input에 `onBlur` 한 자리 패딩 추가. year에는 미추가.
- [ ] 자동 포커스 이동·`padStart` emit·마크업·className 무변경. `DatePicker.tsx` 무변경.
- [ ] `npm run type-check` 통과.
- [ ] (논리 검증) 일 칸에 `1`→`5` 연속 입력 시 `15`가 유지되고, 한 자리만 친 뒤 blur하면 `01`로 보정됨.

## 9. PR 정보

- **Branch:** `refactor/73-fix-smartdateinput-date-padding`
- **PR base:** `refactor/main` (절대 `main` 아님)
- **PR title:** `[refactor-73] fix: SmartDateInput 한 자리 입력 갇힘 버그 (입력 중 raw 유지 + blur 보정)`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 73](../docs/refactor/tasks/73-fix-smartdateinput-date-padding.md)

  ## 변경 요약
  - 한 자리 입력 시 패딩값이 부모 value 역류로 입력칸을 덮어써 두 자리를 못 넣던 버그 수정
  - self-echo 가드(useEffect)로 입력 중 raw 유지 + blur 시 한 자리 보정 공존
  - SmartDateInput.tsx 1파일. DatePicker는 무관(버그 없음)

  ## HARD LIMIT 준수
  - 파일: SmartDateInput.tsx (1개)
  - 라인: ~15

  ## 검수 체크리스트 결과
  - [x] type-check 통과
  - [x] 일 1→5 연속입력 15 유지 / 한자리 blur 보정 확인
  ```
