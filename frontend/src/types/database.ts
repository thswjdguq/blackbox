export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[];

export interface Database {
  public: {
    Tables: {
      users: {
        Row: {
          id: string;
          email: string;
          name: string;
          github_id: string | null;
          google_id: string | null;
          created_at: string;
        };
        Insert: Omit<Database["public"]["Tables"]["users"]["Row"], "id" | "created_at"> &
          Partial<Pick<Database["public"]["Tables"]["users"]["Row"], "id" | "created_at">>;
        Update: Partial<Database["public"]["Tables"]["users"]["Insert"]>;
      };
      teams: {
        Row: {
          id: string;
          name: string;
          professor_id: string;
          start_date: string;
          end_date: string | null;
          created_at: string;
        };
        Insert: Omit<Database["public"]["Tables"]["teams"]["Row"], "id" | "created_at"> &
          Partial<Pick<Database["public"]["Tables"]["teams"]["Row"], "id" | "created_at">>;
        Update: Partial<Database["public"]["Tables"]["teams"]["Insert"]>;
      };
      team_members: {
        Row: {
          id: string;
          team_id: string;
          user_id: string;
          role: "student" | "professor";
          joined_at: string;
        };
        Insert: Omit<Database["public"]["Tables"]["team_members"]["Row"], "id" | "joined_at"> &
          Partial<Pick<Database["public"]["Tables"]["team_members"]["Row"], "id" | "joined_at">>;
        Update: Partial<Database["public"]["Tables"]["team_members"]["Insert"]>;
      };
      activity_logs: {
        Row: {
          id: string;
          user_id: string;
          team_id: string;
          source: "github" | "google_drive" | "manual";
          activity_type: string;
          raw_data: Json;
          quality_score: number | null;
          created_at: string;
        };
        Insert: Omit<Database["public"]["Tables"]["activity_logs"]["Row"], "id" | "created_at"> &
          Partial<Pick<Database["public"]["Tables"]["activity_logs"]["Row"], "id" | "created_at">>;
        Update: Partial<Database["public"]["Tables"]["activity_logs"]["Insert"]>;
      };
      file_vault: {
        Row: {
          id: string;
          team_id: string;
          uploader_id: string;
          file_name: string;
          file_hash: string;
          version: number;
          is_flagged: boolean;
          created_at: string;
        };
        Insert: Omit<Database["public"]["Tables"]["file_vault"]["Row"], "id" | "created_at"> &
          Partial<Pick<Database["public"]["Tables"]["file_vault"]["Row"], "id" | "created_at">>;
        Update: Partial<Database["public"]["Tables"]["file_vault"]["Insert"]>;
      };
    };
  };
}

// Convenience types
export type User = Database["public"]["Tables"]["users"]["Row"];
export type Team = Database["public"]["Tables"]["teams"]["Row"];
export type TeamMember = Database["public"]["Tables"]["team_members"]["Row"];
export type ActivityLog = Database["public"]["Tables"]["activity_logs"]["Row"];
export type FileVault = Database["public"]["Tables"]["file_vault"]["Row"];
