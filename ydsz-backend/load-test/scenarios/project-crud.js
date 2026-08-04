// =============================================================================
// 项目 CRUD 场景（模拟真实业务链路：网关 → 鉴权 → project 服务 → PG）
// 用法：k6 run -e BASE_URL=... -e TOKEN=<测试token> project-crud.js
// =============================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9000';
const TOKEN = __ENV.TOKEN || '';
const PARAMS = {
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json',
  },
};

export const options = {
  stages: [
    { duration: '1m', target: 20 },   // 预热
    { duration: '3m', target: 100 },  // 加压
    { duration: '2m', target: 100 },  // 持续
    { duration: '1m', target: 0 },    // 收尾
  ],
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<800'],
  },
};

// 读多写少：90% 查询 + 10% 写入
export default function () {
  const r = Math.random();
  if (r < 0.4) {
    // 分页列表查询
    const res = http.get(`${BASE_URL}/api/v1/projects?page=1&size=10&status=EXECUTION`, PARAMS);
    check(res, { 'list 200': (x) => x.status === 200 });
  } else if (r < 0.7) {
    // 详情查询
    const res = http.get(`${BASE_URL}/api/v1/projects/1001`, PARAMS);
    check(res, { 'detail 200': (x) => x.status === 200 });
  } else if (r < 0.9) {
    // EVM 指标查询（重查询场景）
    const res = http.get(`${BASE_URL}/api/v1/projects/1001/evm`, PARAMS);
    check(res, { 'evm 200': (x) => x.status === 200 });
  } else {
    // 写入：合同变更（10%）
    const payload = JSON.stringify({
      projectId: 1001,
      type: 'SCOPE',
      title: `压测变更-${__VU}-${__ITER}`,
      description: '自动化压测数据，可删除',
    });
    const res = http.post(`${BASE_URL}/api/v1/projects/1001/changes`, payload, PARAMS);
    check(res, { 'change 200': (x) => x.status === 200 || x.status === 201 });
  }
  sleep(0.5);
}
