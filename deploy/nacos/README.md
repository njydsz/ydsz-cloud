# Nacos 配置中心

将各微服务配置通过 Nacos 集中管理。

## 命名空间

| 环境 | Namespace ID |
|------|--------------|
| dev | pmis-dev |
| test | pmis-test |
| staging | pmis-staging |
| prod | pmis-prod |

## 配置 DataId 命名规则

`<spring.application.name>-<spring.profiles.active>.yaml`

例：`ydsz-pmis-user-dev.yaml`

## 配置内容

每个微服务的公共配置：

```yaml
server:
  port: 9002

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:127.0.0.1}:${DB_PORT:5432}/${DB_NAME:pmis}
    username: ${DB_USER:pmis}
    password: ${DB_PASSWORD:pmis@2026}
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:pmis@2026}

pmis:
  jwt:
    secret: ${JWT_SECRET}

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

## 使用 Nacos 控制台

访问 `http://localhost:8848/nacos`，默认账号 `nacos / nacos`。

在 `配置管理 > 配置列表` 中新增配置。
