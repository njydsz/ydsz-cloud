/**
 * @ydsz/monitor 单元测试
 */
import { describe, expect, it, vi } from 'vitest';

// Mock navigator.sendBeacon
vi.stubGlobal('navigator', {
  sendBeacon: vi.fn(() => true),
  userAgent: 'test-agent',
});

describe('@ydsz/monitor error-monitor', () => {
  it('should export setupErrorMonitoring function', async () => {
    const { setupErrorMonitoring } = await import('../src/error-monitor');
    expect(typeof setupErrorMonitoring).toBe('function');
  });

  it('should export reportError function', async () => {
    const { reportError } = await import('../src/error-monitor');
    expect(typeof reportError).toBe('function');
  });

  it('should accept error reports without throwing', async () => {
    const { reportError } = await import('../src/error-monitor');
    expect(() => {
      reportError('window', 'Test error message', { extra: 'data' });
    }).not.toThrow();
  });
});

describe('@ydsz/monitor web-vitals', () => {
  it('should export setupWebVitals function', async () => {
    const { setupWebVitals } = await import('../src/web-vitals');
    expect(typeof setupWebVitals).toBe('function');
  });

  it('should export reportWebVital function', async () => {
    const { reportWebVital } = await import('../src/web-vitals');
    expect(typeof reportWebVital).toBe('function');
  });

  it('should accept web vital reports without throwing', async () => {
    const { reportWebVital } = await import('../src/web-vitals');
    expect(() => {
      reportWebVital('LCP', 2500);
    }).not.toThrow();
  });
});

describe('@ydsz/monitor setup', () => {
  it('should export setupMonitor function', async () => {
    const { setupMonitor } = await import('../src/setup');
    expect(typeof setupMonitor).toBe('function');
  });
});
