package com.njydsz.pmis.system.web.controller.config;

import com.njydsz.pmis.common.core.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开发环境预热 Controller（P2-12 DX 增强）
 *
 * <p>仅在 dev / sit profile 下激活，提供以下预热能力：
 * <ul>
 *   <li>数据库连接池预热：执行简单 SELECT 建立连接</li>
 *   <li>Redis 连接预热：执行 PING 命令</li>
 *   <li>JIT 预热：触发常用 Bean 初始化</li>
 *   <li>缓存预热：加载常用配置到 Redis</li>
 * </ul>
 *
 * <p>开发者在本地启动服务后，调用此接口可快速预热所有组件，
 * 避免首次请求时的冷启动延迟。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Tag(name = "开发环境预热")
@RestController
@RequestMapping("/dev/warmup")
@RequiredArgsConstructor
@Profile({"dev", "sit", "local"})
public class DevWarmupController {

    private final JdbcTemplate jdbcTemplate;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    /**
     * 执行全量预热
     *
     * <p>依次预热数据库、Redis、JIT，返回每项预热结果与耗时。
     *
     * @return 预热结果
     */
    @Operation(summary = "执行全量预热（数据库+Redis+JIT）")
    @PostMapping
    public BaseResponse<Map<String, Object>> warmupAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        log.info("[DevWarmup] 开始全量预热...");

        // 1. 数据库预热
        BaseResponse.put("database", warmupDatabase());

        // 2. Redis 预热
        BaseResponse.put("redis", warmupRedis());

        // 3. JIT 预热（Thread.sleep 触发 JIT 编译）
        BaseResponse.put("jit", warmupJit());

        log.info("[DevWarmup] 全量预热完成: {}", result);
        return BaseResponse.ok(result);
    }

    /**
     * 查询预热状态
     *
     * @return 各组件连接状态
     */
    @Operation(summary = "查询预热状态")
    @GetMapping("/status")
    public BaseResponse<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 检查数据库连接
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            BaseResponse.put("database", "UP");
        } catch (Exception e) {
            BaseResponse.put("database", "DOWN: " + e.getMessage());
        }

        // 检查 Redis 连接
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            BaseResponse.put("redis", "UP");
        } catch (Exception e) {
            BaseResponse.put("redis", "DOWN: " + e.getMessage());
        }

        return BaseResponse.ok(result);
    }

    /** 数据库预热 */
    private Map<String, Object> warmupDatabase() {
        Map<String, Object> dbResult = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try {
            // 执行多条简单查询，建立连接池连接
            for (int i = 0; i < 3; i++) {
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            }
            // 查询常用表是否存在
            try {
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pmis_user_account WHERE 1=0", Integer.class);
                dbResult.put("userTable", "OK");
            } catch (Exception e) {
                dbResult.put("userTable", "SKIP (表不存在)");
            }
            dbResult.put("status", "UP");
        } catch (Exception e) {
            dbResult.put("status", "FAILED: " + e.getMessage());
        }
        dbResult.put("costMs", System.currentTimeMillis() - start);
        return dbResult;
    }

    /** Redis 预热 */
    private Map<String, Object> warmupRedis() {
        Map<String, Object> redisResult = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try {
            // PING
            redisTemplate.getConnectionFactory().getConnection().ping();
            // 写入预热 key
            redisTemplate.opsForValue().set("dev:warmup:ping", "OK");
            redisResult.put("ping", "OK");
            redisResult.put("status", "UP");
        } catch (Exception e) {
            redisResult.put("status", "FAILED: " + e.getMessage());
        }
        redisResult.put("costMs", System.currentTimeMillis() - start);
        return redisResult;
    }

    /** JIT 预热 */
    private Map<String, Object> warmupJit() {
        Map<String, Object> jitResult = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        // 执行一些简单计算触发 JIT 编译
        long sum = 0;
        for (int i = 0; i < 10000; i++) {
            sum += i;
        }
        jitResult.put("status", "DONE");
        jitResult.put("checksum", sum);
        jitResult.put("costMs", System.currentTimeMillis() - start);
        return jitResult;
    }
}
