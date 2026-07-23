import pathlib

p = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-socket\src\main\java\com\njydsz\common\socket\config\WebSocketAutoConfiguration.java')
t = p.read_text(encoding='utf-8')

# Remove OnlineUserService from heartbeat handler method
t = t.replace(
    'OnlineUserService onlineUserService,\nWebSocketProperties properties) {\nlog.info("[WebSocket] \u6ce8\u518c WebSocketHeartbeatHandler',
    'WebSocketProperties properties) {\nlog.info("[WebSocket] \u6ce8\u518c WebSocketHeartbeatHandler'
)

# Remove onlineUserService from constructor call
t = t.replace(
    'return new WebSocketHeartbeatHandler(onlineUserService, properties);',
    'return new WebSocketHeartbeatHandler(properties);'
)

p.write_text(t, encoding='utf-8')
print('OK')
