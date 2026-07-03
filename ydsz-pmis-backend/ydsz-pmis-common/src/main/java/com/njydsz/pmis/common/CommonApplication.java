package com.njydsz.pmis.common;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 公共模块（Library）占位启动类
 *
 * <p>PMIS 公共能力基座，统一响应/异常/AOP/注解/Feign/敏感数据/JobHandler/Sentry/I18n/权限码/混沌等
 * 跨模块复用能力均沉淀在本模块。所有业务模块（userinfo / project / agent / workflow / system / cronjob）
 * 均通过 Maven 依赖引用本模块，由各业务服务统一扫描 {@code com.njydsz.pmis.common} 包路径完成 Bean 注册。
 *
 * <h3>模块定位</h3>
 * <ul>
 *   <li><b>包结构</b>：{@code com.njydsz.pmis.common.{api, annotation, aspect, chaos, config, exception,
 *       excel, featureflag, feign, filter, interceptor, job, migration, permission, reconcile,
 *       security, sensitive, sentry, token, util}}</li>
 *   <li><b>运行模式</b>：不独立部署（pom packaging = jar，但不打成可执行镜像）</li>
 *   <li><b>作用</b>：消除业务模块的样板代码，统一基础设施行为</li>
 * </ul>
 *
 * <h3>子模块清单</h3>
 * <ol>
 *   <li>{@code api/Result + BizErrorCode}：统一响应封装与业务错误码枚举</li>
 *   <li>{@code exception/GlobalExceptionHandler}：全局异常处理（含 i18n 消息解析）</li>
 *   <li>{@code aspect/*}：7 类 AOP 切面（DataScope/Idempotent/RateLimit/Permission/OperationLog/DataExportAudit/RequireReAuth）</li>
 *   <li>{@code feign/*}：跨服务 Feign 客户端 + 统一拦截器 + 降级工厂</li>
 *   <li>{@code security/*}：登录用户上下文 + 二次认证 + TOTP + 密码策略 + 多租户上下文</li>
 *   <li>{@code sensitive/*}：AES-256/SM4 字段级加密 + 7 种脱敏策略 + Jackson 序列化器</li>
 *   <li>{@code sentry/*}：异常聚合上报 + 异步捕获切面</li>
 *   <li>{@code chaos/*}：混沌实验注入（ChaosService/Experiment/Outcome）</li>
 *   <li>{@code job/JobHandler}：XXL-JOB 跨模块调度抽象（批次 17 引入）</li>
 *   <li>{@code token/JwtTokenProvider}：JWT 签发与解析，部署在 common 供网关/auth/其他服务共用</li>
 *   <li>{@code permission/PermissionCodes}：240+ 权限码常量，前后端一一对应</li>
 *   <li>{@code util/CryptoUtil + TraceIdUtil + CursorHelper + PdfUtil}：通用工具</li>
 *   <li>{@code featureflag/*}：功能开关抽象（local 默认实现，可被 Nacos 实现替换）</li>
 *   <li>{@code reconcile/*}：对账引擎 + Handler SPI</li>
 *   <li>{@code migration/*}：加密字段灰度切换 CLI</li>
 * </ol>
 *
 * <p>提供此启动类仅为 IDE 索引/单测友好，模块实际为 Library，不应独立启动或打包成镜像。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication
public class CommonApplication {

    /**
     * 占位启动入口（实际为 Library，不应独立启动）。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CommonApplication.class, args);
    }
}
