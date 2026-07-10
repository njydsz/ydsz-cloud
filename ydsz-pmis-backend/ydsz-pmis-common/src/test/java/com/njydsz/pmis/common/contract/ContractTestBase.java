package com.njydsz.pmis.common.contract;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring Cloud Contract 契约测试基类（P3-16 落地）。
 *
 * <p>契约测试对标大厂微服务协作标准：
 * <ul>
 *   <li>Provider 端：验证 API 实现符合契约定义</li>
 *   <li>Consumer 端：使用 stub 快速测试，无需启动 Provider 服务</li>
 *   <li>契约变更自动触发 Provider 端验证，防止破坏性变更</li>
 * </ul>
 *
 * <p>使用方法：
 * <ol>
 *   <li>在 Provider 模块的 pom.xml 中添加 spring-cloud-contract-maven-plugin</li>
 *   <li>在 src/test/resources/contracts/ 目录下定义 YAML 契约文件</li>
 *   <li>创建 Provider 端测试类继承此基类</li>
 *   <li>插件自动生成 MvcTest 验证契约是否符合</li>
 * </ol>
 *
 * <p>示例契约文件（src/test/resources/contracts/notification_send.yml）：
 * <pre>
 * name: "notification send contract"
 * request:
 *   method: POST
 *   url: /notifications/send
 *   body:
 *     title: "审批通知"
 *     content: "您有新的审批任务"
 *     receiverIds: ["user001"]
 *   headers:
 *     Content-Type: application/json
 * response:
 *   status: 200
 *   body:
 *     code: 200
 *     data: 1
 *   headers:
 *     Content-Type: application/json
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.1 (P3-16)
 */
@SpringBootTest
public abstract class ContractTestBase {

    /**
     * 子类重写此方法初始化测试上下文。
     * <p>Provider 端需要配置 RestAssuredMockMvc.standaloneSetup(controller)，
     * Consumer 端使用 StubRunner 自动注入 stub。
     */
    protected void setupContractContext() {
        // 子类实现
    }
}
