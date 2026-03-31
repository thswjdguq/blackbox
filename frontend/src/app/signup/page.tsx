"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Shield,
  Sun,
  Moon,
  Eye,
  EyeOff,
  UserPlus,
  Users,
  GraduationCap,
} from "lucide-react";
import { useAuthStore } from "@/lib/store/authStore";

export default function SignupPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"STUDENT" | "PROFESSOR">("STUDENT");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [isDark, setIsDark] = useState(true);
  const setTokens = useAuthStore((s) => s.setTokens);

  useEffect(() => {
    const theme = localStorage.getItem("theme") || "dark";
    setIsDark(theme === "dark");
    document.documentElement.classList.toggle("dark", theme === "dark");
  }, []);

  const toggleTheme = () => {
    const next = !isDark;
    setIsDark(next);
    localStorage.setItem("theme", next ? "dark" : "light");
    document.documentElement.classList.toggle("dark", next);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    try {
      const res = await fetch("/api/auth/signup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, name, password }),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        if (res.status === 409) {
          throw new Error("이미 사용 중인 이메일입니다");
        }
        const fieldErrors = data.errors
          ? Object.values(data.errors).join(", ")
          : null;
        throw new Error(
          fieldErrors || data.detail || "회원가입에 실패했습니다"
        );
      }

      const data = await res.json();
      setTokens(data.accessToken, data.refreshToken);
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "회원가입에 실패했습니다");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-bb-bg flex items-center justify-center p-4">
      {/* Theme Toggle */}
      <button
        onClick={toggleTheme}
        className="fixed top-4 right-4 p-2 rounded-lg bg-bb-surface2 text-bb-text2 hover:bg-bb-border transition-colors"
        aria-label="테마 전환"
      >
        {isDark ? <Sun size={18} /> : <Moon size={18} />}
      </button>

      {/* Card */}
      <div className="w-full max-w-md bg-bb-surface border border-bb-border rounded-xl p-8">
        {/* Header */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-bb-primary/10 mb-4">
            <Shield size={24} className="text-indigo-500" />
          </div>
          <h1 className="text-2xl font-bold text-bb-text">
            Team Blackbox
          </h1>
          <p className="text-sm text-bb-text2 mt-1">
            새 계정 만들기
          </p>
        </div>

        {/* Error */}
        {error && (
          <div className="mb-4 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
            {error}
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Name */}
          <div>
            <label className="block text-sm font-medium text-bb-text2 mb-1.5">
              이름
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="홍길동"
              required
              minLength={2}
              className="w-full bg-bb-surface border border-bb-border rounded-lg px-4 py-2.5 text-sm text-bb-text placeholder-slate-400 focus:outline-none focus:border-indigo-500 transition-colors"
            />
          </div>

          {/* Email */}
          <div>
            <label className="block text-sm font-medium text-bb-text2 mb-1.5">
              이메일
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="email@example.com"
              required
              className="w-full bg-bb-surface border border-bb-border rounded-lg px-4 py-2.5 text-sm text-bb-text placeholder-slate-400 focus:outline-none focus:border-indigo-500 transition-colors"
            />
          </div>

          {/* Password */}
          <div>
            <label className="block text-sm font-medium text-bb-text2 mb-1.5">
              비밀번호
            </label>
            <div className="relative">
              <input
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="8자 이상 입력"
                required
                minLength={8}
                className="w-full bg-bb-surface border border-bb-border rounded-lg px-4 py-2.5 pr-10 text-sm text-bb-text placeholder-slate-400 focus:outline-none focus:border-indigo-500 transition-colors"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-bb-text transition-colors"
                aria-label="비밀번호 표시 전환"
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>

          {/* Role */}
          <div>
            <label className="block text-sm font-medium text-bb-text2 mb-1.5">
              역할
            </label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setRole("STUDENT")}
                className={`flex items-center justify-center gap-2 p-3 rounded-lg border text-sm font-medium transition-colors ${
                  role === "STUDENT"
                    ? "bg-bb-primary/10 border-indigo-500 text-indigo-400"
                    : "bg-slate-100 dark:bg-slate-700 border-slate-300 dark:border-slate-600 text-bb-text2 hover:border-slate-400 dark:hover:border-slate-500"
                }`}
              >
                <Users size={16} />
                학생
              </button>
              <button
                type="button"
                onClick={() => setRole("PROFESSOR")}
                className={`flex items-center justify-center gap-2 p-3 rounded-lg border text-sm font-medium transition-colors ${
                  role === "PROFESSOR"
                    ? "bg-bb-primary/10 border-indigo-500 text-indigo-400"
                    : "bg-slate-100 dark:bg-slate-700 border-slate-300 dark:border-slate-600 text-bb-text2 hover:border-slate-400 dark:hover:border-slate-500"
                }`}
              >
                <GraduationCap size={16} />
                교수
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-bb-primary hover:bg-bb-primary-h disabled:opacity-60 disabled:cursor-not-allowed text-white px-4 py-2.5 rounded-lg text-sm font-medium transition-colors flex items-center justify-center gap-2 mt-2"
          >
            {loading ? (
              <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : (
              <>
                <UserPlus size={16} />
                회원가입
              </>
            )}
          </button>
        </form>

        {/* Footer */}
        <p className="text-center text-sm text-bb-text2 mt-6">
          이미 계정이 있으신가요?{" "}
          <Link
            href="/login"
            className="text-indigo-500 hover:text-indigo-400 font-medium transition-colors"
          >
            로그인
          </Link>
        </p>
      </div>
    </div>
  );
}
