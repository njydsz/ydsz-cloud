/**
 * @ydsz/shared-auth 单元测试
 */
import { describe, expect, it, vi } from 'vitest';

// Mock dependencies before importing
vi.mock('@ydsz/hooks', () => ({
  useAppConfig: () => ({
    apiURL: 'http://localhost:9000',
  }),
}));

vi.mock('@ydsz/preferences', () => ({
  preferences: {
    app: {
      locale: 'zh-CN',
      enableRefreshToken: true,
      loginExpiredMode: 'page',
      defaultHomePath: '/dashboard',
    },
  },
}));

vi.mock('@ydsz/stores', () => ({
  useAccessStore: () => ({
    accessToken: 'test-token',
    isAccessChecked: false,
    loginExpired: false,
    setAccessToken: vi.fn(),
    setLoginExpired: vi.fn(),
    setAccessCodes: vi.fn(),
  }),
  useUserStore: () => ({
    setUserInfo: vi.fn(),
    userInfo: null,
  }),
  resetAllStores: vi.fn(),
}));

vi.mock('@ydsz/locales', () => ({
  $t: (key: string) => key,
  loadLocalesMapFromDir: (
    _pattern: RegExp,
    modules: Record<string, () => Promise<unknown>>,
  ) => ({
    'zh-CN': async () => ({ default: (await modules['./langs/zh-CN/page.json']?.())?.default }),
    'en-US': async () => ({ default: (await modules['./langs/en-US/page.json']?.())?.default }),
  }),
  setupI18n: vi.fn(async (_app: unknown, _opts?: unknown) => {}),
}));

vi.mock('dayjs', () => ({
  default: {
    locale: vi.fn(),
  },
}));

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() },
  ElNotification: { success: vi.fn() },
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({
    currentRoute: { value: { fullPath: '/' } },
    push: vi.fn(),
    replace: vi.fn(),
  }),
}));

describe('@ydsz/shared-auth types', () => {
  it('should export AuthApi namespace with correct interfaces', async () => {
    const { AuthApi } = await import('../src/types');

    // Type-level test: verify the interfaces exist
    const loginParams: AuthApi.LoginParams = {
      password: 'pass',
      username: 'user',
    };
    expect(loginParams.username).toBe('user');
    expect(loginParams.password).toBe('pass');

    const loginResult: AuthApi.LoginResult = {
      accessToken: 'token',
      expiresIn: 3600,
      refreshToken: 'refresh',
      scope: 'all',
      tokenType: 'Bearer',
      userInfo: {
        realName: 'Test User',
        userId: '1',
        username: 'test',
      },
    };
    expect(loginResult.accessToken).toBe('token');
    expect(loginResult.refreshToken).toBe('refresh');
    expect(loginResult.userInfo.realName).toBe('Test User');
  });
});

describe('@ydsz/shared-auth request setup', () => {
  it('should throw when requestClient accessed before initSharedRequest', async () => {
    // The Proxy should throw when not initialized
    const { requestClient } = await import('../src/request-setup');

    expect(() => requestClient.get('/test')).toThrow(
      'requestClient not initialized',
    );
  });

  it('should expose initSharedRequest function', async () => {
    const { initSharedRequest } = await import('../src/request-setup');
    expect(typeof initSharedRequest).toBe('function');
  });
});

describe('@ydsz/shared-auth auth-api endpoints', () => {
  it('should export loginApi function', async () => {
    const { loginApi } = await import('../src/auth-api');
    expect(typeof loginApi).toBe('function');
  });

  it('should export logoutApi function', async () => {
    const { logoutApi } = await import('../src/auth-api');
    expect(typeof logoutApi).toBe('function');
  });

  it('should export refreshTokenApi function', async () => {
    const { refreshTokenApi } = await import('../src/auth-api');
    expect(typeof refreshTokenApi).toBe('function');
  });

  it('should export getAccessCodesApi function', async () => {
    const { getAccessCodesApi } = await import('../src/auth-api');
    expect(typeof getAccessCodesApi).toBe('function');
  });
});

describe('@ydsz/shared-auth user-api endpoints', () => {
  it('should export getUserInfoApi function', async () => {
    const { getUserInfoApi } = await import('../src/user-api');
    expect(typeof getUserInfoApi).toBe('function');
  });
});

describe('@ydsz/shared-auth menu-api endpoints', () => {
  it('should export getAllMenusApi function', async () => {
    const { getAllMenusApi } = await import('../src/menu-api');
    expect(typeof getAllMenusApi).toBe('function');
  });

  it('should export getMenuTreeApi function', async () => {
    const { getMenuTreeApi } = await import('../src/menu-api');
    expect(typeof getMenuTreeApi).toBe('function');
  });
});

describe('@ydsz/shared-auth auth-store', () => {
  it('should export createSharedAuthStore function', async () => {
    const { createSharedAuthStore } = await import('../src/auth-store');
    expect(typeof createSharedAuthStore).toBe('function');
  });
});

describe('@ydsz/shared-auth createSubAppI18n', () => {
  it('should return $t, elementLocale, setupI18n', async () => {
    const { createSubAppI18n } = await import('../src/i18n-setup');

    const modules = {
      './langs/zh-CN/page.json': () => Promise.resolve({ default: { title: '页面' } }),
      './langs/en-US/page.json': () => Promise.resolve({ default: { title: 'Page' } }),
    };

    const instance = createSubAppI18n({ modules });

    expect(typeof instance.$t).toBe('function');
    expect(instance.elementLocale).toBeDefined();
    expect(typeof instance.setupI18n).toBe('function');
  });

  it('should accept custom pattern', async () => {
    const { createSubAppI18n } = await import('../src/i18n-setup');

    const modules = {
      './langs/zh-CN/page.json': () => Promise.resolve({ default: {} }),
    };

    const instance = createSubAppI18n({
      modules,
      pattern: /\.\/langs\/([^/]+)\/(.*)\.json$/,
    });

    expect(instance.elementLocale).toBeDefined();
  });
});
