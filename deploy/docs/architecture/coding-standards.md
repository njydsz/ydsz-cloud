# PMIS 编码规范

> 最后更新：2026-07-29

## 1. 实体类命名规范

数据库实体类（Entity）不以 DO 为后缀，直接使用业务名称。VO/DTO 后缀保留。基类 `BaseDO`→`Base`、`BaseLongDO`→`BaseLong`、`LogBaseDO`→`LogBase`。6 个命名冲突类保留 DO 后缀（`AgentDefinitionDO`/`RuleDefinitionDO`/`RuleExecutionTraceDO`/`RulePackDO`/`RuleChainGraphDO`/`RuleTestCaseDO`）。

## 2. 禁止行内全限定类名（FQN）

Java 代码中不允许出现行内全限定类名用法。所有类型引用必须使用标准 import 语句后在代码中直接使用简单类名。规则文件：`.trae/rules/no-inline-fqn.md`。

## 3. 禁止使用 @SuppressWarnings

Java 代码中不允许出现 `@SuppressWarnings` 注解，所有警告必须从根源修复而非压制。规则文件：`.trae/rules/no-inline-fqn.md`。

## 4. 优先使用 Python 而非 PowerShell

执行脚本命令时必须优先使用 Python，禁止使用 PowerShell。PowerShell 在处理 UTF-8 无 BOM 的源代码文件时会将文件内容转换为乱码。规则文件：`.trae/rules/prefer-python-over-powershell.md`。

## 5. 禁止封装通用 CRUD

> 规则文件：`.trae/rules/no-generic-crud.md`（alwaysApply: true）

无论是后端还是前端，**禁止封装通用 CRUD 基类、接口或工厂函数**。每个业务模块的 API 方法必须显式定义。

### 5.1 后端

- **禁止**创建通用 CRUD Service 基类（如 `BaseCrudService<T>`）
- **禁止**创建通用 CRUD Controller 基类（如 `BaseCrudController<T, DTO>`）
- **禁止**在 MyBatis-Plus `IService`/`ServiceImpl` 之上再封装一层通用 CRUD 接口
- **允许**使用 MyBatis-Plus `IService`/`ServiceImpl`（框架标准接口）
- **允许**使用 `BaseConverter` 接口（方法命名规范接口，不含 CRUD 逻辑）
- 各业务 Service 接口应**显式声明**自身所需的 CRUD 方法签名

### 5.2 前端

- **禁止**创建 CRUD 工厂函数（如 `createCrudApi<T, Q, D>(client, basePath)`）
- **禁止**创建通用 `CrudApi` 接口类型
- **禁止**从已删除的 `@ydsz/shared-api` 包导入 `createCrudApi` 或 `CrudApi`
- **允许**使用 `@ydsz/shared-auth` 的 `requestClient`
- **允许**使用 `@ydsz/types` 的纯类型定义
- 各子应用 API 文件应**显式定义**各个 API 方法

### 5.3 设计理由

1. **可读性优先**：显式定义的方法一目了然
2. **灵活性**：不同模块的 API 差异天然支持
3. **类型安全**：避免泛型推导丢失类型信息
4. **避免过度抽象**：将简单 HTTP 调用隐藏在抽象层之后增加理解成本
5. **后端同理**：通用 CRUD 基类将业务逻辑隐藏在泛型继承链中

### 5.4 检测方式

- 前端：ESLint `.eslintrc-restrict.cjs` 配置 `no-restricted-imports` 禁止 `@ydsz/shared-api`
- 后端：ArchUnit + Code Review 检查 `BaseCrudService`、`AbstractCrudController` 等命名模式

### 5.5 变更记录

- **2026-07-29**：初始创建。删除 `@ydsz/shared-api` 包（`createCrudApi`/`CrudApi`/`createPageQuery`），重构 7 个前端 API 文件为显式方法定义。
