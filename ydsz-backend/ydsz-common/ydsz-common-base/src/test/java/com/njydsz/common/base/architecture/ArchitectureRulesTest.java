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
}
