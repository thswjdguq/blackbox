import { ArrowRight, Check, FileText, MessageSquare, Users } from "lucide-react";

export default function LoginIntroduction() {
  return <section aria-labelledby="login-introduction" className="min-w-0 lg:pr-12">
    <p className="mb-5 text-sm font-semibold tracking-wide text-blue-700 dark:text-blue-300">함께 만드는 대학생 팀 프로젝트</p>
    <h1 id="login-introduction" className="break-keep text-3xl font-bold leading-tight tracking-tight text-bb-text sm:text-4xl lg:text-5xl lg:leading-[1.25]">
      <span className="sm:hidden">함께한 팀플,<br />기록으로 남기세요.</span>
      <span className="hidden sm:inline">팀플의 시작부터 제출까지,<br /> 함께한 과정이 기록으로.</span>
    </h1>
    <p className="mt-5 max-w-lg break-keep text-sm leading-7 text-bb-text2 sm:text-base">업무를 나누고, 결과를 공유하고, 피드백부터 최종 제출 기록까지 한곳에서 이어가세요.</p>
    <div className="mt-9 hidden lg:block">
      <div className="rounded-2xl border border-blue-200/70 bg-bb-surface p-5 shadow-sm dark:border-blue-800/50">
        <div className="flex items-center justify-between border-b border-bb-border pb-4">
          <div><p className="text-xs text-bb-text2">프로젝트 예시</p><h2 className="mt-1 text-sm font-semibold">우리 동네 상권 분석</h2></div>
          <span className="flex items-center gap-1.5 rounded-full bg-blue-500/10 px-3 py-1 text-xs text-blue-700 dark:text-blue-300"><Users size={13} />4인 팀</span>
        </div>
        <div className="mt-4 grid grid-cols-[1fr_auto] items-center gap-3 text-sm">
          <div><p className="font-medium">현장 조사 결과 정리</p><p className="mt-1 text-xs text-bb-text2">담당자 민지 · 최종 보고서에 연결</p></div>
          <span className="rounded-full bg-amber-500/10 px-2.5 py-1 text-xs text-amber-800 dark:text-amber-300">진행 중</span>
        </div>
        <div className="mt-4 flex items-center gap-3 rounded-xl bg-bb-bg p-3 text-sm"><FileText size={18} className="text-blue-500" /><div><p className="font-medium">상권 조사 결과.pdf</p><p className="text-xs text-bb-text2">업무 결과 등록 · 검토를 기다려요</p></div></div>
        <div className="mt-3 flex items-start gap-2 rounded-xl border border-bb-border p-3"><MessageSquare size={15} className="mt-0.5 shrink-0 text-blue-500" /><p className="text-xs leading-5 text-bb-text2"><span className="font-semibold text-bb-text">현우</span> · 비교표 확인했어요. 출처를 추가하면 최종 보고서에 넣을 수 있겠어요.</p></div>
        <div className="mt-4 flex items-center gap-2 text-xs text-bb-text2"><Check size={14} className="text-blue-500" />결과 파일과 피드백이 업무에 함께 남아요</div>
      </div>
      <ol className="mt-6 flex items-center justify-between gap-2 text-xs font-medium text-bb-text2" aria-label="팀플 진행 순서">
        {['업무 분담', '결과·피드백', '최종본·활동 기록'].map((step, i) => <li key={step} className="flex items-center gap-2"><span className="text-blue-600 dark:text-blue-300">0{i + 1}</span>{step}{i < 2 && <ArrowRight size={13} className="ml-2 text-bb-text2" aria-hidden="true" />}</li>)}
      </ol>
    </div>
  </section>;
}
