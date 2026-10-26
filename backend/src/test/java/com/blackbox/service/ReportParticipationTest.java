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
        for(String forbidden:List.of("무임승차","미참여","관찰합성","탈퇴합성","님이","해결된알림표시금지"))
            assertFalse(text.contains(forbidden),forbidden);
        assertEquals("팀장합성님이 미참여했습니다",legacy.getMessage());
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
    @Test void emptyAlertsDoNotAssertBalancedContribution() throws Exception {
        User leader=user("팀장합성");
        when(members.findByProject(project)).thenReturn(List.of(member(leader,"LEADER")));
        String text=render(leader,"empty-participation.pdf");
        assertFalse(text.contains("균형잡혀")); assertTrue(text.contains("모든기여가집계되었다는뜻은아닙니다"));
    }
}
