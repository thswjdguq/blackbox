package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/** 과제 안내문(K-31 2장). 본문을 바꾸면 해시도 함께 바뀌므로 둘은 이 클래스의 메서드로만 정한다. */
@Entity @Table(name = "project_briefs") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectBrief {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false) private Project project;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "body_hash", nullable = false, length = 64) private String bodyHash;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_file_id") private FileVault sourceFile;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false) private User createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "updated_by", nullable = false) private User updatedBy;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    public static ProjectBrief create(Project project, String title, String body, FileVault sourceFile, User by, OffsetDateTime now) {
        ProjectBrief b = new ProjectBrief();
        b.project = project; b.createdBy = by; b.createdAt = now;
        b.edit(title, body, sourceFile, by, now);
        return b;
    }

    public void edit(String title, String body, FileVault sourceFile, User by, OffsetDateTime now) {
        this.title = title; this.body = body; this.bodyHash = hash(body); this.sourceFile = sourceFile;
        updatedBy = by; updatedAt = now;
    }

    /** 본문의 SHA-256(hex). 같은 본문을 찾을 때(K-31 4장)도 이것으로 비교한다 */
    public static String hash(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
