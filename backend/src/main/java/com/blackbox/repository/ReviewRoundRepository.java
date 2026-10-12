package com.blackbox.repository;
import com.blackbox.entity.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ReviewRoundRepository extends JpaRepository<ReviewRound, UUID> {
    // 최신 회차가 먼저. 응답에 쓰는 파일·올린 사람·연 사람·결정자를 함께 읽어 회차 수만큼 조회가 늘지 않게 한다
    @EntityGraph(attributePaths = {"file", "file.uploader", "openedBy", "decidedBy"})
    List<ReviewRound> findByDeliverableOrderByRoundNoDesc(Deliverable deliverable);
    boolean existsByDeliverable(Deliverable deliverable);
    Optional<ReviewRound> findFirstByDeliverableOrderByRoundNoDesc(Deliverable deliverable);
    // 프로젝트의 제출물마다 최신 회차 하나씩. 회차가 없는 제출물은 나오지 않는다
    @Query("select r from ReviewRound r where r.project = :project"
            + " and r.roundNo = (select max(x.roundNo) from ReviewRound x where x.deliverable = r.deliverable)")
    List<ReviewRound> findLatestByProject(@Param("project") Project project);
}
