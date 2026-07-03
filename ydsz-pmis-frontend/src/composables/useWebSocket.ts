/**
 * @file WebSocket 实时推送 composable（全局单例，基于 STOMP 协议）
 * @description P0-1: 使用 @stomp/stompjs 替代原生 WebSocket，与后端 Spring STOMP broker 直连。
 *   - 全局单例：多个组件共享同一个 STOMP 连接，避免重复创建
 *   - 内置心跳：STOMP 协议层 10s/10s 心跳保活（服务端/客户端各 10s）
 *   - 自动重连：@stomp/stompjs 内置指数退避重连（默认 5s 间隔）
 *   - 按 type 分发：后端推送