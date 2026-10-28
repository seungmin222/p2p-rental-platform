import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 로컬은 프록시로 백엔드와 같은 출처를 만든다(CORS 없이 RT 쿠키 전달). 운영은 Nginx가 같은 경로를 넘긴다.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
      // local 프로필의 업로드 이미지(LocalFileStorage가 /files/... 상대 URL을 준다)
      '/files': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.js'],
    // node에는 브라우저 출처가 없으므로 테스트에서만 절대 주소를 쓴다
    env: { VITE_API_URL: 'http://localhost/api' },
  },
});
