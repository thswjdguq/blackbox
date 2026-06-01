export interface BusySlot {
  start: string;
  end: string;
  memberName: string;
}

export interface FreeSlot {
  start: string;
  end: string;
  durationMinutes: number;
}

export interface MemberStatus {
  userId: string;
  name: string;
  connected: boolean;
}

export interface CalendarAvailabilityResponse {
  busySlots: BusySlot[];
  freeSlots: FreeSlot[];
  memberStatuses: MemberStatus[];
}

export interface CalendarRecommendation {
  time: string;
  durationMinutes: number;
  reason: string;
  rank: number;
  score: number;
  softBlockMembers: string[];
  needsConfirm: boolean;
}

export interface CalendarRecommendResponse {
  recommendations: CalendarRecommendation[];
  message?: string;
}

export interface MemberCalendarStatus {
  userId: string;
  name: string;
  email: string;
  connected: boolean;
}

export interface CalendarConnectionStatusResponse {
  connected: boolean;
  members: MemberCalendarStatus[];
}
