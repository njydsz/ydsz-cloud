/**
 * 项目查询压测脚本
 *
 * 场景：模拟高频项目查询（读多写少），验证缓存命中、JWT 校验、DB 查询性能
 * 目标：单实例 ≥ 3000 QPS，P95 ≤ 150ms
 *
 * 运行：
 *   k6 run --vus 300 --duration 15m --env BASE_URL=http://ydsz-gateway.local project_query.js
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
    generateRandomProject,
} from './common.js';

// 自定义 Metrics
const queryLatency = new Trend('project_query_latency', true);
const cacheHitRate = new Rate('cache_hit');

export const options = {
    scenarios: {
        // 恒定负载：300 VU 持续 15 分钟
        constant_load: {
            executor: 'constant-vus',
            vus: 300,
            duration: '15m',
            startTime: '0s',
        },
        // 爬坡负载：模拟流量爬升
        ramp_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '3m', target: 100 },
                { duration: '6m', target: 500 },
                { duration: '4m', target: 200 },
                { duration: '2m', target: 0 },
            ],
            startTime: '2m',
        },
    },
    thresholds: {
        'project_query_latency': ['p(95) < 150', 'p(99) < 300'],
        'http_req_failed': ['rate < 0.005'],
        'cache_hit': ['rate > 0.80'],  // 缓存命中率 > 80%
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://ydsz-gateway.local:9000';

/**
 * 主函数：执行项目查询场景（读多写少比例 9:1）
 */
export default function () {
    const token = getToken();
    if (!token) {
        sleep(1);
        return;
    }

    group('Project List Query', () => {
        // 项目列表查询（高频）
        const listRes = requestGet(
            'projectList',
            '/project/list?page=1&size=20',
            token
        );

        if (listRes.status === 200 && listRes.headers['X-Cache'] === 'HIT') {
            cacheHitRate.add(true);
        } else {
            cacheHitRate.add(false);
        }

        sleep(Math.random() * 1.5);
    });

    group('Project Detail Query', () => {
        // 项目详情查询（中频）
        const projectId = Math.floor(Math.random() * 500000) + 1;
        requestGet(
            'projectDetail',
            `/project/${projectId}/detail`,
            token
        );

        sleep(Math.random() * 2 + 0.5);
    });

    // 低频写入（10% 概率触发）
    if (Math.random() < 0.1) {
        group('Project Create', () => {
            const newProject = generateRandomProject();
            requestPost(
                'createProject',
                '/project',
                newProject,
                token,
                201
            );
        });
    }

    // 模拟真实用户思考时间
    sleep(Math.random() * 3 + 1);
}

/**
 * setup：预加载测试数据
 */
export function setup() {
    console.log('开始项目查询压测，预加载 Token...');

    // 预获取一个 Token 验证接口可用性
    const token = getToken();
    if (!token) {
        throw new Error('无法获取 Token，请检查测试用户数据是否已初始化');
    }

    console.log('✓ Token 获取成功，开始压测');
    return { token };
}
