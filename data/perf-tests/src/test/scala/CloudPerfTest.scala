package com.njydsz.perf

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * 云顶云平台 Gatling 性能测试框架。
 *
 * 目标：
 *   1. LightLoadSimulation -- 轻载基线验证，确认服务在正常负载下 P99 < 500ms
 *   2. StressLoadSimulation -- 压力测试，逐步加压找到吞吐量拐点
 *
 * 目标端点（仅使用已确认存在的公共端点）：
 *   - GET  /actuator/health      网关层健康检查（无需认证）
 *   - GET  /actuator/info        网关层应用信息（无需认证）
 */
class CloudPerfTest extends Simulation {

    // ============================================================
    // HTTP 协议配置
    // ============================================================

    // 基础 URL：可通过 -DbaseUrl=... 覆盖，默认指向本地网关
    val baseUrl: String = System.getProperty("baseUrl", "http://localhost:9000")

    // HTTP 协议：公共头、连接池、超时等
    val httpProtocol = http
        .baseUrl(baseUrl)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling-perf-test/1.0")
        .connectionHeader("keep-alive")
        .disableCaching

    // ============================================================
    // 场景定义
    // ============================================================

    /**
     * 轻载基线场景：50 并发持续 1 分钟。
     * 验证 P99 响应时间 < 500ms。
     */
    val lightLoadScenario = scenario("LightLoad")
        .during(1.minute) {
            exec(
                http("actuator-health")
                    .get("/actuator/health")
                    .check(status.is(200))
                    .check(responseTimeInMillis.lte(500))
            ).pause(50.millis)
        }

    /**
     * 压力测试场景：阶梯加压到 200 并发，持续 3 分钟。
     * 观察吞吐量拐点。
     */
    val stressLoadScenario = scenario("StressLoad")
        .during(3.minutes) {
            exec(
                http("actuator-health-stress")
                    .get("/actuator/health")
                    .check(status.in(200, 503))
                    .check(responseTimeInMillis.lte(2000))
            ).pause(10.millis, 50.millis)
        }

    // ============================================================
    // 注入策略与全局断言
    // ============================================================
    setUp(
        // 轻载：50 并发 ramp up 5s，保持 1min
        lightLoadScenario.inject(
            rampUsers(50).during(5.seconds)
        ).protocols(httpProtocol),

        // 压力阶梯：0 -> 50 -> 100 -> 150 -> 200，每级 30s，阶梯间隔 10s
        stressLoadScenario.inject(
            incrementUsersPerSec(50)
                .times(4)
                .eachLevelLasting(30.seconds)
                .separatedByRampsLasting(10.seconds)
                .startingFrom(0)
        ).protocols(httpProtocol)
    )
    .assertions(
        // 全局 P95 < 500ms
        global.responseTime.percentile(95).lte(500),
        // 错误率 < 1%
        global.failedRequests.percent.lt(1.0),
        // 平均 QPS > 100
        global.requestsPerSec.gt(100.0)
    )
}
