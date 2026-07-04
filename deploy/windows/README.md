# Windows · Windows 原生部署

> Windows 10/11 / Windows Server 上的原生中间件安装 + NSSM 服务托管
> 适用:Windows 内网测试 / 演示环境
> 注意:与 Linux 原生性能相当,Java 中间件要 NSSM 注册为服务

---

## 目录结构

```
windows/
├── install-pmis-infra.ps1    # 一键安装 8 中间件
├── infra-manager.ps1         # 中间件启停/状态管理
└── scripts/                  # 应用层启停脚本(.bat / .ps1)
    ├── start-all.bat
    ├── stop-all.bat
    ├── check-env.bat
    ├── check-env.ps1
    └── import-nacos-config.bat
```

## 前置

| 工具 | 版本 |
|---|---|
| OS | Windows 10/11 / Windows Server 2019+ |
| 权限 | **管理员** PowerShell |
| 软件 | NSSM,PostgreSQL 18,Redis 7 |
| PowerShell | 5.1+(Win 10 自带) |

## 1. 一键安装 8 中间件

**管理员 PowerShell**:

```powershell
# 设置执行策略(首次)
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy Bypass -Force

# 一键安装
.\deploy\windows\install-pmis-infra.ps1
```

脚本会:

1. 检测 NSSM / JDK 21 / PostgreSQL / Redis
2. 安装 Nacos / XXL-Job / Seata / MinIO / RocketMQ / Elasticsearch
3. 复制 `common/conf/` 模板,做 Windows 路径占位符替换
4. 用 NSSM 注册为 Windows 服务
5. 启动并验证

预计耗时:20-40 分钟

## 2. 中间件管理

```powershell
# 查看所有中间件状态
.\deploy\windows\infra-manager.ps1 status

# 启动 / 停止 / 重启
.\deploy\windows\infra-manager.ps1 start postgres
.\deploy\windows\infra-manager.ps1 stop redis
.\deploy\windows\infra-manager.ps1 restart nacos

# 启停全部
.\deploy\windows\infra-manager.ps1 start-all
.\deploy\windows\infra-manager.ps1 stop-all
```

支持 8 个中间件短名:`postgres` / `redis` / `nacos` / `minio` / `seata` / `rocketmq` / `xxl-job` / `elasticsearch`

## 3. 启动 PMIS 应用

中间件就绪后:

```powershell
# 1. 导入 Nacos 共享配置
.\deploy\windows\scripts\import-nacos-config.bat pmis dev

# 2. 一键启动 7 个后端 + 前端
.\deploy\windows\scripts\start-all.bat

# 3. 仅启动后端
.\deploy\windows\scripts\start-all.bat backend

# 4. 停止
.\deploy\windows\scripts\stop-all.bat
.\deploy\windows\scripts\stop-all.bat --with-infra
```

## 4. 数据/日志目录(默认)

| 用途 | 路径 |
|---|---|
| 数据 | `C:\pmis\data\` |
| 日志 | `C:\pmis\logs\` |
| 公共配置 | `C:\pmis\conf\` |
| 启动日志 | `C:\pmis\logs\{middleware}.log` |

`install-pmis-infra.ps1` 启动时会询问 `DataHome` / `LogHome`,可自定义。

## 5. Windows 服务名

`install-pmis-infra.ps1` 会注册:

| 服务 | 显示名 |
|---|---|
| `postgresql-x64-18` | PostgreSQL Server 18 |
| `Redis` | Redis |
| `nacos` | Nacos |
| `minio` | MinIO |
| `seata` | Seata |
| `rocketmq-namesrv` | RocketMQ NameServer |
| `rocketmq-broker` | RocketMQ Broker |
| `xxl-job` | XXL-Job Admin |
| `elasticsearch` | Elasticsearch |

可 `Get-Service` / `Stop-Service` / `Start-Service` 直接管理。

## 6. 故障排查

```powershell
# 查看某个服务日志
Get-EventLog -LogName Application -Source "Nacos" -Newest 30

# 查看脚本输出
Get-Content C:\pmis\logs\nacos.log -Tail 50

# 检查端口
Test-NetConnection 127.0.0.1 -Port 8848
```

详见 [`docs/INFRASTRUCTURE.md`](../../docs/INFRASTRUCTURE.md) Windows 章节。
