/**
 * 登录压测脚本
 *
 * 场景：模拟用户登录，验证网关认证性能 + JWT 签发能力
 * 目标：单实例 ≥ 2000 QPS，P99 ≤ 200ms
 *
 * 运行：
 *   k6 run --vus 100 --duration 10m --env BASE_URL=http://ydsz-gateway.local login.js
 *
 * @since 1.0.0
 * @author ydsz-team
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 自定义 Metrics
const loginSuccessRate = new Rate('login_success');
const loginLatency = new Trend('login_latency', true);
const jwtParseTime = new Trend('jwt_parse_time', true);

// 压测配置（渐进式加压）
export const options = {
    scenarios: {
        // 渐进爬坡
        ramp_up: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '2m', target: 50 },    // 预热
                { duration: '3m', target: 200 },   // 爬坡
                { duration: '5m', target: 500 },   // 持续高压
                { duration: '2m', target: 0 },     // 降载
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        // P99 响应时间 < 300ms
        'login_latency': ['p(99) < 300'],
        // 登录成功率 > 99.5%
        'login_success': ['rate > 0.995'],
        // 错误率 < 0.5%
        'http_req_failed': ['rate < 0.005'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://ydsz-gateway.local:9000';
const TEST_USER_PREFIX = 'loadtest_user_';
const TEST_PASSWORD = 'Load@Test123';

/**
 * 主函数：每个 VU 执行登录
 */
export default function () {
    // 构造登录请求
    const username = `${TEST_USER_PREFIX}${__VU}`;
    const payload = JSON.stringify({
        username: username,
        password: TEST_PASSWORD,
    });

    const startTime = Date.now();
    const res = http.post(`${BASE_URL}/api/v1/user/login`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'X-LoadTest': 'true',
        },
        tags: { name: 'user_login' },
    });

    const duration = Date.now() - startTime;
    loginLatency.add(duration);

    // 断言
    const success = check(res, {
        'login status is 200': (r) => r.status === 200,
        'login response has token': (r) => r.json('data.token') !== undefined,
        'login token not empty': (r) => r.json('data.token') !== '',
        'login code is 0': (r) => r.json('code') === 0,
    });

    loginSuccessRate.add(success);

    if (!success && res.status !== 429) {
        console.error(`[VU ${__VU}] 登录失败: status=${res.status}, body=${res.body?.substring(0, 200)}`);
    }

    // 限流时等待
    if (res.status === 429) {
        const retryAfter = res.headers['Retry-After'] || 1;
        sleep(parseInt(retryAfter));
        return;
    }

    // 模拟真实登录间隔（泊松分布）
    sleep(Math.random() * 2 + 0.5);
}

/**
 * setup：压测开始前检查网关健康
 */
export function setup() {
    const healthUrl = `${BASE_URL}/actuator/health`;
    const res = http.get(healthUrl);

    if (res.status !== 200) {
        throw new Error(`网关健康检查失败: status=${res.status}`);
    }

    console.log('✓ 网关健康检查通过，开始登录压测');
    return { startTime: Date.now() };
}

/**
 * teardown：输出压测汇总
 */
export function teardown(data) {
    const duration = (Date.now() - data.startTime) / 1000;
    console.log(`\n登录压测完成，总耗时: ${duration}s`);
}
