package com.blackbox.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TaskAssigneeId implements Serializable {

    private UUID task;
    private UUID user;

    public TaskAssigneeId() {}

    public TaskAssigneeId(UUID task, UUID user) {
        this.task = task;
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskAssigneeId that)) return false;
        return Objects.equals(task, that.task) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(task, user);
    }
}
