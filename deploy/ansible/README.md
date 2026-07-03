# Ansible 部署目录（批次 19 补全）

PMIS 生产环境的自动化运维脚本，目录结构：

```
deploy/ansible/
├── inventory.yml    # 主机清单（3 应用节点 + PG/Redis/Infra）
├── playbook-os.yml  # OS 调优 + Docker + node_exporter 部署
├── README.md        # 本文件
```

## 拓扑

- **app_gateway** (node1, 10.0.1.11) — gateway / auth / user
- **app_core** (node2, 10.0.1.12) — project / execution / cronjob / workflow
- **app_ai** (node3, 10.0.1.13) — agent / message / notification
- **pg** (10.0.2.11/12) — PostgreSQL 16 主从
- **redis** (10.0.3.11/12/13) — Redis 7 集群三主三从
- **infra** (10.0.4.x) — Nacos / Seata / Sentry / Nginx

## 快速使用

```bash
cd deploy/ansible

# 1. 校验清单
ansible all -i inventory.yml --list-hosts

# 2. OS 调优 + Docker 安装
ansible-playbook -i inventory.yml playbook-os.yml --limit app_gateway,app_core,app_ai

# 3. 仅在指定分组执行
ansible-playbook -i inventory.yml playbook-os.yml --limit app_core

# 4. 检查 dry-run
ansible-playbook -i inventory.yml playbook-os.yml --check --diff --limit app_ai
```

## 调优项

| 项 | 调优值 | 说明 |
|----|--------|------|
| `net.core.somaxconn` | 65535 | 高并发连接队列 |
| `vm.swappiness` | 10 | 容器场景少用 swap |
| `fs.file-max` | 2097152 | 文件句柄上限 |
| `kernel.pid_max` | 4194304 | 进程数上限 |
| `transparent_hugepage` | never | 关闭 THP |
| `nofile`/`nproc` ulimit | 65535 | systemd 进程限制 |
| 时区 | Asia/Shanghai | 业务统一时间 |
| locale | zh_CN.UTF-8 | 中文数据兼容 |

## 前置条件

1. 控制机安装 Ansible 9+：`pip install ansible`
2. SSH 密钥已分发到所有目标主机
3. 目标主机为 Ubuntu 22.04 LTS（其他发行版需调整 apt/yum）
4. sudo 权限或 root 登录可用（playbook 全部 become: yes）

## 下一步

- `playbook-pg.yml` — PostgreSQL 主从初始化（批次 19 后续）
- `playbook-redis.yml` — Redis 集群部署
- `playbook-pmis.yml` — PMIS 14 个微服务部署
