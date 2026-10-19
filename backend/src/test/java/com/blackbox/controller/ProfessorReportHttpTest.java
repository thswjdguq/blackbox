package com.blackbox.controller;

import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 별도 PostgreSQL에서 보고서 조회·권한·증거 ZIP을 확인하고 합성 시각 검수본을 저장한다. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ProfessorReportHttpTest {
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    Project project;
    User leader,observer,outsider;
    final OffsetDateTime at=OffsetDateTime.parse("2026-10-19T03:00:00Z");
    @BeforeEach void setup() {
        leader=user(em,"김민지");var worker=user(em,"이현우");var peer=user(em,"박유나");var fourth=user(em,"최지호");
        observer=user(em,"관찰자표시금지");outsider=user(em,"외부인표시금지");
        var people=List.of(leader,worker,peer,fourth);project=project(em,leader);
        project.setName("[합성 예시] 지역 상권 분석 팀 프로젝트");project.setCourseName("프로젝트 기반 문제 해결");project.setSemester("2026-2학기");
        project.setStartDate(LocalDate.of(2026,10,1));project.setEndDate(LocalDate.of(2026,10,19));
        member(em,project,observer,"OBSERVER");
        var d=deliverable(em,project,"지역 상권 분석 최종 보고서");d.setOwner(leader);
        String[] titles={"조사 계획 및 결과 통합","현장 조사와 인터뷰 정리","분석표와 출처 검토","발표 자료 및 이미지 정리"};
        for(int i=0;i<people.size();i++) {
            var u=people.get(i);member(em,project,u,i==0?"LEADER":"MEMBER");
            var t=new Task();t.setProject(project);t.setTitle(titles[i]);t.setStatus("DONE");t.setPriority("MEDIUM");t.setCreatedBy(leader);t.setDeliverable(d);
            t.setDueDate(LocalDate.of(2026,10,18));t.setCompletionCriteria("조사 결과와 출처 확인");t.setCompletedAt(at);t.setCompletionApprovedAt(at);t.setCompletionApprovedBy(i==0?peer:leader);em.persist(t);
            var assignment=new TaskAssignee();assignment.setTask(t);assignment.setUser(u);em.persist(assignment);
            file(em,project,u,"조사결과_"+(i+1)+".txt",1,(char)('a'+i));
            var m=new TaskMessage();m.setProject(project);m.setTask(t);m.setAuthor(u);m.setBody("출처와 조사 내용을 보완했습니다. 비교표의 기준과 결과 파일을 함께 확인해 주세요.");m.setKind("COMMENT");m.setCreatedAt(at);em.persist(m);
            var s=new ContributionScore();s.setUser(u);s.setProject(project);s.setTaskParticipated(true);s.setFileParticipated(true);s.setMeetingParticipated(i%2==0);s.setActionParticipated(i==0);s.setParticipationLevel(i==0?"FULL":"PARTIAL");s.setCalculatedAt(at);em.persist(s);
        }
        var finalFile=file(em,project,leader,"상권분석_최종보고서.pdf",2,'f');
        var r=round(em,d,1,finalFile,leader,"APPROVED");r.decide("APPROVED",peer);
        var c=new ReviewComment();c.setProject(project);c.setDeliverable(d);c.setReview(r);c.setAuthor(peer);c.setContent("설문 표본 수와 자료 출처를 밝혀주세요.");c.setResolvedBy(leader);c.setResolvedAt(at);c.setResolutionReason("최종본 3쪽에 표본 수와 참고 문헌을 추가했습니다.");em.persist(c);
        var confirmation=DeliverableConfirmation.of(r,leader,at);em.persist(confirmation);
        em.persist(DeliverableSubmission.of(confirmation,"합성 LMS 제출 예시",at,"실제 학교 제출을 하지 않은 보고서 검수용 합성 기록",leader,at));em.flush();
    }
    @Test void reportContainsReadableEvidenceAndPreservesData() throws Exception {
        byte[] pdf=mvc.perform(get("/api/projects/{id}/report",project.getId()).header("Authorization",token(leader)))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        Path file=Path.of("build/reports/professor-report/professor-contribution-example.pdf");Files.createDirectories(file.getParent());Files.write(file,pdf);
        try(var reader=new PdfReader(pdf)) {
            var extract=new PdfTextExtractor(reader);StringBuilder all=new StringBuilder();for(int p=1;p<=reader.getNumberOfPages();p++)all.append(extract.getTextFromPage(p));
            String text=all.toString().replaceAll("\\s+","");
            assertTrue(text.contains("팀프로젝트기여활동보고서"));assertTrue(text.contains("최종제출물진행현황"));
            assertTrue(text.contains("기여율(%)이나100점성적을산출하지않습니다"));assertTrue(text.contains("이현우"));assertTrue(text.contains("박유나"));
            assertTrue(text.contains("2026-10-1912:00"));assertTrue(text.contains("제출사실기록완료"));
            assertFalse(text.contains("관찰자표시금지"));assertFalse(text.contains("외부인표시금지"));assertFalse(text.contains("무임승차"));assertFalse(text.contains("/ ?"));
            assertTrue(reader.getNumberOfPages()>=4);
        }
        assertEquals(4L,em.createQuery("select count(s) from ContributionScore s where s.project=:p",Long.class).setParameter("p",project).getSingleResult());
    }
    @Test void outsiderCannotDownloadAndZipContainsNewReport() throws Exception {
        mvc.perform(get("/api/projects/{id}/report",project.getId()).header("Authorization",token(outsider))).andExpect(status().isForbidden());
        byte[] zip=mvc.perform(get("/api/projects/{id}/evidence-package",project.getId()).header("Authorization",token(observer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        boolean found=false;try(var in=new ZipInputStream(new ByteArrayInputStream(zip))) {for(var entry=in.getNextEntry();entry!=null;entry=in.getNextEntry()) {
            if(entry.getName().endsWith("README.txt")) {
                String readme=new String(in.readAllBytes(),StandardCharsets.UTF_8);
                assertTrue(readme.contains("팀원 수     : 4명"));assertTrue(readme.contains("KST"));assertTrue(readme.contains("기여 활동 보고서"));
            }
            if(!entry.getName().endsWith("contribution-report.pdf"))continue;
            found=true;try(var reader=new PdfReader(in.readAllBytes())) {assertTrue(new PdfTextExtractor(reader).getTextFromPage(1).replaceAll("\\s+","").contains("팀프로젝트기여활동보고서"));}
        }}assertTrue(found);
    }
    @Test void longTitlesAndSharedAssignmentsRemainReadableWithoutChangingCounts() throws Exception {
        var task=em.createQuery("select t from Task t where t.project=:p order by t.title",Task.class).setParameter("p",project).getResultList().get(0);
        task.setTitle("긴 업무 제목의 줄바꿈과 공동 담당 표시를 확인합니다. ".repeat(6)+"끝표시");
        var joint=new TaskAssignee();joint.setTask(task);joint.setUser(observer);em.persist(joint);em.flush();
        deliverable(em,project,"둘째 제출물 발표 슬라이드");em.flush();
        byte[] bytes=mvc.perform(get("/api/projects/{id}/report",project.getId()).header("Authorization",token(leader)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        Path stress=Path.of("build/reports/professor-report/long-title-report.pdf");Files.createDirectories(stress.getParent());Files.write(stress,bytes);
        try(var reader=new PdfReader(bytes)) {
            var all=new StringBuilder();var extract=new PdfTextExtractor(reader);
            for(int i=1;i<=reader.getNumberOfPages();i++)all.append(extract.getTextFromPage(i));
            assertTrue(all.toString().replaceAll("\\s+","").contains("끝표시"));
            // 원 기록 부록은 당시 담당자를 보존하지만 현재 팀원 수·평가 대상에는 관찰자를 넣지 않는다.
            assertFalse(extract.getTextFromPage(1).contains("관찰자표시금지"));
            assertTrue(extract.getTextFromPage(1).contains("둘째 제출물"));
            assertTrue(extract.getTextFromPage(2).replaceAll("\\s+","").contains("팀원별역할과수행기록"));
        }
        assertEquals(4L,em.createQuery("select count(t) from Task t where t.project=:p",Long.class).setParameter("p",project).getSingleResult());
    }
    private String token(User user){return "Bearer "+jwt.generateAccessToken(user.getEmail());}
}
