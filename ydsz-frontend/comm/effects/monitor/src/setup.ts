/**
 * 监控模块统一安装入口
 *
 * 在 app.mount() 之前调用 setupMonitor(app, config) 即可同时启用错误监控和 Web Vitals。
 * v3.1: config 支持 release / sampleRate / beforeSend / getUserId，用于全链路追踪与采样。
 */
import type { MonitorConfig } from './error-monitor';

import { setupErrorMonitoring } from './error-monitor';
import { setupWebVitals } from './web-vitals';

/**
 * 安装全部监控能力
 *
 * @param app - Vue 应用实例
 * @param config - 监控配置（release 版本、采样率、脱敏钩子、用户 ID 获取）
 */
export function setupMonitor(app: any, config: MonitorConfig = {}) {
  setupErrorMonitoring(app, config);
  setupWebVitals();
}
