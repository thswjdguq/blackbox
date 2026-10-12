import fs from 'node:fs/promises';
import {Presentation,PresentationFile,FileBlob} from '@oai/artifact-tool';
import {finalizePresentation} from 'file:///C:/Users/myhyu/.codex/plugins/cache/openai-primary-runtime/presentations/26.909.12148/skills/presentations/container_tools/artifact_tool_utils.mjs';
const root='C:/blackbox', build=root+'/.pptx-build';
const skill='C:/Users/myhyu/.codex/plugins/cache/openai-primary-runtime/presentations/26.909.12148/skills/presentations';
const font='Malgun Gothic', navy='#122C3B', teal='#007D79', gray='#526573';
const p=Presentation.create({slideSize:{width:1280,height:720}});
const tableSlides=[];
function text(s,t,x,y,w,h,size=28,color=navy,bold=false){const z=s.shapes.add({geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});z.text=t;z.text.style={typeface:font,fontSize:size,color,bold,autoFit:'none'};return z;}
function slide(title,sub='',dark=false){const s=p.slides.add();s.background.fill=dark?navy:'#FAFCFC';text(s,title,64,48,1152,100,42,dark?'#FFFFFF':navy,true);if(sub)text(s,sub,66,145,1148,66,23,dark?'#B6D5D5':gray);text(s,String(p.slides.items.length).padStart(2,'0'),1160,666,55,28,17,dark?'#B6D5D5':gray);return s;}
function rows(s,items,y=240,gap=116){items.forEach((a,i)=>{text(s,a[0],68,y+i*gap,250,52,28,teal,true);text(s,a[1],330,y+i*gap,865,gap-12,27);});}
function table(s,headers,data,widths,top=225,height=380,size=24){const values=[headers,...data];const tb=s.tables.add({rows:values.length,columns:headers.length,left:64,top,width:1152,height,columnWidths:widths,values});tb.borders.assign({fill:'#D8E3E6',width:1});for(let r=0;r<values.length;r++){for(let c=0;c<headers.length;c++){const cell=tb.getCell(r,c);cell.fill=r===0?navy:r%2?'#FFFFFF':'#EDF4F4';cell.text.style={typeface:font,fontSize:size,bold:r===0,color:r===0?'#FFFFFF':navy};} }tableSlides.push(p.slides.items.length);return tb;}
function note(s,t){s.speakerNotes.textFrame.setText(t);}
function foot(s,t){text(s,t,66,622,1120,35,18,gray);}

let s=slide('SSAK-LOG','대학생 팀 프로젝트 PMS 고도화 기획',true);
text(s,'과제 시작부터 반복 검토와\n최종 제출까지',68,260,1120,175,58,'#FFFFFF',true);
text(s,'Team Blackbox',70,518,1100,46,28,'#8ED5CD',true);
text(s,'팀장 송승준    팀원 손정협 · 손정효',70,570,1100,40,24,'#FFFFFF');
note(s,'프로젝트명과 조 이름은 기존 자료를 유지했습니다. 사용자 지정 최신 명단: 팀장 송승준, 팀원 손정협, 손정효. 본 발표의 고도화 기능은 개발 계획이며 기존 구현과 구분합니다.');

s=slide('목차','대상 사용자와 개선 근거를 먼저 설명하고, 구현 계획으로 이어집니다');
rows(s,[['01  팀 소개','조 이름과 각오, 목표 산업, 신구 EXPO 출품 여부'],['02  프로젝트','주제와 개요, 대상 사용자, 핵심 기능과 수행 흐름'],['03  고도화 계획','기존 개선점, 우선 기능, 적용 기술, 구현 범위'],['04  수행 계획','스터디, 요소별 담당자, 개발 일정과 검증']],222,98);

s=slide('조 소개와 각오','Team Blackbox');
table(s,['구성','이름','이번 고도화의 주 역할 제안'],[['팀장','송승준','서버·DB·검토·제출 관리, 일정 총괄'],['팀원','손정협','업무·활동 집계·회의 AI·보고서'],['팀원','손정효','사용자 흐름·화면·상태 연결·사용성 평가']],[180,200,772],222,260,25);
text(s,'“학생 팀이 실제 과제 하나를 시작부터 제출까지\n운영할 수 있는 서비스를 완성하겠습니다.”',70,522,1120,93,31,teal,true);
note(s,'명단 및 팀장: 사용자 확정 정보. 세부 역할은 현재 3인 구성에 맞춘 배정 제안이며 팀 합의에 따라 조정합니다. 기존 4인 문서의 송병철 및 기여율을 사용하지 않습니다.');

s=slide('진출 목표 산업과 EXPO','교육 소프트웨어와 협업 소프트웨어');
rows(s,[['목표 산업','EdTech 및 협업 SaaS\n수업 과제의 실행과 제출 과정을 지원'],['첫 적용 대상','공동 결과물을 제출하는 대학생 팀\n한 과목의 소수 팀부터 사용성을 검증'],['신구 EXPO','출품 희망 여부: 미정\n출품 시 초안 검토부터 제출까지 3분 시연']],228,120);
note(s,'목표 산업은 기획 방향입니다. 시장 규모, 고객 확보, 출품 의사는 확인되지 않았습니다. EXPO 희망 여부는 팀 합의 후 갱신합니다.');

s=slide('프로젝트 주제와 개요','SSAK-LOG: 제출물 중심의 대학생 팀 프로젝트 PMS');
text(s,'공동 결과물을 만드는 팀이\n중간부터 검토하고 제출본을 확정하는 서비스',68,238,1130,130,39,navy,true);
rows(s,[['관리의 중심','최종 보고서·발표자료·작품 등 교수님께 낼 제출물'],['연결되는 기록','요구사항, 업무, 결과 버전, 피드백, 제출 기록']],416,94);
note(s,'기획 근거: docs/PMS_고도화_기획안_v2_사용자와수행흐름.md 1·4절. 고도화 목표를 설명하는 슬라이드입니다.');

s=slide('대상 사용자','한 학기 동안 공동 결과물을 제출하는 3~6인 대학생 팀');
rows(s,[['팀장·제출 담당자','빠진 요구사항과 늦어진 작업을 확인하고\n통합본과 실제 제출을 책임진다'],['팀원·검토 담당자','자기 업무와 완료 기준을 확인하고\n초안을 공유하며 다른 팀원의 결과를 검토한다'],['교수자','기존 수업 채널로 결과물을 받고 피드백한다\n교수자 가입 없이 학생의 전체 흐름이 동작한다']],224,126);
foot(s,'초기 범위: 수업 과제형 팀 프로젝트. 개인 과제·기업 프로젝트·연구실 운영은 제외');

s=slide('전공이 달라도 같은 제출 흐름을 사용','작성 도구는 유지하고, 파일·링크·설명을 제출물에 연결합니다');
table(s,['과제 예시','제출물','중간 검토 기준 예시'],[['경영학','시장분석 보고서','조사 출처와 전략의 근거'],['디자인','최종 시안·작품 설명서','피드백 반영과 지정 규격'],['인문사회','조사 보고서','인용 출처와 분석 내용'],['소프트웨어','실행물·시연 자료','시연 조건과 실행 방법']],[220,350,582],222,362,25);
foot(s,'적용 예시이며 실제 사용 사례가 아닙니다. 전공별 전문 편집 도구는 구현 범위에서 제외합니다');

s=slide('기존 프로젝트의 개선점','기존 화면과 데이터 구조를 기준으로 도출했습니다');
table(s,['기존 구조','사용 흐름에서 생기는 문제','고도화 방향'],[['전체 현황 중심 홈','내 다음 행동을 다시 찾아야 함','내 업무·검토·누락 먼저 표시'],['회의 출처를 설명에 저장','결정과 실행 상태 추적이 약함','회의 결정과 업무를 ID로 연결'],['업무 완료 표시만 존재','검토된 완료인지 구분 어려움','버전별 검토 후 완료'],['프로젝트 단위 파일 보관','업무·제출본과의 연결이 약함','산출물 버전과 확정본 연결']],[320,430,402],225,362,23);
foot(s,'분석 범위: 저장소 코드와 문서. 실제 사용자 관찰 결과와 교수님 발언을 대신하지 않습니다');
note(s,'근거: frontend/src/app/projects/[projectId]/page.tsx, backend/src/main/java/com/blackbox/service/MeetingService.java, entity/Task.java, entity/FileVault.java. 기존 회의에서 업무 생성은 이미 있으며 신규 기능으로 주장하지 않습니다.');

s=slide('고도화 기능의 우선 선정','제출 과정의 단절을 해결하고, 시연으로 검증할 수 있는 기능을 먼저 구현');
table(s,['우선 기능','선정 이유','차별성·우수성의 검증 방향'],[['제출물 기반 업무 관리','할 일을 과제 요구사항과 연결','누락 항목과 다음 행동 발견'],['반복 검토와 수정 추적','피드백을 실제 수정으로 연결','중간 검토 순환의 완주'],['통합본 검토와 제출 기록','최종 버전과 제출 상태 구분','잘못된 버전·상태 혼동 방지']],[350,395,407],234,302,24);
text(s,'차별화 방향: 대학 수업의 제출 주기에 맞춘 기본 흐름',68,563,1120,50,29,teal,true);
note(s,'차별성 및 우수성은 개발 목표와 효과 가설입니다. 경쟁 제품 대비 독점 기능 또는 검증된 성능 우위를 주장하지 않습니다.');

s=slide('핵심 기능과 프로젝트 수행 흐름','제출물이 여러 개여도 동일한 흐름을 반복합니다');
table(s,['순서','학생의 행동','PMS의 동작'],[['1  과제 설정','요구사항과 제출일 확인','제출물·기준·책임자 등록'],['2  업무 분담','작업을 나눠 수행','담당자·기한·완료 기준 연결'],['3  반복 검토','초안 공유와 보완','의견·수정 항목·반영 버전 기록'],['4  통합본 검토','개별 결과를 합쳐 확인','필수 누락 확인과 후보 승인'],['5  제출·종료','지정 경로로 제출','확정 버전·제출 근거·종료 보관']],[225,395,532],212,405,24);

s=slide('고도화 1. 제출물 기반 업무 관리','누가 무엇을 끝내야 제출할 수 있는지 연결합니다');
rows(s,[['기능 설명','제출물에 필수 요구사항을 등록하고 업무를 연결\n업무마다 책임자·마감일·완료 기준을 지정'],['적용 기술','Next.js 입력 화면, Spring Boot API\nPostgreSQL 관계 설계와 Flyway 마이그레이션'],['차별성·우수성','학과별 작업 방식이 달라도 같은 틀로 관리\n완료 업무 수와 제출 요구사항 충족도를 구분']],222,126);
foot(s,'담당 제안: 송승준(제출물·DB), 손정협(업무 API), 손정효(화면)');

s=slide('고도화 2. 작업 중간부터 반복 검토','진행 중인 초안에도 의견을 요청할 수 있습니다');
rows(s,[['기능 설명','특정 버전에 중간 의견 또는 완료 검토 요청\n필수 수정에 담당자·기한·반영 결과를 연결'],['적용 기술','버전·검토·수정 항목 모델, 상태 전환 API\nSpring 트랜잭션과 역할 권한 검사'],['차별성·우수성','피드백을 남기는 데서 끝나지 않고 반영까지 추적\n새 버전은 다시 검토하여 승인 대상을 명확히 함']],222,126);
foot(s,'담당 제안: 송승준(검토·버전), 손정협(수정 항목), 손정효(화면)');

s=slide('중간 검토와 완료 검토의 차이','검토는 프로젝트 전 과정에서 반복됩니다');
table(s,['구분','중간 의견 요청','완료 검토 요청'],[['사용 시점','초안·조사 방향을 확인할 때','완료 기준을 충족했을 때'],['업무 상태','진행 중 유지','완료 검토 중'],['검토 결과','의견 또는 수정 항목','승인 또는 보완 요청'],['다음 행동','수정 후 계속 작업','승인 시 완료, 보완 시 진행 중']],[230,461,461],225,334,24);
foot(s,'교수 피드백도 후속 업무로 연결합니다. 학생이 옮겨 적은 의견과 교수자의 직접 승인을 구분합니다');

s=slide('고도화 3. 통합본 검토와 최종 제출','개별 업무가 끝난 뒤, 통합된 결과물을 다시 확인합니다');
rows(s,[['기능 설명','제출 후보 버전 등록 후 필수 조건과 수정 항목 확인\n승인된 파일을 확정하고 실제 제출 기록을 연결'],['적용 기술','기존 파일 저장소·SHA-256·ZIP 기능 확장\n제출 버전 스냅샷과 제출 차수 데이터 모델'],['차별성·우수성','어떤 파일을 검토하고 제출했는지 추적\n재제출해도 이전 제출 기록을 보존']],222,126);
foot(s,'담당 제안: 송승준(확정·제출), 손정협(내보내기 연결), 손정효(제출 화면)');
note(s,'기존 Hash Vault를 결과물과 제출 버전에 연결하는 고도화입니다. SHA-256은 저장 기준과 파일 변경을 비교하는 기술이며 절대적 위변조 불가나 작성 진실성을 보장한다고 주장하지 않습니다.');

s=slide('업무 완료와 실제 제출은 별도 상태','다운로드만으로 제출 완료를 표시하지 않습니다');
rows(s,[['업무 완료','지정 검토자가 개별 작업의 결과 버전을 승인'],['제출 준비 완료','통합본 승인과 필수 조건 확인을 마침\n확정 파일을 다운로드할 수 있음'],['제출 기록 완료','담당자가 LMS·이메일 등으로 직접 제출\n제출 시각·경로·버전·확인 근거를 기록']],222,120);
foot(s,'PMS의 담당자 제출 기록은 외부 시스템 접수 확인이나 교수자의 평가 승인을 뜻하지 않습니다');

s=slide('기존 기능의 재사용과 연결','이미 구현된 자산을 전체 수행 흐름에 배치합니다');
table(s,['기존 기능','고도화 설명','기술 및 담당 제안'],[['회의록·AI 업무 추출','결정과 업무 연결, 확정 전 확인','Claude·관계 API / 손정협'],['칸반·프로젝트 홈','검토 요청과 다음 행동 우선 표시','Next.js·Zustand / 손정효'],['파일 보관·PDF·ZIP','확정 버전·보고서 출처 연결','파일 저장·OpenPDF / 송승준·손정협'],['참여도·알림','사람 순위보다 지연·수정 근거 제공','기간별 집계 API / 손정협']],[300,475,377],223,365,23);
note(s,'현재 참여도는 네 종류의 참여 여부 분류입니다. 근거: backend/src/main/java/com/blackbox/service/ScoreService.java 및 AlertService.java. 기존 발표 문서의 가중치 점수 설명을 현재 구현 사실로 재사용하지 않습니다.');

s=slide('적용 기술과 기능: 화면·서버','기존 기술을 유지하고 데이터 관계와 상태 규칙을 고도화합니다');
table(s,['영역','언어·기술','적용 기능'],[['프론트엔드','TypeScript, Next.js, React','제출물·업무·검토 화면'],['화면 스타일·상태','Tailwind CSS, Zustand, Axios','공통 UI, 데이터 갱신·오류 안내'],['업무 보드','기존 dnd-kit','검토 상태가 있는 업무 보드'],['백엔드','Java 17, Spring Boot','업무·검토·제출 API'],['인증·접근 제어','Spring Security, 기존 JWT 인증','프로젝트·역할별 권한 검사']],[240,470,442],219,400,23);
note(s,'기술 근거: frontend/package.json 및 backend/build.gradle. 현재 저장소 기술을 활용하는 계획이며 최신 버전 비교 자료가 아닙니다.');

s=slide('적용 기술과 기능: 저장·개발·운영','개발 도구와 서버 구성까지 기존 환경을 활용합니다');
table(s,['영역','적용 기술·도구','필요 기능'],[['데이터베이스','PostgreSQL, JPA, Flyway','제출물·검토 관계, 기존 데이터 보존'],['파일·출력','파일 저장소, SHA-256, OpenPDF','버전 식별, PDF·ZIP 내보내기'],['AI 보조','기존 Claude API','회의 업무 초안, 사용자 확인'],['개발 도구','Git, Gradle, npm','변경 관리, 빌드·검증'],['서버·배포','Docker Compose, Nginx','화면·API·DB 서비스 실행']],[240,470,442],219,400,23);
note(s,'기술 근거: backend/build.gradle, docker-compose.yml 및 기존 코드 분석 문서. Notion·Discord·Google Calendar는 선택 연동으로 유지하며 첫 과제 시작의 필수 조건으로 두지 않습니다.');

s=slide('구현 범위','첫 사용 검증에는 시작부터 제출까지의 필수 흐름을 완성합니다');
table(s,['구분','구현 대상'],[['필수 구현','제출물·요구사항, 업무 분담, 결과 버전\n중간 검토·수정, 완료 검토, 통합본 확정\n제출 기록·종료, 내 다음 행동 홈'],['후순위','회의·AI 연결 고도화, 교수 피드백 전용 화면\n주간보고 초안, 외부 알림 안정화'],['이번 범위 제외','LMS 자동 제출, 성적 자동 산정\n공동 편집기, 화상회의, 학과별 전문 도구']],[245,907],220,354,25);
foot(s,'AI나 외부 연동 없이도 필수 흐름이 동작하도록 구현합니다');

s=slide('예외 처리와 완료 기준','실제 작업에서 되돌아가는 상황도 흐름에 포함합니다');
table(s,['상황','처리 규칙'],[['검토 중 새 버전 등록','새 버전에 재검토 요청, 기존 승인 이력 보존'],['필수 수정 미해결','일반 확정 차단, 예외 제출은 사유와 누락 표시'],['검토자 부재','팀장이 사유를 남기고 대체 검토자 지정'],['제출 후 수정 요구','프로젝트 재개, 새 제출 차수 생성'],['기존 완료 업무 이전','이전 완료·검토 기록 없음으로 보존']],[355,797],220,388,24);
note(s,'승인 권한 및 프로젝트 경계는 서버에서 검사합니다. 통합본 최종 검토자는 제출 담당자와 다른 팀원을 기본으로 지정합니다. 외부 링크는 변경될 수 있으므로 확정 내용 고정이 필요한 자료는 파일로 남깁니다.');

s=slide('스터디 계획과 공유 방식','고도화 프로젝트에도 필요한 지식을 식별해 구현과 함께 학습합니다');
table(s,['담당 제안','학습 요소','팀 공유 산출물'],[['송승준','관계형 모델링, 트랜잭션\n권한·버전·제출 이력','ERD, 상태 전환표\n검토·제출 API 시연'],['손정협','업무 상태, 기간별 집계\n구조화 AI 응답·보고서','API 명세, 응답 검증 예제\n보고서 생성 시연'],['손정효','사용자 여정, 공통 UI\n비동기 상태·사용성 평가','화면 흐름, 공통 컴포넌트\n과제 수행 관찰 기록']],[215,505,432],219,324,24);
foot(s,'매주 1회 학습 내용과 구현 예제를 공유하고, 다른 팀원이 실제 시나리오로 확인합니다');

s=slide('식별된 요소 및 요소별 담당자','3인 팀 기준의 고도화 담당 배정안');
table(s,['구현 요소','주 담당','협업·검토'],[['제출물·DB·권한·배포','송승준','손정협'],['버전·검토·확정·제출 기록','송승준','손정효'],['업무·수정 항목·활동 집계','손정협','송승준'],['회의 AI·보고서·내보내기 연결','손정협','손정효'],['사용자 흐름·공통 UI·주요 화면','손정효','송승준·손정협'],['사용성 평가·통합 검증·발표','전원','송승준 취합']],[610,205,337],220,398,23);
note(s,'명단과 팀장 정보는 사용자 확정 사항입니다. 요소별 배정은 본 기획의 제안이며 팀 합의에 따라 변경합니다. 서버 담당자도 자기 기능의 화면 데이터 연결을 분담하고 손정효가 공통 UI를 검토합니다.');

s=slide('개발 일정','12주 계획안. 실제 학사 일정에 맞춰 조정합니다');
table(s,['기간','핵심 작업','확인 가능한 결과'],[['1~2주','전공별 인터뷰·흐름·ERD','한 과제의 시작부터 제출까지 설계'],['3~4주','제출물·요구사항·업무','과제 설정과 업무 분담'],['5~6주','버전·중간 검토·수정','초안 공유와 반영 확인'],['7~8주','완료 검토·통합본 확정','검토된 최종 제출 후보'],['9~10주','제출 기록·종료·데이터 이전','제출 이력까지 연결한 전체 흐름'],['11~12주','파일럿·오류 수정·발표','검증 결과와 시연 자료']],[180,460,512],217,402,23);

s=slide('검증 계획과 기대 효과','수치는 검증 목표이며 아직 측정한 성과가 아닙니다');
table(s,['검증 항목','방법','목표'],[['전공 공통 사용성','서로 다른 전공 3개 팀 파일럿','설명 없이 핵심 흐름 완주'],['반복 검토','초안·피드백·수정·확인 관찰','팀별 최소 2회 순환'],['다음 행동 찾기','내 업무·검토 요청 수행 관찰','참여자 80% 이상 성공'],['제출 준비 부담','기존 방식과 최종본 찾기 비교','중앙값 소요시간 30% 감소']],[320,500,332],220,335,24);
foot(s,'추가 검증: 승인 권한, 새 버전 충돌, 중복 제출 요청, 제출 후 재개, 기존 이력 보존');
note(s,'파일럿 모집은 아직 확보되지 않았습니다. 과제 복잡도와 학습 효과를 기록하고 소규모 결과를 전체 대학생에게 일반화하지 않습니다.');

s=slide('시연 시나리오','가상 시장분석 과제로 한 흐름을 보여줍니다');
table(s,['순서','시연 행동','확인할 가치'],[['1','보고서 조건과 조사 업무 등록','요구사항과 담당 작업 연결'],['2','조사 초안 v1에 보완 요청','진행 중에도 검토 가능'],['3','v2의 근거 보완 확인과 승인','수정 반영과 완료 근거'],['4','통합 보고서 검토·확정','업무 완료와 제출 준비 구분'],['5','제출 기록 저장·프로젝트 종료','어떤 버전을 냈는지 보존']],[130,550,472],218,391,24);

s=slide('고도화의 목표','한 팀이 과제 하나를 처음부터 제출까지 완주',true);
text(s,'제출 조건에서 출발하고\n작업 중간에 계속 검토하며\n확정된 결과물을 제출합니다',70,250,1120,220,47,'#FFFFFF',true);
text(s,'Team Blackbox    송승준 · 손정협 · 손정효',70,558,1100,45,26,'#8ED5CD');
note(s,'요약: 첫 사용자 3~6인 수업 과제 팀, 제출물 중심 관계, 반복 검토, 실제 제출 기록. 향후 효과는 파일럿으로 검증합니다.');

s=slide('부록. 분석 근거와 기획 전제','기존 구현과 이번 고도화 계획을 구분합니다');
rows(s,[['기존 구현 근거','Task·FileVault 모델, MeetingService\nScoreService·AlertService, 프로젝트 홈 코드'],['기획 근거','PMS 고도화 기획안 v2\n대상 사용자·수행 흐름·검토·제출 규칙'],['확정·미정 사항','확정: 팀장 송승준, 팀원 손정협·손정효\n제안: 역할·12주 일정  /  미정: EXPO 출품 여부']],220,120);
note(s,'로컬 출처: docs/PMS_고도화_기획안.md, docs/PMS_고도화_기획안_v2_사용자와수행흐름.md, frontend/package.json, backend/build.gradle, frontend/src/app/projects/[projectId]/page.tsx, backend/src/main/java/com/blackbox/entity/Task.java, entity/FileVault.java, service/MeetingService.java, service/ScoreService.java, service/AlertService.java. 현재 서비스 실행을 통한 사용성 평가는 실시하지 않았으며 코드 구조 분석입니다.');

await fs.mkdir(build+'/renders',{recursive:true});
await (await PresentationFile.exportPptx(p)).save(build+'/candidate.pptx');
await fs.writeFile(build+'/deck.json',JSON.stringify(p.toProto()));
console.log('EXPORTED',p.slides.items.length);
const out=root+'/outputs/SSAK-LOG_PMS_고도화_발표자료.pptx';
const result=await finalizePresentation({workspaceDir:root,candidatePath:build+'/candidate.pptx',finalPath:out,pythonExecutable:'C:/Users/myhyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',integrityValidatorPath:skill+'/container_tools/inspect_presentation_package_integrity.py',layoutValidatorPath:skill+'/container_tools/inspect_presentation_layout_geometry.py',layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit',...tableSlides.flatMap(n=>['--require-native-table-slide',String(n)])],requiredNativeTableOwnerSlides:tableSlides,fontPolicy:{basis:'design',families:[font]},verifyArtifactToolImport:true,receiptPath:build+'/validation.json'});
console.log('FINAL',JSON.stringify(result));
const finalP=await PresentationFile.importPptx(await FileBlob.load(out));
for(let i=0;i<finalP.slides.items.length;i++){let sl=finalP.slides.items[i];let png=await finalP.export({slide:sl,format:'png',scale:1});await fs.writeFile(build+'/renders/'+String(i+1).padStart(2,'0')+'.png',new Uint8Array(await png.arrayBuffer()));console.log('RENDER',i+1);}


