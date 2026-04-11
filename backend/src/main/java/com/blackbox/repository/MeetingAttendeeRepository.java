package com.blackbox.repository;

import com.blackbox.entity.Meeting;
import com.blackbox.entity.MeetingAttendee;
import com.blackbox.entity.MeetingAttendeeId;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MeetingAttendeeRepository extends JpaRepository<MeetingAttendee, MeetingAttendeeId> {
    List<MeetingAttendee> findByMeeting(Meeting meeting);
    Optional<MeetingAttendee> findByMeetingAndUser(Meeting meeting, User user);
    List<MeetingAttendee> findByUserAndCheckedInTrueOrderByCheckedAtDesc(User user);

    /** 해당 프로젝트에서 유저가 실제 체크인한 회의 수 */
    @Query("SELECT COUNT(a) FROM MeetingAttendee a WHERE a.meeting.project = :project AND a.user = :user AND a.checkedIn = true")
    long countCheckedInByProjectAndUser(@Param("project") Project project, @Param("user") User user);
}
