package com.blackbox.dto;

import com.blackbox.dto.ReviewDtos.FileSummary;
import com.blackbox.dto.ReviewDtos.Person;
import com.blackbox.entity.DeliverableConfirmation;
import com.blackbox.entity.DeliverableSubmission;
import com.blackbox.entity.ReviewRound;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 최종 확정과 제출 기록(K-20 3장). 조회, 확정, 제출 기록이 같은 객체를 돌려준다.
 * 값이 없는 candidate·confirmation·submission은 생략하지 않고 null로 내보낸다.
 */
public final class ConfirmationDtos {
    private ConfirmationDtos() {}
    public record Check(String code, boolean passed, String detail) {}
    /** 지금 확정하면 확정본이 될 회차와 파일. file의 모양은 K-10 3장과 같다. */
    public record Candidate(UUID reviewId, int roundNo, FileSummary file) {
        public static Candidate of(ReviewRound r) {
            return new Candidate(r.getId(), r.getRoundNo(), FileSummary.of(r.getFile()));
        }
    }
    public record Confirmation(UUID reviewId, int roundNo, FileSummary file, Person confirmedBy,
            OffsetDateTime confirmedAt, boolean fileStillLatest) {
        public static Confirmation from(DeliverableConfirmation c, boolean fileStillLatest) {
            ReviewRound r = c.getReview();
            return new Confirmation(r.getId(), r.getRoundNo(), FileSummary.of(r.getFile()),
                    Person.of(c.getConfirmedBy()), c.getConfirmedAt(), fileStillLatest);
        }
    }
    public record Submission(String channel, OffsetDateTime submittedAt, String note, Person recordedBy, OffsetDateTime recordedAt) {
        public static Submission from(DeliverableSubmission s) {
            return new Submission(s.getChannel(), s.getSubmittedAt(), s.getNote(), Person.of(s.getRecordedBy()), s.getRecordedAt());
        }
    }
    public record Response(String status, boolean canConfirm, List<Check> checks, Candidate candidate,
            boolean soleContributor, Confirmation confirmation, Submission submission) {}
}
