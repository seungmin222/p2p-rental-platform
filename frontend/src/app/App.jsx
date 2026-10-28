import { QueryClientProvider } from '@tanstack/react-query';
import { lazy, Suspense, use } from 'react';
import { RouterProvider } from 'react-router/dom';

import { queryClient } from './queryClient';
import { router } from './router';

const ReactQueryDevtools = import.meta.env.DEV
  ? lazy(() =>
      import('@tanstack/react-query-devtools').then((module) => ({
        default: module.ReactQueryDevtools,
      })),
    )
  : () => null;

function SessionGate({ sessionRestored, children }) {
  use(sessionRestored);
  return children;
}

function AppLoading() {
  return (
    <div className="flex min-h-dvh items-center justify-center" role="status">
      <span className="text-sm text-gray-500">불러오는 중…</span>
    </div>
  );
}

export default function App({ sessionRestored }) {
  return (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<AppLoading />}>
        <SessionGate sessionRestored={sessionRestored}>
          <RouterProvider router={router} />
        </SessionGate>
      </Suspense>
      <Suspense>
        <ReactQueryDevtools />
      </Suspense>
    </QueryClientProvider>
  );
}
