# WebSocket STOMP 协议迁移说明

> 状态：**暂不实施**，列为后续 Sprint 任务

---

## 一、当前问题

### 后端现状
后端使用 **STOMP over WebSocket** 协议栈，配置方式为：

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");   // 消息代理前缀
        registry.setApplicationDestinationPrefixes("/app"); // 客户端发送前缀
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
    }
}
```

Controller 使用 `@MessageMapping` / `@SendTo` 处理消息。

### 前端现状
前端使用**原生 WebSocket + 自定义 JSON 分发**模式，直接连接 `ws://host/ws`，通过 JSON message 中的 `type` 字段路由到不同 handler：

```javascript
const ws = new WebSocket('ws://host/ws');
ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    dispatchByType(msg);  // 根据 msg.type 分发
};
```

---

## 二、协议不兼容分析

| 维度 | 后端 (STOMP) | 前端 (裸 WS) | 兼容性 |
|------|-------------|-------------|--------|
| 握手协议 | STOMP `CONNECT` 帧 + `CONNECTED` 帧 | 无握手，直接发 JSON | **不兼容** |
| 帧格式 | `COMMAND\nheader:value\n\nbody\0` | 纯 JSON 文本 | **不兼容** |
| 订阅机制 | `SUBSCRIBE\ndestination:/topic/xxx\n` | 无订阅概念 | **不兼容** |
| 发送机制 | `SEND\ndestination:/app/xxx\n` | 直接 `ws.send(json)` | **不兼容** |
| 心跳 | STOMP `heart-beat` header | 无 | **不兼容** |
| 重连 | SockJS fallback + reconnect | 需自研 | 需改造 |

**根因**：STOMP 是基于帧（frame）的文本协议，要求客户端在建立 WS 连接后首先发送 `CONNECT` 帧完成握手。前端直接发送裸 JSON 无法被 STOMP 后端解析，导致服务端日志持续报 `IllegalStateException: Unexpected frame type` 或连接立即关闭。

---

## 三、推荐方案

### 前端引入 STOMP 客户端

引入 `@stomp/stompjs`（STOMP 协议库）+ `sockjs-client`（SockJS 兼容层，支持 fallback）：

```bash
yarn add @stomp/stompjs sockjs-client
```

**选型理由：**
- `@stomp/stompjs`：最新维护的 STOMP-1.2 实现，支持原生 WebSocket（无需 SockJS 即可工作），TypeScript 友好
- `sockjs-client`：当网络环境（防火墙/代理）不支持 WebSocket 时自动降级为 HTTP long-polling，与后端 `withSockJS()` 完全兼容

---

## 四、迁移步骤

### Step 1：安装依赖

```bash
cd ydsz-web          # 或对应的前端项目目录
yarn add @stomp/stompjs sockjs-client
```

### Step 2：创建 `use-stomp.ts` composable

替代现有的 `use-websocket.ts`：

```typescript
// src/composables/use-stomp.ts
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { ref, onUnmounted } from 'vue';

export function useStomp(endpoint = '/ws') {
    const connected = ref(false);
    const messages = ref<any[]>([]);

    const client = new Client({
        webSocketFactory: () => new SockJS(endpoint),
        debug: (str) => console.debug('[STOMP]', str),
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
    });

    client.onConnect = () => {
        connected.value = true;
        console.info('[STOMP] Connected');
    };

    client.onDisconnect = () => {
        connected.value = false;
        console.warn('[STOMP] Disconnected');
    };

    client.onStompError = (frame) => {
        console.error('[STOMP] Broker error:', frame.headers['message']);
    };

    const subscribe = (destination: string, callback: (msg: any) => void): StompSubscription => {
        return client.subscribe(destination, (message: IMessage) => {
            const body = JSON.parse(message.body);
            messages.value.push(body);
            callback(body);
        });
    };

    const send = (destination: string, body: object = {}) => {
        client.publish({
            destination,
            body: JSON.stringify(body),
        });
    };

    const connect = () => client.activate();
    const disconnect = () => client.deactivate();

    onUnmounted(disconnect);

    return { connected, messages, send, subscribe, connect, disconnect };
}
```

### Step 3：订阅与发送路径对照

| 操作 | 路径 | 说明 |
|------|------|------|
| 广播订阅 | `/topic/notifications` | 所有订阅者均收到 |
| 广播订阅 | `/topic/chat/{roomId}` | 群聊广播 |
| 单播订阅 | `/user/queue/messages` | 点对点消息（Spring 自动路由当前用户） |
| 单播订阅 | `/user/queue/notifications` | 个人通知 |
| 消息发送 | `/app/chat.send` | 对应 `@MessageMapping("/chat.send")` |
| 消息发送 | `/app/notification.read` | 已读回执等操作 |
| 消息发送 | `/app/status.update` | 状态更新 |

### Step 4：删除旧的 `use-websocket.ts`

确认所有页面/组件迁移完成后删除原文件。

---

## 五、暂不实施 — 后续 Sprint 任务

本改造**不建议在当前 Sprint 中实施**，原因：

1. **依赖前端 SockJS 兼容性验证** — 需确认生产环境 nginx/proxy 配置支持 WebSocket upgrade
2. **认证 Token 传递** — STOMP `CONNECT` 帧需携带 Authorization header，需与后端 SecurityConfig 联调
3. **灰度切换风险** — 新旧协议不兼容，同时上线后端 STOMP 和前端 STOMP 客户端需保证原子性
4. **测试覆盖** — 需要补充 WS 消息收发集成测试

建议在下一 Sprint 作为独立技术债任务（TICKET-YYYYMMDD-WS-STOMP）列入 backlog，预估工时 3-5 人天。
