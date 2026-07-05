# Windows · Windows 原生部署

> Windows 10/11 / Windows Server 2019+ 上的原生中间件安装 + NSSM 服务托管
> 适用:Windows 内网测试 / 演示环境 / Windows-only 部署
> 提示:Java 中间件需用 NSSM 注册为 Windows 服务

---

## 目录

1. [目录结构](#1-目录结构)
2. [前置](#2-前置)
3. [一键安装 8 中间件](#3-一键安装-8-中间件)
4. [中间件管理](#4-中间件管理)
5. [启动 PMIS 应用](#5-启动-pmis-应用)
6. [数据/日志目录](#6-数据日志目录)
7. [Windows 服务名](#7-windows-服务名)
8. [故障排查](#8-故障排查)
9. [相关链接](#9-相关链接)

---

## 1. 目录结构

```
windows/
├── install-pmis-infra.ps1       # 一键安装 7 中间件
├── infra-manager.ps1            # 中间件启停/状态管理
└── scripts/                     # 应用层启停脚本(.bat / .ps1)
    ├── start-all.bat            # 一键启动 7 后端 + 前端
    ├── stop-all.bat             # 一键停止
    ├── check-env.bat            # 环境检查(wraps check-env.ps1)
    ├── check-env.ps1            # PowerShell 实现
    └── import-nacos-config.bat  # Nacos 共享配置导入
```

---

## 2. 前置

| 项 | 要求 |
|---|---|
| OS | Windows 10/11 / Windows Server 2019+ (x64) |
| 权限 | **管理员** PowerShell |
| 软件 | NSSM(Windows Service Wrapper) — [nssm.cc](https://nssm.cc) |
| PowerShell | 5.1+(Win 10 自带) |
| 内存 | 建议 ≥ 8GB |
| 磁盘 | `C:\` ≥ 20GB |

---

## 3. 一键安装 8 中间件

**管理员 PowerShell**:

```powershell
# 设置执行策略(首次)
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy Bypass -Force

# 一键安装(默认安装到 C:\pmis,数据 C:\pmis-data,日志 C:\pmis-logs)
.\deploy\windows\install-pmis-infra.ps1
```

脚本会自动完成:

1. 检测 NSSM / JDK 21 / PostgreSQL / Redis
2. 安装 Nacos / XXL-Job / Seata / MinIO / RocketMQ / Elasticsearch
3. 复制 [`../common/conf/`](../common/README.md) 模板,做 Windows 路径占位符替换
4. 用 NSSM 注册为 Windows 服务
5. 启动并验证

**预计耗时**:20-40 分钟

可选参数:

```powershell
.\deploy\windows\install-pmis-infra.ps1 -NoStart                          # 只安装不启动
.\deploy\windows\install-pmis-infra.ps1 -Skip "es,minio"                  # 跳过指定中间件
.\deploy\windows\install-pmis-infra.ps1 -InstallHome D:\pmis              # 自定义安装目录
.\deploy\windows\install-pmis-infra.ps1 -DataHome D:\pmis-data -LogHome D:\pmis-logs
.\deploy\windows\install-pmis-infra.ps1 -Uninstall                        # 卸载
```

---

## 4. 中间件管理

```powershell
# 查看所有中间件状态
.\deploy\windows\infra-manager.ps1 status

# 启动 / 停止 / 重启 单个
.\deploy\windows\infra-manager.ps1 start postgres
.\deploy\windows\infra-manager.ps1 stop redis
.\deploy\windows\infra-manager.ps1 restart nacos

# 启停全部
.\deploy\windows\infra-manager.ps1 start-all
.\deploy\windows\infra-manager.ps1 stop-all
```

支持的 7 个短名:`postgres` / `redis` / `nacos` / `minio` / `seata` / `rocketmq` / `xxl-job`,以及 `all`。

---

## 5. 启动 PMIS 应用

中间件就绪后:

```powershell
# 1. 导入 Nacos 共享配置
.\deploy\windows\scripts\import-nacos-config.bat pmis dev

# 2. 一键启动 7 个后端 + 前端
.\deploy\windows\scripts\start-all.bat

# 3. 仅启动后端
.\deploy\windows\scripts\start-all.bat backend

# 4. 仅启动前端
.\deploy\windows\scripts\start-all.bat frontend

# 5. 停止
.\deploy\windows\scripts\stop-all.bat
.\deploy\windows\scripts\stop-all.bat --with-infra
```

> 启动日志位于 `%ROOT%\.run-logs\`,每个服务一个 `.log` + `.pid` 文件。

---

## 6. 数据/日志目录(默认)

| 用途 | 路径 |
|---|---|
| PMIS 数据 | `C:\pmis\data\` |
| PMIS 日志 | `C:\pmis\logs\` |
| 启动脚本输出 | `%ROOT%\.run-logs\{service}.log` |
| PostgreSQL data | `C:\Program Files\PostgreSQL\18\data\` |
| Redis data | `C:\pmis\data\redis\` |
| Nacos data | `C:\pmis\nacos\data\` |
| MinIO data | `C:\pmis\data\minio\` |
| RocketMQ data | `C:\pmis\data\rocketmq\` |
| XXL-Job 日志 | `C:\pmis\logs\xxl-job.log` |

`install-pmis-infra.ps1` 启动时会询问 `DataHome` / `LogHome`,可自定义。

---

## 7. Windows 服务名

`install-pmis-infra.ps1` 会注册 9 个服务(8 中间件 + rocketmq 拆为 2 个):

| 短名 | 服务名 | 显示名 |
|---|---|---|
| postgres | `postgresql-x64-18` | PostgreSQL Server 18 |
| redis | `Redis` | Redis |
| nacos | `nacos` | Nacos |
| minio | `minio` | MinIO |
| seata | `seata` | Seata |
| rocketmq | `rocketmq-namesrv` / `rocketmq-broker` | RocketMQ NameServer / Broker |
| xxl-job | `xxl-job` | XXL-Job Admin |
| elasticsearch | `elasticsearch` | Elasticsearch |

可用 PowerShell 直接管理:

```powershell
Get-Service pmis-*                        # 列出所有 pmis 服务
Start-Service nacos                       # 启动
Stop-Service redis                        # 停止
Restart-Service elasticsearch             # 重启
Set-Service -Name nacos -StartupType Automatic   # 设为自动启动
```

---

## 8. 故障排查

| 现象 | 排查命令 |
|---|---|
| 服务起不来 | `Get-Service pmis-* \| Format-Table Name, Status` |
| 启动失败 | `Get-EventLog -LogName Application -Source "Nacos" -Newest 30` |
| 端口未监听 | `Test-NetConnection 127.0.0.1 -Port 8848` |
| PG 连不上 | `& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -c "SELECT version();"` |
| Nacos 502 | `Get-Content C:\pmis\logs\nacos.log -Tail 50` |
| 磁盘满 | `Get-PSDrive C \| Format-Table` |

详细排查见 [`../README.md §8`](../README.md#8-占位符约定commonconf) 占位符约定 + [`../README.md §4`](../README.md#4-8-大中间件)。

---

## 9. 相关链接

- [deploy/ 总入口](../README.md)
- [common/](../common/README.md) · 共享配置(本目录脚本会从这里读)
- [docker/](../docker/README.md) · 容器化(替代方案,推荐用于本地开发,11 容器)
- [k8s/](../k8s/README.md) · K8S 部署(生产推荐)
- [ubuntu/](../ubuntu/README.md) · Linux 等价方案
- 8 中间件详细步骤见 [`../README.md §4`](../README.md#4-8-大中间件)
