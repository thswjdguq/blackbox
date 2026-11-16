package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.*;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportParticipationTest {
    final ProjectAccessChecker access = mock(ProjectAccessChecker.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final ContributionScoreRepository scores = mock(ContributionScoreRepository.class);
    final AlertRepository alerts = mock(AlertRepository.class);
    final ReportEvidenceReader evidence = mock(ReportEvidenceReader.class);
    final ReportService service = new ReportService(access, members, mock(TaskRepository.class),
            mock(FileVaultRepository.class), scores, alerts, evidence);
    final Project project = new Project();
    @BeforeEach void emptyEvidence() {
        when(evidence.read(any(),anyList())).thenReturn(new ReportEvidenceReader.Evidence(List.of(),List.of(),List.of(),List.of()));
    }

    User user(String name) {
        User user = new User(); user.setId(UUID.randomUUID()); user.setName(name);
        user.setEmail("synthetic@example.test"); return user;
    }
    ProjectMember member(User user, String role) {
        ProjectMember member = new ProjectMember(); member.setProject(project);
        member.setUser(user); member.setRole(role); return member;
    }
    ContributionScore score(User user) {
        ContributionScore score = new ContributionScore(); score.setUser(user); score.setProject(project);
        score.setCalculatedAt(OffsetDateTime.parse("2026-10-17T08:33:00Z")); return score;
    }
    Alert alert(User user, String type, String message) {
        Alert alert = new Alert(); alert.setUser(user); alert.setProject(project);
        alert.setAlertType(type); alert.setSeverity("MEDIUM"); alert.setMessage(message); return alert;
    }
    String render(User requester, String fileName) throws Exception {
        project.setId(UUID.randomUUID()); project.setName("합성 참여 기록 검증");
        when(access.getProject(project.getId())).thenReturn(project);
        byte[] bytes = service.generateReport(project.getId(), requester);
        Path output = Path.of("build/reports/participation", fileName);
        Files.createDirectories(output.getParent()); Files.write(output, bytes);
        try (PdfReader reader = new PdfReader(bytes)) {
            StringBuilder text = new StringBuilder(); PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int i = 1; i <= reader.getNumberOfPages(); i++) text.append(extractor.getTextFromPage(i));
            return text.toString().replaceAll("\\s+", "");
        }
    }
    @Test void reportUsesCurrentContributorsNeutralLegacyMessagesAndKoreanTime() throws Exception {
        User leader=user("팀장합성"), observer=user("관찰합성"), removed=user("탈퇴합성");
        when(members.findByProject(project)).thenReturn(List.of(member(leader,"LEADER"),member(observer,"OBSERVER")));
        when(scores.findByProjectOrderByTotalScoreDesc(project)).thenReturn(List.of(score(leader),score(observer),score(removed)));
        Alert legacy=alert(leader,"FREE_RIDE","팀장합성님이 미참여했습니다");
        Alert resolved=alert(leader,"DROPOUT","해결된알림표시금지"); resolved.setResolvedAt(OffsetDateTime.now());
        when(alerts.findByProjectOrderByCreatedAtDesc(project)).thenReturn(List.of(legacy,
                alert(observer,"FREE_RIDE","관찰합성님이 미참여했습니다"),
                alert(removed,"FREE_RIDE","탈퇴합성님이 미참여했습니다"),
                alert(observer,"TAMPER","무결성확인필요"), resolved));
        String text=render(leader,"neutral-participation.pdf");
        assertTrue(text.contains("팀원수1명")); assertTrue(text.contains("참여기록확인"));
        assertTrue(text.contains("2026-10-1717:33")); assertTrue(text.contains("KST"));
        assertTrue(text.contains("평가·감점기준이아닙니다")); assertTrue(text.contains("무결성확인필요"));
        // FREE_RIDE 안내는 화면의 경보와 같은 문구다(AlertService.PARTICIPATION_NOTICE)
        assertTrue(text.contains("참여기록확인안내1건:" + AlertService.PARTICIPATION_NOTICE.replaceAll("\\s+", "")), text);
        for(String forbidden:List.of("무임승차","미참여","관찰합성","탈퇴합성","님이","해결된알림표시금지"))
            assertFalse(text.contains(forbidden),forbidden);
        assertEquals("팀장합성님이 미참여했습니다",legacy.getMessage());
        assertTrue(text.contains("담당업무중완료(DONE)된업무가1건이상"), "항목이 없는 옛 집계는 지금 표로 그린다");
        verify(alerts,never()).save(any());
    }
    @Test void readingAnAlertDoesNotRemoveItButResolvingItDoes() throws Exception {
        User leader=user("팀장합성");
        when(members.findByProject(project)).thenReturn(List.of(member(leader,"LEADER")));
        Alert active=alert(leader,"TAMPER","파일내용재확인필요");
        when(alerts.findByProjectOrderByCreatedAtDesc(project)).thenReturn(List.of(active));
        assertTrue(render(leader,"before-read.pdf").contains("파일내용재확인필요"));
        active.setRead(true);
        assertTrue(render(leader,"after-read.pdf").contains("파일내용재확인필요"));
        active.setResolvedAt(OffsetDateTime.now());
        assertFalse(render(leader,"after-resolved.pdf").contains("파일내용재확인필요"));
        assertTrue(active.isRead());assertNotNull(active.getResolvedAt());
        verify(alerts,never()).save(any());
    }
    /** 저장된 계산에 항목(K-40 items)이 있으면 그 이름·설명·횟수로 표를 그리고 계산 기준을 적는다. 기준 문구를 PDF에 따로 두지 않는다 */
    @Test void itemizedScoresDrawTheTableFromTheStoredItems() throws Exception {
        User leader=user("팀장합성"), member=user("팀원합성"), fresh=user("신입합성");
        when(members.findByProject(project)).thenReturn(List.of(member(leader,"LEADER"),member(member,"MEMBER"),member(fresh,"MEMBER")));
        ContributionScore a=itemized(leader,true,true,3,3,4,new ScoreReviewActivityReader.Counts(0,0,0,0,0,0));
        ContributionScore b=itemized(member,false,false,0,1,4,new ScoreReviewActivityReader.Counts(1,1,0,0,0,0));
        when(scores.findByProjectOrderByTotalScoreDesc(project)).thenReturn(List.of(a,b));
        String text=render(leader,"itemized-participation.pdf");
        for(var i:a.getItems()) {
            assertTrue(text.contains(i.label().replaceAll("\\s+","")), i.label());
            assertTrue(text.contains(i.description().replaceAll("\\s+","")), i.description());
        }
        assertTrue(text.contains("계산기준:"+ScoreSnapshotService.CRITERIA_VERSION));
        assertTrue(text.contains("확인·3"), "판정 항목은 확인 여부와 횟수");
        assertTrue(text.contains("확인·3/4") && text.contains("기록없음·1/4"), "회의는 체크인/전체");
        assertTrue(text.contains("0건") && text.contains("2건"), "판정에 넣지 않는 항목은 횟수만");
        assertTrue(text.contains("미집계"), "아직 계산되지 않은 팀원");
        assertTrue(text.contains("숫자점수대신4종류의기록을확인합니다"), "판정에 들어가는 항목 수를 저장된 항목에서 센다");
        assertTrue(text.contains("검토·답변활동은참여수준판정에넣지않습니다"));
        assertFalse(text.contains("담당업무중완료(DONE)된업무가1건이상"), "PDF에 따로 적은 옛 기준 문구를 쓰지 않는다");
    }

    /** 회의를 열지 않은 팀은 회의 칸이 "대상 없음"이다. 팀원 중 하나라도 항목 없는 옛 집계면 지금 표로 그린다 */
    @Test void noMeetingsAndMixedScores() throws Exception {
        User leader=user("팀장합성"), member=user("팀원합성");
        when(members.findByProject(project)).thenReturn(List.of(member(leader,"LEADER"),member(member,"MEMBER")));
        ContributionScore a=itemized(leader,true,false,1,0,0,new ScoreReviewActivityReader.Counts(0,0,0,0,0,0));
        when(scores.findByProjectOrderByTotalScoreDesc(project)).thenReturn(List.of(a));
        String text=render(leader,"no-meetings.pdf");
        assertTrue(text.contains("대상없음·0/0"), "회의가 없으면 기록 없음으로 보이지 않는다");
        assertFalse(text.contains("기록없음·0/0"));

        when(scores.findByProjectOrderByTotalScoreDesc(project)).thenReturn(List.of(a, score(member)));
        String mixed=render(leader,"mixed-scores.pdf");
        assertTrue(mixed.contains("담당업무중완료(DONE)된업무가1건이상"), "항목 없는 옛 집계가 섞이면 지금 표");
        assertFalse(mixed.contains("계산기준:"));
    }

    ContributionScore itemized(User user, boolean task, boolean meeting, long taskDone, long checkedIn, long meetings, ScoreReviewActivityReader.Counts reviews) {
        ContributionScore score=score(user); score.setTaskParticipated(task); score.setMeetingParticipated(meeting);
        score.setItems(ScoreSnapshotService.items(score,new ScoreSnapshotService.Extra(0,reviews),taskDone,checkedIn,meetings,0));
        score.setCriteriaVersion(ScoreSnapshotService.CRITERIA_VERSION); return score;
    }

    @Test void emptyAlertsDoNotAssertBalancedContribution() throws Exception {
        User leader=user("팀장합성");
        when(members.findByProject(project)).thenReturn(List.of(member(leader,"LEADER")));
        String text=render(leader,"empty-participation.pdf");
        assertFalse(text.contains("균형잡혀")); assertTrue(text.contains("모든기여가집계되었다는뜻은아닙니다"));
    }
}
