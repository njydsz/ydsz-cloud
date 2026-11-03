package com.njydsz.perf

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * 云顶云平台 Gatling 性能测试框架。
 *
 * 场景（2026-11-03 扩展）：
 *   1. LightLoadSimulation     —— 轻载基线：50 并发，P99 < 500ms
 *   2. StressLoadSimulation    —— 阶梯加压：0→200 并发，找吞吐拐点
 *   3. AuthFlowSimulation      —— 登录链路（POST /api/auth/login）
 *   4. BusinessReadSimulation  —— 核心读路径（字典分页，带 Bearer token）
 *
 * 目标端点（均已在网关路由中确认）：
 *   - GET  /actuator/health      网关健康检查（无需认证）
 *   - POST /api/auth/login       用户登录（userinfo 9002）
 *   - GET  /api/dict/page        字典分页（system 9001，需认证）
 *
 * 运行方式：
 *   # 仅跑免认证场景
 *   mvn -P perf-test test -Dgatling.simulationClass=com.njydsz.perf.CloudPerfTest
 *
 *   # 带账号跑全量场景（登录 + 受保护读接口）
 *   mvn -P perf-test test -DbaseUrl=http://localhost:9000 \
 *       -Dperf.username=admin -Dperf.password=Admin@12345
 *
 * 说明：未提供 perf.username / perf.password 时，AuthFlow 与 BusinessRead 场景
 *       会通过空 feeder 自动跳过（0 用户注入），不影响免认证场景的基线数据。
 */
class CloudPerfTest extends Simulation {

    // ============================================================
    // HTTP 协议配置
    // ============================================================

    val baseUrl: String = System.getProperty("baseUrl", "http://localhost:9000")
    val perfUsername: String = System.getProperty("perf.username", "")
    val perfPassword: String = System.getProperty("perf.password", "")

    val httpProtocol = http
        .baseUrl(baseUrl)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Gatling-perf-test/1.1")
        .connectionHeader("keep-alive")
        .disableCaching

    // ============================================================
    // Feeder：登录凭据（未提供时为空，场景自动跳过）
    // ============================================================

    val credentialFeeder: Iterator[Map[String, String]] = {
        if (perfUsername.nonEmpty) {
          Iterator.continually(Map("username" -> perfUsername, "password" -> perfPassword))
        } else {
          Iterator.empty
        }
    }

    // ============================================================
    // 场景 1：轻载基线（健康检查）
    // ============================================================

    val lightLoadScenario = scenario("LightLoad")
        .during(1.minute) {
            exec(
                http("actuator-health")
                    .get("/actuator/health")
                    .check(status.is(200))
                    .check(responseTimeInMillis.lte(500))
            ).pause(50.millis)
        }

    // ============================================================
    // 场景 2：阶梯压力（健康检查）
    // ============================================================

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
    // 场景 3：登录链路（POST）
    // ============================================================

    val authFlowScenario = scenario("AuthFlow")
        .feed(credentialFeeder)
        .exec(
            http("auth-login")
                .post("/api/auth/login")
                .body(StringBody("""{"username":"${username}","password":"${password}"}"""))
                .check(status.in(200, 401))
                .check(jsonPath("$.code").ofType[String].optional.saveAs("loginCode"))
        )
        .pause(20.millis, 80.millis)

    // ============================================================
    // 场景 4：核心读路径（登录 → 带 token 访问受保护端点）
    // ============================================================

    val businessReadScenario = scenario("BusinessRead")
        .feed(credentialFeeder)
        .exec(
            http("login-for-read")
                .post("/api/auth/login")
                .body(StringBody("""{"username":"${username}","password":"${password}"}"""))
                .check(status.is(200))
                .check(jsonPath("$.data.accessToken").ofType[String].saveAs("accessToken"))
        )
        .pause(10.milliseconds)
        .during(1.minute) {
            exec(
                http("dict-page")
                    .get("/api/dict/page?pageNum=1&pageSize=20")
                    .header("Authorization", "Bearer ${accessToken}")
                    .check(status.in(200, 401, 403))
            ).pause(20.millis)
        }

    // ============================================================
    // 注入策略与全局断言
    // ============================================================

    setUp(
        lightLoadScenario.inject(rampUsers(50).during(5.seconds))
            .protocols(httpProtocol),

        stressLoadScenario.inject(
            incrementUsersPerSec(50)
                .times(4)
                .eachLevelLasting(30.seconds)
                .separatedByRampsLasting(10.seconds)
                .startingFrom(0)
        ).protocols(httpProtocol),

        // 登录链路：10 并发；feeder 为空时注入 0 用户，场景自动跳过
        authFlowScenario.inject(rampUsers(10).during(5.seconds))
            .protocols(httpProtocol),

        // 核心读路径：20 并发；feeder 为空时同样跳过
        businessReadScenario.inject(rampUsers(20).during(10.seconds))
            .protocols(httpProtocol)
    )
    .assertions(
        // P95 < 800ms（含受保护接口，比健康检查宽松）
        global.responseTime.percentile(95).lte(800),
        // 错误率 < 2%
        global.failedRequests.percent.lt(2.0),
        // 平均 QPS > 50
        global.requestsPerSec.gt(50.0)
    )
}
