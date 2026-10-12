const fs=require('fs');
const root='C:/blackbox/teamplay-ui/';
const file=root+'frontend/src/app/projects/[projectId]/meetings/[meetingId]/page.tsx';
let s=fs.readFileSync(file,'utf8').replace(/\r\n/g,'\n');
s=s.replace('import Sidebar from', 'import MinutesEditor from "@/components/meeting/MinutesEditor";\nimport { useMyRole } from "@/hooks/useMyRole";\nimport Link from "next/link";\nimport Sidebar from');
s=s.replace('  // 회의록 편집','  const role = useMyRole(projectId);\n  const canWrite = role === "LEADER" || role === "MEMBER";\n\n  // 회의록 편집');
s=s.replace('  // 코드 재생성',`  const dirty = meeting !== null && (editNotes !== (meeting.notes ?? "") || editDecisions !== (meeting.decisions ?? ""));
  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ""; };
    const followLink = (e: MouseEvent) => {
      const target = e.target instanceof Element ? e.target.closest("a[href]") : null;
      if (target && !window.confirm("저장하지 않은 회의록이 있습니다. 이동할까요?")) { e.preventDefault(); e.stopPropagation(); }
    };
    window.addEventListener("beforeunload", warn);
    document.addEventListener("click", followLink, true);
    return () => { window.removeEventListener("beforeunload", warn); document.removeEventListener("click", followLink, true); };
  }, [dirty]);

  // 코드 재생성`);
s=s.replace(/    \/\/ 빈 문자열은 undefined[\s\S]*?    const payload: UpdateMeetingPayload = \{[\s\S]*?    \};/,`    if (!canWrite) throw new Error("팀장·팀원만 회의록을 수정할 수 있습니다.");
    // Detect an already saved edit before overwriting it. This is not an atomic lock.
    const { data: latest } = await api.get<Meeting>(\`/projects/\${projectId}/meetings/\${meetingId}\`);
    if ((latest.notes ?? "") !== (meeting?.notes ?? "") || (latest.decisions ?? "") !== (meeting?.decisions ?? "")) {
      throw new Error("다른 팀원이 회의록을 수정했습니다. 작성 내용을 복사한 뒤 새로고침하여 변경 내용을 확인하세요.");
    }
    // Empty strings explicitly clear the server field; undefined would keep old content.
    const payload: UpdateMeetingPayload = { notes: editNotes, decisions: editDecisions };`);
s=s.replace('setError(data?.detail ?? data?.message ?? "저장에 실패했습니다.");','setError(data?.detail ?? data?.message ?? (err instanceof Error ? err.message : "저장에 실패했습니다."));');
// Optional external exports are removed from this internal-first screen. Existing stored links remain on the server.
s=s.replace(/  \/\/ Notion 내보내기[\s\S]*?(?=  \/\/ AI 액션아이템 추출)/,'');
s=s.replace('      if (meetRes.data.notionPageId) setNotionUrl(meetRes.data.notionPageId);\n','');
s=s.replace(/  \/\/ ── Notion 캘린더 동기화[\s\S]*?(?=  \/\/ ──)/,'');
s=s.replace(/  \/\/ ── Notion 내보내기[\s\S]*?(?=  \/\/ ──)/,'');
s=s.replace(/\{\/\* Notion 캘린더에 추가 버튼 \*\/\}[\s\S]*?<\/button>/,'');
s=s.replace(/\{\/\* Notion 캘린더 동기화 결과 \*\/\}[\s\S]*?\n            \)\}/,'');
s=s.replace(/\{\/\* Notion 내보내기 성공 링크 \*\/\}[\s\S]*?\n              \)\}/,'');
s=s.replace(/<button\s+onClick=\{handleNotionExport\}[\s\S]*?<\/button>/,'');
s=s.replace(/<textarea\s+value=\{editNotes\}[\s\S]*?\/>/,'<MinutesEditor label="회의 내용" value={editNotes} onChange={setEditNotes} readOnly={!canWrite || saving || summarizing || extracting} />');
s=s.replace(/<textarea\s+value=\{editDecisions\}[\s\S]*?\/>/,'<MinutesEditor label="결정사항" value={editDecisions} onChange={setEditDecisions} readOnly={!canWrite || saving || summarizing || extracting} />');
s=s.replace('<h2 className="text-lg font-semibold text-bb-text mb-4">회의록</h2>',`<div className="flex flex-wrap items-center justify-between gap-3 mb-4">
              <div><h2 className="text-lg font-semibold text-bb-text">우리 팀 회의록</h2><p className="text-xs text-bb-text2 mt-1">이 프로젝트 안에서 작성하고 공유합니다. 노션 연결은 필요하지 않습니다.</p></div>
              {canWrite && !editNotes.trim() && <button type="button" onClick={() => setEditNotes("## 회의 안건\\n\\n- \\n\\n## 논의 내용\\n\\n\\n## 다음 할 일\\n\\n- [ ] ")} className="text-xs px-3 py-2 border border-bb-border rounded-lg">회의록 양식 넣기</button>}
            </div>
            <p role="status" className="mb-3 text-xs text-bb-text2">{!canWrite ? "읽기 전용 · 작성은 팀장과 팀원에게 허용됩니다." : saving ? "저장 중…" : dirty ? "저장하지 않은 변경사항이 있습니다." : "저장된 회의록입니다."}</p>`);
s=s.replace('disabled={saving}', 'disabled={saving || !canWrite || summarizing || extracting}').replace('disabled={summarizing}', 'disabled={summarizing || saving || !canWrite}').replace('disabled={extracting ||', 'disabled={!canWrite || saving || extracting ||');
s=s.replace('onClick={() => router.push(`/projects/${projectId}/meetings`)}','onClick={() => { if (!dirty || window.confirm("저장하지 않은 회의록이 있습니다. 이동할까요?")) router.push(`/projects/${projectId}/meetings`); }}');
s=s.replace('className="max-w-3xl"','className="max-w-5xl mx-auto"');
s=s.replace('      setShowActionForm(false);','      setShowActionForm(false);\n      showToast("회의에서 정한 할 일을 업무에 등록했습니다.");');
s=s.replace('{/* 회의록 편집 */}',`<div className="mb-5 flex items-center justify-between gap-3 rounded-lg bg-bb-surface2 p-4 text-sm">
            <span>회의 결정사항을 실제 할 일로 연결하세요.</span>
            <Link href={\`/projects/\${projectId}/board\`} className="text-bb-primary font-medium">업무 보러 가기 →</Link>
          </div>
          {/* 회의록 편집 */}`);
fs.writeFileSync(file,s);
const css=root+'frontend/src/app/globals.css';
fs.appendFileSync(css,`\n/* Rich minutes remain readable without exposing raw Markdown syntax. */
.minutes-document { min-height: 220px; padding: 24px; font-size: 15px; line-height: 1.85; overflow-wrap: anywhere; outline: none; }
.minutes-document > * + * { margin-top: .75em; }
.minutes-document h1 { font-size: 1.7em; font-weight: 700; }
.minutes-document h2 { font-size: 1.35em; font-weight: 700; margin-top: 1.4em; }
.minutes-document h3 { font-size: 1.15em; font-weight: 600; }
.minutes-document ul { list-style: disc; padding-left: 1.5em; }
.minutes-document ol { list-style: decimal; padding-left: 1.5em; }
.minutes-document ul[data-type="taskList"] { list-style: none; padding-left: 0; }
.minutes-document li[data-type="taskItem"] { display: flex; gap: .65em; align-items: flex-start; }
.minutes-document li[data-type="taskItem"] > div { flex: 1; }
.minutes-document blockquote { border-left: 3px solid rgb(var(--bb-primary)); padding-left: 1em; color: rgb(var(--bb-text2)); }
.minutes-document a { color: rgb(var(--bb-primary)); text-decoration: underline; }
.minutes-document table { border-collapse: collapse; width: 100%; table-layout: fixed; }
.minutes-document th, .minutes-document td { border: 1px solid rgb(var(--bb-border)); padding: .5em; min-width: 60px; }
.minutes-document th { background: rgb(var(--bb-surface2)); font-weight: 600; }
.minutes-document pre { background: rgb(var(--bb-surface2)); padding: 1em; overflow-x: auto; border-radius: 6px; }
`);
