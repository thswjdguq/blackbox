package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** 보고서가 사용하는 원 기록. 계산/저장 없이 요청 프로젝트의 자료만 읽는다. */
@Component @RequiredArgsConstructor
public class ReportEvidenceReader {
    private final TaskAssigneeRepository assignments;
    private final TaskMessageRepository messages;
    private final ReviewCommentRepository comments;
    private final DeliverableRepository deliverables;
    private final ReviewRoundRepository rounds;
    private final DeliverableConfirmationRepository confirmations;
    private final DeliverableSubmissionRepository submissions;
    public record FinalWork(Deliverable deliverable, List<ReviewRound> rounds,
                            DeliverableConfirmation confirmation, DeliverableSubmission submission) {}
    public record Evidence(List<TaskAssignee> assignments, List<TaskMessage> messages,
                           List<ReviewComment> comments, List<FinalWork> finalWorks) {}
    public Evidence read(Project project, List<Task> tasks) {
        var works=deliverables.findByProjectOrderByDueDateAscCreatedAtAsc(project).stream().map(d ->
                new FinalWork(d,rounds.findByDeliverableOrderByRoundNoDesc(d),
                        confirmations.findById(d.getId()).orElse(null),submissions.findById(d.getId()).orElse(null))).toList();
        return new Evidence(tasks.isEmpty()?List.of():assignments.findByTaskIn(tasks),
                messages.findByProjectOrderByCreatedAtDesc(project),comments.findByProjectIdOrderByCreatedAtDesc(project.getId()),works);
    }
}
