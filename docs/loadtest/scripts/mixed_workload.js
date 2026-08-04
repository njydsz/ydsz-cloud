/**
 * 混合流量压测脚本
 *
 * 场景：按真实业务比例模拟多接口混合流量
 * 流量比例（参考生产日志统计）：
 *   - 项目查询：40%
 *   - 用户信息查询：25%
 *   - 流程任务查询：20%
 *   - 流程启动：5%
 *   - 消息发送：5%
 *   - 登录：5%
 *
 * 目标：整体 P99 ≤ 300ms，错误率 < 0.1%
 *
 * 运行：
 *   k6 run --vus 500 --duration 60m --env BASE_URL=http://ydsz-gateway.local mixed_workload.js
 *
 * @since 1.0.0
 * @author ydsz-team
 */

import { sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import {
    getToken,
    requestGet,
    requestPost,
    generateFlowStart,
    generateRandomProject,
} from './common.js';

// 自定义 Metrics（按业务域分组）
const projectLatency = new Trend('mixed_project_latency', true);
const userLatency = new Trend('mixed_user_latency', true);
const flowLatency = new Trend('mixed_flow_latency', true);
const msgLatency = new Trend('mixed_msg_latency', true);
const overallErrorRate = new Rate('mixed_errors');

export const options = {
    scenarios: {
        // 恒定混合流量（最贴近真实生产）
        constant_mixed: {
            executor: 'constant-vus',
            vus: 500,
            duration: '60m',
        },
        // 高峰流量脉冲（模拟上午 10 点高峰）
        peak_pulse: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5m', target: 300 },
                { duration: '10m', target: 800 },
                { duration: '5m', target: 300 },
                { duration: '5m', target: 0 },
            ],
            startTime: '15m',
        },
    },
    thresholds: {
        'mixed_project_latency': ['p(95) < 150'],
        'mixed_user_latency': ['p(95) < 50'],
        'mixed_flow_latency': ['p(95) < 200'],
        'mixed_msg_latency': ['p(95) < 100'],
        'mixed_errors': ['rate < 0.001'],
        'http_req_failed': ['rate < 0.005'],
    },
};

/**
 * 主函数：按概率分布执行不同业务场景
 */
export default function () {
    const token = getToken();
    if (!token) {
        sleep(1);
        return;
    }

    // 按概率分布选择场景
    const rand = Math.random();

    if (rand < 0.40) {
        // 40% - 项目查询
        executeProjectQuery(token);
    } else if (rand < 0.65) {
        // 25% - 用户信息查询
        executeUserQuery(token);
    } else if (rand < 0.85) {
        // 20% - 流程任务查询
        executeFlowTaskQuery(token);
    } else if (rand < 0.90) {
        // 5% - 流程启动
        executeFlowStart(token);
    } else if (rand < 0.95) {
        // 5% - 消息发送
        executeMsgSend(token);
    } else {
        // 5% - 强制重新登录（模拟 Token 刷新）
        executeReLogin();
    }
}

/**
 * 项目查询场景
 */
function executeProjectQuery(token) {
    group('Project Query', () => {
        const start = Date.now();
        const res = requestGet('projectList', '/project/list?page=1&size=20', token);
        projectLatency.add(Date.now() - start);

        if (res.status !== 200) {
            overallErrorRate.add(true);
        }

        sleep(Math.random() * 2 + 0.5);
    });
}

/**
 * 用户信息查询场景
 */
function executeUserQuery(token) {
    group('User Query', () => {
        const start = Date.now();
        const res = requestGet('userInfo', '/user/me', token);
        userLatency.add(Date.now() - start);

        if (res.status !== 200) {
            overallErrorRate.add(true);
        }

        sleep(Math.random() * 1.5 + 0.3);
    });
}

/**
 * 流程任务查询场景
 */
function executeFlowTaskQuery(token) {
    group('Flow Task Query', () => {
        const start = Date.now();
        const res = requestGet('taskList', '/flow/task/pending?page=1&size=50', token);
        flowLatency.add(Date.now() - start);

        if (res.status !== 200) {
            overallErrorRate.add(true);
        }

        sleep(Math.random() * 2 + 0.5);
    });
}

/**
 * 流程启动场景
 */
function executeFlowStart(token) {
    group('Flow Start', () => {
        const payload = generateFlowStart('load_test_flow');
        const res = requestPost('flowStart', '/flow/start', payload, token);

        if (res.status !== 200 || res.json('code') !== 0) {
            overallErrorRate.add(true);
        }

        sleep(Math.random() * 3 + 1);
    });
}

/**
 * 消息发送场景
 */
function executeMsgSend(token) {
    group('Message Send', () => {
        const start = Date.now();
        const payload = {
            receiverId: Math.floor(Math.random() * 100000) + 1,
            title: '压测消息',
            content: `自动压测消息 - ${new Date().toISOString()}`,
            msgType: 'SYSTEM',
        };
        const res = requestPost('msgSend', '/msg/send', payload, token);
        msgLatency.add(Date.now() - start);

        if (res.status !== 200) {
            overallErrorRate.add(true);
        }

        sleep(Math.random() * 2 + 0.5);
    });
}

/**
 * 重新登录场景（模拟 Token 过期）
 */
function executeReLogin() {
    group('Re-Login', () => {
        // 强制刷新 Token
        const newToken = forceRefreshToken();
        if (!newToken) {
            overallErrorRate.add(true);
        }
        sleep(0.5);
    });
}

/**
 * teardown：输出压测汇总
 */
export function teardown() {
    console.log('\n======== 混合流量压测完成 ========');
    console.log('请查看输出报告，关注以下指标：');
    console.log('  - 整体错误率 < 0.1%');
    console.log('  - 各场景 P95 延迟满足 SLA');
    console.log('  - 网关限流命中次数是否正常');
    console.log('==================================');
}
