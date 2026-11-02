"use client";

import { useEffect, useState } from "react";
import { EditorContent, useEditor, useEditorState } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { Markdown } from "@tiptap/markdown";
import TaskList from "@tiptap/extension-task-list";
import TaskItem from "@tiptap/extension-task-item";
import { TableKit } from "@tiptap/extension-table";

interface Props {
  value: string;
  onChange: (value: string) => void;
  label: string;
  readOnly?: boolean;
}

export default function MinutesEditor({ value, onChange, label, readOnly = false }: Props) {
  const [source, setSource] = useState(false);
  const editor = useEditor({
    extensions: [StarterKit.configure({ link: { openOnClick: false } }), Markdown, TaskList, TaskItem.configure({ nested: true }), TableKit],
    content: value,
    contentType: "markdown",
    immediatelyRender: false,
    editable: !readOnly,
    editorProps: { attributes: { class: "minutes-document", role: "textbox", "aria-label": label, "aria-multiline": "true" } },
    onUpdate: ({ editor }) => onChange(editor.getMarkdown()),
  });
  const textStyle = useEditorState({ editor, selector: ({ editor }) =>
    editor?.isActive("heading", { level: 3 }) ? "3" : editor?.isActive("heading", { level: 2 }) ? "2" : "0" });

  useEffect(() => { editor?.setEditable(!readOnly); }, [editor, readOnly]);
  useEffect(() => {
    // Only external changes replace the document; typing must retain the selection.
    if (editor && value !== editor.getMarkdown()) editor.commands.setContent(value, { contentType: "markdown", emitUpdate: false });
  }, [editor, value]);

  const link = () => {
    const url = window.prompt("링크 주소 (https://...)");
    if (url === null) return;
    if (!url.trim()) { editor?.chain().focus().unsetLink().run(); return; }
    if (!/^https?:\/\//i.test(url.trim())) { window.alert("http 또는 https 주소를 입력하세요."); return; }
    editor?.chain().focus().setLink({ href: url.trim() }).run();
  };
  const actions = [
    { name: "굵게", run: () => editor?.chain().focus().toggleBold().run() },
    { name: "목록", run: () => editor?.chain().focus().toggleBulletList().run() },
    { name: "번호", run: () => editor?.chain().focus().toggleOrderedList().run() },
    { name: "체크리스트", run: () => editor?.chain().focus().toggleTaskList().run() },
    { name: "인용", run: () => editor?.chain().focus().toggleBlockquote().run() },
    { name: "링크", run: link },
    { name: "표", run: () => editor?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run() },
    { name: "행 추가", run: () => editor?.chain().focus().addRowAfter().run() },
    { name: "행 삭제", run: () => editor?.chain().focus().deleteRow().run() },
    { name: "표 삭제", run: () => editor?.chain().focus().deleteTable().run() },
    { name: "되돌리기", run: () => editor?.chain().focus().undo().run() },
  ];
  return <div className="rounded-xl border border-bb-border overflow-hidden bg-bb-surface">
    <div className="flex flex-wrap gap-1 p-2 border-b border-bb-border bg-bb-bg" role="toolbar" aria-label={`${label} 서식`}>
      {!readOnly && !source && <select aria-label="텍스트 스타일" disabled={!editor}
        value={textStyle ?? "0"}
        onChange={e => { const level = Number(e.target.value); if (level === 0) editor?.chain().focus().setParagraph().run(); else editor?.chain().focus().setHeading({ level: level as 2 | 3 }).run(); }}
        className="px-2 py-1.5 rounded text-xs bg-bb-surface border border-bb-border text-bb-text">
        <option value="0">본문</option><option value="2">중제목 (H2)</option><option value="3">소제목 (H3)</option>
      </select>}
      {!readOnly && !source && actions.map(action => <button key={action.name} type="button" disabled={!editor} onClick={action.run} className="px-2 py-1.5 rounded text-xs text-bb-text hover:bg-bb-surface2 disabled:opacity-40">{action.name}</button>)}
      <button type="button" aria-pressed={source} onClick={() => setSource(!source)} className="ml-auto px-2 py-1.5 rounded text-xs text-bb-primary">{source ? "문서 보기" : "마크다운 원문"}</button>
    </div>
    {source ? <textarea aria-label={`${label} 마크다운 원문`} readOnly={readOnly} value={value} onChange={e => onChange(e.target.value)} className="w-full min-h-64 p-5 text-sm font-mono bg-bb-surface text-bb-text" /> : <EditorContent editor={editor} />}
  </div>;
}
