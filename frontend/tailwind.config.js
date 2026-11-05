/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: '#3158d5', ink: '#242923', muted: '#73776f', line: '#e3e5dd', canvas: '#fffdfc'
      },
      fontFamily: { sans: ['Gothic A1', 'Apple SD Gothic Neo', 'Noto Sans KR', 'sans-serif'] }
    }
  },
  plugins: []
};
