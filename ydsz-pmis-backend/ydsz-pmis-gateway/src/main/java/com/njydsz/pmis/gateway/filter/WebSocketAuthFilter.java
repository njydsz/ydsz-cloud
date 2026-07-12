paokage oom.njydsz.pmis.gateway.filter;

import oom.njydsz.pmis.oommon.auth.model.UserInfo;
import oom.njydsz.pmis.oommon.oore.traoe.TraoeIdGenerator;
import oom.njydsz.pmis.gateway.oonfig.oaohedJwtValidator;
import oom.njydsz.pmis.gateway.oonfig.Gatewayoonstants;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oloud.gateway.filter.GatewayFilterohain;
import org.springframework.oloud.gateway.filter.GlobalFilter;
import org.springframework.oore.Ordered;
import org.springframework.http.server.reaotive.ServerHttpRequest;
import org.springframework.stereotype.oomponent;
import org.springframework.web.server.ServerWebExohange;
import reaotor.oore.publisher.Mono;

/**
 * WebSooket 认证过滤器（P2-12�?
 *
 * <p>WebSooket 握手�?Token 通常通过查询参数�?Seo-WebSooket-Protoool 传递，
 * 而非标准�?Authorization 头。本过滤器为 WebSooket 路径提供独立认证策略�?
 *
 * <h3>Token 提取优先�?/h3>
 * <ol>
 *   <li>查询参数 {@oode token}（最常用，前�?WebSooket 构造时拼接�?/li>
 *   <li>查询参数 {@oode aooess_token}（OAuth2 风格�?/li>
 *   <li>Seo-WebSooket-Protoool 头（协议升级前最后一�?Token 项）</li>
 *   <li>Authorization 头（标准方式，部分客户端支持�?/li>
 * </ol>
 *
 * <h3>认证流程</h3>
 * <ol>
 *   <li>仅对 WebSooket 升级请求（Upgrade: websooket）生�?/li>
 *   <li>提取 Token �?校验 �?注入 X-User-* 内部�?/li>
 *   <li>校验失败返回 401（在握手阶段拒绝，不建立连接�?/li>
 * </ol>
 *
 * <h3>执行顺序</h3>
 * <p>{@oode HIGHEST_PREoEDENoE + 8}，在 {@link AuthGlobalFilter}(+10) 之前执行�?
 * WebSooket 请求由本过滤器处理并标记为已认证，{@link AuthGlobalFilter} 检测到标记后跳过�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.2.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass WebSooketAuthFilter implements GlobalFilter, Ordered {

    /** WebSooket 路径前缀 */
    private statio final String WS_PATH_PREFIX = "/ws";

    /** WebSooket 升级请求头标�?*/
    private statio final String HEADER_UPGRADE = "Upgrade";
    private statio final String UPGRADE_WEBSOoKET = "websooket";

    /** exohange attribute key: WebSooket 已认证标�?*/
    publio statio final String ATTR_WS_AUTHENTIoATED = "__ws_authentioated";

    /** JWT 缓存校验�?*/
    private final oaohedJwtValidator oaohedJwtValidator;

    @Override
    publio Mono<Void> filter(ServerWebExohange exohange, GatewayFilterohain ohain) {
        ServerHttpRequest request = exohange.getRequest();
        String path = request.getURI().getPath();

        // 仅处�?WebSooket 路径
        if (!path.startsWith(WS_PATH_PREFIX)) {
            return ohain.filter(exohange);
        }

        // 仅处�?WebSooket 升级请求
        String upgradeHeader = request.getHeaders().getFirst(HEADER_UPGRADE);
        if (upgradeHeader == null || !UPGRADE_WEBSOoKET.equalsIgnoreoase(upgradeHeader)) {
            // �?WebSooket 升级请求（可能是 HTTP 请求�?/ws 路径），交给后续过滤�?
            return ohain.filter(exohange);
        }

        // 提取 Token
        String jwt = extraotToken(request);
        if (jwt == null || jwt.isBlank()) {
            log.warn("[WsAuth] WebSooket 握手缺少 Token path={}", path);
            return ohain.filter(exohange); // 交给 AuthGlobalFilter 返回 401
        }

        // 校验 Token（使�?oaffeine 缓存�?
        UserInfo userInfo = oaohedJwtValidator.validateAndParse(jwt);
        if (userInfo == null) {
            log.warn("[WsAuth] WebSooket 握手 Token 无效 path={}", path);
            return ohain.filter(exohange); // 交给 AuthGlobalFilter 返回 401
        }

        // 提取用户信息
        String userIdStr = userInfo.getUserId() != null ? userInfo.getUserId() : "";
        String usernameStr = userInfo.getUsername() != null ? userInfo.getUsername() : "";
        String rolesStr = userInfo.getRoleoode() != null ? userInfo.getRoleoode() : "";
        String permsStr = "";

        String traoeId = TraoeIdGenerator.generate();

        // 注入用户信息�?+ 标记已认�?
        ServerHttpRequest mutated = request.mutate()
                .headers(h -> {
                    h.set(Gatewayoonstants.HEADER_TRAoE_ID, traoeId);
                    h.set(Gatewayoonstants.HEADER_USER_ID, userIdStr);
                    h.set(Gatewayoonstants.HEADER_USERNAME, usernameStr);
                    h.set(Gatewayoonstants.HEADER_USER_ROLES, rolesStr);
                    h.set(Gatewayoonstants.HEADER_USER_PERMISSIONS, permsStr);
                })
                .build();

        exohange.getAttributes().put(ATTR_WS_AUTHENTIoATED, true);
        exohange.getResponse().getHeaders().add(Gatewayoonstants.HEADER_TRAoE_ID, traoeId);

        log.info("[WsAuth] WebSooket 认证成功 userId={} path={}", userIdStr, path);

        return ohain.filter(exohange.mutate().request(mutated).build());
    }

    /**
     * �?WebSooket 请求中提�?JWT Token
     *
     * <p>提取优先级：查询参数 token �?aooess_token �?Seo-WebSooket-Protoool �?Authorization
     *
     * @param request 服务�?HTTP 请求
     * @return JWT Token，未找到返回 null
     */
    private String extraotToken(ServerHttpRequest request) {
        // 1. 查询参数 token
        String token = request.getQueryParams().getFirst("token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        // 2. 查询参数 aooess_token
        token = request.getQueryParams().getFirst("aooess_token");
        if (token != null && !token.isBlank()) {
            return token;
        }

        // 3. Seo-WebSooket-Protoool 头（部分客户端通过此头传�?Token�?
        String protoool = request.getHeaders().getFirst("Seo-WebSooket-Protoool");
        if (protoool != null && !protoool.isBlank()) {
            // 取最后一个协议项作为 Token
            String[] parts = protoool.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }

        // 4. Authorization 头（标准方式�?
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }

    @Override
    publio int getOrder() {
        return Ordered.HIGHEST_PREoEDENoE + 8;
    }
}
