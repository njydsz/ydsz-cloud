/**
 * 网络状态感知 composable（v4.0）
 *
 * 为 UI 提供：
 * - isOnline: 是否联网
 * - isSlow: 是否慢速网络
 * - isSaveData: 是否开启省流量模式
 * - effectiveType: 实际网络类型（4g/3g/2g/slow-2g）
 * - downlink: 估算带宽（Mbps）
 * - rtt: 往返延迟（ms）
 *
 * 用法：在 NetworkAlert 组件或任意 setup 内 useNetworkStatus()
 *
 * @path main/src/hooks/use-network-status.ts
 * @since 4.0.0
 */
import { ref, readonly, onMounted, onUnmounted } from 'vue';

export type EffectiveType = '4g' | '3g' | '2g' | 'slow-2g' | 'unknown';

export interface NetworkStatus {
  isOnline: boolean;
  isSlow: boolean;
  isSaveData: boolean;
  effectiveType: EffectiveType;
  downlink: number;
  rtt: number;
}

const STATUS_KEY = '__MICRO_NETWORK_STATUS__';

function readNavigatorConnection(): NetworkStatus {
  const nav = navigator as Navigator & {
    connection?: {
      effectiveType?: string;
      saveData?: boolean;
      downlink?: number;
      rtt?: number;
      addEventListener?: (type: string, listener: () => void) => void;
      removeEventListener?: (type: string, listener: () => void) => void;
    };
  };
  const conn = nav.connection;
  const type = (conn?.effectiveType || '4g') as EffectiveType;
  const isSlow = ['slow-2g', '2g', '3g'].includes(type);
  const isSaveData = conn?.saveData === true;
  return {
    isOnline: navigator.onLine, isSlow, isSaveData,
    effectiveType: type, downlink: conn?.downlink ?? 10, rtt: conn?.rtt ?? 50,
  };
}

// 单例：多组件共享
let stateInitialized = false;
let refInstance: ReturnType<typeof ref<NetworkStatus>> | undefined;
const listeners: Set<() => void> = new Set();

function initState() {
  if (refInstance) return refInstance;
  refInstance = ref<NetworkStatus>(readNavigatorConnection());
  const update = () => {
    if (!refInstance) return;
    refInstance.value = readNavigatorConnection();
    listeners.forEach((fn) => fn());
  };
  window.addEventListener('online', update);
  window.addEventListener('offline', update);
  if ((navigator as any).connection?.addEventListener) {
    (navigator as any).connection.addEventListener('change', update);
  }
  return refInstance;
}

/**
 * 响应式网络状态（主/子应用均可调用，单例共享）。
 */
export function useNetworkStatus() {
  if (!stateInitialized && !refInstance) { initState(); stateInitialized = true; }
  const state = refInstance ?? initState()!;
  const onChange = (fn: () => void) => { listeners.add(fn); onUnmounted(() => listeners.delete(fn)); };
  return {
    networkStatus: readonly(state),
    isOnline: () => state.value.isOnline,
    isSlow: () => state.value.isSlow,
    isSaveData: () => state.value.isSaveData,
    onChange,
  };
}

/**
 * 是否应跳过预加载（慢网络/省流量时返回 true）。
 * 供 prefetch 调度器在注入 Speculation Rules 前调用。
 */
export function shouldSkipPrefetchDueToNetwork(): boolean {
  const s = readNavigatorConnection();
  return !s.isOnline || s.isSlow || s.isSaveData;
}

export { STATUS_KEY };
