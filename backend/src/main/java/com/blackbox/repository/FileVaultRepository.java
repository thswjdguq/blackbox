package com.blackbox.repository;

import com.blackbox.entity.FileVault;
import com.blackbox.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileVaultRepository extends JpaRepository<FileVault, UUID> {
    List<FileVault> findByProjectOrderByUploadedAtDesc(Project project);
    List<FileVault> findByProjectAndFileNameOrderByVersionDesc(Project project, String fileName);
    Optional<FileVault> findTopByProjectAndFileNameOrderByVersionDesc(Project project, String fileName);
    int countByProjectAndFileName(Project project, String fileName);
}
