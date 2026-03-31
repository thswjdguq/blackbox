import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        // Semantic design tokens — auto-switch light/dark via CSS vars
        "bb-bg":       "rgb(var(--bb-bg) / <alpha-value>)",
        "bb-surface":  "rgb(var(--bb-surface) / <alpha-value>)",
        "bb-surface2": "rgb(var(--bb-surface2) / <alpha-value>)",
        "bb-border":   "rgb(var(--bb-border) / <alpha-value>)",
        "bb-primary":  "rgb(var(--bb-primary) / <alpha-value>)",
        "bb-primary-h":"rgb(var(--bb-primary-h) / <alpha-value>)",
        "bb-accent":   "rgb(var(--bb-accent) / <alpha-value>)",
        "bb-text":     "rgb(var(--bb-text) / <alpha-value>)",
        "bb-text2":    "rgb(var(--bb-text2) / <alpha-value>)",
        "bb-sidebar":  "rgb(var(--bb-sidebar) / <alpha-value>)",
      },
    },
  },
  plugins: [],
};

export default config;
