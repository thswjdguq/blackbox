package com.blackbox.repository;

import com.blackbox.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);

    // DB의 기록 보존 제약과 CASCADE를 적용하고 권한 확인 중 읽은 멤버는 캐시에서 비운다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Project p where p.id = :id")
    int deleteProjectRow(@Param("id") UUID id);

    // 보관(K-60). 이미 보관됐으면 바꾸지 않아, 동시에 두 번 눌러도 처음 보관 시각과 보관한 사람이 남는다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Project p set p.archivedAt = :at, p.archivedBy = :by where p.id = :id and p.archivedAt is null")
    int archive(@Param("id") UUID id, @Param("at") OffsetDateTime at, @Param("by") UUID by);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Project p set p.archivedAt = null, p.archivedBy = null where p.id = :id")
    int unarchive(@Param("id") UUID id);
}
