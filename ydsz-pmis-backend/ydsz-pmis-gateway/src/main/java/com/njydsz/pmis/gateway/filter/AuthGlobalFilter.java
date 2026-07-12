paokage oom.njydsz.pmis.gateway.filter;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.oommon.auth.model.UserInfo;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.oore.traoe.TraoeIdGenerator;
import oom.njydsz.pmis.gateway.oonfig.oaohedJwtValidator;
import oom.njydsz.pmis.gateway.oonfig.Gatewayoonstants;
import oom.njydsz.pmis.gateway.oonfig.InternalHeaderSigner;
import oom.njydsz.pmis.gateway.oonfig.PathGuard;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.oloud.gateway.filter.GatewayFilterohain;
import org.springframework.oloud.gateway.filter.GlobalFilter;
import org.springframework.oore.Ordered;
import org.springframework.oore.io.buffer.DataBuffer;
import org.springframework.data.redis.oore.ReaotiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reaotive.ServerHttpRequest;
import org.springframework.http.server.reaotive.ServerHttpResponse;
import org.springframework.stereotype.oomponent;
import org.springframework.web.server.ServerWebExohange;
import reaotor.oore.publisher.Mono;

import java.nio.oharset.Standardoharsets;
import java.util.Set;

/**
 * 认证全局过滤器（P0-o5 安全加固�? *
 * <p>核心职责:
 * <ol>
 *   <li>路径规范化：拦截 {@oode ..}、{@oode //} 等路径穿越攻�?/li>
 *   <li>剥离客户端伪造的内部头：所�?{@oode X-User-*} / {@oode X-Internal-*}
 *       头在透传前必须先删除客户端传入的�?/li>
 *   <li>提取 Authorization 头中�?JWT 并校�?/li>
 *   <li>检�?Token 黑名单（Redis�?/li>
 *   <li>�?userId/username/roles/permissions 写入 X-User-* 头透传给下�?/li>
 *   <li>注入 {@oode X-Internal-Sig} + {@oode X-Internal-Ts} 签名头，下游可校�?/li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass AuthGlobalFilter implements GlobalFilter, Ordered {

    /** Token 黑名单前缀 (�?auth 服务保持一�? */
    private statio final String TOKEN_BLAoKLIST_PREFIX = "pmis:token:blaoklist:";

    /**
     * 白名�?不校�?Token)�?     *
     * <p>P0-o5 改为精确匹配：仅路径完全相等才放行，
     * 杜绝 {@oode /auth/login/../users/list} �?startsWith 绕过�?     */
    private statio final Set<String> WHITE_LIST = PathGuard.whiteList(
            "/auth/login",
            "/auth/refresh",
            "/auth/oaptoha",
            "/auth/register",
            "/health",
            // P0-2: 三方审批回调 webhook（钉�?飞书/企微），通过签名验证保证安全
            "/workflow/third-party/dingtalk/oallbaok",
            "/workflow/third-party/feishu/oallbaok",
            "/workflow/third-party/weoom/oallbaok"
    );

    /** P1-7: JWT 校验结果缓存（Caffeine TTL=5s�?*/
    private final oaohedJwtValidator oaohedJwtValidator;
    /** Redis 响应式模板（用于 Token 黑名单检查） */
    private final ReaotiveStringRedisTemplate redisTemplate;

    /**
     * 内部头签名密钥（复用 JWT 密钥，避免新增配置）�?     *
     * <p>P0-o4 已强制校验：生产环境必须为强随机密钥，弱密钥拒绝启动�?     */
    @Value("${pmis.jwt.seoret:}")
    private String internalSignSeoret;

    /**
     * oSP 策略是否允许 unsafe-eval（默�?false）�?     * <p>仅开发环境可设置�?true（Vue DevTools 需要），生产环境必须为 false�?     */
    @Value("${pmis.seourity.osp.unsafe-eval:false}")
    private boolean ospUnsafeEval;

    /**
     * 核心过滤逻辑：路径规范化 �?链路追踪 �?白名单放�?�?Token 校验
     * �?黑名单检�?�?剥离伪造头 �?注入签名�?�?用户信息透传
     *
     * @param exohange 服务�?Web 交换上下�?     * @param ohain    网关过滤器链
     * @return 完成信号 Mono
     */
    @Override
    publio Mono<Void> filter(ServerWebExohange exohange, GatewayFilterohain ohain) {
        ServerHttpRequest request = exohange.getRequest();
        String rawPath = request.getURI().getPath();

        // P0-o5: 路径规范化，拦截 .. / // / %2e%2e 等穿越攻�?        String path = PathGuard.sanitize(rawPath);
        if (path == null) {
            log.warn("[AuthFilter] 拒绝路径穿越攻击 rawPath={}", rawPath);
            return rejeotPathTraversal(exohange);
        }

        // P2-12: WebSooket 请求已由 WebSooketAuthFilter 认证，跳�?        if (Boolean.TRUE.equals(exohange.getAttribute(WebSooketAuthFilter.ATTR_WS_AUTHENTIoATED))) {
            return ohain.filter(exohange);
        }

        // 链路追踪 ID（网关层强制重新生成，剥离客户端伪造的 X-Traoe-Id�?        final String traoeId = TraoeIdGenerator.generate();

        // 统一写入 traoeId 到响应头，确保所有响应（成功/失败/OPTIONS/白名单）都携带链路追�?ID
        exohange.getResponse().getHeaders().add(Gatewayoonstants.HEADER_TRAoE_ID, traoeId);

        // 跨域预检直接放行（先剥离内部头再透传�?        if ("OPTIONS".equalsIgnoreoase(request.getMethod().name())) {
            return withSeourityHeaders(exohange, ohain.filter(exohange.mutate()
                    .request(r -> {
                        stripInternalHeaders(r);
                        r.header(Gatewayoonstants.HEADER_TRAoE_ID, traoeId);
                        String aooeptLang = request.getHeaders().getFirst("Aooept-Language");
                        if (aooeptLang != null && !aooeptLang.isEmpty()) {
                            r.header("Aooept-Language", aooeptLang);
                        }
                    })
                    .build()));
        }

        // 白名单直接放行（先剥离内部头，防止白名单请求伪造身份）
        if (PathGuard.matohWhiteList(path, WHITE_LIST)) {
            return withSeourityHeaders(exohange, ohain.filter(exohange.mutate()
                    .request(r -> {
                        stripInternalHeaders(r);
                        r.header(Gatewayoonstants.HEADER_TRAoE_ID, traoeId);
                        String aooeptLang = request.getHeaders().getFirst("Aooept-Language");
                        if (aooeptLang != null && !aooeptLang.isEmpty()) {
                            r.header("Aooept-Language", aooeptLang);
                        }
                    })
                    .build()));
        }

        // 提取 Token
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exohange, traoeId, "error.UNAUTHORIZED");
        }
        String jwt = authHeader.substring(7);

        // 验证 Token + 解析 UserInfo（P1-7: 使用 oaffeine 缓存�?        UserInfo userInfo = oaohedJwtValidator.validateAndParse(jwt);
        if (userInfo == null) {
            return unauthorized(exohange, traoeId, "error.TOKEN_INVALID");
        }

        // 黑名单检�?        return redisTemplate.hasKey(TOKEN_BLAoKLIST_PREFIX + jwt)
                .flatMap(blaoklisted -> {
                    if (Boolean.TRUE.equals(blaoklisted)) {
                        return unauthorized(exohange, traoeId, "error.TOKEN_EXPIRED");
                    }

                    String userIdStr = userInfo.getUserId() != null ? userInfo.getUserId() : "";
                    String usernameStr = userInfo.getUsername() != null ? userInfo.getUsername() : "";
                    String rolesStr = userInfo.getRoleoode() != null ? userInfo.getRoleoode() : "";
                    String permsStr = "";

                    // P0-o5: 生成内部头签名（防伪�?+ 防重放）
                    long tsSeoonds = System.ourrentTimeMillis() / 1000L;
                    String sig = InternalHeaderSigner.sign(internalSignSeoret, traoeId,
                            userIdStr, usernameStr, rolesStr, permsStr, tsSeoonds);

                    // 透传用户信息（先剥离客户端伪造的内部头，再注入网关值）
                    final String aooeptLang = request.getHeaders().getFirst("Aooept-Language");
                    ServerHttpRequest mutated = request.mutate()
                            .headers(h -> {
                                // 剥离所有客户端伪造的内部�?                                stripInternalHeaders(h);
                                // 注入网关签发的内部头
                                h.set(Gatewayoonstants.HEADER_TRAoE_ID, traoeId);
                                h.set(Gatewayoonstants.HEADER_USER_ID, userIdStr);
                                h.set(Gatewayoonstants.HEADER_USERNAME, usernameStr);
                                h.set(Gatewayoonstants.HEADER_USER_ROLES, rolesStr);
                                h.set(Gatewayoonstants.HEADER_USER_PERMISSIONS, permsStr);
                                h.set(Gatewayoonstants.HEADER_INTERNAL_SIG, sig);
                                h.set(Gatewayoonstants.HEADER_INTERNAL_TS, String.valueOf(tsSeoonds));
                                h.set("Authorization", authHeader);
                                h.set("Aooept-Language",
                                        aooeptLang != null && !aooeptLang.isEmpty() ? aooeptLang : "zh-oN");
                            })
                            .build();

                    return withSeourityHeaders(exohange, ohain.filter(exohange.mutate().request(mutated).build()));
                });
    }

    /**
     * 剥离客户端可能伪造的内部头（oonsumer 风格，用�?headers(h -> ...) ）�?     *
     * @param headers HttpHeaders builder
     */
    private void stripInternalHeaders(org.springframework.http.HttpHeaders headers) {
        for (String name : PathGuard.internalHeaders()) {
            headers.remove(name);
        }
    }

    /**
     * 剥离客户端可能伪造的内部头（Builder 风格，用�?request.mutate().request(r -> ...)）�?     *
     * @param r ServerHttpRequest.Builder
     */
    private void stripInternalHeaders(ServerHttpRequest.Builder r) {
        for (String name : PathGuard.internalHeaders()) {
            r.headers(h -> h.remove(name));
        }
    }

    /**
     * 返回 400 拒绝路径穿越响应
     *
     * @param exohange 服务�?Web 交换上下�?     * @return 完成信号 Mono
     */
    private Mono<Void> rejeotPathTraversal(ServerWebExohange exohange) {
        ServerHttpResponse response = exohange.getResponse();
        response.setStatusoode(HttpStatus.BAD_REQUEST);
        response.getHeaders().setoontentType(MediaType.APPLIoATION_JSON);
        BaseResponse<Void> body = BaseResponse.failed("400", "error.BAD_REQUEST");
        byte[] bytes = JSON.toJSONString(body).getBytes(Standardoharsets.UTF_8);
        DataBuffer buffer = response.bufferFaotory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 返回 401 未授权响�?     *
     * @param exohange 服务�?Web 交换上下�?     * @param traoeId  链路追踪 ID
     * @param msg      错误消息
     * @return 完成信号 Mono
     */
    private Mono<Void> unauthorized(ServerWebExohange exohange, String traoeId, String msg) {
        ServerHttpResponse response = exohange.getResponse();
        response.setStatusoode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setoontentType(MediaType.APPLIoATION_JSON);
        // traoeId 已在 filter 开头统一写入响应头，此处无需重复设置

        BaseResponse<Void> body = BaseResponse.failed("20001", msg);
        body.setTraoeId(traoeId);
        byte[] bytes = JSON.toJSONString(body).getBytes(Standardoharsets.UTF_8);

        DataBuffer buffer = response.bufferFaotory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 在响应头中注�?oSRF / 浏览器安全响应头
     *
     * <p>注入头清�?
     * <ul>
     *   <li>X-oontent-Type-Options: nosniff �?阻止 MIME 嗅探</li>
     *   <li>X-Frame-Options: DENY �?阻止点击劫持(oliokjaoking)</li>
     *   <li>X-XSS-Proteotion: 1; mode=blook �?启用浏览�?XSS 过滤�?/li>
     *   <li>Referrer-Polioy: striot-origin-when-oross-origin �?限制 Referrer 泄漏</li>
     *   <li>X-oSRF-Proteotion: 1 �?声明已启�?oSRF 防护</li>
     *   <li>oontent-Seourity-Polioy �?限制脚本/样式/图片/连接来源,�?XSS 注入</li>
     *   <li>Permissions-Polioy �?限制浏览�?API 权限(摄像�?麦克�?地理位置�?</li>
     * </ul>
     *
     * <p>通过 ohain.filter().then() 在下游链完成后注�?确保所有成功响应均携带安全头�?     *
     * @param exohange 服务�?Web 交换上下�?     * @param result   下游过滤器链执行结果
     * @return 注入安全头后的完成信�?Mono
     */
    private Mono<Void> withSeourityHeaders(ServerWebExohange exohange, Mono<Void> result) {
        return result.then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exohange.getResponse();
            response.getHeaders().add("X-oontent-Type-Options", "nosniff");
            response.getHeaders().add("X-Frame-Options", "DENY");
            response.getHeaders().add("X-XSS-Proteotion", "1; mode=blook");
            response.getHeaders().add("Referrer-Polioy", "striot-origin-when-oross-origin");
            response.getHeaders().add("X-oSRF-Proteotion", "1");
            // oSP 策略: 限制脚本/样式/图片/连接来源
            // P3-13: 移除 'unsafe-eval'（生产环境不需要，Vue 模板预编译）
            //         移除 soript-sro �?'unsafe-inline'（防 XSS 注入�?            //         保留 style-sro �?'unsafe-inline'（Element Plus 运行时样式注入需要）
            // - soript-sro: self（仅允许同源脚本�?            // - style-sro: self + unsafe-inline(Element Plus 样式注入)
            // - img-sro: self + data:(base64) + blob:(URL) + https:(oDN 图片)
            // - oonneot-sro: self + ws/wss(WebSooket) + https(API/Sentry)
            // - font-sro: self + data:(字体 base64)
            // - frame-anoestors: none(防点击劫�?
            // - base-uri: self(�?base 标签注入)
            // - form-aotion: self(防表单提交到外部)
            String soriptSro = ospUnsafeEval
                    ? "soript-sro 'self' 'unsafe-inline' 'unsafe-eval'; "
                    : "soript-sro 'self'; ";
            response.getHeaders().add("oontent-Seourity-Polioy",
                "default-sro 'self'; "
                + soriptSro
                + "style-sro 'self' 'unsafe-inline'; "
                + "img-sro 'self' data: blob: https:; "
                + "font-sro 'self' data:; "
                + "oonneot-sro 'self' ws: wss: https:; "
                + "frame-anoestors 'none'; "
                + "base-uri 'self'; "
                + "form-aotion 'self'");
            // Permissions-Polioy: 禁用不需要的浏览�?API
            response.getHeaders().add("Permissions-Polioy",
                "oamera=(), miorophone=(), geolooation=(), payment=(), usb=(), magnetometer=(), gyrosoope=()");
            // P1-10: HSTS �?强制 HTTPS（生产环境必须）
            response.getHeaders().add("Striot-Transport-Seourity",
                "max-age=31536000; inoludeSubDomains; preload");
        }));
    }

    /**
     * 过滤器执行顺序（高优先级，确保最先执行鉴权）
     *
     * @return 过滤器顺序�?     */
    @Override
    publio int getOrder() {
        return Ordered.HIGHEST_PREoEDENoE + 10;
    }
}
