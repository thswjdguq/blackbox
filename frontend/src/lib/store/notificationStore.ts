import { create } from "zustand";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import { onSessionReset, sessionGeneration } from "@/lib/sessionCache";
import type { NotificationPage } from "@/types/notification";

interface Entry { count: number | null; countError: string; page: NotificationPage | null; loading: boolean; error: string; readError: string; failedReadId: string | null }
const empty = (): Entry => ({ count: null, countError: "", page: null, loading: false, error: "", readError: "", failedReadId: null });
interface State {
  projects: Record<string, Entry>;
  count: (id: string) => Promise<void>;
  load: (id: string, more?: boolean) => Promise<void>;
  read: (id: string, notificationId: string) => Promise<void>;
}
let pending = new Set<string>();
const countVersions = new Map<string, number>();
export const useNotificationStore = create<State>((set, get) => {
  const update = (id: string, fn: (entry: Entry) => Partial<Entry>) => set(s => ({ projects: { ...s.projects, [id]: { ...(s.projects[id] ?? empty()), ...fn(s.projects[id] ?? empty()) } } }));
  return {
    projects: {},
    count: async id => {
      const key = `count:${id}`; if (pending.has(key)) return; pending.add(key);
      const generation = sessionGeneration(), version = countVersions.get(id) ?? 0;
      try {
        const { data } = await api.get<{ unreadCount: number }>(`/projects/${id}/notifications/unread-count`);
        if (generation === sessionGeneration() && version === (countVersions.get(id) ?? 0)) update(id, () => ({ count: data.unreadCount, countError: "" }));
      } catch (e) { if (generation === sessionGeneration()) update(id, () => ({ countError: apiError(e, "알림 수를 확인하지 못했습니다.") })); }
      finally { if (generation === sessionGeneration()) { pending.delete(key); if (version !== (countVersions.get(id) ?? 0)) void get().count(id); } }
    },
    load: async (id, more = false) => {
      const key = `list:${id}`; if (pending.has(key)) return;
      const cursor = more ? get().projects[id]?.page?.nextCursor : null;
      if (more && !cursor) return;
      pending.add(key); const generation = sessionGeneration();
      const version = countVersions.get(id) ?? 0;
      update(id, () => ({ loading: true, error: "" }));
      try {
        const { data } = await api.get<NotificationPage>(`/projects/${id}/notifications?limit=20${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ""}`);
        if (generation !== sessionGeneration()) return;
        update(id, entry => {
          const old = entry.page?.items ?? [];
          // 조회 중 성공한 읽음 처리를 늦은 목록 응답이 되돌리지 않는다.
          const items = data.items.map(item => ({ ...item, readAt: old.find(v => v.id === item.id)?.readAt ?? item.readAt }));
          return { page: { ...data, items: more ? [...old, ...items.filter(v => !old.some(o => o.id === v.id))] : items }, ...(version === (countVersions.get(id) ?? 0) ? { count: data.unreadCount, countError: "" } : {}) };
        });
      } catch (e) { if (generation === sessionGeneration()) update(id, () => ({ error: apiError(e, "알림을 불러오지 못했습니다. 서버 연결을 확인해 주세요.") })); }
      finally { if (generation === sessionGeneration()) { pending.delete(key); update(id, () => ({ loading: false })); } }
    },
    read: async (id, notificationId) => {
      const key = `read:${id}:${notificationId}`; if (pending.has(key)) return;
      pending.add(key); const generation = sessionGeneration();
      try {
        const { data } = await api.put<{ id: string; readAt: string }>(`/projects/${id}/notifications/${notificationId}/read`);
        if (generation !== sessionGeneration()) return;
        countVersions.set(id, (countVersions.get(id) ?? 0) + 1);
        update(id, entry => ({ readError: "", failedReadId: null, page: entry.page ? { ...entry.page, items: entry.page.items.map(v => v.id === data.id ? { ...v, readAt: data.readAt } : v) } : null }));
      } catch (e) { if (generation === sessionGeneration()) update(id, () => ({ readError: apiError(e, "읽음 처리에 실패했습니다. 업무는 계속 확인할 수 있습니다."), failedReadId: notificationId })); }
      finally { if (generation === sessionGeneration()) { pending.delete(key); void get().count(id); } }
    },
  };
});
onSessionReset(() => { pending = new Set(); countVersions.clear(); useNotificationStore.setState({ projects: {} }); });
