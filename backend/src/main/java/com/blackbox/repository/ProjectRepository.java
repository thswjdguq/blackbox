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

    /** 엔티티를 거치지 않고 프로젝트 한 줄을 지운다. 멤버·업무·제출물은 DB가 함께 지우고, 지울 수 없는 기록이 있으면 무결성 위반이 난다. */
    @Modifying
    @Query("delete from Project p where p.id = :id")
    void deleteRowById(@Param("id") UUID id);
}
