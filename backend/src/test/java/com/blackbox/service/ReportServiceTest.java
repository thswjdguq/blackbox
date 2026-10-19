package com.blackbox.service;

import com.blackbox.entity.Alert;
import com.blackbox.entity.ContributionScore;
import com.blackbox.entity.FileVault;
import com.blackbox.entity.Task;
import com.blackbox.entity.User;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 교수가 받는 PDF 리포트의 판정 두 가지를 본다. PDF는 만들지 않는다. */
class ReportServiceTest {
    final UUID project = UUID.randomUUID();

    /** 같은 기록이면 조회 순서나 점수의 계산 시각이 달라도 같은 해시다. 기록이 바뀌면 해시도 바뀐다 */
    @Test void sameRecordsGiveTheSameHash() {
        List<ContributionScore> scores = new ArrayList<>(List.of(score(true), score(false), score(false)));
        List<Task> tasks = new ArrayList<>(List.of(task("DONE"), task("TODO")));
        List<FileVault> files = List.of(file("a1"));
        List<Alert> alerts = List.of(alert());
        String first = ReportService.computeReportHash(project, scores, tasks, files, alerts);

        Collections.reverse(scores);
        Collections.reverse(tasks);
        scores.forEach(s -> s.setCalculatedAt(OffsetDateTime.now().plusMinutes(30)));   // 스케줄러가 다시 계산함
        assertEquals(first, ReportService.computeReportHash(project, scores, tasks, files, alerts));

        alerts.get(0).setResolvedAt(OffsetDateTime.now());   // 경보가 풀림
        String resolved = ReportService.computeReportHash(project, scores, tasks, files, alerts);
        assertNotEquals(first, resolved);

        tasks.get(0).setStatus("IN_PROGRESS");
        assertNotEquals(resolved, ReportService.computeReportHash(project, scores, tasks, files, alerts));
    }

    /** 당사자가 읽음으로 표시해도 해제되기 전까지는 활성 경보다 */
    @Test void aReadButUnresolvedAlertIsStillActive() {
        Alert read = alert(), resolved = alert(), fresh = alert();
        read.setRead(true);
        resolved.setRead(true);
        resolved.setResolvedAt(OffsetDateTime.now());
        resolved.setResolveReason("이팀원님이 참여 활동을 완료했습니다");

        assertEquals(List.of(read, fresh), ReportService.activeAlerts(List.of(read, resolved, fresh)));
    }

    private ContributionScore score(boolean full) {
        User user = new User();
        user.setId(UUID.randomUUID());
        ContributionScore s = new ContributionScore();
        s.setUser(user);
        s.setTaskParticipated(full);
        s.setParticipationLevel(full ? "FULL" : "NONE");
        s.setCalculatedAt(OffsetDateTime.now());
        return s;
    }

    private Task task(String status) {
        Task t = new Task();
        t.setId(UUID.randomUUID());
        t.setStatus(status);
        t.setUpdatedAt(OffsetDateTime.parse("2026-10-18T10:00:00+09:00"));
        return t;
    }

    private FileVault file(String hash) {
        FileVault v = new FileVault();
        v.setId(UUID.randomUUID());
        v.setFileHash(hash);
        v.setUploadedAt(OffsetDateTime.parse("2026-10-18T10:00:00+09:00"));
        return v;
    }

    private Alert alert() {
        Alert a = new Alert();
        a.setId(UUID.randomUUID());
        a.setAlertType("FREE_RIDE");
        a.setMessage("경보");
        return a;
    }
}
