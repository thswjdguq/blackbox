import type { ProjectMember } from "@/types/member";
import type { Task } from "@/types/task";

/**
 * 이 업무의 결과를 확인해 줄 수 있는 팀원(K-58 REVIEWER와 같은 기준).
 * 팀장·팀원 가운데 수행자(담당자, 담당자가 없으면 만든 사람)와 요청자를 뺀다.
 * 요청자는 확인 요청 중이면 요청한 사람이고, 아직 요청 전이면 지금 요청하려는 나다.
 * 요청 중인데 응답에 요청자 ID가 없으면 이름으로 찾는다. 같은 이름의 팀원이 둘 이상이라
 * 누구인지 정할 수 없으면 null을 돌려 이름 목록을 보이지 않는다(동명이인을 잘못 빼지 않게).
 * 화면 안내용일 뿐이며 판정은 서버가 한다.
 */
export function eligibleReviewers(task: Task, members: ProjectMember[], myUserId: string): ProjectMember[] | null {
  const performers = task.assignees.length > 0 ? task.assignees.map((a) => a.userId) : [task.createdBy];
  let requesterId: string | null = myUserId;
  if (task.completionRequestedAt) {
    requesterId = task.completionRequestedBy ?? null;
    if (!requesterId) {
      const sameName = members.filter((m) => m.name === task.completionRequestedByName);
      if (sameName.length !== 1) return null;
      requesterId = sameName[0].userId;
    }
  }
  return members.filter((m) => m.role !== "OBSERVER" && !performers.includes(m.userId) && m.userId !== requesterId);
}
