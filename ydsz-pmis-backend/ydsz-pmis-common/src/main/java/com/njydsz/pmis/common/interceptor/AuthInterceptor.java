package com.njydsz.pmis.common.interceptor;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 鉴权拦截器
 *
 * <p>解析请求头中的 JWT Token，构造 LoginUser 并放入 SecurityContext。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** JWT 签名密钥（明文，来自配置） */
    @Value("${pmis.jwt.secret:pmis-default-jwt-secret-key-please-change-in-production-environment-must-be-256-bits}")
    private String secret;

    /** 签名密钥对象 */
    private SecretKey key;

    /**
     * 初始化签名密钥对象
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 请求预处理：解析 Token 并构造 LoginUser 放入 SecurityContext
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @return true 表示放行
     * @throws BizException 未携带 Token / Token 无效时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            throw new BizException(BizErrorCode.UNAUTHORIZED, "缺少认证 Token");
        }

        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            LoginUser user = buildLoginUser(claims, token);
            SecurityContext.setCurrent(user);
            return true;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Auth] Token 解析失败: {}", e.getMessage());
            throw new BizException(BizErrorCode.TOKEN_INVALID);
        }
    }

    /**
     * 请求完成后清理线程上下文，避免内存泄漏与跨请求串号
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @param ex       请求处理过程中抛出的异常（可为 null）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        SecurityContext.clear();
    }

    /**
     * 从请求中提取 Token，依次尝试 Authorization Bearer / X-Access-Token / query 参数
     *
     * @param request HTTP 请求
     * @return Token 字符串，可能为 null
     */
    private String extractToken(HttpServletRequest request) {
        // 1. 优先从 Authorization 头读取
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        // 2. 从 X-Access-Token 头读取
        String accessToken = request.getHeader("X-Access-Token");
        if (StringUtils.hasText(accessToken)) {
            return accessToken;
        }
        // 3. 从 query 参数读取
        String queryToken = request.getParameter("access_token");
        return queryToken;
    }

    /**
     * 根据 Claims 构造登录用户对象
     *
     * @param claims JWT Claims
     * @param token  原始 Token 字符串
     * @return 登录用户对象
     */
    private LoginUser buildLoginUser(Claims claims, String token) {
        Long userId = Long.parseLong(claims.getSubject());
        String username = (String) claims.get("username");
        Long deptId = claims.get("deptId") != null ? Long.parseLong(claims.get("deptId").toString()) : null;
        String deptName = (String) claims.get("deptName");
        String levelCode = (String) claims.get("levelCode");
        String dataScope = (String) claims.get("dataScope");

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);

        return LoginUser.builder()
                .userId(userId)
                .username(username)
                .deptId(deptId)
                .deptName(deptName)
                .levelCode(levelCode)
                .dataScope(dataScope)
                .roles(roles != null ? roles : List.of())
                .permissions(permissions != null ? permissions : List.of())
                .token(token)
                .loginTime(claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : null)
                .expireTime(claims.getExpiration() != null ? claims.getExpiration().getTime() : null)
                .build();
    }
}
