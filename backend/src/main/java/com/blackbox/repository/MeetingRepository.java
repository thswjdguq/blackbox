package com.blackbox.repository;

import com.blackbox.entity.Meeting;
import com.blackbox.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {
    List<Meeting> findByProjectOrderByMeetingDateDesc(Project project);
    Optional<Meeting> findByCheckinCode(String checkinCode);
    boolean existsByCheckinCode(String checkinCode);
}
