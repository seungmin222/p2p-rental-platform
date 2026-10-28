import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import App from './app/App';
import { restoreSession } from './app/session';
import './styles/index.css';

/** 개발 서버 + VITE_USE_MOCK=true일 때만 MSW를 켠다. 운영 빌드에서는 이 분기가 제거되어 번들에 포함되지 않는다. */
async function enableMocking() {
  if (!import.meta.env.DEV || import.meta.env.VITE_USE_MOCK !== 'true') {
    return;
  }
  const { worker } = await import('./mocks/browser');
  await worker.start({ onUnhandledRequest: 'bypass' });
}

enableMocking().then(() => {
  const sessionRestored = restoreSession();
  createRoot(document.getElementById('root')).render(
    <StrictMode>
      <App sessionRestored={sessionRestored} />
    </StrictMode>,
  );
});
