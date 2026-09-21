export interface DeliverableRequirement {
  id: string;
  content: string;
  required: boolean;
}

export interface Deliverable {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  dueDate: string;
  submissionMethod: string | null;
  ownerId: string | null;
  ownerName: string | null;
  requirements: DeliverableRequirement[];
}
