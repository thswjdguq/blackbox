import { ChevronRight } from "lucide-react";

/** 진행 순서 안내. 실제 완료 상태는 서버 결과로 판단한다. */
export default function ProjectWorkflow() {
  const steps = ["제출물·마감 정하기", "업무 진행·자료 등록", "검토·수정 후 제출"];
  return <section className="mb-6 rounded-xl border border-blue-500/30 bg-blue-500/10 px-4 py-4" aria-label="프로젝트 진행 순서">
    <ol className="flex flex-wrap items-center gap-x-3 gap-y-3">{steps.map((label, i) => <li key={label} className="flex items-center gap-3 text-sm">
      {i > 0 && <ChevronRight size={14} aria-hidden="true" className="text-bb-text2" />}<span className="flex items-center gap-2"><span className="w-6 h-6 rounded-full bg-blue-500/10 text-blue-700 dark:text-blue-300 text-xs flex items-center justify-center">{i+1}</span>{label}</span>
    </li>)}</ol>
    <p className="mt-3 text-xs text-bb-text2">작업 중에도 제출물에서 중간 검토를 요청할 수 있습니다.</p>
  </section>;
}
