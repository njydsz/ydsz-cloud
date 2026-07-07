package com.njydsz.pmis.common.interceptor;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 鉴权拦截器
 *
 * <p>解析请求头中的 JWT Token，构造 LoginUser 并放入 SecurityContext。
 * Token 的解析统一委托给 {@link JwtTokenProvider}，避免重复的密钥初始化与解析逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

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
            throw new BizException(BizErrorCode.UNAUTHORIZED, "error.common.msg_4b43f121");
        }

        // 解析 Token，失败统一转为 TOKEN_INVALID
        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(token);
        } catch (Exception e) {
            log.warn("[Auth] Token 解析失败: {}", e.getMessage());
            throw new BizException(BizErrorCode.TOKEN_INVALID);
        }

        // 校验 Token 类型，仅允许 access token 访问业务接口（拒绝 refresh token）
        String tokenType = claims.get("type", String.class);
        if (!"access".equals(tokenType)) {
            log.warn("[Auth] Token 类型非法, 期望 access, 实际: {}", tokenType);
            throw new BizException(BizErrorCode.TOKEN_INVALID);
        }

        // 构造登录用户对象，字段缺失/格式异常同样视为 Token 无效
        LoginUser user;
        try {
            user = buildLoginUser(claims, token);
        } catch (Exception e) {
            log.warn("[Auth] LoginUser 构造失败: {}", e.getMessage());
            throw new BizException(BizErrorCode.TOKEN_INVALID);
        }
        SecurityContext.setCurrent(user);
        return true;
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
     * <p>P3-1：userId / deptId 已统一为雪花算法字符串（VARCHAR(20)），
     * JWT 中以字符串形式承载 subject 与 deptId claim，避免大数精度丢失。
     *
     * @param claims JWT Claims
     * @param token  原始 Token 字符串
     * @return 登录用户对象
     */
    private LoginUser buildLoginUser(Claims claims, String token) {
        String userId = claims.getSubject();
        String username = (String) claims.get("username");
        String deptId = claims.get("deptId") != null ? claims.get("deptId").toString() : null;
        String deptName = (String) claims.get("deptName");
        String levelCode = (String) claims.get("levelCode");
        String dataScope = (String) claims.get("dataScope");

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);

        // P1-6 修复：解析 customDeptIds（CUSTOM 模式）和 deptIds（DEPT_AND_CHILD 模式）
        // P3-1：部门 ID 已统一为雪花字符串，改为 parseStringList
        List<String> customDeptIds = parseStringList(claims.get("customDeptIds"));
        List<String> deptIds = parseStringList(claims.get("deptIds"));

        return LoginUser.builder()
                .userId(userId)
                .username(username)
                .deptId(deptId)
                .deptName(deptName)
                .levelCode(levelCode)
                .dataScope(dataScope)
                .roles(roles != null ? roles : List.of())
                .permissions(permissions != null ? permissions : List.of())
                .customDeptIds(customDeptIds)
                .deptIds(deptIds)
                .token(token)
                .loginTime(claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : null)
                .expireTime(claims.getExpiration() != null ? claims.getExpiration().getTime() : null)
                .build();
    }

    /**
     * 从 JWT claim 解析 String 列表
     *
     * <p>JWT 中 List 字段反序列化后通常为 {@code List<?>}，元素可能是任意类型；
     * 统一调用 {@code toString()} 得到部门 ID 字符串，避免 ClassCastException。
     *
     * @param claim JWT claim 值
     * @return String 列表，null 时返回 null
     */
    private List<String> parseStringList(Object claim) {
        if (claim == null) {
            return null;
        }
        if (claim instanceof List<?> list) {
            return list.stream()
                    .filter(o -> o != null)
                    .map(Object::toString)
                    .toList();
        }
        return null;
    }
}
