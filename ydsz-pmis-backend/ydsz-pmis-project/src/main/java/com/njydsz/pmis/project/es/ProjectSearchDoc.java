package com.njydsz.pmis.project.es;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * 项目全文检索文档。
 *
 * <p>聚合立项、合同、WBS 关键字段供全文检索，
 * 使用 IK 分词器实现中文分词搜索。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Document(indexName = "pmis_project_search")
public class ProjectSearchDoc {

    /** 文档 ID（与立项 ID 字符串一致） */
    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    /** 立项 ID */
    @Field(type = FieldType.Long)
    private Long initiationId;

    /** 项目名称（IK 最大分词） */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String projectName;

    /** 客户名称（IK 最大分词） */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String customerName;

    /** 合同名称（IK 最大分词） */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String contractName;

    /** 项目类型 */
    @Field(type = FieldType.Keyword)
    private String projectType;

    /** 项目状态 */
    @Field(type = FieldType.Keyword)
    private String status;

    /** 项目经理姓名 */
    @Field(type = FieldType.Keyword)
    private String pmName;

    /** 创建时间 */
    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    /** 默认构造函数 */
    public ProjectSearchDoc() {
    }

    /**
     * 全参构造函数（不含时间字段，时间自动填充为当前时间）。
     *
     * @param id            文档 ID
     * @param initiationId  立项 ID
     * @param projectName   项目名称
     * @param customerName  客户名称
     * @param contractName  合同名称
     * @param projectType   项目类型
     * @param status        项目状态
     * @param pmName        项目经理姓名
     */
    public ProjectSearchDoc(String id, Long initiationId, String projectName, String customerName,
                            String contractName, String projectType, String status, String pmName) {
        this.id = id;
        this.initiationId = initiationId;
        this.projectName = projectName;
        this.customerName = customerName;
        this.contractName = contractName;
        this.projectType = projectType;
        this.status = status;
        this.pmName = pmName;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** @return 文档 ID */
    public String getId() { return id; }
    /** @param id 文档 ID */
    public void setId(String id) { this.id = id; }
    /** @return 立项 ID */
    public Long getInitiationId() { return initiationId; }
    /** @param initiationId 立项 ID */
    public void setInitiationId(Long initiationId) { this.initiationId = initiationId; }
    /** @return 项目名称 */
    public String getProjectName() { return projectName; }
    /** @param projectName 项目名称 */
    public void setProjectName(String projectName) { this.projectName = projectName; }
    /** @return 客户名称 */
    public String getCustomerName() { return customerName; }
    /** @param customerName 客户名称 */
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    /** @return 合同名称 */
    public String getContractName() { return contractName; }
    /** @param contractName 合同名称 */
    public void setContractName(String contractName) { this.contractName = contractName; }
    /** @return 项目类型 */
    public String getProjectType() { return projectType; }
    /** @param projectType 项目类型 */
    public void setProjectType(String projectType) { this.projectType = projectType; }
    /** @return 项目状态 */
    public String getStatus() { return status; }
    /** @param status 项目状态 */
    public void setStatus(String status) { this.status = status; }
    /** @return 项目经理姓名 */
    public String getPmName() { return pmName; }
    /** @param pmName 项目经理姓名 */
    public void setPmName(String pmName) { this.pmName = pmName; }
    /** @return 创建时间 */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param createdAt 创建时间 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    /** @return 更新时间 */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /** @param updatedAt 更新时间 */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
