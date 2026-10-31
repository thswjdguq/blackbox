package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/** 결과와 근거부터 읽는 보고서. 기존 네 항목의 집계 계산과 저장은 변경하지 않는다. */
@Service
public class ReportService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter FMT=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Color INK=new Color(25,45,64), BLUE=new Color(39,78,124), MUTED=new Color(91,108,123),
            LINE=new Color(218,226,233), PALE=new Color(242,246,250), WHITE=Color.WHITE;
    private static final BaseFont NANUM;
    static {
        try { NANUM=BaseFont.createFont("/fonts/NanumGothic.ttf",BaseFont.IDENTITY_H,BaseFont.EMBEDDED); }
        catch(Exception e) {throw new ExceptionInInitializerError(e);}
    }
    private final ProjectAccessChecker access;
    private final ProjectMemberRepository memberRepo;
    private final TaskRepository taskRepo;
    private final FileVaultRepository vaultRepo;
    private final ContributionScoreRepository scoreRepo;
    private final AlertRepository alertRepo;
    private final ReportEvidenceReader evidenceReader;

    public ReportService(ProjectAccessChecker access, ProjectMemberRepository memberRepo, TaskRepository taskRepo,
                         FileVaultRepository vaultRepo, ContributionScoreRepository scoreRepo, AlertRepository alertRepo,
                         ReportEvidenceReader evidenceReader) {
        this.access=access;this.memberRepo=memberRepo;this.taskRepo=taskRepo;this.vaultRepo=vaultRepo;
        this.scoreRepo=scoreRepo;this.alertRepo=alertRepo;this.evidenceReader=evidenceReader;
    }

    @Transactional(readOnly=true)
    public byte[] generateReport(UUID projectId, User requester) {
        Project project=access.getProject(projectId);access.requireMember(project,requester);
        List<ProjectMember> members=memberRepo.findByProject(project).stream().filter(ProjectAccessChecker::canContribute)
                .sorted(Comparator.comparing((ProjectMember m)->!"LEADER".equals(m.getRole()))
                        .thenComparing(m->m.getUser().getName()).thenComparing(m->m.getUser().getId())).toList();
        Set<UUID> ids=members.stream().map(m->m.getUser().getId()).collect(Collectors.toSet());
        var tasks=taskRepo.findByProjectOrderByCreatedAtDesc(project);
        var files=vaultRepo.findByProjectOrderByUploadedAtDesc(project);
        var scores=scoreRepo.findByProjectOrderByTotalScoreDesc(project).stream().filter(s->ids.contains(s.getUser().getId()))
                .collect(Collectors.toMap(s->s.getUser().getId(),s->s));
        var alerts=alertRepo.findByProjectOrderByCreatedAtDesc(project).stream()
                .filter(a->a.getResolvedAt()==null)
                .filter(a->!AlertService.PARTICIPATION_TYPES.contains(a.getAlertType()) || a.getUser()!=null&&ids.contains(a.getUser().getId())).toList();
        var evidence=evidenceReader.read(project,tasks);
        String reportId="BB-"+LocalDate.now(ZONE).toString().replace("-","")+"-"+UUID.randomUUID().toString().substring(0,8);
        try(var out=new ByteArrayOutputStream()) {
            Document doc=new Document(PageSize.A4,46,46,49,49);
            PdfWriter writer=PdfWriter.getInstance(doc,out);writer.setPageEvent(new Footer(reportId));
            doc.addTitle("팀 프로젝트 기여 활동 보고서 - "+project.getName());doc.addAuthor("Team Blackbox");doc.open();
            summary(doc,project,requester,members,tasks,files,evidence);
            if(!members.isEmpty()) {doc.newPage();people(doc,members,tasks,files,evidence);}
            doc.newPage();method(doc,members,scores,alerts);
            if(!tasks.isEmpty()||!files.isEmpty()) {doc.newPage();appendix(doc,tasks,files,evidence);}
            doc.close();return out.toByteArray();
        }catch(Exception e){throw new IllegalStateException("기여 활동 PDF 생성 실패",e);}
    }

    private void summary(Document doc, Project project, User requester, List<ProjectMember> members,
                         List<Task> tasks,List<FileVault> files,ReportEvidenceReader.Evidence e) throws DocumentException {
        doc.add(p("TEAM BLACKBOX  /  PROJECT REPORT",10,BLUE,true));
        doc.add(p("팀 프로젝트 기여 활동 보고서",24,INK,true));
        doc.add(p(project.getName(),16,BLUE,true));
        doc.add(p("업무·협업 기록을 정리했습니다. 기여율(%)이나 100점 성적을 산출하지 않습니다.",10,MUTED,false));
        var meta=table(1,3);row(meta,"과목 · 학기",join(project.getCourseName(),project.getSemester()));
        row(meta,"프로젝트 기간",date(project.getStartDate())+" ~ "+date(project.getEndDate()));
        row(meta,"작성 정보",requester.getName()+" / "+time(OffsetDateTime.now())+" KST");
        compact(meta,6);doc.add(meta);
        var metrics=table(1,1,1,1);
        row(metrics,"팀원 수\n"+members.size()+"명","업무 완료\n"+tasks.stream().filter(t->"DONE".equals(t.getStatus())).count()+" / "+tasks.size()+"건",
                "최종 제출물\n"+e.finalWorks().size()+"건","등록 파일\n"+files.size()+"개 버전");doc.add(metrics);
        heading(doc,"01", "팀원별 기여 근거 한눈에 보기");
        doc.add(p("인원별 순위가 아닌 역할·기록의 요약입니다. 공동 담당 업무는 각 담당자에게 표시되므로 개인별 건수 합계와 팀 업무 수는 다를 수 있습니다.",9,MUTED,false));
        var team=table(1.3f,1.15f,1.05f,1.05f,1.15f);headers(team,"팀원 / 역할","담당 · 완료","파일 등록","의견·답변","확인·검토");
        for(var m:members) {
            var u=m.getUser();var own=assigned(e,u);long done=own.stream().filter(t->"DONE".equals(t.getStatus())).count();
            row(team,u.getName()+" · "+role(m),own.size()+" · "+done+"건",uploads(files,u)+"회",feedback(e,u)+"건",decisions(e,tasks,u)+"건");
        }
        if(members.isEmpty()) row(team,"표시할 현재 팀원이 없습니다.","-","-","-","-");doc.add(team);
        doc.add(p("의견·답변 = 업무 댓글·의견 요청·답글 + 최종 제출물 검토 피드백. 확인·검토 = 업무 완료 확인 + 검토 회차의 승인·수정 요청 결정. 양이나 횟수만으로 기여의 질을 판정하지 않습니다.",8,MUTED,false));
        heading(doc,"02","최종 제출물 진행 현황");
        if(e.finalWorks().isEmpty()) doc.add(p("등록된 최종 제출물이 없습니다. 업무·파일 기록은 뒤에서 확인할 수 있습니다.",10,MUTED,false));
        for(var work:e.finalWorks()) {
            var d=work.deliverable();var c=work.confirmation();var s=work.submission();
            var latest=work.rounds().stream().findFirst().orElse(null);
            var file=c!=null?c.getReview().getFile():latest==null?null:latest.getFile();
            var box=table(2,1,1.6f);headers(box,"제출물 / 담당","기한","현재 상태");
            // 선택 시간이 없으면 날짜만 표시한다. 좁은 기한 열에서 불필요한 문구가 줄을 늘리지 않게 한다.
            String deadline=date(d.getDueDate())+(d.getDueTime()==null?"":"\n"+d.getDueTime().format(DateTimeFormatter.ofPattern("HH:mm"))+" KST");
            row(box,d.getTitle()+" / "+(d.getOwner()==null?"미지정":d.getOwner().getName()),deadline,s!=null?"제출 사실 기록 완료":c!=null?"팀장 최종본 확정":latest!=null?"검토 중 / "+decision(latest.getDecision()):"준비 중");
            compact(box,4);
            String detail="대상 파일: "+(file==null?"미지정":file.getFileName()+" / v"+file.getVersion());
            if(c!=null)detail+="\n확정: "+c.getConfirmedBy().getName()+" / "+time(c.getConfirmedAt())+" KST";
            if(s!=null)detail+=" · 제출 기록: "+time(s.getSubmittedAt())+" KST / "+safe(s.getChannel());
            // 파일·확정 설명이 표에서 떨어져 한 문장만 다음 장에 남지 않도록 함께 배치한다.
            var group=table(1);var wrapper=new PdfPCell();wrapper.setBorder(Rectangle.NO_BORDER);wrapper.setPadding(0);
            wrapper.addElement(box);
            if(file!=null||c!=null||s!=null)wrapper.addElement(p(detail,8,MUTED,false));
            group.addCell(wrapper);group.setSpacingBefore(5);group.setSpacingAfter(0);group.setKeepTogether(true);doc.add(group);
        }
    }

    private void people(Document doc,List<ProjectMember> members,List<Task> tasks,List<FileVault> files,ReportEvidenceReader.Evidence e) throws DocumentException {
        heading(doc,"03","팀원별 역할과 수행 기록");
        doc.add(p("이름순(팀장 우선)으로 정리했습니다. 담당 배정과 실제 수행 기록을 함께 읽어 주세요.",10,MUTED,false));
        int index=0;
        for(var m:members) {
            if(index>0 && index%2==0) {doc.newPage();heading(doc,"03","팀원별 역할과 수행 기록 · 계속");}
            var u=m.getUser();var own=assigned(e,u);
            var box=table(1);box.setKeepTogether(false);box.setHeaderRows(1);
            box.addCell(cell(u.getName()+"  /  "+role(m),true));
            box.addCell(cell("업무 "+own.size()+"건 담당 · "+own.stream().filter(t->"DONE".equals(t.getStatus())).count()+"건 완료  |  파일 "+uploads(files,u)+"회 등록  |  의견·답변 "+feedback(e,u)+"건  |  확인·검토 "+decisions(e,tasks,u)+"건",false));
            String work=own.isEmpty()?"현재 배정된 업무 없음":own.stream().map(t->"• "+t.getTitle()+" ["+status(t.getStatus())+"]").collect(Collectors.joining("\n"));
            box.addCell(cell("담당 업무\n"+work,false));
            String uploaded=files.stream().filter(v->same(v.getUploader(),u)).limit(3).map(v->"• "+v.getFileName()+" / v"+v.getVersion()+" / "+time(v.getUploadedAt())).collect(Collectors.joining("\n"));
            box.addCell(cell("최근 등록 파일\n"+(uploaded.isEmpty()?"등록 기록 없음":uploaded),false));
            long comments=e.comments().stream().filter(c->same(c.getAuthor(),u)).count();
            long resolved=e.comments().stream().filter(c->same(c.getResolvedBy(),u)).count();
            box.addCell(cell("피드백 근거\n업무 대화 "+e.messages().stream().filter(v->same(v.getAuthor(),u)).count()+"건 · 최종 제출물 피드백 "+comments+"건 · 해결 기록 "+resolved+"건",false));
            var review=e.comments().stream().filter(v->same(v.getAuthor(),u)).findFirst();
            var reflection=e.comments().stream().filter(v->same(v.getResolvedBy(),u)).findFirst();
            var recent=e.messages().stream().filter(v->same(v.getAuthor(),u)).findFirst();
            if(review.isPresent())box.addCell(cell("검토 의견 예시 / "+review.get().getDeliverable().getTitle()+"\n“"+excerpt(review.get().getContent(),180)+"”",false));
            else if(reflection.isPresent())box.addCell(cell("수정·반영 기록 예시 / "+reflection.get().getDeliverable().getTitle()+"\n“"+excerpt(safe(reflection.get().getResolutionReason()),180)+"”",false));
            else if(recent.isPresent())box.addCell(cell("최근 대화 예시 / "+recent.get().getTask().getTitle()+"\n“"+excerpt(recent.get().getBody(),180)+"”",false));
            doc.add(box);index++;
        }
        doc.add(p("파일 수는 버전별 등록 횟수입니다. 한 파일을 여러 번 수정하면 각각 집계됩니다. 검토·답변 활동은 아래 기존 4개 집계 항목과 별도로 제시한 근거이며 새 점수에 합산하지 않았습니다.",9,MUTED,false));
    }

    private void method(Document doc,List<ProjectMember> members,Map<UUID,ContributionScore> scores,List<Alert> alerts) throws DocumentException {
        heading(doc,"04","자동 집계 기준과 해석");
        note(doc,"숫자 점수 대신 네 종류의 기록을 확인합니다","저장된 집계는 아래 조건의 충족 여부입니다. 네 항목은 가중치·기여율·성적이 아니며 평가·감점 기준이 아닙니다. ‘기록 없음’은 실제 활동이 없었다는 확정 판단이 아닙니다.");
        var rules=table(1,3);headers(rules,"집계 항목","현재 시스템의 확인 조건");
        row(rules,"업무 완료","담당 업무 중 완료(DONE)된 업무가 1건 이상");
        row(rules,"회의 참석","프로젝트 회의의 50% 이상에 체크인 기록 존재. 회의가 없으면 이 항목은 확인되지 않음");
        row(rules,"파일 등록","프로젝트 파일을 1회 이상 업로드");
        row(rules,"회의 후속 업무","회의에서 생성된 후속 업무 중 담당 업무가 1건 이상 완료");doc.add(rules);
        doc.add(p("확인된 항목의 수는 활동 종류의 폭을 나타냅니다. 모든 항목이 확인되어야 좋은 기여라는 뜻은 아닙니다. 회의를 열지 않은 팀 등 활동 방식에 따른 차이도 함께 고려해 주세요.",9,MUTED,false));
        var matrix=table(1.3f,.8f,.8f,.8f,.9f,1.8f);headers(matrix,"팀원","업무","회의","파일","후속 업무","마지막 집계 (KST)");
        for(var m:members) {
            var s=scores.get(m.getUser().getId());
            row(matrix,m.getUser().getName(),mark(s,s!=null&&s.isTaskParticipated()),mark(s,s!=null&&s.isMeetingParticipated()),
                    mark(s,s!=null&&s.isFileParticipated()),mark(s,s!=null&&s.isActionParticipated()),s==null?"아직 집계되지 않음":time(s.getCalculatedAt()));
        }
        if(members.isEmpty())row(matrix,"현재 팀원 없음","-","-","-","-","-");doc.add(matrix);
        doc.add(p("집계 시각 이후의 활동은 이 표에 아직 반영되지 않을 수 있습니다. 앞의 업무·파일·피드백 근거는 보고서 생성 시점에 조회하므로 저장된 집계와 시점이 다를 수 있습니다. 관찰자는 팀원 집계에서 제외합니다.",9,MUTED,false));
        heading(doc,"05","추가로 함께 확인할 기록");
        long notices=alerts.stream().filter(a->"FREE_RIDE".equals(a.getAlertType())).count();
        if(notices>0)doc.add(p("참여 기록 확인 안내 "+notices+"건: 자동 집계가 포착하지 못한 검토·답변·오프라인 역할이 있는지 함께 확인해 주세요. 개인의 불성실이나 감점을 뜻하지 않습니다.",10,MUTED,false));
        var other=alerts.stream().filter(a->!"FREE_RIDE".equals(a.getAlertType())).toList();
        if(other.isEmpty())doc.add(p("표시할 미해결 추가 알림이 없습니다. 모든 기여가 집계되었다는 뜻은 아닙니다.",10,MUTED,false));
        else {var t=table(1,3);headers(t,"확인 유형","시스템 안내");for(var a:other)row(t,alertLabel(a.getAlertType()),safe(a.getMessage()));doc.add(t);}
        note(doc,"교수자 검토 시 참고","담당 역할, 결과물 내용, 수정·검토 근거와 팀원의 설명을 함께 확인해 주세요. 보고서에 나타나지 않는 오프라인 조사·발표 준비·회의 밖 협의는 별도 자료로 보완할 수 있습니다. 학교 제출 기록은 팀원이 남긴 사실 기록이며 학교의 접수 확인을 대신하지 않습니다.");
    }

    private void appendix(Document doc,List<Task> tasks,List<FileVault> files,ReportEvidenceReader.Evidence e) throws DocumentException {
        heading(doc,"부록 A","업무별 상세 근거");
        if(tasks.isEmpty())doc.add(p("등록된 업무가 없습니다.",10,MUTED,false));
        else {
            var t=table(2.1f,1.1f,.8f,1.2f,1.8f);headers(t,"업무","담당자","상태","마감일","완료 확인");
            for(var task:tasks) {
                String names=e.assignments().stream().filter(a->a.getTask().getId().equals(task.getId())).map(a->a.getUser().getName()).sorted().collect(Collectors.joining(", "));
                row(t,task.getTitle(),names.isEmpty()?"미배정":names,status(task.getStatus()),date(task.getDueDate()),
                        task.getCompletionApprovedBy()==null?"확인 기록 없음":task.getCompletionApprovedBy().getName()+"\n"+time(task.getCompletionApprovedAt()));
            }doc.add(t);
        }
        if(!files.isEmpty()) {
            doc.newPage();heading(doc,"부록 B","파일 버전과 등록 이력");
            doc.add(p("파일명·버전·등록자로 원 기록을 찾을 수 있습니다. 아래 SHA-256은 파일 내용 비교를 위한 식별값이며 작성자의 기여 정도나 학교 접수를 증명하는 값이 아닙니다.",10,MUTED,false));
            int i=0;
            for(var file:files) {
                var t=table(1,3);row(t,"F-"+String.format("%03d",++i),file.getFileName()+" / v"+file.getVersion());
                row(t,"등록 기록",(file.getUploader()==null?"등록자 미상":file.getUploader().getName())+" / "+time(file.getUploadedAt())+" KST");
                PdfPCell label=cell("내용 식별값",false);PdfPCell hash=cell(safe(file.getFileHash()),false);hash.setPhrase(new Phrase(safe(file.getFileHash()),f(7,MUTED,false)));t.addCell(label);t.addCell(hash);t.setKeepTogether(true);doc.add(t);
            }
        }
    }

    private List<Task> assigned(ReportEvidenceReader.Evidence e,User u) {return e.assignments().stream().filter(a->same(a.getUser(),u)).map(TaskAssignee::getTask).distinct().toList();}
    private long uploads(List<FileVault> files,User u) {return files.stream().filter(v->same(v.getUploader(),u)).count();}
    private long feedback(ReportEvidenceReader.Evidence e,User u) {return e.messages().stream().filter(v->same(v.getAuthor(),u)).count()+e.comments().stream().filter(v->same(v.getAuthor(),u)).count();}
    private long decisions(ReportEvidenceReader.Evidence e,List<Task> tasks,User u) {return tasks.stream().filter(t->same(t.getCompletionApprovedBy(),u)).count()+e.finalWorks().stream().flatMap(w->w.rounds().stream()).filter(r->r.getDecision()!=null&&same(r.getDecidedBy(),u)).count();}
    private static boolean same(User a,User b) {return a!=null&&b!=null&&Objects.equals(a.getId(),b.getId());}
    private static String time(OffsetDateTime t) {return t==null?"미기록":t.atZoneSameInstant(ZONE).format(FMT);}
    private static String date(LocalDate d) {return d==null?"미설정":d.toString();}
    private static String safe(String s) {return s==null||s.isBlank()?"미기록":s;}
    private static String join(String a,String b) {return safe(a)+" / "+safe(b);}
    private static String role(ProjectMember m) {return "LEADER".equals(m.getRole())?"팀장":"팀원";}
    private static String mark(ContributionScore s,boolean b) {return s==null?"미집계":b?"확인":"기록 없음";}
    private static String excerpt(String s,int n) {return s==null?"":s.length()>n?s.substring(0,n)+"...":s;}
    private static String status(String s) {return "DONE".equals(s)?"완료":"IN_PROGRESS".equals(s)?"진행 중":"할 일";}
    private static String decision(String s) {return "APPROVED".equals(s)?"승인":"CHANGES_REQUESTED".equals(s)?"수정 요청":"결정 전";}
    private static String alertLabel(String s) {return switch(s){case "OVERLOAD"->"업무 분배 확인";case "DROPOUT"->"활동 기록 확인";case "TAMPER"->"파일 내용 확인";default->"추가 확인";};}
    private static Font f(float size,Color color,boolean bold) {return new Font(NANUM,size,bold?Font.BOLD:Font.NORMAL,color);}
    private static Paragraph p(String text,float size,Color color,boolean bold) {var p=new Paragraph(text,f(size,color,bold));p.setLeading(size*1.4f);p.setSpacingAfter(7);return p;}
    private static void heading(Document doc,String number,String text) throws DocumentException {var p=p(number+"  "+text,15,BLUE,true);p.setSpacingBefore(6);p.setKeepTogether(true);doc.add(p);}
    private static PdfPTable table(float... widths) {var t=new PdfPTable(widths);t.setWidthPercentage(100);t.setSpacingBefore(4);t.setSpacingAfter(8);t.setSplitLate(false);return t;}
    private static PdfPCell cell(String text,boolean header) {var c=new PdfPCell(new Phrase(safe(text),f(header?9:9.5f,header?WHITE:INK,header)));c.setBackgroundColor(header?BLUE:WHITE);c.setPadding(9);c.setLeading(0,1.45f);c.setBorderColor(LINE);c.setBorderWidth(.5f);c.setVerticalAlignment(Element.ALIGN_TOP);return c;}
    private static void row(PdfPTable t,String... cells) {for(String s:cells)t.addCell(cell(s,false));}
    private static void headers(PdfPTable t,String... cells) {for(String s:cells)t.addCell(cell(s,true));t.setHeaderRows(1);}
    private static void compact(PdfPTable table,float padding) {for(var row:table.getRows())for(var c:row.getCells())if(c!=null)c.setPadding(padding);}
    private static void note(Document d,String title,String body) throws DocumentException {var t=table(1);var c=cell(title+"\n"+body,false);c.setBackgroundColor(PALE);t.addCell(c);t.setKeepTogether(true);d.add(t);}
    private static class Footer extends PdfPageEventHelper {
        private final String id;Footer(String id){this.id=id;}
        @Override public void onEndPage(PdfWriter writer,Document doc) {
            var cb=writer.getDirectContent();cb.setColorStroke(LINE);cb.moveTo(doc.left(),36);cb.lineTo(doc.right(),36);cb.stroke();
            ColumnText.showTextAligned(cb,Element.ALIGN_LEFT,new Phrase("Team Blackbox  |  "+id,f(7,MUTED,false)),doc.left(),24,0);
            ColumnText.showTextAligned(cb,Element.ALIGN_RIGHT,new Phrase(writer.getPageNumber()+"쪽",f(8,MUTED,false)),doc.right(),24,0);
        }
    }
}
