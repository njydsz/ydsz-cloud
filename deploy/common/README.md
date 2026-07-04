# Common · 跨环境共享资源

> 8 大中间件的配置模板 + Nacos 共享配置 + 通用 SQL
> 所有环境(Docker / Ubuntu / Windows / K8S)都从这里读取

---

## 目录结构

```
common/
├── conf/                  # 8 中间件原生部署的配置模板
│   ├── elasticsearch/
│   ├── nacos/
│   ├── postgres/
│   ├── redis/
│   ├── rocketmq/
│   ├── seata/
│   └── xxl-job/
├── nacos/                 # PMIS 业务 Nacos 共享配置
│   └── ydsz-pmis-common.yaml
└── sql/                   # 通用 SQL(非主库初始化)
    └── tables_xxl_job_pg.sql
```

## 使用方式

### 1. 中间件配置(conf/)

`conf/{middleware}/*` 是 **中间件原生部署**(apt / Windows 安装)时使用的配置文件。

- **ubuntu/** 脚本会在安装时复制并做占位符替换
- **windows/** 脚本同样机制
- **docker/** 用 `docker-compose.dev.yml` 内嵌配置,不直接读这里

占位符约定(由 ubuntu/windows 安装脚本替换):

| 占位符 | 含义 | 示例 |
|---|---|---|
| `__PMIS_DATA_HOME__` | 数据根目录 | `/opt/pmis/data` |
| `__PMIS_LOG_HOME__` | 日志根目录 | `/var/log/pmis` |
| `__PG_DATA__` | PG 数据目录 | `/var/lib/postgresql/18/main` |

### 2. Nacos 共享配置(nacos/ydsz-pmis-common.yaml)

PMIS 7 个微服务启动时会从 Nacos 拉取 `ydsz-pmis-common.yaml`,内容包含:

- `spring.datasource.*` (PG / Druid)
- `spring.redis.*`
- `spring.cloud.nacos.*`
- 各服务 Feign 客户端地址
- Sentinel / Seata / RocketMQ 客户端配置

**导入命令**:

```bash
# Ubuntu
./deploy/ubuntu/scripts/import-nacos-config.sh pmis dev

# Windows
deploy\windows\scripts\import-nacos-config.bat pmis dev
```

### 3. 通用 SQL(sql/)

非主库表结构,通常用于中间件自身初始化:
- `tables_xxl_job_pg.sql` — XXL-Job 的 PostgreSQL 表(主库用 PG 时)

主库初始化走 `docs/V1.0.0.sql`,**不在本目录**。

## 修改流程

1. **修改 conf/ 模板** → 影响后续 ubuntu/windows 安装的中间件
2. **修改 nacos/ydsz-pmis-common.yaml** → 重新 `import-nacos-config.sh` 后重启服务
3. **修改 sql/** → 通常只对新部署生效
4. **不要把 .env 密码提交进 git** → 见仓库根 `.gitignore`
