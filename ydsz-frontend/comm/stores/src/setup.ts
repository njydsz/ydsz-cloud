/**
 * setup Pinia 状态管理
 *
 * @path comm\stores\src\setup.ts
 * @author ydsz-team
 * @since 1.0.0
 *
 * @important 安全提醒（v3.0）
 * secure-ls AES 加密的密钥 VITE_APP_STORE_SECURE_KEY 在构建期注入并随 JS bundle
 * 分发到浏览器，任何人拿到 bundle 均可提取密钥解密存储数据。属于**伪安全**，
 * 仅提供混淆层次的保护。token 等敏感凭据应改用后端下发的 HttpOnly Secure Cookie
 * （SameSite=Lax），前端不存储凭据。详见 docs/FRONTEND_OPTIMIZATION_REVIEW.md 安全章节。
 */
import type { Pinia } from 'pinia';

import type { App } from 'vue';

import { createPinia } from 'pinia';
import SecureLS from 'secure-ls';

let pinia: Pinia;

/** 已注册的 store 列表，用于替代访问 Pinia 内部 _s 属性 */
const registeredStores: Set<ReturnType<Pinia['_s']['get']>> = new Set();

/**
 * {@link initStores} 的初始化参数。
 *
 * @remarks
 * 该配置直接决定持久化存储的键名前缀，修改后**历史缓存将无法读取**（等同于清空本地状态），
 * 因此上线后不要随意变更。
 */
export interface InitStoreOptions {
  /**
   * @zh_CN 应用名,由于 @ydsz/stores 是公用的，后续可能有多个app，为了防止多个app缓存冲突，可在这里配置应用名,应用名将被用于持久化的前缀
   */
  namespace: string;
}

/**
 * @zh_CN 初始化pinia
 *
 * @remarks
 * 创建 Pinia 实例、装配持久化插件并安装到应用，应在应用启动阶段调用一次。
 *
 * 持久化行为：
 * - 存储键统一加上 `${namespace}-` 前缀，用于隔离同域下多个应用的缓存；
 * - **开发环境直接使用明文 `localStorage`**，便于调试；
 *   生产环境改用 `secure-ls`（AES 加密 + 压缩）写入，密钥取自 `VITE_APP_STORE_SECURE_KEY`，
 *   该环境变量缺失会导致加解密失败、持久化数据读不回来；
 * - 只有显式声明了 `persist` 的 store 才会被持久化。
 *
 * 副作用：以 `pinia-plugin-persistedstate` 的动态 `import()` 延迟加载插件（利于首屏分包）；
 * 同时劫持 `pinia._s.set` 以登记所有已注册 store，供 {@link resetAllStores} 遍历重置。
 *
 * @param app - Vue 应用实例
 * @param options - 初始化配置，见 {@link InitStoreOptions}
 * @returns 已安装的 Pinia 实例
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
