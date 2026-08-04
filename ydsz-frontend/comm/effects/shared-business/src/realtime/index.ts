/**
 * realtime 模块入口
 *
 * @path comm\effects\shared-business\src\realtime\index.ts
 * @author ydsz-team
 * @since 1.1.0
 */
export { RealtimeClient, type RealtimeOptions, type RealtimeStatus } from './realtime-client';
export { useRealtime, getRealtimeClient } from './use-realtime';
