#!/bin/bash
# =====================================================================
#  PMIS Redis 集群自动化部署 + 验证脚本（批次 19）
# ---------------------------------------------------------------------
#  功能：
#  1. 启动 6 节点 Redis 集群（3 主 + 3 从）
#  2. 自动初始化集群拓扑
#  3. 验证集群健康度
#  4. 验证数据读写（slot 分布）
#  5. 输出集群诊断报告
# =====================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

REDIS_PASSWORD="${REDIS_PASSWORD:-pmisRedis2026}"
REDIS_PORT_BASE=6379
REDIS_NODES=("redis-1" "redis-2" "redis-3" "redis-4" "redis-5" "redis-6")

echo "=========================================="
echo "  PMIS Redis Cluster Deployment"
echo "  Password: ${REDIS_PASSWORD:0:3}***"
echo "=========================================="

# 1. 创建外部网络（如果不存在）
echo "[1/5] 创建 Docker 网络 pmis-net"
docker network create --driver overlay pmis-net 2>/dev/null || \
  echo "  - Network pmis-net 已存在"

# 2. 启动 Redis 6 节点
echo "[2/5] 启动 6 节点 Redis"
docker compose -f redis-cluster.yml up -d redis-1 redis-2 redis-3 redis-4 redis-5 redis-6

# 3. 等待节点就绪
echo "[3/5] 等待节点就绪（最多 60s）"
for i in $(seq 1 30); do
    ready=0
    for node in "${REDIS_NODES[@]}"; do
        if docker exec "pmis-${node}" redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null | grep -q PONG; then
            ready=$((ready+1))
        fi
    done
    echo "  - 就绪节点: $ready/6"
    if [ "$ready" -eq 6 ]; then
        break
    fi
    sleep 2
done

if [ "$ready" -lt 6 ]; then
    echo "❌ 节点未全部就绪，请检查日志"
    docker compose -f redis-cluster.yml logs --tail=50
    exit 1
fi

# 4. 初始化集群
echo "[4/5] 初始化集群（3 主 + 3 从）"
docker compose -f redis-cluster.yml run --rm redis-cluster-init

# 5. 集群健康度验证
echo "[5/5] 集群健康度验证"
echo "--- cluster info ---"
docker exec pmis-redis-1 redis-cli -a "$REDIS_PASSWORD" cluster info

echo ""
echo "--- cluster nodes ---"
docker exec pmis-redis-1 redis-cli -a "$REDIS_PASSWORD" cluster nodes

# 6. 验证数据读写
echo ""
echo "--- 验证 SET/GET (slot 路由) ---"
docker exec pmis-redis-1 redis-cli -a "$REDIS_PASSWORD" -c SET test:cluster "PMIS-Redis-OK"
sleep 1
result=$(docker exec pmis-redis-1 redis-cli -a "$REDIS_PASSWORD" -c GET test:cluster)
echo "  GET test:cluster = $result"
if [ "$result" = "PMIS-Redis-OK" ]; then
    echo "  ✅ SET/GET 通过"
else
    echo "  ❌ SET/GET 失败"
    exit 1
fi

# 7. 验证 slot 分布
echo ""
echo "--- slot 分布（应 5461/5461/5461）---"
for node in "${REDIS_NODES[@]}"; do
    role=$(docker exec "pmis-${node}" redis-cli -a "$REDIS_PASSWORD" role 2>/dev/null | head -1)
    slots=$(docker exec "pmis-redis-1" redis-cli -a "$REDIS_PASSWORD" cluster nodes | \
            grep "pmis-${node}" | awk '{print $3}')
    echo "  $node: role=$role cluster-node-info=$slots"
done

# 8. 启动 exporter 与 UI
echo ""
echo "--- 启动 Redis Exporter + RedisInsight ---"
docker compose -f redis-cluster.yml up -d redis-exporter redisinsight

echo ""
echo "=========================================="
echo "  ✅ Redis 集群部署完成"
echo ""
echo "  连接信息:"
echo "  - 单节点:    redis-cli -h localhost -p 6379 -a \$REDIS_PASSWORD"
echo "  - 集群入口:  redis-cli -h localhost -p 6379 -c -a \$REDIS_PASSWORD"
echo "  - Exporter:  http://localhost:9121/metrics"
echo "  - Insight:   http://localhost:8001 (Web UI)"
echo "  - 客户端配置: spring.redis.cluster.nodes=redis-1:6379,redis-2:6379,..."
echo "=========================================="
