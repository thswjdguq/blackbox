package com.blackbox.dto;

import com.blackbox.entity.FileVault;
import com.blackbox.entity.ProjectBrief;
import com.blackbox.entity.User;
import com.blackbox.service.DeliverableStatusReader;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 과제 안내문(K-31 2장) */
public final class BriefDtos {
    private BriefDtos() {}

    /**
     * 빈 값과 길이는 앞뒤 공백(전각 공백 포함)을 뗀 뒤 서비스가 확인한다. 검증 주석은 공백을 ASCII로만 판단하고,
     * 본문의 상한은 설정(app.agent.brief-max-length)이기 때문이다
     */
    public record SaveRequest(@NotNull(message = "제목을 입력해 주세요") String title,
            @NotNull(message = "본문을 입력해 주세요") String body, UUID sourceFileId) {}

    public record Response(UUID id, String title, String body, SourceFile sourceFile, Person createdBy, OffsetDateTime createdAt,
            Person updatedBy, OffsetDateTime updatedAt, String bodyHash, Usage usage, boolean locked) {
        public static Response from(ProjectBrief b, Usage usage) {
            boolean locked = usage.deliverables().stream().anyMatch(d -> DeliverableStatusReader.isConfirmed(d.status()));
            return new Response(b.getId(), b.getTitle(), b.getBody(), SourceFile.of(b.getSourceFile()), Person.of(b.getCreatedBy()),
                    b.getCreatedAt(), Person.of(b.getUpdatedBy()), b.getUpdatedAt(), b.getBodyHash(), usage, locked);
        }
    }

    public record SourceFile(UUID id, String fileName, Integer version) {
        static SourceFile of(FileVault f) { return f == null ? null : new SourceFile(f.getId(), f.getFileName(), f.getVersion()); }
    }

    public record Person(UUID id, String name) {
        static Person of(User u) { return new Person(u.getId(), u.getName()); }
    }

    /** 이 안내문을 쓰는 곳. 삭제를 막는 409(BRIEF_IN_USE)에도 같은 모양으로 담는다 */
    public record Usage(List<UsedBy> deliverables, long pendingProposals) {
        public boolean isEmpty() { return deliverables.isEmpty() && pendingProposals == 0; }
    }

    public record UsedBy(UUID id, String title, String status) {}
}
