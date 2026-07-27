import type { Pinia } from 'pinia';

import type { App } from 'vue';

import { createPinia } from 'pinia';
import SecureLS from 'secure-ls';

let pinia: Pinia;

/** 已注册的 store 列表，用于替代访问 Pinia 内部 _s 属性 */
const registeredStores: Set<ReturnType<Pinia['_s']['get']>> = new Set();

export interface InitStoreOptions {
  /**
   * @zh_CN 应用名,由于 @ydsz/stores 是公用的，后续可能有多个app，为了防止多个app缓存冲突，可在这里配置应用名,应用名将被用于持久化的前缀
   */
  namespace: string;
}

/**
 * @zh_CN 初始化pinia
 */
export async function initStores(app: App, options: InitStoreOptions) {
  const { createPersistedState } = await import('pinia-plugin-persistedstate');
  pinia = createPinia();
  const { namespace } = options;
  const ls = new SecureLS({
    encodingType: 'aes',
    encryptionSecret: import.meta.env.VITE_APP_STORE_SECURE_KEY,
    isCompression: true,
    // @ts-expect-error secure-ls 缺少该属性的类型定义
    metaKey: `${namespace}-secure-meta`,
  });
  pinia.use(
    createPersistedState({
      key: (storeKey) => `${namespace}-${storeKey}`,
      storage: import.meta.env.DEV
        ? localStorage
        : {
            getItem(key) {
              return ls.get(key);
            },
            setItem(key, value) {
              ls.set(key, value);
            },
          },
    }),
  );

  const originalUse = pinia._s.set.bind(pinia._s);
  pinia._s.set = function (key: string, value: any) {
    registeredStores.add(value);
    originalUse(key, value);
  };

  app.use(pinia);
  return pinia;
}

/**
 * 重置所有已注册的 store
 * @description 遍历所有已注册的 store 并调用 $reset 方法
 * @throws 当 Pinia 未安装时抛出错误
 */
export function resetAllStores() {
  if (!pinia) {
    throw new Error('[resetAllStores] Pinia 尚未安装，请先调用 initStores');
  }
  for (const store of registeredStores) {
    if (store && typeof store.$reset === 'function') {
      store.$reset();
    }
  }
}
