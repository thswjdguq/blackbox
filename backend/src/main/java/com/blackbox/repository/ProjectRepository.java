package com.blackbox.repository;

import com.blackbox.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);

    // DB의 기록 보존 제약과 CASCADE를 적용하고 권한 확인 중 읽은 멤버는 캐시에서 비운다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Project p where p.id = :id")
    int deleteProjectRow(@Param("id") UUID id);
}
