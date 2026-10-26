"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Shield, Sun, Moon, Eye, EyeOff, LogIn } from "lucide-react";
import api from "@/lib/api";
import { useAuthStore } from "@/lib/store/authStore";
import LoginIntroduction from "@/components/LoginIntroduction";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [isDark, setIsDark] = useState(false);
  const setTokens = useAuthStore((s) => s.setTokens);

  useEffect(() => {
    const theme = localStorage.getItem("teamplay-ui-theme") || "light";
    setIsDark(theme === "dark");
    document.documentElement.classList.toggle("dark", theme === "dark");
  }, []);

  const toggleTheme = () => {
    const next = !isDark;
    setIsDark(next);
    localStorage.setItem("teamplay-ui-theme", next ? "dark" : "light");
    document.documentElement.classList.toggle("dark", next);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    try {
      await api.post("/auth/login", { email, password });
      setTokens();
      router.replace("/dashboard");
    } catch (err) {
      const message =
        (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail ||
        (err instanceof Error ? err.message : "로그인에 실패했습니다");
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-bb-bg text-bb-text">
      <header className="mx-auto flex max-w-7xl items-center gap-2 px-6 py-6 sm:px-10"><Shield size={23} className="text-blue-600 dark:text-blue-300" /><span className="font-semibold tracking-tight">Team Blackbox</span></header>
      {/* Theme Toggle */}
      <button
        onClick={toggleTheme}
        className="fixed top-4 right-4 p-2 rounded-lg bg-bb-surface2 text-bb-text2 hover:bg-bb-border transition-colors"
        aria-label="테마 전환"
      >
        {isDark ? <Sun size={18} /> : <Moon size={18} />}
      </button>

      <main className="mx-auto grid w-full max-w-7xl gap-8 px-6 pb-12 pt-4 sm:px-10 lg:min-h-[calc(100vh-80px)] lg:grid-cols-[1.35fr_1fr] lg:items-center lg:gap-14 lg:py-12">
      <LoginIntroduction />
      <section aria-labelledby="login-title" className="w-full max-w-md justify-self-center rounded-2xl border border-bb-border bg-bb-surface p-6 shadow-sm sm:p-8 lg:p-10">
        {/* Header */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-bb-primary/10 mb-4">
            <Shield size={24} className="text-indigo-500" />
          </div>
          <h2 id="login-title" className="text-2xl font-bold text-bb-text">팀플을 이어가세요</h2>
          <p className="text-sm text-bb-text2 mt-1">
            로그인하고 우리 팀의 다음 할 일을 확인하세요.
          </p>
        </div>

        {/* Error */}
        {error && (
          <div role="alert" className="mb-4 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
            {error}
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="login-email" className="block text-sm font-medium text-bb-text2 mb-1.5">
              이메일
            </label>
            <input
              type="email"
              id="login-email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="email@example.com"
              required
              className="w-full bg-bb-surface border border-bb-border rounded-lg px-4 py-2.5 text-sm text-bb-text placeholder-slate-400 focus:outline-none focus:border-indigo-500 transition-colors"
            />
          </div>

          <div>
            <label htmlFor="login-password" className="block text-sm font-medium text-bb-text2 mb-1.5">
              비밀번호
            </label>
            <div className="relative">
              <input
                type={showPassword ? "text" : "password"}
                id="login-password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
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

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-bb-primary hover:bg-bb-primary-h disabled:opacity-60 disabled:cursor-not-allowed text-white px-4 py-2.5 rounded-lg text-sm font-medium transition-colors flex items-center justify-center gap-2 mt-2"
          >
            {loading ? (
              <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : (
              <>
                <LogIn size={16} />
                로그인
              </>
            )}
          </button>
        </form>

        {/* Footer */}
        <p className="text-center text-sm text-bb-text2 mt-6">
          계정이 없으신가요?{" "}
          <Link
            href="/signup"
            className="text-indigo-500 hover:text-indigo-400 font-medium transition-colors"
          >
            회원가입
          </Link>
        </p>
      </section>
      </main>
    </div>
  );
}
