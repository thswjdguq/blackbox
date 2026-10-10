package com.blackbox.repository;
import com.blackbox.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface DeliverableRepository extends JpaRepository<Deliverable, UUID> {
    List<Deliverable> findByProjectOrderByDueDateAscCreatedAtAsc(Project project);
    Optional<Deliverable> findByIdAndProject(UUID id, Project project);

    // 같은 제출물의 회차 열기·결정·피드백 쓰기를 차례대로 처리하기 위한 행 잠금(K-10 5장). 트랜잭션 안에서 부른다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deliverable d where d.id = :id and d.project = :project")
    Optional<Deliverable> lockByIdAndProject(@Param("id") UUID id, @Param("project") Project project);
}
