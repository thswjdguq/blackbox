package com.blackbox.repository;

import com.blackbox.entity.FileVault;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileVaultRepository extends JpaRepository<FileVault, UUID> {
    List<FileVault> findByProjectOrderByUploadedAtDesc(Project project);
    List<FileVault> findByProjectAndFileNameOrderByVersionDesc(Project project, String fileName);
    Optional<FileVault> findTopByProjectAndFileNameOrderByVersionDesc(Project project, String fileName);
    int countByProjectAndFileName(Project project, String fileName);

    /** 해당 프로젝트에서 유저가 업로드한 파일 수 */
    long countByProjectAndUploader(Project project, User uploader);
}
