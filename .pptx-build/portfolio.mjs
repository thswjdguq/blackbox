import fs from 'node:fs/promises';
import {PresentationFile,FileBlob} from '@oai/artifact-tool';
import {finalizePresentation} from 'file:///C:/Users/myhyu/.codex/plugins/cache/openai-primary-runtime/presentations/26.909.12148/skills/presentations/container_tools/artifact_tool_utils.mjs';
const p=await PresentationFile.importPptx(await FileBlob.load('C:/blackbox/outputs/손에송잡고_프로젝트고도화_디자인수정.pptx'));
const s=p.slides.add();s.background.fill='#FFFFFF';
function text(t,x,y,w,h,sz=27,bold=false,color='#344B59'){let a=s.shapes.add({geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});a.text=t;a.text.style={typeface:'Malgun Gothic',fontSize:sz,bold,color,autoFit:'none'};}
text('개인 포트폴리오 적용 계획',70,55,1140,95,42,true,'#163A50');
const vals=[['팀원','개인 포트폴리오에 담을 내용'],['송승준','서버·DB 설계, 권한 및 검토·제출 관리\n설계 이유와 배포 과정의 문제 해결 사례'],['손정협','업무 상태·수정 처리, 진행 현황 구현\n처리 흐름과 기능 개선 과정'],['손정효','사용자 흐름 설계, 업무·검토·제출 화면\n화면 개선 전후와 사용성 보완 과정']];
const tb=s.tables.add({rows:4,columns:2,left:70,top:190,width:1140,height:326,columnWidths:[220,920],values:vals});tb.borders.assign({fill:'#D6E5E9',width:0.7});for(let r=0;r<4;r++)for(let c=0;c<2;c++){let a=tb.getCell(r,c);a.fill=r===0?'#163A50':r%2?'#FFFFFF':'#F1F7F8';a.text.style={typeface:'Malgun Gothic',fontSize:25,bold:r===0||c===0,color:r===0?'#FFFFFF':c===0?'#078C87':'#344B59'};}
text('공통 정리 방식',78,555,245,50,27,true,'#078C87');text('담당 역할·구현 과정·문제 해결·결과를 정리하고\n관련 코드, 화면, 시연 자료를 개인 포트폴리오에 연결',350,553,855,86,25);
text('14',1160,665,50,26,17,false,'#6A8999');
const dir='C:/blackbox/.pptx-build/portfolio';await fs.mkdir(dir,{recursive:true});await(await PresentationFile.exportPptx(p)).save(dir+'/candidate.pptx');
const out='C:/blackbox/outputs/손에송잡고_프로젝트고도화_포트폴리오추가.pptx',skill='C:/Users/myhyu/.codex/plugins/cache/openai-primary-runtime/presentations/26.909.12148/skills/presentations';
await finalizePresentation({workspaceDir:'C:/blackbox',candidatePath:dir+'/candidate.pptx',finalPath:out,pythonExecutable:'C:/Users/myhyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',integrityValidatorPath:skill+'/container_tools/inspect_presentation_package_integrity.py',layoutValidatorPath:skill+'/container_tools/inspect_presentation_layout_geometry.py',layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-heading-fit','--require-native-table-slide','14'],requiredNativeTableOwnerSlides:[14],fontPolicy:{basis:'design',families:['Malgun Gothic']},verifyArtifactToolImport:true,receiptPath:dir+'/validation.json'});
const q=await PresentationFile.importPptx(await FileBlob.load(out));for(let i=0;i<14;i++){let b=await q.export({slide:q.slides.items[i],format:'png',scale:1});await fs.writeFile(dir+'/'+(i+1)+'.png',new Uint8Array(await b.arrayBuffer()));}console.log('DONE');
