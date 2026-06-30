# 文档与交付规范

> 文档版本: V1.0 | 编制日期: 2026-06-30

## 1. 必交付文档清单

每个微服务/前端模块交付时必须包含：

| 文档 | 路径 | 内容 |
|------|------|------|
| README.md | 模块根目录 | 模块概述、技术栈、构建命令、目录结构 |
| CHANGELOG.md | 模块根目录 | 变更日志（版本、日期、变更内容） |
| OpenAPI 文档 | 自动生成 | 接口文档（Swagger UI） |
| 架构决策记录 (ADR) | `docs/adr/` | 重大技术决策与原因 |
| 部署文档 | `deploy/README.md` | 部署架构、环境要求、操作步骤 |
| 运维手册 | `runbooks/` | 常见问题、应急处理、回滚方案 |

## 2. README 模板

```markdown
# <模块名>

> 简介：一句话描述

## 技术栈
- ...

## 目录结构
...

## 本地开发
\`\`\`bash
# 安装依赖
pnpm install   # 前端
mvn install    # 后端

# 启动
pnpm dev       # 前端
mvn spring-boot:run  # 后端
\`\`\`

## 环境变量
| 变量 | 说明 | 默认值 |
|------|------|--------|
| ... | ... | ... |

## 测试
\`\`\`bash
pnpm test      # 前端
mvn test       # 后端
\`\`\`

## 部署
...

## 联系方式
- Owner: @<负责人>
- Maintainer: @<维护人>
```

## 3. CHANGELOG 格式

遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)：

```markdown
# Changelog

## [1.2.0] - 2026-09-30

### Added
- 新增商机分级管理

### Changed
- 优化报表查询性能

### Fixed
- 修复登录 Token 刷新失败

### Removed
- 移除废弃的 v1 接口

## [1.1.0] - 2026-08-30
...
```

## 4. ADR (架构决策记录)

每次重大技术决策（如引入新框架、修改架构）必须写 ADR：

```markdown
# ADR-001: 引入 Flowable 作为工作流引擎

- 状态: 已接受
- 日期: 2026-06-30
- 决策人: <姓名>

## 背景
公司业务涉及多级审批、门径式评审、会签/或签，亟需工作流引擎。

## 备选方案
1. Flowable
2. Activiti
3. Camunda
4. 自研

## 决策
选择 Flowable。

## 理由
- 与 Spring Boot 集成完善
- 支持 BPMN 2.0 标准
- 社区活跃，文档齐全
- 性能满足业务需求

## 后果
- 学习成本中等
- 需要维护 Flowable 数据表
- 后续可平滑升级
```

## 5. 注释规范

### Java / JSDoc

```java
/**
 * 项目服务
 *
 * @author zhangsan
 * @since 1.0.0
 */
public interface ProjectService {

    /**
     * 创建项目
     *
     * @param dto 项目创建参数
     * @return 项目 ID
     * @throws BizException 项目名称重复时抛出
     */
    Long create(ProjectCreateDTO dto);
}
```

### TS

```typescript
/**
 * 用户登录
 * @param params 登录参数
 * @returns 登录结果（含 Token）
 */
export async function login(params: LoginParams): Promise<LoginResult> {
  // ...
}
```

## 6. 提交信息

- 遵循 Conventional Commits（见 git-workflow.md）
- 提交信息清晰描述变更
- 一个提交只做一件事
- 大型变更拆分为多个原子提交

## 7. PR / MR 模板

```markdown
## 变更说明
<!-- 描述本次变更的目的、范围 -->

## 变更类型
- [ ] 新功能
- [ ] 缺陷修复
- [ ] 重构
- [ ] 文档
- [ ] 性能优化

## 关联 Issue
<!-- PMIS-xxx -->

## 测试
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 手动测试通过
- [ ] 性能测试（如适用）

## 截图/录屏
<!-- 涉及 UI 变更时附图 -->

## Checklist
- [ ] 代码符合规范
- [ ] 测试覆盖达标
- [ ] 文档已更新
- [ ] 无敏感信息泄露
- [ ] 无 SQL 注入风险
```

## 8. 文档存放

```
docs/
├── prd/                  # 产品需求文档
├── standards/            # 工程规范（本文档）
├── adr/                  # 架构决策记录
├── runbooks/             # 运维手册
├── api/                  # API 文档（自动生成）
└── archive/              # 归档
```
