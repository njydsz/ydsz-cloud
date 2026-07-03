<!--
  ===========================================================================
  文件名: naming-convention.md
  路径:   docs/standards/naming-convention.md
  作用:   PMIS 编码与命名规范：语言版本、标识符命名、后端/前端/数据库命名细则
  对标:   阿里巴巴《Java 开发手册》/ Google Style Guide
  ===========================================================================
-->

# 编码与命名规范

> 文档版本: V1.0 | 编制日期: 2026-06-30 | 最近更新: 2026-07-03
> 对标: 阿里巴巴《Java 开发手册》、Google Style Guide

## 1. 语言版本

| 维度 | 强制版本 |
|------|----------|
| Java | JDK 17 LTS |
| Node.js | 20 LTS |
| TypeScript | 5.x |
| SQL | PostgreSQL 18 兼容标准 |

## 2. 标识符命名通用规则

| 类型 | 规则 | 示例 |
|------|------|------|
| 包名 | 全小写，点分隔 | `com.njydsz.pmis.user` |
| 类名 | 大驼峰，名词 | `UserService`, `ProjectController` |
| 接口名 | 大驼峰，名词/形容词 | `UserService`, `Serializable` |
| 方法名 | 小驼峰，动词开头 | `getUserById`, `saveProject` |
| 变量名 | 小驼峰，名词 | `userId`, `projectList` |
| 常量名 | 全大写，下划线分隔 | `MAX_RETRY_COUNT` |
| 枚举名 | 大驼峰，值全大写 | `enum Level { L1, L2 }` |
| 数据库表名 | 小写下划线，`pmis_` 前缀 | `pmis_user`, `pmis_project` |
| 数据库字段 | 小写下划线 | `user_id`, `created_at` |
| URL 路径 | 全小写，连字符分隔 | `/api/v1/projects/{id}` |

## 3. 后端命名细则

### 3.1 类命名后缀

| 后缀 | 含义 | 强制要求 |
|------|------|----------|
| `Controller` | REST 控制器 | 仅处理入参校验与组装返回 |
| `Service` / `ServiceImpl` | 业务服务 | 业务编排与事务控制 |
| `Mapper` | MyBatis DAO | 仅数据访问，禁止业务逻辑 |
| `Entity` / `DO` | 持久化对象 | 与表结构一一对应 |
| `DTO` | 数据传输对象 | 跨层数据传输 |
| `VO` | 视图对象 | 接口返回展示 |
| `BO` | 业务对象 | 服务内部组合数据 |
| `Converter` | 对象转换 | MapStruct 实现 |
| `Enum` | 枚举 | 必须为枚举值 |
| `Constants` | 常量类 | 不允许定义接口常量 |
| `Config` | 配置类 | `@Configuration` 标注 |
| `Utils` / `Helper` | 工具类 | 构造器私有 |

### 3.2 禁止命名

- ❌ `xxxManager`, `xxxProcessor`, `xxxHandler` 等无明确语义后缀
- ❌ 拼音命名：`userList` 不可写为 `yongHuLieBiao`
- ❌ 单字符命名（循环变量除外）：禁止 `int a;`
- ❌ 中文/特殊字符：禁止 `用户列表` `user-list`（除 URL 外）
- ❌ 缩写歧义：`dao` 不允许，必须为 `dataAccessObject` 或全写

## 4. 前端命名细则

### 4.1 目录与文件

| 类别 | 命名 | 示例 |
|------|------|------|
| 组件目录 | 大驼峰 | `UserCard/`, `ProjectTable/` |
| 组件文件 | 大驼峰 | `UserCard.vue`, `index.ts` |
| 工具文件 | 小驼峰 | `request.ts`, `format.ts` |
| Store 文件 | 小驼峰 | `useUserStore.ts` |
| 常量文件 | 小驼峰 | `constants.ts` |
| 类型文件 | 大驼峰 | `User.ts`, `Project.ts` |
| 路由文件 | 小驼峰 | `routes.ts` |
| Hook 文件 | useXxx | `useTable.ts` |

### 4.2 变量与方法

- 变量：小驼峰，**必须**有类型注解（TS 严格模式）
- 常量：全大写下划线 `MAX_PAGE_SIZE`
- 组件 props：明确类型与默认值
- 方法名：动词开头 `fetchList`, `handleSubmit`

## 5. 数据库命名细则

### 5.1 表命名

- 业务表：`pmis_<业务域>_<实体>`，例：`pmis_project_main`, `pmis_contract_info`
- 关联表：`pmis_<主实体>_<从实体>_rel`
- 日志表：`pmis_<实体>_<动作>_log`
- 配置表：`pmis_cfg_<配置名>`
- 字典表：`pmis_dict_<字典名>`

### 5.2 字段命名

- 主键：`id`（自增）或 `id`（雪花算法）
- 外键：`<关联实体>_id`，例：`user_id`
- 状态：`status`（SMALLINT 或 VARCHAR）
- 逻辑删除：`deleted`（SMALLINT，0/1）
- 审计字段统一：见下

### 5.3 必含审计字段

每张业务表必须包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| `created_by` | BIGINT | 创建人 ID |
| `created_at` | TIMESTAMP | 创建时间（默认 CURRENT_TIMESTAMP） |
| `updated_by` | BIGINT | 最后修改人 |
| `updated_at` | TIMESTAMP | 最后修改时间 |
| `deleted` | SMALLINT | 逻辑删除（0/1） |

状态字段使用 `status`，与 `deleted` 区分业务状态与删除状态。

## 6. 常量管理

- 后端：每个业务模块独立 `Constants` 类，按业务域分组
- 前端：每个模块独立 `constants.ts`
- 禁止使用魔法值，重复出现 ≥2 次的字符串/数字必须抽取为常量

## 7. 注释规范

- 类/接口必须有 JSDoc/JavaDoc 类注释
- 公共方法必须有方法注释，说明：用途、入参、返回、异常、事务
- 复杂业务逻辑必须有行内注释
- TODO 必须包含 `// TODO <作者> <日期> <说明>`
- 禁止无效注释（`// 设置 name 变量` 紧随 `user.setName(name)`）

## 8. 错误与日志

- 异常统一抛出 `BizException` / `CommonException`
- 业务错误使用 `Result.failed(BizErrorCode.X)`
- 日志使用 SLF4J，级别严格：ERROR（系统异常）/ WARN（业务警告）/ INFO（关键节点）/ DEBUG（详细信息）
- 日志格式：`<时间> <级别> <线程> <Logger> [<traceId>] - <message>`
- 禁止 `System.out.println` 打印日志

## 9. 命名反模式（Code Smells）

| 反模式 | 正确做法 |
|--------|----------|
| `userList`, `dataMap` | `users`, `userMap`（集合类型已隐含） |
| `processData()`, `handleInfo()` | `parseUser()`, `validateOrder()`（动词+具体对象） |
| `temp1`, `a`, `info` | 业务含义命名：`userId`, `projectName` |
| `getData()` 返回 `Object` | 强类型返回：`UserVO`, `PageResult<X>` |
| `MyClass` + `Impl` 命名类 | 业务领域命名：`UserServiceImpl`, `OrderServiceImpl` |

## 10. 命名评审 CheckList

- [ ] 是否使用业务术语（非技术黑话）
- [ ] 是否避免缩写歧义
- [ ] 是否见名知意
- [ ] 是否符合所在语言惯例（Java/TS/SQL）
- [ ] 是否与同模块其他命名风格一致

## 11. 变更记录

| 日期 | 版本 | 变更人 | 变更内容 |
|------|------|--------|----------|
| 2026-07-03 | 1.1 | 架构组 | 新增 §9 反模式、§10 评审 CheckList |
| 2026-06-30 | 1.0 | 架构组 | 初始版本 |
