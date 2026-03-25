package com.blackbox.repository;

import com.blackbox.entity.Meeting;
import com.blackbox.entity.MeetingAttendee;
import com.blackbox.entity.MeetingAttendeeId;
import com.blackbox.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingAttendeeRepository extends JpaRepository<MeetingAttendee, MeetingAttendeeId> {
    List<MeetingAttendee> findByMeeting(Meeting meeting);
    Optional<MeetingAttendee> findByMeetingAndUser(Meeting meeting, User user);
}
