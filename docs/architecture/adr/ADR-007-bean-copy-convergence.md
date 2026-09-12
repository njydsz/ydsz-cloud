# ADR-007：Bean 拷贝能力双轨收敛（Spring BeanUtils vs ydsz-common-util BeanMapper）

**状态：** 已接受
**日期：** 2026-09-12
**决策者：** ydsz-team

## 背景与问题陈述

业务模块中 Bean 属性拷贝存在双轨并存：Spring `org.springframework.beans.BeanUtils` 与 common-util `BeanMapper` 均在使用（实测 BeanUtils 导入 32 个文件、BeanMapper 导入 22 个文件）。两种工具在属性复制语义、错误处理、扩展点（自定义转换器、忽略策略）上不一致，导致：

1. 同类场景（DTO → Entity / Entity → VO）出现两种写法，代码评审无判断依据
2. `BeanUtils.copyProperties` 基于字段名反射浅拷贝，类型不兼容时静默跳过或抛非受检异常，错误难以追踪
3. `BeanUpdateUtil`（BeanMapper 同包的非空字段更新工具）能力无人知悉，部分模块自行手写拷贝循环

依据《云顶编码规范》§33.7，本 ADR 仅裁决"双轨并存"问题，不以引用热度评判 BeanMapper 价值。

## 决策驱动因素

* 平台内同能力两套实现，违反"业务模块优先复用 common"原则
* BeanMapper 具备统一扩展点（自定义 Converter 注册、忽略字段策略、集合元素级映射）
* BeanUpdateUtil 提供非空字段增量更新语义，`BeanUtils.copyProperties` 无法等价替代
* 全量替换风险：两者在 null/类型不兼容时的行为有细微差异，机械替换可能引入行为变化

## 考虑的选项

* **维持双轨：** 不收敛，成本为零，但约定持续漂移
* **全量收敛至 BeanMapper：** 唯一入口，替换成本一次性支出
* **全量收敛至 MapStruct：** 编译期生成，性能最优，但引入注解处理器构建链，与本仓自研轻量化风格冲突

## 决策结果

**收敛至 ydsz-common-util `BeanMapper` / `BeanUpdateUtil` 作为唯一 Bean 拷贝入口。**

* 新代码禁止使用 `BeanUtils.copyProperties`（checkstyle/守护脚本覆盖后生效）
* 存量 32 处按"改哪个文件、顺带替换"原则渐进迁移，不设一次性大爆炸替换
* 迁移时必须人工核对字段差异（尤其集合/嵌套对象/类型不兼容字段），禁止机械全局替换
* 明确豁免：Spring 框架内部机制（如 `BeanUtils.instantiateClass`）不受本 ADR 约束

### 正面后果

* Bean 拷贝单入口，扩展点（Converter/忽略策略）统一生效
* `BeanUpdateUtil` 非空更新语义得以推广，减少手写拷贝循环
* 评审判断依据唯一

### 负面后果

* 存量迁移需逐处核对行为差异，有一定人力成本
* BeanMapper 依赖运行时反射，性能敏感热路径需基准验证（对应 docs/performance/baseline-plan.md）

## 关联

* 守护规则：`../../../sqls/scripts/check-common-reuse.py`
* 规范章节：《云顶编码规范》§33.7、§22（公共能力复用）
