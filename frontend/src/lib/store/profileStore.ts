import { create } from "zustand";
import api from "@/lib/api";
import { onSessionReset } from "@/lib/sessionCache";

export interface CurrentProfile { id: string; name: string; email: string; role: string; affiliation?: {source:"CATALOG"|"SELF_REPORTED";schoolId:string|null;departmentId:string|null;schoolName:string;departmentName:string;catalogYear:number|null}|null }
interface ProfileState {
  profile: CurrentProfile | null;
  loading: boolean;
  failed: boolean;
  load: () => Promise<void>;
  update: (profile: CurrentProfile) => void;
}

let revision = 0;
let pending: Promise<void> | null = null;
export const useProfileStore = create<ProfileState>((set, get) => ({
  profile: null, loading: false, failed: false,
  load: () => {
    if (pending) return pending;
    if (get().profile) return Promise.resolve();
    const ticket = revision;
    set({ loading: true, failed: false });
    pending = api.get<CurrentProfile>("/auth/profile")
      .then(({ data }) => { if (ticket === revision) set({ profile: data, failed: false }); })
      .catch(() => { if (ticket === revision) set({ profile: null, failed: true }); })
      .finally(() => {
        if (ticket === revision) { pending = null; set({ loading: false }); }
      });
    return pending;
  },
  update: profile => {
    // 이름 변경보다 먼저 시작한 조회가 늦게 도착해 표시를 되돌리지 못하게 한다.
    revision++; pending = null;
    set({ profile, loading: false, failed: false });
  },
}));

onSessionReset(() => {
  revision++; pending = null;
  useProfileStore.setState({ profile: null, loading: false, failed: false });
});
