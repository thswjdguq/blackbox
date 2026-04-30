package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "meeting_attendees")
@IdClass(MeetingAttendeeId.class)
@Getter
@Setter
@NoArgsConstructor
public class MeetingAttendee {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "checked_in", nullable = false)
    private boolean checkedIn;

    @Column(name = "checked_at")
    private OffsetDateTime checkedAt;

    @Column(name = "checked_in_date")
    private LocalDate checkedInDate;

    public boolean isCheckedInToday() {
        return checkedInDate != null && checkedInDate.equals(LocalDate.now());
    }
}
