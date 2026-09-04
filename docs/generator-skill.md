# ydsz-generator — CatPaw 代码生成技能

> CatPaw Skill 定义文件  
> 用途：让 CatPaw AI 助手能够通过自然语言触发代码生成

## 技能名称

ydsz-generator

## 触发关键词

- 代码生成
- 生成代码
- CRUD 生成
- 模块脚手架
- 自动生成代码
- 生成 Entity/Mapper/Service/Controller

## 技能描述

基于数据库表结构自动生成 ydsz-cloud 项目的 DDD 分层 CRUD 代码。
支持自定义模板、多数据库方言（MySQL/PostgreSQL/Oracle）、分组配置。

## 使用方式

### 方式一：REST API（推荐）

启动代码生成器服务后调用 REST 接口：

```bash
# 生成单表代码
curl -X POST http://localhost:9010/api/v1/generate \
  -H "Content-Type: application/json" \
  -d '{"tableName": "ydsz_sys_tenant"}'

# 预览代码（不写文件）
curl "http://localhost:9010/api/v1/generate/preview?tableName=ydsz_sys_tenant"

# 列出可用模板
curl http://localhost:9010/api/v1/generate/templates
```

### 方式二：CLI 命令行

```bash
# 基本用法
java -cp "target/classes:target/dependency/*" \
     com.njydsz.generator.cli.GeneratorCliApplication \
     --module=system \
     --package=com.njydsz.system \
     --tables=ydsz_sys_tenant \
     --output=D:/Code/open/ydsz-cloud

# MySQL 数据库
java -cp "..." com.njydsz.generator.cli.GeneratorCliApplication \
     --module=userinfo \
     --package=com.njydsz.userinfo \
     --tables=ydsz_user_info \
     --output=D:/Code/open/ydsz-cloud \
     --jdbc-url=jdbc:mysql://localhost:3306/ydsz_cloud \
     --jdbc-user=root \
     --jdbc-pass=password
```

### 方式三：配置文件

创建 `generator.properties`：

```properties
module=system
package=com.njydsz.system
tables=ydsz_sys_tenant,ydsz_sys_user
output=D:/Code/open/ydsz-cloud
jdbc.url=jdbc:postgresql://localhost:5432/ydsz_cloud
jdbc.username=ydsz
jdbc.password=ydsz123
```

运行：

```bash
java -cp "..." com.njydsz.generator.cli.GeneratorCliApplication \
     --config=generator.properties
```

## 生成文件清单

一次完整生成会输出以下文件：

| 层 | 文件路径 | 说明 |
|----|----------|------|
| domain | `domain/entity/{EntityName}.java` | 领域实体（继承 MpBaseEntity） |
| domain | `domain/dto/{EntityName}DTO.java` | 数据传输对象 |
| domain | `domain/vo/{EntityName}VO.java` | 视图对象 |
| domain | `domain/query/{EntityName}PageQuery.java` | 分页查询参数 |
| domain | `domain/repository/{EntityName}Repository.java` | 仓储接口 |
| domain | `domain/enums/{EnumName}.java` | 枚举类（自动识别）|
| infra | `infra/mapper/{EntityName}Mapper.java` | MyBatis-Plus Mapper |
| infra | `infra/converter/{ModuleName}Converter.java` | MapStruct 转换器 |
| infra | `infra/repository/{EntityName}RepositoryImpl.java` | 仓储实现 |
| server | `server/service/{EntityName}Service.java` | 服务接口 |
| server | `server/service/impl/{EntityName}ServiceImpl.java` | 服务实现 |
| web | `web/controller/{EntityName}Controller.java` | REST 控制器 |
| api | `api/{EntityName}FeignClient.java` | FeignClient 接口 |
| api | `api/assembler/{EntityName}Assembler.java` | 参数装配器 |
| api | `api/{EntityName}ClientFallbackFactory.java` | 熔断降级 |

## 配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `ydsz.generator.module-name` | 目标模块名 | system |
| `ydsz.generator.package-name` | 目标包名 | com.njydsz.system |
| `ydsz.generator.table-names` | 表名列表 | [] |
| `ydsz.generator.table-prefix` | 表名前缀 | ydsz_ |
| `ydsz.generator.output-dir` | 输出目录 | 必填 |
| `ydsz.generator.author` | 作者署名 | ydsz-team |
| `ydsz.generator.generate-*` | 各层生成开关 | true |
| `ydsz.generator.file-conflict-strategy` | 文件冲突策略 | prompt |
| `ydsz.generator.active-group` | 激活的分组 | 无 |
| `ydsz.generator.groups.*` | 分组配置 | {} |

## 分组配置示例

```yaml
ydsz:
  generator:
    active-group: dev-system
    groups:
      dev-system:
        module-name: system
        package-name: com.njydsz.system
        table-names: [ydsz_sys_tenant, ydsz_sys_user]
        output-dir: "D:/Code/open/ydsz-cloud"
      dev-userinfo:
        module-name: userinfo
        package-name: com.njydsz.userinfo
        table-names: [ydsz_user_info, ydsz_user_profile]
        output-dir: "D:/Code/open/ydsz-cloud"
```

## 枚举类识别

在数据库字段注释中使用 `名称:值=标签;值=标签` 格式定义枚举值：

```sql
COMMENT ON COLUMN ydsz_sys_tenant.status IS '状态:1=启用;0=禁用';
```

生成器会自动解析并创建对应的枚举类。

## 编码规范

生成的代码遵循云顶编码规范 v1.0.6：
- YDIZ-CODE-001: UTF-8 无 BOM
- YDIZ-IMPORT-001: 禁止 FQN
- YDIZ-NAME-001: 禁止 DO 后缀
- YDIZ-DDD-005: api 层不自建 dto/vo/query
- YDIZ-ARCH-001: 单向依赖
- YDIZ-WARN-001: 禁止 @SuppressWarnings
