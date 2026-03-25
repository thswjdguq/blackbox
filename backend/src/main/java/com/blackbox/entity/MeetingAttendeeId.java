package com.blackbox.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class MeetingAttendeeId implements Serializable {

    private UUID meeting;
    private UUID user;

    public MeetingAttendeeId() {}

    public MeetingAttendeeId(UUID meeting, UUID user) {
        this.meeting = meeting;
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MeetingAttendeeId that)) return false;
        return Objects.equals(meeting, that.meeting) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meeting, user);
    }
}
