package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "course_name", length = 255)
    private String courseName;

    @Column(length = 20)
    private String semester;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "invite_code", unique = true, length = 8)
    private String inviteCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    // 보관(K-60). 보관된 프로젝트는 읽기 전용이다. 두 칸은 함께 있거나 함께 없다(V41)
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    /** 보관 여부만 읽는 곳(스케줄러, 이메일 발송 재확인)은 이것으로 건너뛴다. 409를 던지지 않는다 */
    public boolean isArchived() { return archivedAt != null; }
}
