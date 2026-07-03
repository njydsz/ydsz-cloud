# 分布式执行

P2-16 引入的分布式分片执行能力，基于一致性 Hash 将规则分片到集群节点，每个节点只执行属于自己的规则，避免重复计算。

## 启用配置

```yaml
pmis:
  literule:
    distributed:
      enabled: true                  # 启用分布式分片（默认 false）
      virtual-nodes: 150             # 虚拟节点数（越大越均匀）
      refresh-interval-ms: 10000     # 节点列表刷新间隔
      heartbeat-timeout-ms: 30000    # 心跳超时
      heartbeat-interval-ms: 5000    # 心跳发送间隔
```

## 架构

```
ShardAwareRuleEngine（装饰器，实现 RuleEngine 接口）
  ├── NodeRegistry           # 节点注册表（注册/注销/心跳/存活检测）
  │     └── InMemoryNodeRegistry   # 内存实现（ConcurrentHashMap）
  ├── ConsistentHashSharder  # 一致性 Hash 分片器
  │     ├── MD5 hash 取前 8 字节转 long
  │     ├── TreeMap<Long, ClusterNode> 环结构
  │     ├── 虚拟节点（默认 150，支持权重）
  │     └── 节点签名变更检测（避免重复重建环）
  └── 定时任务
        ├── 心跳发送（5 秒间隔）
        └── 节点刷新 + 死节点清理（10 秒间隔）
```

## 工作原理

### 1. 节点注册

节点启动时自动注册到 `NodeRegistry`，节点 ID 为 `hostname:pid`。每个节点定时发送心跳（默认 5 秒），超过 30 秒未心跳的节点视为下线并被清理。

### 2. 一致性 Hash 分片

- 使用 MD5 hash 取前 8 字节转 long 作为 hash 值
- TreeMap 维护 `hash -> node` 的环结构
- 每个物理节点默认 150 个虚拟节点（支持权重扩展）
- `shard(key)` 使用 `ceilingEntry` 查找，环回绕到 `firstEntry`
- 节点列表变更时通过签名检测触发环重建

### 3. 分片执行

`ShardAwareRuleEngine` 装饰 `RuleEngine`：

1. `evaluate()` / `dryRun()` 调用前，先按规则编码分片过滤
2. 只执行属于当前节点的规则（`isMine(ruleCode, selfNodeId)`）
3. 按 `severity.weight` 降序执行子集规则

### 4. 自动降级

::: tip 集群 ≤1 自动降级
当集群规模 ≤1 时（单节点或未启用分布式），`ShardAwareRuleEngine` 自动关闭分片，全部规则本地执行，保持向后兼容。
:::

## 均匀性验证

1000 个 key 在 3 节点上的分布偏差 ≤15%（280~390 范围），节点下线迁移量 ≤40%。

## 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enabled` | false | 是否启用分布式分片执行 |
| `virtual-nodes` | 150 | 虚拟节点数，越大越均匀 |
| `refresh-interval-ms` | 10000 | 节点列表刷新间隔（毫秒） |
| `heartbeat-timeout-ms` | 30000 | 心跳超时时间（毫秒） |
| `heartbeat-interval-ms` | 5000 | 心跳发送间隔（毫秒） |

## 自动装配

`DistributedAutoConfiguration` 通过 `@ConditionalOnProperty(prefix = "pmis.literule.distributed", name = "enabled", havingValue = "true")` 条件装配：

- `nodeId`：当前节点 ID（hostname:pid）
- `NodeRegistry`：节点注册表
- `ConsistentHashSharder`：一致性 Hash 分片器
- `ShardAwareRuleEngine`：分片感知规则引擎装饰器
- 定时心跳 + 节点刷新任务（5 秒初始延迟，10 秒间隔）

::: warning 注意
分布式执行为内部机制，不暴露 REST 端点。启用后对所有规则评估请求透明生效。
:::
