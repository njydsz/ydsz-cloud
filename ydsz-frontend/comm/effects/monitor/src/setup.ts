/**
 * 监控模块统一安装入口
 *
 * 在 app.mount() 之前调用 setupMonitor(app) 即可同时启用错误监控和 Web Vitals。
 */
import { setupErrorMonitoring } from './error-monitor';
import { setupWebVitals } from './web-vitals';

/**
 * 安装全部监控能力
 */
export function setupMonitor(app: any) {
  setupErrorMonitoring(app);
  setupWebVitals();
}
