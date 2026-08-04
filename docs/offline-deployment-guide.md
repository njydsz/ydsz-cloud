# 离线环境部署适配方案

> 适用于政务云、军工、金融隔离网络等无互联网访问场景

## 1. Maven 依赖离线化

### 步骤 1: 在线环境导出依赖

```bash
# 下载所有依赖到本地目录
mvn dependency:go-offline -pl ydsz-backend

# 或导出完整的 local repository
mvn dependency:copy-dependencies -DoutputDirectory=/path/to/offline-repo
```

### 步骤 2: 搭建私有 Maven 仓库

```bash
# 部署 Nexus / Artifactory
docker run -d --name nexus -p 8081:8081 sonatype/nexus3

# 上传依赖到私有仓库
mvn deploy:deploy-file -Dfile=<jar> -DgroupId=<group> -DartifactId=<artifact> \
  -Dversion=<version> -Dpackaging=jar -Durl=http://nexus:8081/repository/maven-releases/
```

### 步骤 3: 配置 settings.xml

```xml
<settings>
  <mirrors>
    <mirror>
      <id>nexus</id>
      <mirrorOf>*</mirrorOf>
      <url>http://nexus:8081/repository/maven-public/</url>
    </mirror>
  </mirrors>
</settings>
```

## 2. Docker 镜像离线化

### 方式一: Docker Save/Load

```bash
# 在线环境: 打包镜像
docker save -o ydsz-gateway.tar registry.cn-hangzhou.aliyuncs.com/ydsz/ydsz-gateway:latest

# 传输到离线环境 (U盘/内网传输)
scp ydsz-gateway.tar user@offline-server:/tmp/

# 离线环境: 加载镜像
docker load -i /tmp/ydsz-gateway.tar
```

### 方式二: 私有 Harbor 仓库

```bash
# 在隔离环境部署 Harbor
# 在线环境推送到中转仓库
docker tag ydsz-gateway registry.example.com/mirror/ydsz-gateway:latest
docker push registry.example.com/mirror/yzsz-gateway:latest

# 离线 Harbor 通过复制策略同步
```

## 3. Helm Chart 离线化

```bash
# 在线环境: 下载并打包 Helm Chart
helm dependency update deploy/helm/ydsz-backend
helm package deploy/helm/ydsz-backend -d /tmp/charts/

# 上传到私有 Chart Museum
curl --data-binary "@ydsz-backend-1.0.0.tgz" http://chartmuseum:8080/api/charts
```

## 4. 系统镜像离线化

基础镜像也需要提前准备：

```bash
# 运行时镜像
docker save -o eclipse-temurin-21-jre.tar eclipse-temurin:21-jre

# 基础镜像
docker save -o postgres-18.tar pgvector/pgvector:pg18
docker save -o redis-8.tar redis:8-alpine
docker save -o nacos-2.3.2.tar nacos/nacos-server:v2.3.2
```

## 5. 离线部署清单

| 组件 | 离线方式 | 注意事项 |
|------|---------|---------|
| Maven 依赖 | 私有 Nexus | 提前同步所有传递依赖 |
| Docker 镜像 | Docker Save/Load + Harbor | 基础镜像也需要打包 |
| Helm Chart | Chart Museum | 包含模板和 values |
| SQL 脚本 | 包含在代码仓库 | 版本化管理 |
| 配置文件 | 代码仓库 + ConfigMap | 环境差异配置分离 |

## 6. 离线部署验证

部署完成后验证清单：
- [ ] 所有 10 个微服务 Pod Running
- [ ] API 网关访问正常
- [ ] 数据库连接正常
- [ ] 缓存操作正常
- [ ] 消息队列收发正常
- [ ] 文件上传下载正常
- [ ] 健康检查全部通过
