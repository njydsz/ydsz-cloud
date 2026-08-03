/**
 * logger 测试
 *
 * 覆盖：
 *   - 日志级别过滤（debug/info/warn/error）
 *   - 模块过滤（include/exclude）
 *   - 生产 vs 开发默认级别
 *   - 动态级别调整
 *
 * @path comm/@core/base/shared/src/utils/__tests__/logger.test.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  LogLevel,
  createLogger,
  getLogLevel,
  initLogger,
  resetLogger,
  setLogLevel,
} from '../logger';

describe('logger', () => {
  beforeEach(() => {
    resetLogger();
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    resetLogger();
  });

  describe('日志级别过滤', () => {
    it('生产环境默认 INFO 级别：debug 不输出，info/warn/error 输出', () => {
      initLogger({ isDev: false });
      const logger = createLogger('TestModule');

      const debug = vi.spyOn(console, 'debug').mockImplementation(() => {});
      const info = vi.spyOn(console, 'info').mockImplementation(() => {});
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const error = vi.spyOn(console, 'error').mockImplementation(() => {});

      logger.debug('debug msg');
      logger.info('info msg');
      logger.warn('warn msg');
      logger.error('error msg');

      expect(debug).not.toHaveBeenCalled();
      expect(info).toHaveBeenCalledTimes(1);
      expect(warn).toHaveBeenCalledTimes(1);
      expect(error).toHaveBeenCalledTimes(1);
    });

    it('开发环境默认 DEBUG 级别：全部输出', () => {
      initLogger({ isDev: true });
      const logger = createLogger('TestModule');

      const debug = vi.spyOn(console, 'debug').mockImplementation(() => {});
      const info = vi.spyOn(console, 'info').mockImplementation(() => {});
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const error = vi.spyOn(console, 'error').mockImplementation(() => {});

      logger.debug('debug msg');
      logger.info('info msg');
      logger.warn('warn msg');
      logger.error('error msg');

      expect(debug).toHaveBeenCalledTimes(1);
      expect(info).toHaveBeenCalledTimes(1);
      expect(warn).toHaveBeenCalledTimes(1);
      expect(error).toHaveBeenCalledTimes(1);
    });

    it('WARN 级别：仅 warn/error 输出', () => {
      initLogger({ minLevel: LogLevel.WARN });
      const logger = createLogger('TestModule');

      const info = vi.spyOn(console, 'info').mockImplementation(() => {});
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});

      logger.info('info msg');
      logger.warn('warn msg');

      expect(info).not.toHaveBeenCalled();
      expect(warn).toHaveBeenCalledTimes(1);
    });

    it('ERROR 级别：仅 error 输出', () => {
      initLogger({ minLevel: LogLevel.ERROR });
      const logger = createLogger('TestModule');

      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const error = vi.spyOn(console, 'error').mockImplementation(() => {});

      logger.warn('warn msg');
      logger.error('error msg');

      expect(warn).not.toHaveBeenCalled();
      expect(error).toHaveBeenCalledTimes(1);
    });
  });

  describe('模块过滤', () => {
    it('localStorage ydsz:debug 设置后仅匹配模块输出 debug', () => {
      localStorage.setItem('ydsz:debug', 'MicroKernel:*');
      initLogger({ isDev: true });

      const kernelLogger = createLogger('MicroKernel');
      const otherLogger = createLogger('OtherModule');

      const kernelDebug = vi.spyOn(console, 'debug').mockImplementation(() => {});
      vi.spyOn(console, 'info').mockImplementation(() => {});

      kernelLogger.debug('kernel debug');
      otherLogger.debug('other debug');

      // MicroKernel:* 匹配 MicroKernel 开头的模块
      expect(kernelDebug).toHaveBeenCalledTimes(1);
      // otherLogger 的 debug 应被过滤
      // 由于 spy 是按 console.debug 计数，需要分别 spy
    });

    it('排除模式 -MicroKernel:* 跳过指定模块', () => {
      localStorage.setItem('ydsz:debug', '*,-MicroKernel:*');
      initLogger({ isDev: true });

      const kernelLogger = createLogger('MicroKernel');
      const otherLogger = createLogger('OtherModule');

      const debug = vi.spyOn(console, 'debug').mockImplementation(() => {});

      kernelLogger.debug('kernel debug');
      otherLogger.debug('other debug');

      // MicroKernel 被排除
      const kernelCalls = debug.mock.calls.filter(
        (call) => typeof call[0] === 'string' && call[0].includes('[MicroKernel]'),
      );
      const otherCalls = debug.mock.calls.filter(
        (call) => typeof call[0] === 'string' && call[0].includes('[OtherModule]'),
      );
      expect(kernelCalls).toHaveLength(0);
      expect(otherCalls).toHaveLength(1);
    });

    it('warn/error 不受模块过滤影响', () => {
      localStorage.setItem('ydsz:debug', 'OnlyThis:*');
      initLogger({ isDev: true });

      const logger = createLogger('ExcludedModule');
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const error = vi.spyOn(console, 'error').mockImplementation(() => {});

      logger.warn('warn msg');
      logger.error('error msg');

      expect(warn).toHaveBeenCalledTimes(1);
      expect(error).toHaveBeenCalledTimes(1);
    });
  });

  describe('动态级别调整', () => {
    it('setLogLevel 运行期切换级别', () => {
      initLogger({ isDev: true });
      expect(getLogLevel()).toBe(LogLevel.DEBUG);

      setLogLevel(LogLevel.ERROR);
      expect(getLogLevel()).toBe(LogLevel.ERROR);

      const logger = createLogger('Test');
      const info = vi.spyOn(console, 'info').mockImplementation(() => {});
      logger.info('should not log');
      expect(info).not.toHaveBeenCalled();
    });
  });

  describe('未初始化时的兜底', () => {
    it('未调用 initLogger 时首次调用自动初始化为 INFO', () => {
      const logger = createLogger('AutoInit');
      const info = vi.spyOn(console, 'info').mockImplementation(() => {});
      const debug = vi.spyOn(console, 'debug').mockImplementation(() => {});

      logger.info('info msg');
      logger.debug('debug msg');

      expect(info).toHaveBeenCalledTimes(1);
      expect(debug).not.toHaveBeenCalled();
    });
  });

  describe('日志前缀', () => {
    it('日志输出包含模块名前缀', () => {
      initLogger({ isDev: true });
      const logger = createLogger('MyModule');
      const info = vi.spyOn(console, 'info').mockImplementation(() => {});

      logger.info('hello');

      expect(info).toHaveBeenCalledWith(
        expect.stringContaining('[ydsz][MyModule]'),
        'hello',
      );
    });
  });
});
