/**
 * Auth Store — re-export from @ydsz/shared-auth
 */
export { createSharedAuthStore } from '@ydsz/shared-auth';

const useAuthStore = createSharedAuthStore();
export { useAuthStore };
