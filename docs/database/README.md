# 数据库读写分离方案

> P2-优化：PostgreSQL 主从架构 + Spring Boot AbstractRoutingDataSource

## 目录

- [架构概览](#架构概览)
- [PostgreSQL 主从配置](#postgresql-主从配置)
- [Spring Boot 读写分离](#spring-boot-读写分离)
- [延迟监控与熔断](#延迟监控与熔断)
- [Nacos 配置](#nacos-配置)
- [验证方案](#验证方案)

---

## 架构概览

```
                            ┌─────────────────────────────────────┐
                            │       ydsz-common-datasource        │
                            │     (AbstractRoutingDataSource)     │
                            └──────────────┬──────────────────────┘
                                           │
              ┌────────────────────────────┼────────────────────────────┐
              │                            │                            │
              ▼                            ▼                            ▼
    ┌─────────────────┐        ┌─────────────────┐        ┌─────────────────┐
    │    MASTER       │        │    SLAVE 1      │        │    SLAVE 2      │
    │  (写 + 强一致读) │        │   (普通读)      │        │   (报表/分析)   │
    │  ydsz-pg-master │        │  ydsz-pg-slave1 │        │  ydsz-pg-slave2 │
    └─────────────────┘        └─────────────────┘        └─────────────────┘
              │                            │                            │
              └────────────── 流复制 ───────┴────────────────────────────┘
```

### 路由策略

| 场景 | 数据源 | 说明 |
|------|--------|------|
| INSERT / UPDATE / DELETE | master | 所有写操作 |
| SELECT（事务内） | master | 事务内保证一致性 |
| SELECT（普通） | slave1 | 读操作负载均衡 |
| SELECT（报表） | slave2 | 隔离分析查询 |
| SELECT（带 Hint） | master | 强制走主库 |

---

## PostgreSQL 主从配置

### 主库配置 (`postgresql.conf`)

```ini
# 基础配置
listen_addresses = '*'
max_connections = 200
shared_buffers = 4GB
effective_cache_size = 12GB
work_mem = 16MB
maintenance_work_mem = 1GB

# WAL 配置（流复制）
wal_level = replica
max_wal_senders = 10
wal_keep_size = 1024MB
max_replication_slots = 10
hot_standby = on
synchronous_commit = remote_apply

# 同步复制（可根据需要调整为异步）
synchronous_physical_replicas = 1
```

### 主库创建复制用户

```sql
-- 创建复制专用角色
CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD 'repl_password_here';

-- 授权（pg_hba.conf）
-- host replication replicator 10.0.0.0/8 md5
```

### 从库搭建（pg_basebackup）

```bash
# 1. 停止从库
pg_ctl -D /var/lib/postgresql/data stop

# 2. 清理旧数据
rm -rf /var/lib/postgresql/data/*

# 3. 从主库拉取基础备份
pg_basebackup -h ydsz-pg-master -U replicator -D /var/lib/postgresql/data \
              -Xs -P -R --slot=replication_slot_1

# 4. 启动从库
pg_ctl -D /var/lib/postgresql/data start
```

### 验证复制状态

```sql
-- 主库查看复制客户端
SELECT * FROM pg_stat_replication;

-- 从库查看复制状态
SELECT * FROM pg_stat_wal_receiver;

-- 查看复制延迟
SELECT 
    now() - pg_last_xact_replay_timestamp() AS replication_delay,
    pg_is_wal_replay_paused() AS is_paused;
```

---

## Spring Boot 读写分离

### 1. 数据源路由（AbstractRoutingDataSource）

```java
package com.njydsz.common.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 动态数据源路由
 *
 * <p>基于 DataSourceContextHolder 中的 key 选择数据源。
 */
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.get();
    }
}
```

### 2. 数据源上下文

```java
package com.njydsz.common.datasource;

/**
 * 数据源上下文
 *
 * <p>线程级别的数据源标识持有者。
 */
public class DataSourceContextHolder {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static final String MASTER = "master";
    public static final String SLAVE = "slave";

    public static void set(String ds) {
        CONTEXT.set(ds);
    }

    public static String get() {
        return CONTEXT.get();
    }

    public static void master() {
        set(MASTER);
    }

    public static void slave() {
        set(SLAVE);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
```

### 3. 强制主库注解

```java
package com.njydsz.common.datasource.annotation;

import java.lang.annotation.*;

/**
 * 强制走主库（用于强一致性读场景）
 *
 * <p>使用示例：
 * <pre>
 * @MasterRead
 * public Order getOrderWithFreshData(Long orderId) {
 *     return orderMapper.selectById(orderId);
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MasterRead {
}
```

### 4. AOP 切面

```java
package com.njydsz.common.datasource.aop;

import com.njydsz.common.datasource.DataSourceContextHolder;
import com.njydsz.common.datasource.annotation.MasterRead;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DataSourceAspect implements Ordered {

    @Around("@annotation(masterRead)")
    public Object aroundMasterRead(ProceedingJoinPoint point, MasterRead masterRead) throws Throwable {
        try {
            DataSourceContextHolder.master();
            return point.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }

    @Override
    public int getOrder() {
        // 必须在事务之前执行
        return -1;
    }
}
```

### 5. 数据源配置类

```java
package com.njydsz.common.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Bean(name = "masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.druid.master")
    public DataSource masterDataSource() {
        return new DruidDataSource();
    }

    @Bean(name = "slaveDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.druid.slave")
    public DataSource slaveDataSource() {
        return new DruidDataSource();
    }

    @Bean(name = "routingDataSource")
    @Primary
    public DataSource routingDataSource(
            @Qualifier("masterDataSource") DataSource master,
            @Qualifier("slaveDataSource") DataSource slave) {
        DynamicRoutingDataSource routing = new DynamicRoutingDataSource();
        Map<Object, Object> target = new HashMap<>();
        target.put(DataSourceContextHolder.MASTER, master);
        target.put(DataSourceContextHolder.SLAVE, slave);
        routing.setTargetDataSources(target);
        routing.setDefaultTargetDataSource(master);
        return routing;
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource routingDataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(routingDataSource);
        return bean.getObject();
    }

    @Bean
    @Primary
    public PlatformTransactionManager txManager(DataSource routingDataSource) {
        return new DataSourceTransactionManager(routingDataSource);
    }
}
```

---

## 延迟监控与熔断

### 健康检查与熔断

```java
package com.njydsz.common.datasource;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReplicationHealthChecker {

    private final DataSource slaveDataSource;
    private final MeterRegistry meterRegistry;

    volatile boolean slaveAvailable = true;
    volatile Duration replicationDelay = Duration.ZERO;

    @PostConstruct
    public void init() {
        Gauge.builder("pg.replication.delay_seconds", replicationDelay, Duration::getSeconds)
             .description("PostgreSQL replication lag in seconds")
             .register(meterRegistry);

        Gauge.builder("pg.replication.slave_available", slaveAvailable, b -> b ? 1 : 0)
             .description("PostgreSQL slave availability")
             .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${ydsz.datasource.health-check-interval:PT10S}")
    public void checkReplicationHealth() {
        // 检查从库可达性
        try (Connection conn = slaveDataSource.getConnection()) {
            if (conn.isValid(3)) {
                slaveAvailable = true;
            }
        } catch (SQLException e) {
            log.warn("[DataSource] Slave 不可达: {}", e.getMessage());
            slaveAvailable = false;
        }

        // 检查复制延迟（超过 5s 自动切写到主库）
        if (slaveAvailable && replicationDelay.getSeconds() > 5) {
            log.warn("[DataSource] 复制延迟 {}s，临时切换到主库", replicationDelay.getSeconds());
            slaveAvailable = false;
        }
    }
}
```

---

## Nacos 配置

### ydsz-common-datasource.yaml 更新

```yaml
spring:
  datasource:
    druid:
      # 主库配置
      master:
        url: jdbc:postgresql://${PG_HOST:ydsz-pg-master}:5432/ydsz?useUnicode=true&characterEncoding=utf8
        username: ${PG_USER:ydsz}
        password: ${PG_PASSWORD:password}
        driver-class-name: org.postgresql.Driver
        # Druid 详细配置同现有
        initial-size: 10
        max-active: 30
        max-wait: 5000
        # ... 其他 Druid 配置保持不变
      
      # 从库配置
      slave:
        url: jdbc:postgresql://${PG_SLAVE_HOST:ydsz-pg-slave1}:5432/ydsz?useUnicode=true&characterEncoding=utf8&targetSessionAttrs=read-only
        username: ${PG_USER:ydsz}
        password: ${PG_PASSWORD:password}
        driver-class-name: org.postgresql.Driver
        initial-size: 10
        max-active: 50   # 读库连接数可以更大
        max-wait: 5000
        # 读库允许只读
        default-transaction-isolation: READ_COMMITTED
        connection-init-sqls: SET default_transaction_read_only = on

    # 读写分离配置
    dynamic:
      enabled: true
      health-check-interval: 10s      # 健康检查间隔
      failover-delay: 30s             # 故障转移冷却时间
      replication-delay-threshold: 5s # 复制延迟熔断阈值

```

---

## 验证方案

### 功能测试

```java
@SpringBootTest
class DataSourceRoutingTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void testRouting() {
        // 写操作：应在 master
        User user = new User();
        user.setUsername("routing_test");
        userMapper.insert(user);

        // 读操作：应在 slave
        User read = userMapper.selectById(user.getId());
        assertNotNull(read);

        // @MasterRead 应在 master
        User fresh = userMapper.selectFreshById(user.getId());
        assertNotNull(fresh);
    }
}
```

### 故障模拟

```bash
# 1. 从库宕机测试
docker stop ydsz-pg-slave1
# 预期：所有读自动切换到 master，熔断告警触发

# 2. 高延迟测试（模拟网络抖动）
tc qdisc add dev eth0 root netem delay 2000ms
# 预期：熔断器触发，读操作切到 master

# 3. 从库恢复后自动切回
docker start ydsz-pg-slave1
# 预期：健康检查通过后，读取重新路由到 slave
```

### 监控指标

| 指标 | 类型 | 告警阈值 |
|------|------|----------|
| `pg.replication.delay_seconds` | Gauge | > 5s (Warning), > 30s (Critical) |
| `pg.replication.slave_available` | Gauge | = 0 持续 1min |
| `jdbc.connections.active{role=master}` | Gauge | > 80% max |
| `jdbc.connections.active{role=slave}` | Gauge | > 80% max |

---

## 故障场景与应对

| 场景 | 影响 | 自动应对 |
|------|------|----------|
| 从库宕机 | 读操作降级到主库 | 熔断器触发，自动切主库 |
| 主库宕机 | 写入不可用 | 需人工介入（触发主从切换） |
| 复制延迟过高 | 读操作读到旧数据 | 自动切到主库，直到恢复 |
| 从库恢复 | 读操作自动切回 | 健康检查通过后恢复 |
| 全链路断连 | 部分功能可用 | Druid 熔断 + 兜底 |

---

> 文档更新: 2026-08-04 | 维护人: ydsz-team
