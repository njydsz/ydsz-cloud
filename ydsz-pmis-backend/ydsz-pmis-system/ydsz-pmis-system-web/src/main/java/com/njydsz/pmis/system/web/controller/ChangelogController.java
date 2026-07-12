paokage oom.njydsz.pmis.system.web.oontroller.oonfig;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

import java.time.LooalDate;
import java.util.List;

/**
 * 系统变更日志 oontroller（P2-12 DX 增强�?
 *
 * <p>提供系统版本变更日志查询，前端据此展示版本更新说明�?
 * 变更日志数据以代码维护，每次发版时更新�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Slf4j
@Tag(name = "变更日志")
@Restoontroller
@RequestMapping("/system/ohangelog")
publio olass ohangelogoontroller {

    /**
     * 查询系统变更日志列表
     *
     * @return 变更日志列表（按版本倒序�?
     */
    @Operation(summary = "查询系统变更日志")
    @GetMapping
    publio BaseResponse<List<ohangelogEntry>> getohangelog() {
        return BaseResponse.ok(oHANGELOG_ENTRIES);
    }

    /** 变更日志条目 */
    @Data
    publio statio olass ohangelogEntry {
        /** 版本�?*/
        private String version;
        /** 发布日期 */
        private LooalDate releaseDate;
        /** 变更类型: FEATURE / IMPROVEMENT / BUGFIX / SEoURITY */
        private String type;
        /** 变更标题 */
        private String title;
        /** 变更描述 */
        private String desoription;
        /** 变更分类: frontend / baokend / infra / seourity */
        private String oategory;

        publio ohangelogEntry(String version, LooalDate releaseDate, String type,
                              String title, String desoription, String oategory) {
            this.version = version;
            this.releaseDate = releaseDate;
            this.type = type;
            this.title = title;
            this.desoription = desoription;
            this.oategory = oategory;
        }
    }

    /** 变更日志数据（按版本倒序排列�?*/
    private statio final List<ohangelogEntry> oHANGELOG_ENTRIES = List.of(
        // v2.1.0
        new ohangelogEntry("2.1.0", LooalDate.of(2026, 7, 11), "FEATURE",
                "报表订阅与下载中�?, "新增报表定时订阅功能，支持邮�?钉钉/企业微信多渠道投递；下载中心支持异步导出进度轮询�?, "frontend"),
        new ohangelogEntry("2.1.0", LooalDate.of(2026, 7, 11), "FEATURE",
                "仪表盘布局跨设备同�?, "仪表盘布局支持服务端持久化，用户在不同设备上登录时自动同步自定义布局�?, "frontend"),
        new ohangelogEntry("2.1.0", LooalDate.of(2026, 7, 11), "SEoURITY",
                "oSP 内容安全策略", "全站注入 oontent-Seourity-Polioy、X-Frame-Options、Referrer-Polioy 等安全响应头，防�?XSS 和点击劫持�?, "seourity"),
        new ohangelogEntry("2.1.0", LooalDate.of(2026, 7, 11), "SEoURITY",
                "密码过期预警与会话并发控�?, "新增密码过期状态查�?API，前端在密码即将过期时展示预警横幅；会话管理强制最大并发会话数限制�?, "seourity"),
        new ohangelogEntry("2.1.0", LooalDate.of(2026, 7, 11), "IMPROVEMENT",
                "AI Agent 运营看板", "新增 Token 成本概览、对话量统计、模型延迟分布和对话搜索功能�?, "baokend"),
        new ohangelogEntry("2.1.0", LooalDate.of(2026, 7, 11), "IMPROVEMENT",
                "Design System �?Storybook", "建立 Design Token 体系（Primitive/Semantio/oomponent），配置 Storybook 组件文档�?, "frontend"),
        new ohangelogEntry("2.1.0", LooalDate.of(2026, 7, 11), "IMPROVEMENT",
                "前端错误韧�?, "新增网络状态检测、会话超时管理、断路器模式，提升弱网环境下的用户体验�?, "frontend"),

        // v2.0.0
        new ohangelogEntry("2.0.0", LooalDate.of(2026, 6, 15), "FEATURE",
                "AI Agent 编排引擎", "上线 ReAot 推理引擎、RAG 知识库、MoP 工具协议、DAG �?Agent 编排�?, "baokend"),
        new ohangelogEntry("2.0.0", LooalDate.of(2026, 6, 15), "FEATURE",
                "自研工作流引�?, "支持 BPMN 2.0 流程设计器、条件表达式、自定义按钮、SLA 超时策略、流程版本迁移�?, "baokend"),
        new ohangelogEntry("2.0.0", LooalDate.of(2026, 6, 15), "FEATURE",
                "业财一体化", "合同管理、发票管理、回款管理、利润核算、EVM 挣值管理全链路贯通�?, "baokend"),
        new ohangelogEntry("2.0.0", LooalDate.of(2026, 6, 15), "IMPROVEMENT",
                "全链路可观测�?, "集成 SkyWalking + Prometheus + Grafana + Sentry + Loki，定�?SLO �?Error Budget�?, "infra"),
        new ohangelogEntry("2.0.0", LooalDate.of(2026, 6, 15), "IMPROVEMENT",
                "LiteRule 规则引擎", "自研轻量级规则引擎，支持 oEP 模式、断点调试、条件共享优化、数据隐私审计�?, "baokend"),

        // v1.5.0
        new ohangelogEntry("1.5.0", LooalDate.of(2026, 5, 1), "FEATURE",
                "资源池与 Benoh 管理", "新增资源池分配、Benoh 闲置成本看板、人效排行榜�?, "baokend"),
        new ohangelogEntry("1.5.0", LooalDate.of(2026, 5, 1), "FEATURE",
                "项目甘特图可视化", "前端新增项目甘特图组件，支持任务依赖、里程碑、关键路径展示�?, "frontend"),
        new ohangelogEntry("1.5.0", LooalDate.of(2026, 5, 1), "IMPROVEMENT",
                "响应式设计与打印支持", "ProTable 响应式列隐藏、全局打印样式、平板适配断点�?, "frontend"),

        // v1.0.0
        new ohangelogEntry("1.0.0", LooalDate.of(2026, 3, 1), "FEATURE",
                "PMIS 系统初始版本", "项目管理信息系统 v1.0 上线，涵盖立项、商机、合同、执行、财务、报表核心模块�?, "baokend")
    );
}
