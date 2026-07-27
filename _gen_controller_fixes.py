#!/usr/bin/env python3
"""
Scan controllers for direct Entity returns and generate edit instructions.
This script finds all BaseResponse<EntityType> patterns and outputs the fixes needed.
"""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

MODULES = [
    ('cronjob', 'ydsz-cronjob'),
    ('workflow', 'ydsz-workflow'),
    ('project', 'ydsz-project'),
    ('literule', 'ydsz-literule'),
    ('agent', 'ydsz-agent'),
]

# Known entity class names per module (from the VO generation)
ENTITY_CLASSES = {
    'cronjob': ['GlueCode', 'Job', 'JobAlertLog', 'JobAlertRule', 'JobArtifact', 'JobDag', 'JobDagInstance', 'JobDagNodeInstance', 'JobDagVersion', 'JobDailyStats', 'JobHistory', 'JobLog', 'JobLogContent', 'JobNode', 'JobSla', 'JobTask', 'JobWebhook'],
    'workflow': ['FlowAdminRole', 'FlowAttachment', 'FlowAuditLog', 'FlowAutoTrigger', 'FlowCategory', 'FlowCc', 'FlowCcRule', 'FlowComment', 'FlowDefinition', 'FlowDelegateAuth', 'FlowDmnDecision', 'FlowDmnRule', 'FlowEventSubscription', 'FlowHisInstance', 'FlowHisTask', 'FlowInstance', 'FlowNode', 'FlowQuickComment', 'FlowRunTask', 'FlowSkip', 'FlowTemplate', 'FlowThirdPartyAccount', 'FlowThirdPartyLog', 'FlowTimer', 'FlowUser'],
    'project': ['AlertDispatch', 'BillableUtilizationSnapshot', 'CostAllocation', 'CostPurchase', 'EvmMeasure', 'ExecutionClosure', 'ExecutionDeliveryItem', 'ExecutionDeliveryStandard', 'ExecutionRisk', 'ExecutionTimeEntry', 'ExecutionWbsTask', 'OpsTicket', 'ProjectBudgetItem', 'ProjectChange', 'ProjectContract', 'ProjectContractChange', 'ProjectContractSupplement', 'ProjectContractTemplate', 'ProjectCustomerCredit', 'ProjectExpense', 'ProjectGateReview', 'ProjectInitiation', 'ProjectInvoice', 'ProjectOpportunity', 'ProjectOpportunityFollow', 'ProjectPayment', 'ProjectProfitSimulation', 'ProjectProfitSnapshot', 'ProjectReconcileDaily', 'ProjectRevenue', 'RateCard', 'RateInternal', 'Satisfaction', 'Warranty'],
    'literule': ['DecisionTable', 'RuleABPolicy', 'RuleABRollback', 'RuleCanaryBucket', 'RuleChainGraphDO', 'RuleDecisionTree', 'RuleDefinitionDO', 'RuleDependency', 'RuleExecutionTraceDO', 'RulePackDO', 'RuleScorecard', 'RuleScript', 'RuleTemplate', 'RuleTestCaseDO', 'RuleVariableDef', 'RuleVersionHistory'],
    'agent': ['AgentDefinitionDO'],
}

for mod_key, mod_dir in MODULES:
    web_path = os.path.join(BACKEND, mod_dir, f'ydsz-{mod_key}-web', 'src', 'main', 'java')
    if not os.path.exists(web_path):
        print(f'### {mod_key}: no web dir')
        continue
    
    entities = ENTITY_CLASSES.get(mod_key, [])
    # Also match DO-suffixed names
    entity_pattern = '|'.join(entities)
    
    controller_files = []
    for root, dirs, files in os.walk(web_path):
        for fn in files:
            if fn.endswith('.java') and 'Controller' in fn:
                controller_files.append(os.path.join(root, fn))
    
    print(f'\n=== {mod_key}: {len(controller_files)} controllers ===')
    for cf in controller_files:
        with open(cf, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Find all return types that use Entity directly
        matches = re.findall(rf'BaseResponse<(?:Page<)?({entity_pattern})(?:>|>>)', content)
        if matches:
            print(f'  {os.path.basename(cf)}: returns {set(matches)}')
