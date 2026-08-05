package com.remisoft.common.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Remi Core 模块配置属性
 *
 * <p>对应配置前缀 {@code remi.core}，提供分页、上下文、响应等核心能力的运行时参数绑定。
 *
 * <h3>配置示例：</h3>
 * <pre>{@code
 * remi:
 *   core:
 *     page:
 *       max-page-size: 1000
 *       default-page-size: 20
 *     response:
 *       include-timestamp: true
 *       rfc9457:
 *         enabled: false
 *     context:
 *       mdc:
 *         enabled: true
 * }</pre>
 *
 * @author remi-team
 * @since 1.8.0
 */
@ConfigurationProperties(prefix = "remi.core")
public class CoreProperties {

    /**
     * 分页相关配置
     */
    private final Page page = new Page();

    /**
     * 响应相关配置
     */
    private final Response response = new Response();

    /**
     * 请求上下文相关配置
     */
    private final Context context = new Context();

    public Page getPage() {
        return page;
    }

    public Response getResponse() {
        return response;
    }

    public Context getContext() {
        return context;
    }

    /**
     * 分页配置
     */
    public static class Page {

        /**
         * 默认每页记录数
         */
        private int defaultPageSize = 20;

        /**
         * 默认页码
         */
        private int defaultPageNum = 1;

        /**
         * 允许的最大每页记录数（防御深度分页攻击）
         */
        private int maxPageSize = 1000;

        public int getDefaultPageSize() {
            return defaultPageSize;
        }

        public void setDefaultPageSize(int defaultPageSize) {
            this.defaultPageSize = defaultPageSize;
        }

        public int getDefaultPageNum() {
            return defaultPageNum;
        }

        public void setDefaultPageNum(int defaultPageNum) {
            this.defaultPageNum = defaultPageNum;
        }

        public int getMaxPageSize() {
            return maxPageSize;
        }

        public void setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
        }
    }

    /**
     * 响应配置
     */
    public static class Response {

        /**
         * 是否在响应体中自动包含 timestamp（默认 true）
         */
        private boolean includeTimestamp = true;

        /**
         * RFC 9457 Problem Detail 支持
         */
        private final Rfc9457 rfc9457 = new Rfc9457();

        public boolean isIncludeTimestamp() {
            return includeTimestamp;
        }

        public void setIncludeTimestamp(boolean includeTimestamp) {
            this.includeTimestamp = includeTimestamp;
        }

        public Rfc9457 getRfc9457() {
            return rfc9457;
        }

        /**
         * RFC 9457 Problem Detail 配置
         */
        public static class Rfc9457 {

            /**
             * 是否启用 RFC 9457 响应格式（默认 false，保持向后兼容）
             */
            private boolean enabled = false;

            /**
             * Problem Detail 的 type URI 前缀
             */
            private String typeUriPrefix = "https://docs.remisoft.com/problems";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getTypeUriPrefix() {
                return typeUriPrefix;
            }

            public void setTypeUriPrefix(String typeUriPrefix) {
                this.typeUriPrefix = typeUriPrefix;
            }
        }
    }

    /**
     * 请求上下文配置
     */
    public static class Context {

        /**
         * MDC 桥接配置
         */
        private final Mdc mdc = new Mdc();

        public Mdc getMdc() {
            return mdc;
        }

        /**
         * MDC 配置
         */
        public static class Mdc {

            /**
             * 是否启用 RequestContext 与 MDC 的自动桥接
             */
            private boolean enabled = true;

            /**
             * MDC 中 tenantId 的键名
             */
            private String tenantIdKey = "tenantId";

            /**
             * MDC 中 userId 的键名
             */
            private String userIdKey = "userId";

            /**
             * MDC 中 traceId 的键名
             */
            private String traceIdKey = "traceId";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getTenantIdKey() {
                return tenantIdKey;
            }

            public void setTenantIdKey(String tenantIdKey) {
                this.tenantIdKey = tenantIdKey;
            }

            public String getUserIdKey() {
                return userIdKey;
            }

            public void setUserIdKey(String userIdKey) {
                this.userIdKey = userIdKey;
            }

            public String getTraceIdKey() {
                return traceIdKey;
            }

            public void setTraceIdKey(String traceIdKey) {
                this.traceIdKey = traceIdKey;
            }
        }
    }
}
