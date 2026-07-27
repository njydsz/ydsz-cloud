# ydsz 全局 Feign 调用拓扑图

> 版本: 1.0.0 | 更新日期: 2026-07-27
>
> 本文档描述 ydsz 项目各微服务模块之间的 Feign 调用关系。

---

## 调用拓扑总览

所有业务服务均直接注册在 `gateway` 之下，不存在二级网关代理。图中红框标出的 `cronjob`、`nextwiki`、`agent`、`message` 与第一行的 `workflow`、`userinfo`、`system`、`project`、`literule` 处于同一层级，均为 gateway 的直接下游服务。

```
                         ┌──────────────┐
                         │