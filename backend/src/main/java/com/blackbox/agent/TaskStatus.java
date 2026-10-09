package com.blackbox.agent;

import com.blackbox.dto.TaskResponse;

/** 업무 상태 중 에이전트가 뜻을 알아야 하는 값. 업무 쪽 코드는 상태를 문자열로 다룬다. */
public final class TaskStatus {
    private static final String DONE = "DONE";

    private TaskStatus() {}

    public static boolean isDone(TaskResponse task) { return DONE.equals(task.status()); }
}
