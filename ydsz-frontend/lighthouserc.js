module.exports = {
  ci: {
    collect: {
      // 采集配置
      startServerCommand: 'pnpm run preview',
      startServerReadyPattern: 'Local:',
      startServerReadyTimeout: 30000,
      numberOfRuns: 3,
      url: [
        'http://localhost:4173/',
        'http://localhost:4173/ydsz-proj/',
        'http://localhost:4173/ydsz-user/',
      ],
      settings: {
        // 模拟移动设备
        preset: 'desktop',
        // 禁用某些不必要的检查
        disableStorageReset: false,
        // Chrome 启动参数
        chromeFlags: '--no-sandbox --disable-gpu --disable-dev-shm-usage',
      },
    },
    assert: {
      // 性能预算断言
      assertions: {
        // === Core Web Vitals ===
        'categories:performance': ['warn', { minScore: 0.9 }],
        'first-contentful-paint': ['warn', { maxNumericValue: 2000 }],
        'largest-contentful-paint': ['warn', { maxNumericValue: 2500 }],
        'interactive': ['warn', { maxNumericValue: 3800 }],
        'total-blocking-time': ['warn', { maxNumericValue: 300 }],
        'cumulative-layout-shift': ['warn', { maxNumericValue: 0.1 }],
        'speed-index': ['warn', { maxNumericValue: 3400 }],
        
        // === 资源大小预算 ===
        'resource-summary:script:count': ['warn', { maxNumericValue: 50 }],
        'resource-summary:stylesheet:count': ['warn', { maxNumericValue: 10 }],
        'resource-summary:image:size': ['warn', { maxNumericValue: 1024 * 1024 }],
        'resource-summary:script:size': ['warn', { maxNumericValue: 512 * 1024 }],
        'resource-summary:stylesheet:size': ['warn', { maxNumericValue: 128 * 1024 }],
        'resource-summary:third-party:size': ['warn', { maxNumericValue: 256 * 1024 }],
        
        // === 最佳实践 ===
        'categories:accessibility': ['error', { minScore: 0.9 }],
        'categories:best-practices': ['warn', { minScore: 0.9 }],
        'categories:seo': ['warn', { minScore: 0.9 }],
        
        // === 具体检查项 ===
        'dom-size': ['warn', { maxNumericValue: 1500 }],
        'uses-rel-preconnect': 'warn',
        'uses-responsive-images': 'warn',
        'offscreen-images': 'warn',
        'unoptimized-images': 'warn',
        'render-blocking-resources': 'warn',
        'unused-css-rules': 'warn',
        'unused-javascript': 'warn',
        'modern-image-formats': 'warn',
        'uses-text-compression': 'warn',
        'uses-long-cache-ttl': 'warn',
      },
    },
    upload: {
      // 上传配置（可选，用于持久化报告）
      target: 'temporary-public-storage',
    },
    server: {
      // CI 服务器配置
      port: 9009,
    },
  },
};
