/**
 * PMIS 性能基线测试 — k6 脚本
 *
 * 测试场景:
 *   1. 用户登录 → 获取 JWT Token
 *   2. 项目列表查询（分页）
 *   3. 项目详情查询
 *   4. 并发规则评估
 *
 * 运行方式:
 *   k6 run --env BASE_URL=http://localhost:8080 deploy/perf/k6-baseline-test.js
 *   k6 run --env BASE_URL=https://pmis.example.com --env STRESS=true deploy/perf/k6-baseline-test.js
 *
 * 指标基线（SIT 环境）:
 *   - p95 < 500ms（登录）
 *   - p95 < 300ms（列表查询）
 *   - p95 < 200ms（详情查询）
 *   - 错误率 < 1%
 *
 * @author ydsz-team
 * @since 1.0.0
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ============================================================
// 配置
// ============================================================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const STRESS = __ENV.STRESS === 'true';

// 自定义指标
const loginDuration = new Trend('login_duration', true);
const queryDuration = new Trend('query_duration', true);
const ruleEvalDuration = new Trend('rule_eval_duration', true);
const errorRate = new Rate('errors');

// ============================================================
// 负载配置
// ============================================================
export const options = STRESS ? {
    // 压力测试：逐步加压到 500 VU
    stages: [
        { duration: '2m', target: 50 },   // 预热
        { duration: '5m', target: 200 },  // 正常负载
        { duration: '3m', target: 500 },  // 峰值
        { duration: '2m', target: 0 },    // 降温
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        errors: ['rate<0.05'],
    },
} : {
    // 基线测试：20 VU 持续 3 分钟
    vus: 20,
    duration: '3m',
    thresholds: {
        'login_duration': ['p(95)<500'],
        'query_duration': ['p(95)<300'],
        'rule_eval_duration': ['p(95)<200'],
        'errors': ['rate<0.01'],
    },
};

// ============================================================
// 测试数据（凭据通过环境变量注入，避免硬编码）
//   K6_TEST_USER / K6_TEST_PASS 必填
//   示例: k6 run --env BASE_URL=... --env K6_TEST_USER=admin --env K6_TEST_PASS=xxx ...
// ============================================================
const TEST_USER = __ENV.K6_TEST_USER || 'admin';
const TEST_PASS = __ENV.K6_TEST_PASS || '';
const TEST_USERS = TEST_PASS
    ? [{ username: TEST_USER, password: TEST_PASS }]
    : [];

// ============================================================
// 工具函数
// ============================================================
function getTestUser() {
    return TEST_USERS[Math.floor(Math.random() * TEST_USERS.length)];
}

function login(user) {
    const payload = JSON.stringify({
        username: user.username,
        password: user.password,
        captcha: 'test-captcha-bypass',
    });

    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    const res = http.post(`${BASE_URL}/auth/login`, payload, params);

    const success = check(res, {
        'login status 200': (r) => r.status === 200,
        'login has token': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.code === 0 && body.data && body.data.accessToken;
            } catch (e) {
                return false;
            }
        },
    });

    loginDuration.add(res.timings.duration);
    errorRate.add(!success);

    if (success) {
        return JSON.parse(res.body).data.accessToken;
    }
    return null;
}

function queryProjectList(token, page, size) {
    const params = {
        headers: { 'Authorization': `Bearer ${token}` },
        qs: { page: page || 1, size: size || 20 },
    };

    const res = http.get(`${BASE_URL}/project/list`, params);

    const success = check(res, {
        'list status 200': (r) => r.status === 200,
        'list has data': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.code === 0;
            } catch (e) {
                return false;
            }
        },
    });

    queryDuration.add(res.timings.duration);
    errorRate.add(!success);

    return success ? JSON.parse(res.body) : null;
}

function queryProjectDetail(token, projectId) {
    const params = {
        headers: { 'Authorization': `Bearer ${token}` },
    };

    const res = http.get(`${BASE_URL}/project/${projectId}`, params);

    const success = check(res, {
        'detail status 200': (r) => r.status === 200,
    });

    queryDuration.add(res.timings.duration);
    errorRate.add(!success);

    return success ? JSON.parse(res.body) : null;
}

function evaluateRules(token, facts) {
    const payload = JSON.stringify({
        facts: facts || {
            projectAmount: 1000000,
            projectType: 'SOFTWARE',
            riskLevel: 'MEDIUM',
        },
        scenario: 'RISK_ASSESS',
    });

    const params = {
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json',
        },
    };

    const res = http.post(`${BASE_URL}/rule/evaluate`, payload, params);

    const success = check(res, {
        'rule eval status 200': (r) => r.status === 200,
        'rule eval has results': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.code === 0;
            } catch (e) {
                return false;
            }
        },
    });

    ruleEvalDuration.add(res.timings.duration);
    errorRate.add(!success);
}

// ============================================================
// 测试场景
// ============================================================
export default function () {
    let token = null;

    group('用户登录', () => {
        const user = getTestUser();
        token = login(user);
        if (!token) {
            console.error('登录失败，跳过后续测试');
            return;
        }
    });

    if (!token) {
        sleep(1);
        return;
    }

    sleep(0.5);

    group('项目列表查询', () => {
        queryProjectList(token, 1, 20);
    });

    sleep(0.3);

    group('项目详情查询', () => {
        // 使用一个固定的测试项目 ID
        queryProjectDetail(token, 'PRJ-TEST-001');
    });

    sleep(0.3);

    group('规则评估', () => {
        evaluateRules(token, {
            projectAmount: Math.random() * 5000000,
            projectType: ['SOFTWARE', 'HARDWARE', 'CONSULTING'][Math.floor(Math.random() * 3)],
            riskLevel: ['LOW', 'MEDIUM', 'HIGH'][Math.floor(Math.random() * 3)],
        });
    });

    sleep(1);
}

// ============================================================
// 测试结束报告
// ============================================================
export function handleSummary(data) {
    const summary = {
        baseline: !STRESS,
        vu_count: STRESS ? '50-500' : '20',
        duration: STRESS ? '12m' : '3m',
        metrics: {
            login_p95: data.metrics.login_duration ? data.metrics.login_duration['p(95)'] : 'N/A',
            query_p95: data.metrics.query_duration ? data.metrics.query_duration['p(95)'] : 'N/A',
            rule_eval_p95: data.metrics.rule_eval_duration ? data.metrics.rule_eval_duration['p(95)'] : 'N/A',
            error_rate: data.metrics.errors ? data.metrics.errors.rate : 'N/A',
            http_reqs: data.metrics.http_reqs ? data.metrics.http_reqs.count : 'N/A',
        },
        thresholds: data.thresholds,
    };

    return {
        'stdout': JSON.stringify(summary, null, 2),
    };
}
