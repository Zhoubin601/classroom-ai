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
        'card': '0 1px 3px 0 rgba(0, 0, 0, 0.05), 0 1px 2px -1px rgba(0, 0, 0, 0.05)',
        'card-hover': '0 4px 6px -1px rgba(0, 0, 0, 0.07), 0 2px 4px -2px rgba(0, 0, 0, 0.05)',
        'modal': '0 20px 25px -5px rgba(0, 0, 0, 0.08), 0 8px 10px -6px rgba(0, 0, 0, 0.05)',
        'glow-cyan': '0 1px 2px 0 rgba(0, 0, 0, 0.04)',
        'glow-blue': '0 1px 2px 0 rgba(0, 0, 0, 0.04)',
        'glow-green': '0 1px 2px 0 rgba(0, 0, 0, 0.04)',
        'glow-danger': '0 1px 2px 0 rgba(0, 0, 0, 0.04)',
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      }
    },
  },
  plugins: [],
}

