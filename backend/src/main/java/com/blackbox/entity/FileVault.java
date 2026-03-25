package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * file_vault 레코드는 INSERT 전용 — DB 트리거로 UPDATE/DELETE 차단됨.
 * @Immutable 은 Hibernate dirty-check 비활성화로 이중 방어.
 */
@Entity
@Table(name = "file_vault")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class FileVault {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "storage_path", nullable = false, columnDefinition = "TEXT")
    private String storagePath;

    // Java에서 직접 설정 — @Immutable 엔티티에서 refresh() 불가이므로 DB DEFAULT 미사용
    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "is_immutable", nullable = false)
    private boolean isImmutable = true;

    @Column(name = "version", nullable = false)
    private Integer version = 1;
}
