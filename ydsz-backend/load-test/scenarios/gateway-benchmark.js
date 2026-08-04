// =============================================================================
// 网关层基准压测场景
// 目标：测量网关纯转发能力（限流/鉴权白名单路径）
// 用法：docker run --rm -v ./scenarios:/scenarios grafana/k6 run /scenarios/gateway-benchmark.js
// =============================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';

// 压测环境网关地址（覆盖：k6 run -e BASE_URL=http://gateway:9000 ...）
const BASE_URL = __ENV.BASE_URL || 'http://localhost:9000';

export const options = {
  scenarios: {
    // 基准：固定 50 并发，5 分钟
    baseline: {
      executor: 'constant-vus',
      vus: 50,
      duration: '5m',
      exec: 'benchmark',
    },
    // 容量：阶梯加压（用 --scenario ramp 单独运行）
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 },
        { duration: '2m', target: 200 },
        { duration: '2m', target: 400 },
        { duration: '2m', target: 200 },
        { duration: '2m', target: 100 },
      ],
      exec: 'benchmark',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],          // 错误率 < 1%
    http_req_duration: ['p(95)<500', 'p(99)<1000'],  // P95 < 500ms, P99 < 1s
  },
};

export function benchmark() {
  // 健康检查路径（限流白名单，测纯转发）
  const res = http.get(`${BASE_URL}/actuator/health`, { tags: { name: 'health' } });
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response < 500ms': (r) => r.timings.duration < 500,
  });
  sleep(0.1); // 模拟思考时间
}

// 注意：正式压测应使用带鉴权的真实路径（如 /api/v1/projects?page=1&size=10），
// 先在网关放行一个测试账号的压测专用路径，避免登录接口成为瓶颈。
