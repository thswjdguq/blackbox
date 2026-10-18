package com.blackbox.service;

import com.blackbox.entity.Alert;
import com.blackbox.entity.ContributionScore;
import com.blackbox.entity.Deliverable;
import com.blackbox.entity.DeliverableConfirmation;
import com.blackbox.entity.DeliverableSubmission;
import com.blackbox.entity.FileVault;
import com.blackbox.entity.Project;
import com.blackbox.entity.ReviewRound;
import com.blackbox.entity.Task;
import com.blackbox.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 교수가 받는 PDF 리포트의 판정을 본다. PDF는 만들지 않는다. */
class ReportServiceTest {
    final UUID project = UUID.randomUUID();
    final ReportService.DeliverableRecords noDeliverables = new ReportService.DeliverableRecords(List.of(), List.of(), Map.of(), List.of(), List.of());

    /** 같은 기록이면 조회 순서나 점수의 계산 시각이 달라도 같은 해시다. 기록이 바뀌면 해시도 바뀐다 */
    @Test void sameRecordsGiveTheSameHash() {
        List<ContributionScore> scores = new ArrayList<>(List.of(score(true), score(false), score(false)));
        List<Task> tasks = new ArrayList<>(List.of(task("DONE"), task("TODO")));
        List<FileVault> files = List.of(file("a1"));
        List<Alert> alerts = List.of(alert());
        String first = ReportService.computeReportHash(project, scores, tasks, files, alerts, noDeliverables);

        Collections.reverse(scores);
        Collections.reverse(tasks);
        scores.forEach(s -> s.setCalculatedAt(OffsetDateTime.now().plusMinutes(30)));   // 스케줄러가 다시 계산함
        assertEquals(first, ReportService.computeReportHash(project, scores, tasks, files, alerts, noDeliverables));

        alerts.get(0).setResolvedAt(OffsetDateTime.now());   // 경보가 풀림
        String resolved = ReportService.computeReportHash(project, scores, tasks, files, alerts, noDeliverables);
        assertNotEquals(first, resolved);

        tasks.get(0).setStatus("IN_PROGRESS");
        assertNotEquals(resolved, ReportService.computeReportHash(project, scores, tasks, files, alerts, noDeliverables));
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

    /** 제출물마다 회차 열림·결정·확정·제출 기록이 시각 순으로 나오고, 다른 제출물의 기록은 섞이지 않는다 */
    @Test void deliverableEventsAreInTimeOrder() {
        Deliverable report = deliverable("중간 보고서"), other = deliverable("발표 자료");
        ReviewRound first = round(report, 1, "2026-10-01T10:00:00+09:00"), second = round(report, 2, "2026-10-03T10:00:00+09:00");
        first.decide("CHANGES_REQUESTED", user("이팀원"));
        first.setDecidedAt(OffsetDateTime.parse("2026-10-02T10:00:00+09:00"));
        second.decide("APPROVED", user("이팀원"));
        second.setDecidedAt(OffsetDateTime.parse("2026-10-04T10:00:00+09:00"));
        ReviewRound otherRound = round(other, 1, "2026-10-01T11:00:00+09:00");
        DeliverableConfirmation confirmed = DeliverableConfirmation.of(second, user("김팀장"), OffsetDateTime.parse("2026-10-05T10:00:00+09:00"));
        DeliverableSubmission submitted = DeliverableSubmission.of(confirmed, "LMS", OffsetDateTime.parse("2026-10-06T09:00:00+09:00"),
                null, user("김팀장"), OffsetDateTime.parse("2026-10-06T10:00:00+09:00"));
        Map<UUID, Integer> feedback = new HashMap<>(Map.of(first.getId(), 3));
        var records = new ReportService.DeliverableRecords(List.of(report, other), List.of(first, otherRound, second), feedback,
                List.of(confirmed), List.of(submitted));

        assertEquals(List.of("검토 1회차 열림 — 파일 없음 · 피드백 3건", "검토 1회차 수정 요청", "검토 2회차 열림 — 파일 없음 · 피드백 0건",
                        "검토 2회차 승인", "최종 확정 — 2회차 승인본", "제출 기록 — LMS, 낸 시각 2026-10-06 09:00"),
                ReportService.events(report, records).stream().map(ReportService.Event::what).toList());
        assertEquals(1, ReportService.events(other, records).size());

        // 해시에 제출물 쪽 기록이 들어간다
        String before = ReportService.computeReportHash(project, List.of(), List.of(), List.of(), List.of(), records);
        assertNotEquals(before, ReportService.computeReportHash(project, List.of(), List.of(), List.of(), List.of(), noDeliverables));
        feedback.put(second.getId(), 1);
        assertNotEquals(before, ReportService.computeReportHash(project, List.of(), List.of(), List.of(), List.of(), records));
    }

    private Deliverable deliverable(String title) {
        Deliverable d = new Deliverable();
        d.setId(UUID.randomUUID());
        d.setTitle(title);
        d.setDueDate(LocalDate.parse("2026-10-10"));
        return d;
    }

    private ReviewRound round(Deliverable d, int no, String openedAt) {
        Project p = new Project();
        p.setId(project);
        ReviewRound r = new ReviewRound();
        r.setId(UUID.randomUUID());
        r.setProject(p);
        r.setDeliverable(d);
        r.setRoundNo(no);
        r.setOpenedBy(user("박팀원"));
        r.setOpenedAt(OffsetDateTime.parse(openedAt));
        return r;
    }

    private User user(String name) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setName(name);
        return u;
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
