# Redis 部署目录（批次 19 补全）

PMIS 生产环境 Redis 7 集群（3 主 + 3 从）的部署产物。

## 目录结构

```
deploy/redis/
├── redis-cluster.yml    # Docker Compose 编排（6 节点 + Exporter + Insight）
├── cluster-verify.sh    # 自动化部署 + 健康度验证脚本
└── README.md            # 本文件
```

## 集群拓扑

| 节点 | 角色 | 端口 | 集群总线 | 数据卷 |
|------|------|------|----------|--------|
| redis-1 | master | 6379 | 16379 | redis-1-data |
| redis-2 | master | 6380 | 16380 | redis-2-data |
| redis-3 | master | 6381 | 16381 | redis-3-data |
| redis-4 | slave  | 6382 | 16382 | redis-4-data |
| redis-5 | slave  | 6383 | 16383 | redis-5-data |
| redis-6 | slave  | 6384 | 16384 | redis-6-data |

## 关键调优

| 参数 | 值 | 说明 |
|------|----|------|
| `maxmemory` | 6GB | 8GB 物理节点保留 25% 给 fork |
| `maxmemory-policy` | allkeys-lru | LRU 淘汰 |
| `appendonly` | yes | AOF 持久化 |
| `appendfsync` | everysec | 平衡性能与安全 |
| `io-threads` | 4 | 多线程 IO |
| `cluster-node-timeout` | 5000ms | 故障检测 5s |
| `slowlog-log-slower-than` | 10000 | 记录 > 10ms 慢命令 |

## 部署

```bash
# 1. 执行自动部署
chmod +x cluster-verify.sh
./cluster-verify.sh

# 2. 验证集群状态
docker exec pmis-redis-1 redis-cli -a $REDIS_PASSWORD cluster info
docker exec pmis-redis-1 redis-cli -a $REDIS_PASSWORD cluster nodes
```

## 客户端配置

### Spring Boot application.yml

```yaml
spring:
  redis:
    cluster:
      nodes:
        - redis-1:6379
        - redis-2:6379
        - redis-3:6379
        - redis-4:6379
        - redis-5:6379
        - redis-6:6379
      password: pmisRedis2026
      max-redirects: 3
    timeout: 2000
    lettuce:
      pool:
        max-active: 200
        max-idle: 50
        min-idle: 10
        max-wait: 1000ms
```

### PMIS Lua 脚本示例（幂等操作）

```lua
-- SET NX EX 原子操作（IdempotentAspect 使用）
if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', tonumber(ARGV[2])) then
    return 1
else
    return 0
end
```

## 监控

- **Exporter**: http://localhost:9121/metrics
- **Insight UI**: http://localhost:8001
- **关键指标**：
  - `redis_cluster_connected_slaves` ≥ 3
  - `redis_cluster_state` = ok
  - `redis_memory_used_bytes` < 6GB
  - `redis_commands_duration_seconds_p99` < 50ms

## 高可用验证

```bash
# 模拟主节点故障
docker stop pmis-redis-1
# 等待 30s，观察从库自动晋升
docker exec pmis-redis-2 redis-cli -a $REDIS_PASSWORD cluster nodes

# 恢复主节点（自动加入集群）
docker start pmis-redis-1
```

## 常见问题

1. **slot not served** — 执行 `redis-cli --cluster fix redis-1:6379`
2. **MOVED 错误** — 客户端需加 `-c`（cluster 模式）
3. **AOF 重写卡顿** — 调整 `auto-aof-rewrite-percentage=200`
