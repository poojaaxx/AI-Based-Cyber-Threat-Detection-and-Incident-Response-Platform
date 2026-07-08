/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        'cg-bg': '#0a0e17',
        'cg-surface': '#111827',
        'cg-surface-alt': '#161f2e',
        'cg-border': '#1f2937',
        'cg-accent': '#00e5c7',
        'cg-accent-dim': '#0a8f7f',
        'cg-danger': '#ef4444',
        'cg-warning': '#f59e0b',
        'cg-success': '#22c55e',
        'cg-info': '#3b82f6',
      },
      boxShadow: {
        glow: '0 0 20px rgba(0, 229, 199, 0.15)',
        'glow-success': '0 0 20px rgba(34, 197, 94, 0.15)',
        'glow-warning': '0 0 20px rgba(245, 158, 11, 0.15)',
        'glow-danger': '0 0 20px rgba(239, 68, 68, 0.18)',
        'glow-info': '0 0 20px rgba(59, 130, 246, 0.15)',
      },
      backgroundImage: {
        'cg-app': 'radial-gradient(ellipse 1200px 800px at 15% -10%, rgba(0,229,199,0.06), transparent), radial-gradient(ellipse 1000px 700px at 100% 0%, rgba(59,130,246,0.05), transparent), linear-gradient(180deg, #0b1120 0%, #0a0e17 45%, #090c14 100%)',
      },
      fontFamily: {
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0', transform: 'translateY(4px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        scaleIn: {
          '0%': { opacity: '0', transform: 'scale(0.96)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        slideInRight: {
          '0%': { opacity: '0', transform: 'translateX(8px)' },
          '100%': { opacity: '1', transform: 'translateX(0)' },
        },
      },
      animation: {
        'fade-in': 'fadeIn 0.2s ease-out',
        'scale-in': 'scaleIn 0.18s ease-out',
        'slide-in-right': 'slideInRight 0.2s ease-out',
      },
    },
  },
  plugins: [],
};
