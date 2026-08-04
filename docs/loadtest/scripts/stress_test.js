/**
 * 极限压力测试脚本（找瓶颈）
 *
 * 场景：持续加压直到系统触发限流/错误率飙升，定位性能拐点
 * 输出：确定各服务的极限容量上限
 *
 * ⚠️ 警告：此脚本会快速消耗系统资源，请在独立的压测环境运行
 *
 * 运行：
 *   k6 run --vus 1000 --duration 30m --env BASE_URL=http://ydsz-gateway.local stress_test.js
 *
 * @since 1.0.0
 * @author ydsz-team
 */

import { sleep, group } from 'k6';
import { Trend, Rate, Counter, Gauge } from 'k6/metrics';
import {
    getToken,
    requestGet,
    requestPost,
    generateRandomProject,
} from './common.js';

// 自定义 Metrics
const activeVUs = Gauge('active_vus');
const errorRate = new Rate('errors');
const rateLimitCount = new Counter('rate_limit_429');
const timeoutCount = new Counter('timeout_504');
const p99Latency = new Trend('p99_latency', true);
const throughputGauge = new Counter('total_requests');

export const options = {
    scenarios: {
        // 极速爬坡（5min 爬升至 800 VU）
        fast_ramp: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m', target: 100 },
                { duration: '1m', target: 300 },
                { duration: '1m', target: 500 },
                { duration: '1m', target: 800 },
                { duration: '1m', target: 1000 },
                { duration: '5m', target: 1000 },
                { duration: '10m', target: 500 },
                { duration: '5m', target: 0 },
            ],
            gracefulStop: '30s',
        },
        // 洪泛测试（突发流量）
        spike_test: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            preAllocatedVUs: 200,
            maxVUs: 500,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '30s', target: 500 },
                { duration: '30s', target: 1000 },
                { duration: '30s', target: 50 },
                { duration: '1m', target: 0 },
            ],
            startTime: '2m',
            exec: 'spikeScenario',
        },
    },
    thresholds: {
        // 极限测试时放宽阈值（关注错误率拐点）
        'p99_latency': ['p(99) < 2000'],
        'errors': ['rate < 0.05'],  // 极端压力下允许 5% 错误
        'http_req_failed': ['rate < 0.10'],
    },
    // 超时设置（极限测试下放宽）
    noConnectionReuse: false,
    batch: 20,
    batchPerHost: 10,
};

/**
 * 主场景：持续加压
 */
export default function () {
    activeVUs.add(__VU);

    const token = getToken();
    if (!token) {
        sleep(1);
        return;
    }

    group('Stress Query', () => {
        const endpoints = [
            { name: 'projectList', path: '/project/list?page=1&size=20' },
            { name: 'userInfo', path: '/user/me' },
            { name: 'userPermissions', path: '/user/permissions' },
            { name: 'taskList', path: '/flow/task/pending?page=1&size=50' },
        ];

        const endpoint = endpoints[Math.floor(Math.random() * endpoints.length)];
        const start = Date.now();

        const res = requestGet(endpoint.name, endpoint.path, token);
        const duration = Date.now() - start;
        p99Latency.add(duration);
        throughputGauge.add(1);

        // 统计各类错误
        if (res.status !== 200) {
            errorRate.add(true);
            if (res.status === 429) rateLimitCount.add(1);
            if (res.status === 504) timeoutCount.add(1);
        } else {
            errorRate.add(false);
        }

        // 模拟真实用户行为
        sleep(Math.random() * 0.5);
    });
}

/**
 * 洪泛测试场景（短时突发）
 */
export function spikeScenario() {
    group('Spike', () => {
        requestGet('spikeProject', '/project/list', getToken());
    });
}

/**
 * 数据输出：每个阶段结束后输出关键指标
 */
export function handleSummary(data) {
    // 生成 JSON 格式报告
    const reportDir = __ENV.REPORT_DIR || './reports';

    return {
        [`${reportDir}/stress_summary.json`]: JSON.stringify({
            test_type: 'stress_test',
            timestamp: new Date().toISOString(),
            duration: data.state.testDurationMs,
            metrics: {
                http_reqs: data.metrics.http_reqs?.values || {},
                http_req_failed: data.metrics.http_req_failed?.values || {},
                p99_latency: data.metrics.p99_latency?.values || {},
                errors: data.metrics.errors?.values || {},
                rate_limit_429: data.metrics.rate_limit_429?.values || {},
                total_requests: data.metrics.total_requests?.values || {},
            },
            thresholds: Object.entries(data.root_group.checks || {}).map(([name, check]) => ({
                name,
                passed: check.passes > check.fails,
                passes: check.passes,
                fails: check.fails,
            })),
        }, null, 2),
        stdout: textSummary(data, { indent: ' ' }),
    };
}

/**
 * 简化版文本摘要（k6 默认 handleSummary 的可读格式）
 */
function textSummary(data, options) {
    const indent = options?.indent || '';
    let summary = `\n${indent}========== 极限压测结果 ==========\n`;
    summary += `${indent}总请求数: ${data.metrics.http_reqs?.values?.count || 0}\n`;
    summary += `${indent}错误率: ${((data.metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2)}%\n`;
    summary += `${indent}P99延迟: ${data.metrics.p99_latency?.values?.p99?.toFixed(2) || 0}ms\n`;
    summary += `${indent}限流命中: ${data.metrics.rate_limit_429?.values?.count || 0}\n`;
    summary += `${indent}超时次数: ${data.metrics.timeout_504?.values?.count || 0}\n`;
    summary += `${indent}==================================\n`;
    return summary;
}
