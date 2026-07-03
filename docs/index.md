---
layout: home

hero:
  name: PMIS 文档
  text: 南京云顶项目管理系统
  tagline: 轻量规则引擎 · AI 增强 · 分布式执行 · 全生命周期管理
  actions:
    - theme: brand
      text: 快速开始
      link: /api/rules-engine
    - theme: alt
      text: Python SDK
      link: /sdk/python-sdk

features:
  - title: 轻量规则引擎
    details: 基于 Aviator 表达式引擎，支持条件/严重度/模板表达式、dry-run 仿真、A/B 测试、灰度发布、冲突检测。
    link: /api/rules-engine
  - title: AI 增强
    details: 自然语言转规则（NL2Rule）、规则描述生成、表达式优化建议、4 维健康度评分、启发式规则推荐。
    link: /api/rules-ai
  - title: 分布式执行
    details: 一致性 Hash 分片 + 虚拟节点，集群节点心跳注册，按规则编码分片执行，集群 ≤1 自动降级。
    link: /api/rules-distributed
  - title: 规则集市场
    details: 规则打包发布、版本管理、一键安装、官方标记、评分体系，支持跨环境规则复用。
    link: /rules/rule-pack-market
  - title: 链路追踪与回放
    details: 异步 Trace 落库，按 traceId 查询执行链路，支持历史快照回放与差异分析。
    link: /rules/rule-trace-replay
  - title: Python SDK & CLI
    details: 零第三方依赖的 urllib SDK，argparse CLI 工具，支持规则管理/评估/AI 增强/规则集安装。
    link: /sdk/python-sdk
---
