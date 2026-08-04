/**
 * K6 压测公共模块
 *
 * 提供：
 * - 统一请求构造
 * - JWT Token 获取与复用
 * - 断言封装
 * - 自定义 Metrics
 * - 压测流量标识
 *
 * @since 1.0.0
 * @author ydsz-team
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Trend, Rate, Gauge } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ============================
// 环境配置（通过 --env 传入）
// ============================
const BASE_URL = __ENV.BASE_URL || 'http://ydsz-gateway.local:9000';
const API_VERSION = __ENV.API_VERSION || 'v1';
const TEST_USER = __ENV.TEST_USER || 'loadtest_user';
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'Load@Test123';

// ============================
// 自定义 Metrics
// ============================
// 业务错误率（HTTP 200 但业务 code !== 0）
const businessErrorRate = new Rate('business_errors');

// 按接口统计响应时间
const endpointLatency = new Trend('endpoint_latency', true);

// 网关限流命中次数
const rateLimitHits = new Counter('rate_limit_hits');

// 登录成功率
const loginSuccessRate = new Rate('login_success');

// JWT 校验耗时
const jwtValidationTime = new Trend('jwt_validation_time', true);

// ============================
// Token 缓存（VU 级别复用）
// ============================
let cachedToken = null;
let tokenExpireAt = 0;

// ============================
// 请求构造器
// ============================

/**
 * 构造标准请求头（含压测标识 + TraceId）
 */
function buildHeaders(token = null) {
    const headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'X-LoadTest': 'true',
        'X-Request-ID': `loadtest-${__VU}-${__ITER}-${Date.now()}`,
        'X-Tenant-Id': 'default',
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    return headers;
}

/**
 * 发送 GET 请求（带标准断言）
 */
function requestGet(name, path, token = null, expectedStatus = 200) {
    const url = `${BASE_URL}/api/${API_VERSION}${path}`;
    const startTime = Date.now();

    const res = http.get(url, {
        headers: buildHeaders(token),
        tags: { endpoint: name },
    });

    const duration = Date.now() - startTime;
    endpointLatency.add(duration, { endpoint: name });

    // 检查限流
    const isRateLimited = res.status === 429;
    if (isRateLimited) {
        rateLimitHits.add(1, { endpoint: name });
    }

    // 标准响应断言
    const success = check(res, {
        [`${name} - status is ${expectedStatus}`]: (r) => r.status === expectedStatus,
        [`${name} - response time < 500ms`]: (r) => r.timings.duration < 500,
        [`${name} - has valid JSON`]: (r) => {
            try { JSON.parse(r.body); return true; }
            catch (e) { return false; }
        },
    });

    // 业务码检查（HTTP 200 时）
    if (res.status === 200 && res.body) {
        try {
            const body = JSON.parse(res.body);
            const bizSuccess = body.code === 0 || body.code === 200;
            businessErrorRate.add(!bizSuccess);
        } catch (e) {
            // 非 JSON 响应忽略
        }
    }

    return res;
}

/**
 * 发送 POST 请求（带 JSON body）
 */
function requestPost(name, path, body, token = null, expectedStatus = 200) {
    const url = `${BASE_URL}/api/${API_VERSION}${path}`;
    const payload = JSON.stringify(body);
    const startTime = Date.now();

    const res = http.post(url, payload, {
        headers: buildHeaders(token),
        tags: { endpoint: name },
    });

    const duration = Date.now() - startTime;
    endpointLatency.add(duration, { endpoint: name });

    if (res.status === 429) {
        rateLimitHits.add(1, { endpoint: name });
    }

    check(res, {
        [`${name} - status ${expectedStatus}`]: (r) => r.status === expectedStatus,
        [`${name} - SLA`]: (r) => r.timings.duration < 1000,
    });

    return res;
}

/**
 * 发送 DELETE 请求
 */
function requestDelete(name, path, token = null, expectedStatus = 200) {
    const url = `${BASE_URL}/api/${API_VERSION}${path}`;

    const res = http.del(url, null, {
        headers: buildHeaders(token),
        tags: { endpoint: name },
    });

    endpointLatency.add(Date.now(), { endpoint: name });

    if (res.status === 429) {
        rateLimitHits.add(1, { endpoint: name });
    }

    check(res, {
        [`${name} - status ${expectedStatus}`]: (r) => r.status === expectedStatus,
    });

    return res;
}

// ============================
// 认证模块
// ============================

/**
 * 获取有效的 JWT Token（带缓存）
 *
 * 每个 VU 首次调用时执行登录，后续调用复用缓存 Token。
 * 缓存过期前 60s 主动刷新，避免 Token 过期导致 401。
 */
function getToken() {
    const now = Date.now();

    if (cachedToken && now < tokenExpireAt) {
        return cachedToken;
    }

    const startTime = Date.now();
    const res = http.post(
        `${BASE_URL}/api/${API_VERSION}/user/login`,
        JSON.stringify({
            username: `${TEST_USER}_${__VU}`,
            password: TEST_PASSWORD,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-LoadTest': 'true',
            },
            tags: { endpoint: 'login' },
        }
    );

    const duration = Date.now() - startTime;
    jwtValidationTime.add(duration);

    const success = check(res, {
        'login - status is 200': (r) => r.status === 200,
        'login - token present': (r) => r.json('data.token') !== '',
    });

    loginSuccessRate.add(success);

    if (res.status === 200 && res.json('data.token')) {
        cachedToken = res.json('data.token');
        // Token 有效期假设为 2h：提前 60s 刷新
        tokenExpireAt = now + 7200000 - 60000;
        return cachedToken;
    }

    console.error(`[VU ${__VU}] 登录失败: status=${res.status} body=${res.body}`);
    return null;
}

/**
 * 强制刷新 Token（用于测试异常场景）
 */
function forceRefreshToken() {
    cachedToken = null;
    tokenExpireAt = 0;
    return getToken();
}

// ============================
// 测试数据生成器
// ============================

/**
 * 生成随机用户信息
 */
function generateRandomUser() {
    const id = randomIntBetween(1, 1000000);
    return {
        username: `loadtest_user_${id}`,
        email: `loadtest_${id}@test.com`,
        phone: `138${String(randomIntBetween(10000000, 99999999))}`,
        realName: `测试用户${id}`,
        departmentId: randomIntBetween(1, 100),
    };
}

/**
 * 生成随机项目信息
 */
function generateRandomProject() {
    const id = randomIntBetween(1, 500000);
    const statuses = ['PLANNING', 'EXECUTING', 'PAUSED', 'COMPLETED'];
    return {
        name: `压测项目_${id}`,
        description: `自动生成的压测项目 - ${new Date().toISOString()}`,
        status: statuses[randomIntBetween(0, 3)],
        startDate: '2026-01-01',
        endDate: '2026-12-31',
        managerId: randomIntBetween(1, 10000),
    };
}

/**
 * 生成随机流程启动请求
 */
function generateFlowStart(flowCode = 'test_flow') {
    return {
        flowCode: flowCode,
        businessKey: `LOADTEST_${Date.now()}_${__VU}_${__ITER}`,
        variables: {
            testData: true,
            source: 'k6_loadtest',
            timestamp: new Date().toISOString(),
        },
    };
}

// ============================
// 场景执行器
// ============================

/**
 * 执行登录场景（独立压测认证服务时使用）
 */
export function loginScenario() {
    group('Login', () => {
        getToken();
        sleep(randomIntBetween(1, 3));
    });
}

/**
 * 执行用户查询场景
 */
export function userQueryScenario() {
    const token = getToken();
    if (!token) return;

    group('User Queries', () => {
        requestGet('getUserInfo', '/user/me', token);
        sleep(randomIntBetween(0.5, 2));

        requestGet('getUserPermissions', '/user/permissions', token);
        sleep(randomIntBetween(1, 3));
    });
}

/**
 * 执行项目查询场景（读多写少）
 */
export function projectQueryScenario() {
    const token = getToken();
    if (!token) return;

    group('Project Queries', () => {
        requestGet('listProjects', '/project/list', token);
        sleep(randomIntBetween(1, 3));

        const projectId = randomIntBetween(1, 500000);
        requestGet('getProjectDetail', `/project/${projectId}/detail`, token);
        sleep(randomIntBetween(1, 3));
    });
}

// ============================
// 导出公共接口
// ============================
export {
    BASE_URL,
    API_VERSION,
    buildHeaders,
    requestGet,
    requestPost,
    requestDelete,
    getToken,
    forceRefreshToken,
    generateRandomUser,
    generateRandomProject,
    generateFlowStart,
    // Metrics（供报告使用）
    businessErrorRate,
    endpointLatency,
    rateLimitHits,
    loginSuccessRate,
    jwtValidationTime,
};
