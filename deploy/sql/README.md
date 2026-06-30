# 数据库 SQL 脚本

## 命名规范

```
V<MAJOR>_<MINOR>_<PATCH>_<SEQ>__<description>.sql
```

例：`V1.0.0_001__init_pmis_schema.sql`

## 执行顺序

1. `V1.0.0_001__init_pmis_schema.sql` - 初始化 Schema + 核心表 + 初始数据

## 工具建议

- Flyway / Liquibase：生产环境使用
- DBeaver / Navicat：本地开发使用
- psql 命令行：CI/CD 使用

## 初始化

```bash
# 使用 docker-compose 自动初始化（推荐）
docker compose up -d

# 手动执行
psql -h 127.0.0.1 -p 5432 -U pmis -d pmis -f V1.0.0_001__init_pmis_schema.sql
```

## 默认账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | admin123 | SUPER_ADMIN |
