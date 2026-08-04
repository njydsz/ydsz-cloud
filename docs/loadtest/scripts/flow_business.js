/**
 * 流程引擎压测脚本
 *
 * 场景：模拟流程启动、任务查询等读多写少场景
 * 目标：启动 ≥ 500 QPS（写），任务查询 ≥ 2000 QPS（读）
 *
 * 运行：
 *   k6 run --vus 200 --duration 20m --env BASE_URL=http://ydsz-gateway.local flow_business.js
 *
 * @since 1.0.0
 * @author ydsz-team
 */

import { sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import {
    getToken,
    requestGet,
    requestPost,
    generateFlowStart,
} from './common.js';

// 自定义 Metrics
const flowStartLatency = new Trend('flow_start_latency', true);
const taskQueryLatency = new Trend('task_query_latency', true);
const flowStartSuccessRate = new Rate('flow_start_success');
const mqDeliveryTime = new Trend('mq_delivery_time', true);

export const options = {
    scenarios: {
        // 流程启动压力（写操作，较低 QPS）
        flow_starters: {
            executor: 'constant-arrival-rate',
            rate: 50,           // 每秒 50 个新实例
            timeUnit: '1s',
            duration: '20m',
            preAllocatedVUs: 100,
            maxVUs: 200,
            exec: 'flowStart',
        },
        // 任务查询压力（读操作，高 QPS）
        task_queriers: {
            executor: 'constant-arrival-rate',
            rate: 200,          // 每秒 200 次查询
            timeUnit: '1s',
            duration: '20m',
            preAllocatedVUs: 300,
            maxVUs: 500,
            exec: 'taskQuery',
            startTime: '30s',
        },
    },
    thresholds: {
        'flow_start_latency': ['p(95) < 500', 'p(99) < 1000'],
        'task_query_latency': ['p(95) < 200', 'p(99) < 500'],
        'flow_start_success': ['rate > 0.99'],
        'http_req_failed': ['rate < 0.005'],
    },
};

/**
 * 流程启动场景（写操作）
 */
export function flowStart() {
    const token = getToken();
    if (!token) return;

    group('Flow Start', () => {
        const payload = generateFlowStart('load_test_flow');
        const startTime = Date.now();

        const res = requestPost(
            'flowStart',
            '/flow/start',
            payload,
            token,
            200
        );

        const duration = Date.now() - startTime;
        flowStartLatency.add(duration);

        const success = res.status === 200 && res.json('code') === 0;
        flowStartSuccessRate.add(success);

        if (success) {
            // 记录流程实例 ID，供后续查询使用
            const flowId = res.json('data.flowId');
            if (flowId) {
                // 存入 VU 级别的临时变量
                flowStart.add(1, { flowId: flowId });
            }
        }
    });

    sleep(Math.random() * 2 + 0.5);
}

/**
 * 任务查询场景（读操作）
 */
export function taskQuery() {
    const token = getToken();
    if (!token) return;

    group('Task Query', () => {
        // 待办列表查询
        const startTime = Date.now();
        requestGet(
            'pendingTasks',
            '/flow/task/pending?page=1&size=50',
            token
        );
        taskQueryLatency.add(Date.now() - startTime);

        sleep(Math.random() * 1 + 0.2);

        // 已办列表查询
        requestGet(
            'completedTasks',
            '/flow/task/completed?page=1&size=50',
            token
        );

        sleep(Math.random() * 1.5 + 0.5);
    });
}

// 流程启动达成统计
const flowStart = new Counter('flow_instances_started');

/**
 * setup：检查流程引擎健康状态
 */
export function setup() {
    const token = getToken();
    if (!token) {
        throw new Error('无法获取 Token，流程压测中止');
    }
    console.log('✓ 流程引擎压测准备完成');
    return { token };
}
