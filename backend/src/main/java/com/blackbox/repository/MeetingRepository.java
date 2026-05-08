package com.blackbox.repository;

import com.blackbox.entity.Meeting;
import com.blackbox.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {
    List<Meeting> findByProjectOrderByMeetingDateDesc(Project project);
    Optional<Meeting> findByCheckinCode(String checkinCode);
    boolean existsByCheckinCode(String checkinCode);

    /** 10분 전 알림용: meetingDate가 [from, to] 범위이고 아직 알림 미발송인 회의 */
    @Query("SELECT m FROM Meeting m JOIN FETCH m.project WHERE m.meetingDate BETWEEN :from AND :to AND m.discordNotified = false")
    List<Meeting> findUpcomingUnnotified(@Param("from") OffsetDateTime from,
                                         @Param("to")   OffsetDateTime to);
}
