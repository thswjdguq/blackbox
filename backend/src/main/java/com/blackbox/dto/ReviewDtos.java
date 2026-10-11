package com.blackbox.dto;

import com.blackbox.entity.*;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

/** 검토 회차(K-10) 요청·응답. 값이 없는 file·decision은 생략하지 않고 null로 내보낸다. */
public final class ReviewDtos {
    private ReviewDtos() {}
    // fileId가 null이거나 빠져 있으면 파일 없는 중간 검토다
    public record OpenRequest(UUID fileId) {}
    public record DecisionRequest(@NotNull @Pattern(regexp = "APPROVED|CHANGES_REQUESTED") String decision) {}
    public record Person(UUID userId, String name) {
        static Person of(User u) { return new Person(u.getId(), u.getName()); }
    }
    public record FileSummary(UUID fileId, String fileName, int version, String shortHash, UUID uploaderId, String uploaderName) {}
    public record Decision(String result, Person decidedBy, OffsetDateTime decidedAt) {}
    public record Round(UUID id, int roundNo, boolean latest, FileSummary file, Person openedBy,
            OffsetDateTime openedAt, Decision decision, boolean currentBasis) {
        public static Round from(ReviewRound r, boolean latest, boolean currentBasis) {
            FileVault f = r.getFile();
            FileSummary file = f == null ? null : new FileSummary(f.getId(), f.getFileName(), f.getVersion(),
                    f.getFileHash().substring(0, 7), f.getUploader().getId(), f.getUploader().getName());
            Decision decision = r.getDecision() == null ? null
                    : new Decision(r.getDecision(), Person.of(r.getDecidedBy()), r.getDecidedAt());
            return new Round(r.getId(), r.getRoundNo(), latest, file, Person.of(r.getOpenedBy()), r.getOpenedAt(), decision, currentBasis);
        }
    }
    public record ListResponse(String deliverableStatus, long unresolvedCommentCount, List<Round> rounds) {}
}
