import { QueryClient } from '@tanstack/react-query';

const NO_RETRY_STATUSES = [401, 403, 404, 409];

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) =>
        !NO_RETRY_STATUSES.includes(error?.status) && failureCount < 1,
      staleTime: 30_000,
    },
  },
});
