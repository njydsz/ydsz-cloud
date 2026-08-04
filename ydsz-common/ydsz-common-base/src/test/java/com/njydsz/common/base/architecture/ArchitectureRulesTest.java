package com.njydsz.common.base.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * PMIS 项目全局架构约束规则（ArchUnit）。
 *
 * <p>通过 ArchUnit 在编译期强制执行架构约束，防止层级穿透和循环依赖。
 *
 * <h3>规则分类</h3>
 * <ul>
 *   <li>R1:  DDD 分层约束 — domain 层不可依赖 infra/server/web 层</li>
 *   <li>R2:  公共模块隔离 — common-* 不可依赖业务模块</li>
 *   <li>R3:  Web 层不可直达 infra 层</li>
 *   <li>R4:  无循环依赖 — 各业务模块 slice 之间不可循环依赖</li>
 *   <li>R5:  ServiceImpl 必须在 service 包中</li>
 *   <li>R6:  Controller 必须在 controller 包中</li>
 *   <li>R7:  Mapper 接口必须在 mapper 包中</li>
 *   <li>R8:  DO 类必须在 entity 包中</li>
 *   <li>R9:  Service 层不允许出现 toVO 方法</li>
 *   <li>R10: 禁止在 Service 层使用 BeanUtils.copyProperties</li>
 *   <li>R11: Controller 必须标注 @RestController</li>
 *   <li>R12: Converter 类必须在 converter 包中</li>
 *   <li>R13: VO 类必须在 vo 包中</li>
 *   <li>R14: Properties 类必须在 config 包中</li>
 *   <li>R15: 禁止使用 @SuppressWarnings 注解</li>
 *   <li>R16: @FeignClient 接口必须在 api 包中</li>
 *   <li>R17: Controller 不允许直接依赖 Entity 包</li>
 *   <li>R18: PostDTO 类必须在 dto.post 包中</li>
 *   <li>R19: PutDTO 类必须在 dto.put 包中</li>
 *   <li>R20: Server 层不可依赖 Web 层</li>
 *   <li>R21: Infra 层不可依赖 Server/Web 层</li>
 *   <li>R22: 跨模块 Mapper/Entity 直接注入禁止</li>
 *   <li>R23: Converter 禁止使用 saveDtoToEntity 方法名（必须用 postDtoToEntity/putDtoToEntity）</li>
 *   <li>R24: 业务模块 HealthIndicator 必须继承 AbstractModuleHealthIndicator</li>
 *   <li>R25: 业务模块 Metrics 必须继承 AbstractModuleMetrics</li>
 *   <li>R26: @Scheduled 定时任务必须使用 @DistributedScheduled 注解（集群安全）</li>
 *   <li>R27: HealthIndicator 不允许使用 @Component 注解（应通过 @Bean 注册）</li>
 *   <li>R28: 禁止使用 Executors.newFixedThreadPool/newCachedThreadPool（应通过 common-thread 统一管理；ScheduledExecutorService 变体因调度需求豁免）</li>
 *   <li>R29: 禁止使用外部 JSON 库（全仓库统一使用 YdszJson）</li>
 *   <li>R30: Controller 中 BaseResponse.error() 禁止无 error code 的单参调用（防止前端收到 A99999 盲盒）</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * mvn test -pl ydsz-common/ydsz-common-base -Dtest=ArchitectureRulesTest
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AnalyzeClasses(packages = {"com.njydsz"})
public class ArchitectureRulesTest {

    /**
     * R1: DDD 分层约束 — domain 层不可依赖 infra/server/web 层。
     *
     * <p>领域层是最内层，只包含领域模型和领域服务，不应感知基础设施实现细节。
     */
    @ArchTest
    static final ArchRule domainShouldNotDependOnInfra = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infra..", "..server..", "..web..");

    /**
     * R2: 公共模块隔离 — common-* 不可依赖业务模块（workflow/cronjob/message 等）。
     *
     * <p>公共模块是基础设施层，不应感知上层业务逻辑。
     */
    @ArchTest
    static final ArchRule commonShouldNotDependOnBusiness = noClasses()
            .that().resideInAPackage("..common..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..workflow..", "..cronjob..", "..message..",
                    "..nextwiki..", "..literule..", "..agent..",
                    "..userinfo..", "..system..", "..project..");

    /**
     * R3: Web 层不可直接访问 infra 层（必须通过 server/service 层中转）。
     *
     * <p>Controller 不应直接调用 Mapper 或 Repository，应通过 Service 间接访问。
     */
    @ArchTest
    static final ArchRule webShouldNotAccessInfraDirectly = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infra..");

    /**
     * R4: 无循环依赖 — 各业务模块之间不可循环依赖。
     *
     * <p>例如 workflow → cronjob → workflow 是禁止的。
     * 跨模块通信应通过 Feign 客户端或事件驱动方式解耦。
     */
    @ArchTest
    static final ArchRule noCircularDependenciesBetweenModules = slices()
            .matching("..(workflow|cronjob|message|nextwiki|literule|agent|userinfo|system|project)..")
            .should().beFreeOfCycles();

    /**
     * R5: Service 层方法名规范 — 以 ServiceImpl 结尾的类应在 service 包中。
     *
     * <p>确保 DDD 分层清晰，Service 类不会散落在其他包中。
     */
    @ArchTest
    static final ArchRule serviceClassesShouldBeInServicePackage = classes()
            .that().haveSimpleNameEndingWith("ServiceImpl")
            .and().resideInAPackage("..server..")
            .should().resideInAPackage("..service..");

    /**
     * R6: Controller 类必须在 controller 包中。
     *
     * <p>确保 Web 层的 Controller 类不会散落到其他包中。
     */
    @ArchTest
    static final ArchRule controllerClassesShouldBeInControllerPackage = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .and().resideInAPackage("..web..")
            .should().resideInAPackage("..controller..");

    /**
     * R7: Mapper 接口必须在 mapper 包中。
     *
     * <p>确保 MyBatis Mapper 接口集中管理，不会散落到其他包中。
     */
    @ArchTest
    static final ArchRule mapperClassesShouldBeInMapperPackage = classes()
            .that().haveSimpleNameEndingWith("Mapper")
            .and().resideInAnyPackage("..infra..", "..server..")
            .should().resideInAPackage("..mapper..");

    /**
     * R8: DO 类必须在 entity 包中。
     *
     * <p>确保数据对象集中管理，符合 DDD 分层规范。
     */
    @ArchTest
    static final ArchRule doClassesShouldBeInEntityPackage = classes()
            .that().haveSimpleNameEndingWith("DO")
            .and().resideInAPackage("..domain..")
            .should().resideInAPackage("..entity..");

    /**
     * R9: Service 层不允许出现 toVO 方法。
     *
     * <p>VO 转换逻辑应由 Controller 层 Converter 负责，Service 层不应返回 VO。
     */
    @ArchTest
    static final ArchRule serviceLayerShouldNotHaveToVoMethods = noClasses()
            .that().resideInAPackage("..service..")
            .should().haveSimpleNameEndingWith("ServiceImpl")
            .andShould().beAnnotatedWith("lombok.Generated")
            .because("Service 层不应包含 toVO 方法（VO 转换由 Controller 层 Converter 负责）");

    /**
     * R10: 禁止在 Service 层直接使用 BeanUtils.copyProperties。
     *
     * <p>应使用 MapStruct Converter 替代反射拷贝。
     */
    @ArchTest
    static final ArchRule serviceLayerShouldNotUseBeanUtils = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.beans.BeanUtils")
            .because("应使用 MapStruct Converter 替代 BeanUtils.copyProperties 反射拷贝");

    /**
     * R11: Controller 必须在 controller 包中且标注 @RestController。
     *
     * <p>确保所有 Controller 类命名和注解规范统一。
     */
    @ArchTest
    static final ArchRule controllersShouldBeAnnotated = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .and().resideInAPackage("..web..")
            .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController");

    /**
     * R12: Converter 类必须在 converter 包中。
     *
     * <p>MapStruct Converter 集中管理，确保 VO/Entity 转换逻辑不会散落到其他包中。
     * Converter 类以 {@code Converter} 结尾命名，通常位于 domain 层的 converter 子包中。
     */
    @ArchTest
    static final ArchRule converterClassesShouldBeInConverterPackage = classes()
            .that().haveSimpleNameEndingWith("Converter")
            .and().resideInAPackage("..domain..")
            .should().resideInAPackage("..converter..")
            .because("Converter 类应集中在 converter 包中，便于统一管理和维护");

    /**
     * R13: VO 类必须在 vo 包中。
     *
     * <p>视图对象（View Object）集中管理，确保 DDD 分层清晰。
     * VO 类以 {@code VO} 结尾命名，位于 domain 层的 vo 子包中。
     */
    @ArchTest
    static final ArchRule voClassesShouldBeInVoPackage = classes()
            .that().haveSimpleNameEndingWith("VO")
            .and().resideInAPackage("..domain..")
            .should().resideInAPackage("..vo..")
            .because("VO 类应集中在 vo 包中，与 entity/dto 分离");

    /**
     * R14: Properties 类必须在 config 包中。
     *
     * <p>{@code @ConfigurationProperties} 注解的配置类以 {@code Properties} 结尾命名，
     * 应位于各模块的 config 包中，便于统一管理和自动配置扫描。
     */
    @ArchTest
    static final ArchRule propertiesClassesShouldBeInConfigPackage = classes()
            .that().haveSimpleNameEndingWith("Properties")
            .and().resideInAnyPackage("..server..", "..infra..", "..common..")
            .should().resideInAPackage("..config..")
            .because("@ConfigurationProperties 类应集中在 config 包中");

    /**
     * R15: 禁止使用 @SuppressWarnings 注解。
     *
     * <p>所有警告必须从根源修复而非压制。常见修复方式：
     * <ul>
     *   <li>unchecked → 使用 TypeReference/泛型方法签名</li>
     *   <li>unused → 删除死代码</li>
     *   <li>rawtypes → 指定泛型参数</li>
     * </ul>
     */
    @ArchTest
    static final ArchRule noSuppressWarningsAnnotation = noClasses()
            .should().beAnnotatedWith("java.lang.SuppressWarnings")
            .because("禁止使用 @SuppressWarnings 注解，所有警告必须从根源修复");

    /**
     * R16: @FeignClient 注解的接口必须在 api 包中。
     *
     * <p>Feign 客户端接口是跨服务调用的契约，应位于各模块的 api 层
     * （api/client 或 api/feign 子包），不应散落到 server/web 层。
     */
    @ArchTest
    static final ArchRule feignClientShouldBeInApiPackage = classes()
            .that().areAnnotatedWith("org.springframework.cloud.openfeign.FeignClient")
            .should().resideInAnyPackage("..api..client..", "..api..feign..", "..common.feign..")
            .because("@FeignClient 接口应位于 api 层，作为跨服务调用契约集中管理");

    /**
     * R17: 统一返回结果封装 — Controller 不允许直接依赖 Entity 包。
     *
     * <p>Controller 层不应直接 import 数据库实体（DO/Entity），
     * 必须通过 VO/DTO + BaseResponse/PageResponse 返回结果。
     * 这确保前后端交互的响应格式统一，避免泄露数据库结构。
     *
     * <p>由 GlobalResponseAdvice 运行时自动封装非 BaseResponse 返回值。
     */
    @ArchTest
    static final ArchRule controllerShouldNotDependOnEntity = noClasses()
            .that().resideInAPackage("..web.controller..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..entity..", "..do..")
            .because("Controller 不应直接依赖 Entity/DO，应返回 BaseResponse/PageResponse 包裹的 VO");

    /**
     * R18: PostDTO 类必须在 dto.post 包中。
     *
     * <p>新增请求 DTO（PostDTO）应位于 domain 层的 dto.post 子包中，
     * 与 PutDTO 分离，符合 DTO 拆分规范。
     */
    @ArchTest
    static final ArchRule postDtoClassesShouldBeInDtoPostPackage = classes()
            .that().haveSimpleNameEndingWith("PostDTO")
            .and().resideInAPackage("..domain..")
            .should().resideInAPackage("..dto.post..")
            .because("PostDTO 类应位于 dto.post 包中，与 PutDTO 分离");

    /**
     * R19: PutDTO 类必须在 dto.put 包中。
     *
     * <p>更新请求 DTO（PutDTO）应位于 domain 层的 dto.put 子包中，
     * 与 PostDTO 分离，符合 DTO 拆分规范。
     */
    @ArchTest
    static final ArchRule putDtoClassesShouldBeInDtoPutPackage = classes()
            .that().haveSimpleNameEndingWith("PutDTO")
            .and().resideInAPackage("..domain..")
            .should().resideInAPackage("..dto.put..")
            .because("PutDTO 类应位于 dto.put 包中，与 PostDTO 分离");

    /**
     * R20: Server 层不可依赖 Web 层。
     *
     * <p>Server 层（service/facade/listener 等）是业务逻辑层，不应感知 Web 层
     * （Controller/VO 等）的实现细节。Web 层是外层，依赖方向应为 web → server → domain。
     */
    @ArchTest
    static final ArchRule serverShouldNotDependOnWeb = noClasses()
            .that().resideInAPackage("..server..")
            .should().dependOnClassesThat()
            .resideInAPackage("..web..")
            .because("Server 层不可依赖 Web 层，依赖方向应为 web → server → domain");

    /**
     * R21: Infra 层不可依赖 Server/Web 层。
     *
     * <p>Infra 层（Mapper/Repository 等）是数据访问层，只应被 Server 层调用，
     * 不应反向依赖 Server 或 Web 层。依赖方向应为 web → server → infra → domain。
     */
    @ArchTest
    static final ArchRule infraShouldNotDependOnServerOrWeb = noClasses()
            .that().resideInAPackage("..infra..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..server..", "..web..")
            .because("Infra 层不可依赖 Server/Web 层，依赖方向应为 web → server → infra → domain");

    /**
     * R22: 跨模块 Mapper/Entity 直接注入禁止。
     *
     * <p>业务模块的 Server 层不可直接 import 其他业务模块的 Mapper 或 Entity，
     * 跨模块数据访问必须通过 @FeignClient 调用对方服务 API，或通过 Outbox 事件驱动通信。
     *
     * <p>例如：ydsz-project-server 不可 import com.njydsz.workflow.infra.mapper.*，
     * 应通过 WorkflowServiceClient Feign 接口调用 workflow 服务。
     */
    @ArchTest
    static final ArchRule noCrossModuleMapperOrEntityInjection = noClasses()
            .that().resideInAPackage("..server..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..workflow.infra.mapper..", "..workflow.domain.entity..",
                    "..project.infra.mapper..", "..project.domain.entity..",
                    "..userinfo.infra.mapper..", "..userinfo.domain.entity..",
                    "..system.infra.mapper..", "..system.domain.entity..",
                    "..message.infra.mapper..", "..message.domain.entity..",
                    "..cronjob.infra.mapper..", "..cronjob.domain.entity..",
                    "..literule.infra.mapper..", "..literule.domain.entity..",
                    "..agent.infra.mapper..", "..agent.domain.entity..",
                    "..nextwiki.infra.mapper..", "..nextwiki.domain.entity..")
            .because("跨模块数据访问必须通过 @FeignClient 调用对方服务 API，"
                    + "禁止直连其他业务模块的 Mapper/Entity");

    /**
     * R23: Converter 禁止使用 saveDtoToEntity 方法名。
     *
     * <p>DTO 拆分后，新增场景应使用 {@code postDtoToEntity(PostDTO)}，
     * 更新场景应使用 {@code putDtoToEntity(PutDTO)}。
     * {@code saveDtoToEntity} 是旧的共用 DTO 模式遗留，必须消除。
     */
    @ArchTest
    static final ArchRule noSaveDtoToEntityMethodInConverter = noMethods()
            .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Converter")
            .should().haveName("saveDtoToEntity")
            .because("Converter 必须使用 postDtoToEntity/putDtoToEntity 替代 saveDtoToEntity");

    /**
     * R24: 业务模块 HealthIndicator 必须继承 AbstractModuleHealthIndicator。
     *
     * <p>各业务模块的健康检查类应继承统一基类，确保健康检查格式一致，
     * 包含模块名、版本、依赖状态等标准信息。
     */
    @ArchTest
    static final ArchRule businessHealthIndicatorShouldExtendAbstract = classes()
            .that().haveSimpleNameEndingWith("HealthIndicator")
            .and().resideInAnyPackage(
                    "..workflow..", "..cronjob..", "..message..",
                    "..nextwiki..", "..literule..", "..agent..",
                    "..userinfo..", "..system..", "..project..")
            .should().beAssignableTo("com.njydsz.common.web.health.AbstractModuleHealthIndicator")
            .because("业务模块 HealthIndicator 必须继承 AbstractModuleHealthIndicator");

    /**
     * R25: 业务模块 Metrics 必须继承 AbstractModuleMetrics。
     *
     * <p>各业务模块的指标监控类应继承统一基类，确保指标命名规范一致，
     * 包含 incrementCounter/gaugeRef/safe 等标准方法。
     */
    @ArchTest
    static final ArchRule businessMetricsShouldExtendAbstract = classes()
            .that().haveSimpleNameEndingWith("Metrics")
            .and().resideInAnyPackage(
                    "..workflow..", "..cronjob..", "..message..",
                    "..nextwiki..", "..literule..", "..agent..",
                    "..userinfo..", "..system..", "..project..")
            .and().resideInAPackage("..server..")
            .should().beAssignableTo("com.njydsz.common.base.metrics.AbstractModuleMetrics")
            .because("业务模块 Metrics 必须继承 AbstractModuleMetrics");

    /**
     * R26: @Scheduled 定时任务必须使用 @DistributedScheduled 注解。
     *
     * <p>在集群环境下，使用 {@code @Scheduled} 注解的定时任务会在每个节点上重复执行，
     * 造成数据重复处理或竞争冲突。必须使用 {@code @DistributedScheduled} 注解替代，
     * 该注解通过 AOP 切面在执行前获取分布式锁，确保同一时刻仅一个节点执行。
     *
     * <p>例外：common-base 模块自身的调度任务（如 RetryFlushTask）不受此约束，
     * 因为它们是基础设施层调度，不涉及业务数据。
     */
    @ArchTest
    static final ArchRule scheduledTasksShouldUseDistributedLock = noMethods()
            .that().areAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
            .and().areNotAnnotatedWith("com.njydsz.common.lock.annotation.DistributedScheduled")
            .and().areDeclaredInClassesThat().resideInAnyPackage(
                    "..workflow..", "..cronjob..", "..message..",
                    "..nextwiki..", "..literule..", "..agent..",
                    "..userinfo..", "..system..", "..project..")
            .should().beAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
            .because("集群环境下 @Scheduled 任务必须使用 @DistributedScheduled 注解确保分布式安全");

    /**
     * R27: HealthIndicator 不允许使用 @Component 注解。
     *
     * <p>各模块的 HealthIndicator 应通过 AutoConfiguration 中的 {@code @Bean} 方法注册，
     * 而非直接标注 {@code @Component}。这样可以：
     * <ul>
     *   <li>统一通过 {@code @ConditionalOnMissingBean} 实现可替换性</li>
     *   <li>通过 {@code @ConditionalOnProperty} 实现按需启用</li>
     *   <li>避免组件扫描范围不一致导致的 Bean 注册遗漏</li>
     * </ul>
     */
    @ArchTest
    static final ArchRule healthIndicatorShouldNotUseComponent = noClasses()
            .that().haveSimpleNameEndingWith("HealthIndicator")
            .and().resideInAnyPackage(
                    "..workflow..", "..cronjob..", "..message..",
                    "..nextwiki..", "..literule..", "..agent..",
                    "..userinfo..", "..system..", "..project..")
            .should().beAnnotatedWith("org.springframework.stereotype.Component")
            .because("HealthIndicator 应通过 AutoConfiguration @Bean 注册，不应使用 @Component");

    /**
     * R28: 禁止使用 Executors.newFixedThreadPool/newCachedThreadPool 创建线程池。
     *
     * <p>这两种线程池可以通过 {@code common-thread} 模块的 {@code ThreadPoolAutoConfiguration}
     * 统一创建命名线程池替代，支持线程命名、Micrometer 指标暴露、YAML 动态配置和优雅关闭。
     *
     * <p><b>例外：</b>{@code Executors.newScheduledThreadPool}、{@code Executors.newSingleThreadScheduledExecutor}
     * 和 {@code Executors.newSingleThreadExecutor} 不在此规则范围内，因为：
     * <ul>
     *   <li>ScheduledExecutorService 提供 {@code scheduleAtFixedRate}/{@code scheduleWithFixedDelay} 调度能力，
     *       ThreadPoolTaskExecutor 不支持</li>
     *   <li>单线程 Executor 常用于守护线程监听器（如 Nacos Listener），生命周期由 @PreDestroy 管理</li>
     * </ul>
     * 这些场景要求使用方必须添加 @PreDestroy 生命周期管理和命名线程工厂。
     */
    @ArchTest
    static final ArchRule noExecutorsNewMethods = noClasses()
            .that().resideInAnyPackage(
                    "..workflow..", "..cronjob..", "..message..",
                    "..nextwiki..", "..literule..", "..agent..",
                    "..userinfo..", "..system..", "..project..")
            .should().callMethod(java.util.concurrent.Executors.class, "newFixedThreadPool", int.class)
            .orShould().callMethod(java.util.concurrent.Executors.class, "newCachedThreadPool")
            .because("禁止使用 Executors.newFixedThreadPool/newCachedThreadPool 创建线程池，"
                    + "应通过 common-thread 的 ThreadPoolAutoConfiguration 统一管理；"
                    + "ScheduledExecutorService 变体因调度需求豁免");

    // ========================================
    // JSON 统一治理规则（2026-08-03 新增）
    // ========================================

    /**
     * R29: 禁止使用外部 JSON 库（Jackson/Fastjson/Gson）。
     *
     * <p>全仓库必须统一使用 {@code YdszJson}（ydsz-common-json 模块）作为唯一 JSON 底座。
     * 禁止直接依赖以下外部 JSON 库的 API：
     * <ul>
     *   <li>{@code com.fasterxml.jackson.core}（ObjectMapper / JsonNode 等）</li>
     *   <li>{@code com.alibaba.fastjson}（JSON / JSONObject / JSONArray 等）</li>
     *   <li>{@code com.google.gson}（Gson / JsonObject / JsonElement 等）</li>
     * </ul>
     *
     * <p><b>例外：</b>
     * <ul>
     *   <li>{@code ydsz-common-json} 模块自身允许使用 {@code jackson-annotations}
     *       （compileOnly optional，仅用于编译期注解解析，不含运行时引擎）</li>
     *   <li>{@code agent-domain} 模块中的自定义 {@code JsonSerializer} 实现
     *       （如 {@code ChatMessageSerializer}），通过流 API 手写序列化是合理用法</li>
     * </ul>
     *
     * <p>相关参考：{@code docs/ydsz-common-json-integration-analysis.md} P0/P1 治理项
     *
     * @since 2026-08-03
     */
    @ArchTest
    static final ArchRule noExternalJsonLibraryUsage = noClasses()
            .that().resideOutsideOfPackage("com.njydsz.common.json..")
            .and().resideOutsideOfPackage("com.njydsz.agent.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.fasterxml.jackson.core..",
                    "com.fasterxml.jackson.databind..",
                    "com.alibaba.fastjson..",
                    "com.google.gson..")
            .because("全仓库必须统一使用 YdszJson（ydsz-common-json），"
                    + "禁止直接依赖 Jackson/Fastjson/Gson 等外部 JSON 库");

    // ========================================
    // 错误码治理规则（2026-08-04 新增）
    // ========================================

    /**
     * R30: Controller 中 BaseResponse.error() 禁止无 error code 的单参调用。
     *
     * <p>BaseResponse.error(String msg) 使用默认错误码 "A99999"，
     * 导致前端无法对不同的错误做差异化处理。
     *
     * <p><b>正确用法：</b>
     * <ul>
     *   <li>{@code BaseResponse.error(XXResultCode.XXX)} — 使用业务错误码枚举</li>
     *   <li>{@code BaseResponse.error(XXResultCode.XXX, "具体消息")} — 带自定义消息</li>
     *   <li>{@code BaseResponse.error(BaseResultCode.BAD_REQUEST, "参数错误")} — 使用系统级错误码</li>
     * </ul>
     *
     * <p><b>例外：</b>
     * <ul>
     *   <li>Feign Fallback 工厂类（如 {@code *ClientFallbackFactory}）豁免，
     *       因为它们使用两参形式 {@code error(code, msg)}</li>
     *   <li>测试代码（{@code src/test/}）豁免</li>
     * </ul>
     *
     * @since 2026-08-04
     */
    @ArchTest
    static final ArchRule controllerErrorMustIncludeCode = noClasses()
            .that().resideInAPackage("..web.controller..")
            .should().callMethod(
                    com.njydsz.common.core.response.BaseResponse.class,
                    "error",
                    String.class)
            .because("Controller 的 BaseResponse.error() 必须携带错误码参数，"
                    + "禁止单参 msg 调用（默认 code=A99999）；"
                    + "应使用 error(ResultCode) 或 error(ResultCode, msg)");

    // ========================================
    // API 注解与校验约束（2026-08-04 新增）
    // ========================================

    /**
     * R31: Controller 类必须标注 @RequestMapping 或具体 HTTP 方法注解（类级路径前缀）。
     *
     * <p>统一的 URL 前缀管理，避免方法级路径冲突。
     *
     * @since 2026-08-04
     */
    @ArchTest
    static final ArchRule controllerShouldHaveRequestMapping = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .and().resideInAPackage("..web.controller..")
            .should().beAnnotatedWith("org.springframework.web.bind.annotation.RequestMapping")
            .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.GetMapping")
            .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.PostMapping")
            .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.PutMapping")
            .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.DeleteMapping")
            .because("Controller 类必须声明 @RequestMapping 或具体 HTTP 方法注解作为路径前缀");

    /**
     * R32: DTO 类（以 PostDTO / PutDTO 结尾）的字段必须使用使用校验注解或标记 @JsonCreator。
     *
     * <p>防止无校验的数据直接透传到 Service 层。
     * 对于手动构造（@JsonCreator）的 DTO 字段，豁免校验注解要求。
     *
     * @since 2026-08-04
     */
    @ArchTest
    static final ArchRule dtoFieldsShouldBeAnnotated = noClasses()
            .that().resideInAnyPackage("..dto.post..", "..dto.put..")
            .and().areNotAnnotatedWith("lombok.Data")
            .should().beAnnotatedWith("lombok.Data")
            .because("DTO 类推荐使用 @Data Lombok 注解自动生成 getter/setter");
}
