/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef2ff',
          100: '#e0e7ff',
          200: '#c7d2fe',
          300: '#a5b4fc',
          400: '#818cf8',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
          800: '#3730a3',
          900: '#312e81',
          950: '#1e1b4b',
        },
        navy: {
          50: '#f0f5fa',
          100: '#e1ecf5',
          200: '#c4d9ec',
          300: '#97bedf',
          400: '#639ccd',
          500: '#3f7eb9',
          600: '#2d649d',
          700: '#25507f',
          800: '#1e3a5f',
          900: '#1a334f',
          950: '#112135',
        },
        cyber: {
          bg: '#f8fafc',
          card: '#ffffff',
          cardHover: '#f8fafc',
          border: '#e2e8f0',
          cyan: '#0284c7',
          blue: '#4f46e5',
          green: '#10b981',
          danger: '#ef4444',
          warning: '#f59e0b',
          muted: '#64748b'
        }
      },
      boxShadow: {
        'subtle': '0 1px 2px 0 rgba(0, 0, 0, 0.04)',
        'card': '0 1px 3px 0 rgba(0, 0, 0, 0.04), 0 1px 2px -1px rgba(0, 0, 0, 0.04)',
        'card-hover': '0 10px 15px -3px rgba(0, 0, 0, 0.06), 0 4px 6px -4px rgba(0, 0, 0, 0.04)',
        'card-elevated': '0 20px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.04)',
        'modal': '0 25px 50px -12px rgba(15, 23, 42, 0.18)',
        'glow-cyan': '0 0 15px -3px rgba(2, 132, 199, 0.25)',
        'glow-blue': '0 0 15px -3px rgba(79, 70, 229, 0.25)',
        'glow-green': '0 0 15px -3px rgba(16, 185, 129, 0.25)',
        'glow-danger': '0 0 15px -3px rgba(239, 68, 68, 0.25)',
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'fade-in': 'fadeIn 0.2s cubic-bezier(0.16, 1, 0.3, 1) forwards',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0', transform: 'translateY(4px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        }
      }
    },
  },
  plugins: [],
}
