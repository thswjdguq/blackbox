import assert from 'node:assert/strict';
import { MarkdownManager } from '@tiptap/markdown';
import StarterKit from '@tiptap/starter-kit';
import TaskList from '@tiptap/extension-task-list';
import TaskItem from '@tiptap/extension-task-item';
import { TableKit } from '@tiptap/extension-table';

const manager = new MarkdownManager({ extensions: [StarterKit, TaskList, TaskItem.configure({ nested: true }), TableKit] });
const samples = [
  ['기존 일반 텍스트', '기존 일반 텍스트'],
  ['## 안건\n\n**중요한 결정**', '**중요한 결정**'],
  ['## 논의 안건\n\n### 담당자별 현황\n\n본문', '### 담당자별 현황'],
  ['- [ ] 자료 조사\n- [x] 회의 완료', '- [x] 회의 완료'],
  ['| 담당 | 업무 |\n| --- | --- |\n| 팀원 | 조사 |', '팀원'],
  ['[참고](https://example.com)', 'https://example.com'],
];
for (const [input, expected] of samples) {
  const output = manager.serialize(manager.parse(input));
  assert.ok(output.includes(expected), `${expected}: ${output}`);
  assert.equal(manager.serialize(manager.parse(output)), output, '저장 후 다시 읽었을 때 서식이 안정적으로 유지되어야 함');
}
assert.equal(manager.serialize(manager.parse('')).trim(), '');
console.log('회의록 마크다운 왕복 변환 7개 시나리오 통과 (H2·H3 포함)');
