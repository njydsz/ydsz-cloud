package com.njydsz.project.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.project.domain.entity.alert.AlertDispatch;
import com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot;
import com.njydsz.project.domain.entity.cost.CostAllocation;
import com.njydsz.project.domain.entity.cost.CostPurchase;
import com.njydsz.project.domain.entity.evm.EvmMeasure;
import com.njydsz.project.domain.entity.execution.ExecutionClosure;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard;
import com.njydsz.project.domain.entity.execution.ExecutionRisk;
import com.njydsz.project.domain.entity.execution.ExecutionTimeEntry;
import com.njydsz.project.domain.entity.execution.ExecutionWbsTask;
import com.njydsz.project.domain.entity.ops.OpsTicket;
import com.njydsz.project.domain.entity.project.ProjectBudgetItem;
import com.njydsz.project.domain.entity.project.ProjectChange;
import com.njydsz.project.domain.entity.project.ProjectContract;
import com.njydsz.project.domain.entity.project.ProjectContractChange;
import com.njydsz.project.domain.entity.project.ProjectContractSupplement;
import com.njydsz.project.domain.entity.project.ProjectContractTemplate;
import com.njydsz.project.domain.entity.project.ProjectCustomerCredit;
import com.njydsz.project.domain.entity.project.ProjectExpense;
import com.njydsz.project.domain.entity.project.ProjectGateReview;
import com.njydsz.project.domain.entity.project.ProjectInitiation;
import com.njydsz.project.domain.entity.project.ProjectInvoice;
import com.njydsz.project.domain.entity.project.ProjectOpportunity;
import com.njydsz.project.domain.entity.project.ProjectOpportunityFollow;
import com.njydsz.project.domain.entity.project.ProjectPayment;
import com.njydsz.project.domain.entity.project.ProjectProfitSimulation;
import com.njydsz.project.domain.entity.project.ProjectProfitSnapshot;
import com.njydsz.project.domain.entity.project.ProjectReconcileDaily;
import com.njydsz.project.domain.entity.project.ProjectRevenue;
import com.njydsz.project.domain.entity.rate.RateCard;
import com.njydsz.project.domain.entity.rate.RateInternal;
import com.njydsz.project.domain.entity.satisfaction.Satisfaction;
import com.njydsz.project.domain.entity.warranty.Warranty;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.project.domain.vo.AlertDispatchVO;
import com.njydsz.project.domain.vo.BillableUtilizationSnapshotVO;
import com.njydsz.project.domain.vo.CostAllocationVO;
import com.njydsz.project.domain.vo.CostPurchaseVO;
import com.njydsz.project.domain.vo.EvmMeasureVO;
import com.njydsz.project.domain.vo.ExecutionClosureVO;
import com.njydsz.project.domain.vo.ExecutionDeliveryItemVO;
import com.njydsz.project.domain.vo.ExecutionDeliveryStandardVO;
import com.njydsz.project.domain.vo.ExecutionRiskVO;
import com.njydsz.project.domain.vo.ExecutionTimeEntryVO;
import com.njydsz.project.domain.vo.ExecutionWbsTaskVO;
import com.njydsz.project.domain.vo.OpsTicketVO;
import com.njydsz.project.domain.vo.ProjectBudgetItemVO;
import com.njydsz.project.domain.vo.ProjectChangeVO;
import com.njydsz.project.domain.vo.ProjectContractVO;
import com.njydsz.project.domain.vo.ProjectContractChangeVO;
import com.njydsz.project.domain.vo.ProjectContractSupplementVO;
import com.njydsz.project.domain.vo.ProjectContractTemplateVO;
import com.njydsz.project.domain.vo.ProjectCustomerCreditVO;
import com.njydsz.project.domain.vo.ProjectExpenseVO;
import com.njydsz.project.domain.vo.ProjectGateReviewVO;
import com.njydsz.project.domain.vo.ProjectInitiationVO;
import com.njydsz.project.domain.vo.ProjectInvoiceVO;
import com.njydsz.project.domain.vo.ProjectOpportunityVO;
import com.njydsz.project.domain.vo.ProjectOpportunityFollowVO;
import com.njydsz.project.domain.vo.ProjectPaymentVO;
import com.njydsz.project.domain.vo.ProjectProfitSimulationVO;
import com.njydsz.project.domain.vo.ProjectProfitSnapshotVO;
import com.njydsz.project.domain.vo.ProjectReconcileDailyVO;
import com.njydsz.project.domain.vo.ProjectRevenueVO;
import com.njydsz.project.domain.vo.RateCardVO;
import com.njydsz.project.domain.vo.RateInternalVO;
import com.njydsz.project.domain.vo.SatisfactionVO;
import com.njydsz.project.domain.vo.WarrantyVO;
import com.njydsz.project.domain.vo.FileStorageVO;
import com.njydsz.project.domain.dto.post.AlertDispatchPostDTO;
import com.njydsz.project.domain.dto.post.BillableUtilizationSnapshotPostDTO;
import com.njydsz.project.domain.dto.post.CostAllocationPostDTO;
import com.njydsz.project.domain.dto.post.CostPurchasePostDTO;
import com.njydsz.project.domain.dto.post.EvmMeasurePostDTO;
import com.njydsz.project.domain.dto.post.ExecutionClosurePostDTO;
import com.njydsz.project.domain.dto.post.ExecutionDeliveryItemPostDTO;
import com.njydsz.project.domain.dto.post.ExecutionDeliveryStandardPostDTO;
import com.njydsz.project.domain.dto.post.ExecutionRiskPostDTO;
import com.njydsz.project.domain.dto.post.ExecutionTimeEntryPostDTO;
import com.njydsz.project.domain.dto.post.ExecutionWbsTaskPostDTO;
import com.njydsz.project.domain.dto.post.OpsTicketPostDTO;
import com.njydsz.project.domain.dto.post.ProjectBudgetItemPostDTO;
import com.njydsz.project.domain.dto.post.ProjectInitiationPostDTO;
import com.njydsz.project.domain.dto.post.ProjectChangePostDTO;
import com.njydsz.project.domain.dto.post.ProjectContractChangePostDTO;
import com.njydsz.project.domain.dto.post.ProjectContractPostDTO;
import com.njydsz.project.domain.dto.post.ProjectContractSupplementPostDTO;
import com.njydsz.project.domain.dto.post.ProjectContractTemplatePostDTO;
import com.njydsz.project.domain.dto.post.ProjectCustomerCreditPostDTO;
import com.njydsz.project.domain.dto.post.ProjectExpensePostDTO;
import com.njydsz.project.domain.dto.post.ProjectGateReviewPostDTO;
import com.njydsz.project.domain.dto.post.ProjectInvoicePostDTO;
import com.njydsz.project.domain.dto.post.ProjectOpportunityPostDTO;
import com.njydsz.project.domain.dto.post.ProjectOpportunityFollowPostDTO;
import com.njydsz.project.domain.dto.post.ProjectPaymentPostDTO;
import com.njydsz.project.domain.dto.post.ProjectProfitSimulationPostDTO;
import com.njydsz.project.domain.dto.post.ProjectProfitSnapshotPostDTO;
import com.njydsz.project.domain.dto.post.ProjectReconcileDailyPostDTO;
import com.njydsz.project.domain.dto.post.ProjectRevenuePostDTO;
import com.njydsz.project.domain.dto.post.RateCardPostDTO;
import com.njydsz.project.domain.dto.post.RateInternalPostDTO;
import com.njydsz.project.domain.dto.post.SatisfactionPostDTO;
import com.njydsz.project.domain.dto.post.WarrantyPostDTO;
import com.njydsz.project.domain.dto.ProjectInitiationDTO;
import com.njydsz.project.domain.dto.put.AlertDispatchPutDTO;
import com.njydsz.project.domain.dto.put.BillableUtilizationSnapshotPutDTO;
import com.njydsz.project.domain.dto.put.CostAllocationPutDTO;
import com.njydsz.project.domain.dto.put.CostPurchasePutDTO;
import com.njydsz.project.domain.dto.put.EvmMeasurePutDTO;
import com.njydsz.project.domain.dto.put.ExecutionClosurePutDTO;
import com.njydsz.project.domain.dto.put.ExecutionDeliveryItemPutDTO;
import com.njydsz.project.domain.dto.put.ExecutionDeliveryStandardPutDTO;
import com.njydsz.project.domain.dto.put.ExecutionRiskPutDTO;
import com.njydsz.project.domain.dto.put.ExecutionTimeEntryPutDTO;
import com.njydsz.project.domain.dto.put.ExecutionWbsTaskPutDTO;
import com.njydsz.project.domain.dto.put.OpsTicketPutDTO;
import com.njydsz.project.domain.dto.put.ProjectBudgetItemPutDTO;
import com.njydsz.project.domain.dto.put.ProjectInitiationPutDTO;
import com.njydsz.project.domain.dto.put.ProjectChangePutDTO;
import com.njydsz.project.domain.dto.put.ProjectContractChangePutDTO;
import com.njydsz.project.domain.dto.put.ProjectContractPutDTO;
import com.njydsz.project.domain.dto.put.ProjectContractSupplementPutDTO;
import com.njydsz.project.domain.dto.put.ProjectContractTemplatePutDTO;
import com.njydsz.project.domain.dto.put.ProjectCustomerCreditPutDTO;
import com.njydsz.project.domain.dto.put.ProjectExpensePutDTO;
import com.njydsz.project.domain.dto.put.ProjectGateReviewPutDTO;
import com.njydsz.project.domain.dto.put.ProjectInvoicePutDTO;
import com.njydsz.project.domain.dto.put.ProjectOpportunityPutDTO;
import com.njydsz.project.domain.dto.put.ProjectOpportunityFollowPutDTO;
import com.njydsz.project.domain.dto.put.ProjectPaymentPutDTO;
import com.njydsz.project.domain.dto.put.ProjectProfitSimulationPutDTO;
import com.njydsz.project.domain.dto.put.ProjectProfitSnapshotPutDTO;
import com.njydsz.project.domain.dto.put.ProjectReconcileDailyPutDTO;
import com.njydsz.project.domain.dto.put.ProjectRevenuePutDTO;
import com.njydsz.project.domain.dto.put.RateCardPutDTO;
import com.njydsz.project.domain.dto.put.RateInternalPutDTO;
import com.njydsz.project.domain.dto.put.SatisfactionPutDTO;
import com.njydsz.project.domain.dto.put.WarrantyPutDTO;

/**
 * project 模块统一 MapStruct 转换器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface ProjectConverter {

    ProjectConverter INSTANT = Mappers.getMapper(ProjectConverter.class);

    // ===== AlertDispatch =====
    AlertDispatchVO entityToVO(AlertDispatch entity);
    List<AlertDispatchVO> alertDispatchListToVO(List<AlertDispatch> entities);

    // ===== BillableUtilizationSnapshot =====
    BillableUtilizationSnapshotVO entityToVO(BillableUtilizationSnapshot entity);
    List<BillableUtilizationSnapshotVO> billableUtilizationSnapshotListToVO(List<BillableUtilizationSnapshot> entities);

    // ===== CostAllocation =====
    CostAllocationVO entityToVO(CostAllocation entity);
    List<CostAllocationVO> costAllocationListToVO(List<CostAllocation> entities);

    // ===== CostPurchase =====
    CostPurchaseVO entityToVO(CostPurchase entity);
    List<CostPurchaseVO> costPurchaseListToVO(List<CostPurchase> entities);

    // ===== EvmMeasure =====
    EvmMeasureVO entityToVO(EvmMeasure entity);
    List<EvmMeasureVO> evmMeasureListToVO(List<EvmMeasure> entities);

    // ===== ExecutionClosure =====
    ExecutionClosureVO entityToVO(ExecutionClosure entity);
    List<ExecutionClosureVO> executionClosureListToVO(List<ExecutionClosure> entities);

    // ===== ExecutionDeliveryItem =====
    ExecutionDeliveryItemVO entityToVO(ExecutionDeliveryItem entity);
    List<ExecutionDeliveryItemVO> executionDeliveryItemListToVO(List<ExecutionDeliveryItem> entities);

    // ===== ExecutionDeliveryStandard =====
    ExecutionDeliveryStandardVO entityToVO(ExecutionDeliveryStandard entity);
    List<ExecutionDeliveryStandardVO> executionDeliveryStandardListToVO(List<ExecutionDeliveryStandard> entities);

    // ===== ExecutionRisk =====
    ExecutionRiskVO entityToVO(ExecutionRisk entity);
    List<ExecutionRiskVO> executionRiskListToVO(List<ExecutionRisk> entities);

    // ===== ExecutionTimeEntry =====
    ExecutionTimeEntryVO entityToVO(ExecutionTimeEntry entity);
    List<ExecutionTimeEntryVO> executionTimeEntryListToVO(List<ExecutionTimeEntry> entities);

    // ===== ExecutionWbsTask =====
    ExecutionWbsTaskVO entityToVO(ExecutionWbsTask entity);
    List<ExecutionWbsTaskVO> executionWbsTaskListToVO(List<ExecutionWbsTask> entities);

    // ===== OpsTicket =====
    OpsTicketVO entityToVO(OpsTicket entity);
    List<OpsTicketVO> opsTicketListToVO(List<OpsTicket> entities);

    // ===== ProjectBudgetItem =====
    ProjectBudgetItemVO entityToVO(ProjectBudgetItem entity);
    List<ProjectBudgetItemVO> projectBudgetItemListToVO(List<ProjectBudgetItem> entities);

    // ===== ProjectChange =====
    ProjectChangeVO entityToVO(ProjectChange entity);
    List<ProjectChangeVO> projectChangeListToVO(List<ProjectChange> entities);

    // ===== ProjectContract =====
    ProjectContractVO entityToVO(ProjectContract entity);
    List<ProjectContractVO> projectContractListToVO(List<ProjectContract> entities);

    // ===== ProjectContractChange =====
    ProjectContractChangeVO entityToVO(ProjectContractChange entity);
    List<ProjectContractChangeVO> projectContractChangeListToVO(List<ProjectContractChange> entities);

    // ===== ProjectContractSupplement =====
    ProjectContractSupplementVO entityToVO(ProjectContractSupplement entity);
    List<ProjectContractSupplementVO> projectContractSupplementListToVO(List<ProjectContractSupplement> entities);

    // ===== ProjectContractTemplate =====
    ProjectContractTemplateVO entityToVO(ProjectContractTemplate entity);
    List<ProjectContractTemplateVO> projectContractTemplateListToVO(List<ProjectContractTemplate> entities);

    // ===== ProjectCustomerCredit =====
    ProjectCustomerCreditVO entityToVO(ProjectCustomerCredit entity);
    List<ProjectCustomerCreditVO> projectCustomerCreditListToVO(List<ProjectCustomerCredit> entities);

    // ===== ProjectExpense =====
    ProjectExpenseVO entityToVO(ProjectExpense entity);
    List<ProjectExpenseVO> projectExpenseListToVO(List<ProjectExpense> entities);

    // ===== ProjectGateReview =====
    ProjectGateReviewVO entityToVO(ProjectGateReview entity);
    List<ProjectGateReviewVO> projectGateReviewListToVO(List<ProjectGateReview> entities);

    // ===== ProjectInitiation =====
    ProjectInitiationVO entityToVO(ProjectInitiation entity);
    List<ProjectInitiationVO> projectInitiationListToVO(List<ProjectInitiation> entities);

    // ===== ProjectInvoice =====
    ProjectInvoiceVO entityToVO(ProjectInvoice entity);
    List<ProjectInvoiceVO> projectInvoiceListToVO(List<ProjectInvoice> entities);

    // ===== ProjectOpportunity =====
    ProjectOpportunityVO entityToVO(ProjectOpportunity entity);
    List<ProjectOpportunityVO> projectOpportunityListToVO(List<ProjectOpportunity> entities);

    // ===== ProjectOpportunityFollow =====
    ProjectOpportunityFollowVO entityToVO(ProjectOpportunityFollow entity);
    List<ProjectOpportunityFollowVO> projectOpportunityFollowListToVO(List<ProjectOpportunityFollow> entities);

    // ===== ProjectPayment =====
    ProjectPaymentVO entityToVO(ProjectPayment entity);
    List<ProjectPaymentVO> projectPaymentListToVO(List<ProjectPayment> entities);

    // ===== ProjectProfitSimulation =====
    ProjectProfitSimulationVO entityToVO(ProjectProfitSimulation entity);
    List<ProjectProfitSimulationVO> projectProfitSimulationListToVO(List<ProjectProfitSimulation> entities);

    // ===== ProjectProfitSnapshot =====
    ProjectProfitSnapshotVO entityToVO(ProjectProfitSnapshot entity);
    List<ProjectProfitSnapshotVO> projectProfitSnapshotListToVO(List<ProjectProfitSnapshot> entities);

    // ===== ProjectReconcileDaily =====
    ProjectReconcileDailyVO entityToVO(ProjectReconcileDaily entity);
    List<ProjectReconcileDailyVO> projectReconcileDailyListToVO(List<ProjectReconcileDaily> entities);

    // ===== ProjectRevenue =====
    ProjectRevenueVO entityToVO(ProjectRevenue entity);
    List<ProjectRevenueVO> projectRevenueListToVO(List<ProjectRevenue> entities);

    // ===== RateCard =====
    RateCardVO entityToVO(RateCard entity);
    List<RateCardVO> rateCardListToVO(List<RateCard> entities);

    // ===== RateInternal =====
    RateInternalVO entityToVO(RateInternal entity);
    List<RateInternalVO> rateInternalListToVO(List<RateInternal> entities);

    // ===== Satisfaction =====
    SatisfactionVO entityToVO(Satisfaction entity);
    List<SatisfactionVO> satisfactionListToVO(List<Satisfaction> entities);

    // ===== Warranty =====
    WarrantyVO entityToVO(Warranty entity);
    List<WarrantyVO> warrantyListToVO(List<Warranty> entities);

    // ===== FileStorage =====
    FileStorageVO entityToVO(FileStorage entity);


    // ===== AlertDispatch PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AlertDispatch postDtoToEntity(AlertDispatchPostDTO dto);

    // ===== BillableUtilizationSnapshot PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BillableUtilizationSnapshot postDtoToEntity(BillableUtilizationSnapshotPostDTO dto);

    // ===== CostAllocation PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CostAllocation postDtoToEntity(CostAllocationPostDTO dto);

    // ===== CostPurchase PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CostPurchase postDtoToEntity(CostPurchasePostDTO dto);

    // ===== EvmMeasure PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    EvmMeasure postDtoToEntity(EvmMeasurePostDTO dto);

    // ===== ExecutionClosure PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionClosure postDtoToEntity(ExecutionClosurePostDTO dto);

    // ===== ExecutionDeliveryItem PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionDeliveryItem postDtoToEntity(ExecutionDeliveryItemPostDTO dto);

    // ===== ExecutionDeliveryStandard PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionDeliveryStandard postDtoToEntity(ExecutionDeliveryStandardPostDTO dto);

    // ===== ExecutionRisk PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionRisk postDtoToEntity(ExecutionRiskPostDTO dto);

    // ===== ExecutionTimeEntry PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionTimeEntry postDtoToEntity(ExecutionTimeEntryPostDTO dto);

    // ===== ExecutionWbsTask PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionWbsTask postDtoToEntity(ExecutionWbsTaskPostDTO dto);

    // ===== OpsTicket PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    OpsTicket postDtoToEntity(OpsTicketPostDTO dto);

    // ===== ProjectBudgetItem PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectBudgetItem postDtoToEntity(ProjectBudgetItemPostDTO dto);

    // ===== ProjectChange PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectChange postDtoToEntity(ProjectChangePostDTO dto);

    // ===== ProjectContractChange PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectContractChange postDtoToEntity(ProjectContractChangePostDTO dto);

    // ===== ProjectContract PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectContract postDtoToEntity(ProjectContractPostDTO dto);

    // ===== ProjectContractSupplement PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectContractSupplement postDtoToEntity(ProjectContractSupplementPostDTO dto);

    // ===== ProjectContractTemplate PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectContractTemplate postDtoToEntity(ProjectContractTemplatePostDTO dto);

    // ===== ProjectCustomerCredit PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectCustomerCredit postDtoToEntity(ProjectCustomerCreditPostDTO dto);

    // ===== ProjectExpense PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectExpense postDtoToEntity(ProjectExpensePostDTO dto);

    // ===== ProjectGateReview PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectGateReview postDtoToEntity(ProjectGateReviewPostDTO dto);

    // ===== ProjectInvoice PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectInvoice postDtoToEntity(ProjectInvoicePostDTO dto);

    // ===== ProjectOpportunity PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectOpportunity postDtoToEntity(ProjectOpportunityPostDTO dto);

    // ===== ProjectOpportunityFollow PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectOpportunityFollow postDtoToEntity(ProjectOpportunityFollowPostDTO dto);

    // ===== ProjectPayment PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectPayment postDtoToEntity(ProjectPaymentPostDTO dto);

    // ===== ProjectProfitSimulation PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectProfitSimulation postDtoToEntity(ProjectProfitSimulationPostDTO dto);

    // ===== ProjectProfitSnapshot PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectProfitSnapshot postDtoToEntity(ProjectProfitSnapshotPostDTO dto);

    // ===== ProjectReconcileDaily PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectReconcileDaily postDtoToEntity(ProjectReconcileDailyPostDTO dto);

    // ===== ProjectRevenue PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectRevenue postDtoToEntity(ProjectRevenuePostDTO dto);

    // ===== RateCard PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RateCard postDtoToEntity(RateCardPostDTO dto);

    // ===== RateInternal PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RateInternal postDtoToEntity(RateInternalPostDTO dto);

    // ===== Satisfaction PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Satisfaction postDtoToEntity(SatisfactionPostDTO dto);

    // ===== Warranty PostDTO → Entity =====
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Warranty postDtoToEntity(WarrantyPostDTO dto);

    // ===== AlertDispatch PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AlertDispatch putDtoToEntity(AlertDispatchPutDTO dto);

    // ===== BillableUtilizationSnapshot PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BillableUtilizationSnapshot putDtoToEntity(BillableUtilizationSnapshotPutDTO dto);

    // ===== CostAllocation PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CostAllocation putDtoToEntity(CostAllocationPutDTO dto);

    // ===== CostPurchase PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CostPurchase putDtoToEntity(CostPurchasePutDTO dto);

    // ===== EvmMeasure PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    EvmMeasure putDtoToEntity(EvmMeasurePutDTO dto);

    // ===== ExecutionClosure PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionClosure putDtoToEntity(ExecutionClosurePutDTO dto);

    // ===== ExecutionDeliveryItem PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionDeliveryItem putDtoToEntity(ExecutionDeliveryItemPutDTO dto);

    // ===== ExecutionDeliveryStandard PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionDeliveryStandard putDtoToEntity(ExecutionDeliveryStandardPutDTO dto);

    // ===== ExecutionRisk PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionRisk putDtoToEntity(ExecutionRiskPutDTO dto);

    // ===== ExecutionTimeEntry PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionTimeEntry putDtoToEntity(ExecutionTimeEntryPutDTO dto);

    // ===== ExecutionWbsTask PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ExecutionWbsTask putDtoToEntity(ExecutionWbsTaskPutDTO dto);

    // ===== OpsTicket PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    OpsTicket putDtoToEntity(OpsTicketPutDTO dto);

    // ===== ProjectBudgetItem PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectBudgetItem putDtoToEntity(ProjectBudgetItemPutDTO dto);

    // ===== ProjectChange PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectChange putDtoToEntity(ProjectChangePutDTO dto);

    // ===== ProjectContractChange PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectContractChange putDtoToEntity(ProjectContractChangePutDTO dto);

    // ===== ProjectContract PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectContract putDtoToEntity(ProjectContractPutDTO dto);

    // ===== ProjectContractSupplement PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectContractSupplement putDtoToEntity(ProjectContractSupplementPutDTO dto);

    // ===== ProjectContractTemplate PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectContractTemplate putDtoToEntity(ProjectContractTemplatePutDTO dto);

    // ===== ProjectCustomerCredit PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectCustomerCredit putDtoToEntity(ProjectCustomerCreditPutDTO dto);

    // ===== ProjectExpense PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectExpense putDtoToEntity(ProjectExpensePutDTO dto);

    // ===== ProjectGateReview PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectGateReview putDtoToEntity(ProjectGateReviewPutDTO dto);

    // ===== ProjectInvoice PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectInvoice putDtoToEntity(ProjectInvoicePutDTO dto);

    // ===== ProjectOpportunity PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectOpportunity putDtoToEntity(ProjectOpportunityPutDTO dto);

    // ===== ProjectOpportunityFollow PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectOpportunityFollow putDtoToEntity(ProjectOpportunityFollowPutDTO dto);

    // ===== ProjectPayment PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectPayment putDtoToEntity(ProjectPaymentPutDTO dto);

    // ===== ProjectProfitSimulation PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectProfitSimulation putDtoToEntity(ProjectProfitSimulationPutDTO dto);

    // ===== ProjectProfitSnapshot PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectProfitSnapshot putDtoToEntity(ProjectProfitSnapshotPutDTO dto);

    // ===== ProjectReconcileDaily PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectReconcileDaily putDtoToEntity(ProjectReconcileDailyPutDTO dto);

    // ===== ProjectRevenue PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProjectRevenue putDtoToEntity(ProjectRevenuePutDTO dto);

    // ===== RateCard PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RateCard putDtoToEntity(RateCardPutDTO dto);

    // ===== RateInternal PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RateInternal putDtoToEntity(RateInternalPutDTO dto);

    // ===== Satisfaction PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Satisfaction putDtoToEntity(SatisfactionPutDTO dto);

    // ===== Warranty PutDTO → Entity =====
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Warranty putDtoToEntity(WarrantyPutDTO dto);

    // ===== ProjectInitiationDTO → ProjectInitiation Entity =====
    /**
     * 项目立项 DTO → 实体（用于新增/更新）。
     *
     * <p>DTO 仅包含前端可控字段，实体上的 stage/currentGate/status/actualStartDate/actualEndDate/
     * durationDays/customerName/pmName/sponsorName 等运行时字段通过 @Mapping(ignore = true) 隔离。
     * id 字段：新增时为 null（雪花算法生成），更新时从 DTO 透传到 entity 用于 WHERE 条件。
     *
     * @param dto 项目立项 DTO
     * @return ProjectInitiation 实体
     */
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "customerName", ignore = true)
    @Mapping(target = "pmName", ignore = true)
    @Mapping(target = "sponsorName", ignore = true)
    @Mapping(target = "durationDays", ignore = true)
    @Mapping(target = "stage", ignore = true)
    @Mapping(target = "currentGate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "actualStartDate", ignore = true)
    @Mapping(target = "actualEndDate", ignore = true)
    @Mapping(target = "id", ignore = true)
    ProjectInitiation postDtoToEntity(ProjectInitiationPostDTO dto);

    @Mapping(target = "stage", ignore = true)
    @Mapping(target = "currentGate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "actualStartDate", ignore = true)
    @Mapping(target = "actualEndDate", ignore = true)
    ProjectInitiation putDtoToEntity(ProjectInitiationPutDTO dto);

}