# PMIS 灾备演练 SOP（批次 19）

> 版本：v1.0  
> 适用范围：生产环境 PMIS 全栈  
> 演练频次：每季度一次  
> 上次演练：YYYY-MM-DD

## 1. 演练目标

1. 验证 RPO（数据丢失容忍）≤ 5 分钟
2. 验证 RTO（恢复时间）≤ 30 分钟
3. 验证 14 个微服务在主节点故障下的自动切换
4. 验证 PostgreSQL 主从切换后业务无感知
5. 验证 Redis 集群在单节点故障下服务不中断

## 2. 演练场景

### 场景 1：单应用节点故障（node2 宕机）

**预期**：
- Nginx 上游剔除 node2（health check 30s 内）
- 流量自动切到 node1/node3
- 用户无感知，部分业务（执行服务）需重试

**操作**：
```bash
# 1. 演练前
ansible app_core -i deploy/ansible/inventory.yml -m ping

# 2. 模拟故障（强制关机）
ssh node2 "sudo shutdown -h now"

# 3. 30s 后验证 Nginx
curl -i http://nginx/health
# 期望：自动剔除 node2，记录日志
tail -100 /var/log/nginx/pmis_error.log

# 4. 恢复
ssh node2 "sudo reboot"

# 5. 验证自动加入
curl -s http://node2:9005/actuator/health
```

**通过标准**：
- [ ] 30s 内 Nginx 上游剔除故障节点
- [ ] 业务请求成功率 ≥ 99.9%
- [ ] 故障节点恢复后自动加入

---

### 场景 2：PostgreSQL 主库故障

**预期**：
- pg-replica 自动晋升为新主库
- 微服务连接池重连（最长 60s）
- 业务数据零丢失（RPO = 0 with synchronous_commit=on）

**操作**：
```bash
# 1. 演练前
psql -h pg-primary -U pmis_app -c "SELECT pg_is_in_recovery();"
# 期望：f

# 2. 切换为同步复制（生产建议）
psql -h pg-primary -U postgres -c "
  ALTER SYSTEM SET synchronous_standby_names = '*';
  SELECT pg_reload_conf();
"

# 3. 模拟主库故障
ssh pg-primary "sudo systemctl stop postgresql"

# 4. 在备库上激活
ssh pg-replica
sudo -u postgres /usr/lib/postgresql/16/bin/pg_ctl promote -D /var/lib/postgresql/16/main

# 5. 修改微服务配置（使用 consul/etcd 动态配置）
# 或使用 PgBouncer 故障转移

# 6. 验证
psql -h pg-replica -U pmis_app -c "SELECT pg_is_in_recovery();"
# 期望：f（已晋升）
```

**通过标准**：
- [ ] 备库 30s 内晋升
- [ ] 微服务连接 60s 内恢复
- [ ] 写入数据 0 丢失

---

### 场景 3：Redis 集群单节点故障

**预期**：
- 故障主节点的从节点自动晋升
- 业务感知轻微延迟（slot 重定向）
- 客户端自动重试（max-redirects=3）

**操作**：
```bash
# 1. 演练前
docker exec pmis-redis-1 redis-cli -a $REDIS_PASSWORD cluster info
# 期望：cluster_known_nodes=6, cluster_size=3

# 2. 停止一个主节点
docker stop pmis-redis-1

# 3. 30s 后查看集群状态
docker exec pmis-redis-2 redis-cli -a $REDIS_PASSWORD cluster nodes
# 期望：原 redis-1 的 slot 已被原从库（redis-4）接管

# 4. 恢复
docker start pmis-redis-1
# 期望：原 redis-1 作为从库重新加入
```

**通过标准**：
- [ ] 30s 内主从切换
- [ ] 业务 SET/GET 无失败
- [ ] 客户端无感知

---

### 场景 4：完整机房断电（灾备级）

**预期**：
- 异地灾备中心（距离 > 200km）拉起备用环境
- 通过 DNS 切换（TTL ≤ 60s）
- 数据从异地灾备 PG 还原

**操作**：
```bash
# 1. 灾备中心启动（提前部署好的备用环境）
ssh dr-center "cd /opt/pmis && docker compose -f deploy/docker/docker-compose.yml up -d"

# 2. 还原数据库
psql -h dr-pg-primary -U pmis_app -d pmis -f /backup/daily/$(date +%Y%m%d)/full_backup.sql

# 3. 还原 Redis
# （重启时从 AOF/RDB 自动恢复）

# 4. DNS 切换
# 提前降低 TTL 至 60s
# 切换 A 记录到 dr-center 公网 IP

# 5. 验证
dig pmis.example.com
curl -i https://pmis.example.com/health
```

**通过标准**：
- [ ] 30 分钟内业务恢复
- [ ] 数据丢失 ≤ 5 分钟
- [ ] DNS 切换生效 ≤ 5 分钟

---

## 3. 演练记录表

| 演练日期 | 场景 | RTO 实际 | RPO 实际 | 通过 | 改进项 |
|----------|------|----------|----------|------|--------|
| YYYY-MM-DD | node2 宕机 | min | min | ☐ | |
| YYYY-MM-DD | PG 主从切换 | min | min | ☐ | |
| YYYY-MM-DD | Redis 单点 | min | min | ☐ | |
| YYYY-MM-DD | 灾备级 | min | min | ☐ | |

## 4. 演练失败应急

若演练中发现实际 RTO/RPO 超标：

1. **立即停止演练**（避免影响生产）
2. **通知相关方**：DBA / 运维 / 业务方
3. **问题根因分析**（RCA 24h 内出报告）
4. **改进措施**：
   - 优化备份/恢复脚本
   - 调整同步复制策略
   - 增强健康检查
5. **下季度复测**

## 5. 自动化验证脚本

```bash
# 一键演练（含通知）
./deploy/dr/auto-drill.sh \
  --scenario node2-down \
  --notify ops@ydsz-pmis.cn \
  --auto-recover

# 季度定时任务
# 0 2 1 */3 * /opt/pmis/deploy/dr/auto-drill.sh --scenario all --report
```
