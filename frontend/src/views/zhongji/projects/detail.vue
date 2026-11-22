<template><main class="zj-workspace" v-loading="loading"><el-alert v-if="error" title="项目加载失败，请刷新重试" type="error" :closable="false"/><template v-if="project"><header class="zj-heading"><div><div class="zj-eyebrow">众基消防 / {{projectTypes[project.projectType]}}</div><h1>{{project.projectName}}</h1><p>{{project.projectNo}} · {{project.contractName||'待关联合同'}}</p></div><div><el-button @click="back">返回列表</el-button><el-button icon="Refresh" aria-label="刷新项目" @click="refresh"/></div></header><el-alert v-if="!project.contractId||project.numberPending||project.classificationPending" title="此历史项目存在待确认信息，请核对所属合同、类型或编号；原始记录已保留。" type="warning" :closable="false"/>
<section class="zj-card zj-project-overview" aria-label="项目业务概览">
  <div class="zj-guidance-heading"><h2>业务概览</h2><span class="zj-sub">以下信息来自当前项目档案</span></div>
  <dl class="zj-overview-grid">
    <div v-for="item in overviewFields" :key="item.key" class="zj-overview-field">
      <dt>{{ item.label }}</dt><dd>{{ item.value }}</dd>
    </div>
  </dl>
  <div class="zj-guidance-actions">
    <el-button @click="openBasic">核对基本信息</el-button>
    <el-button @click="openSection('registration')">查看现场与报告登记</el-button>
  </div>
</section>
<section class="zj-card zj-project-guidance" aria-label="项目下一步操作">
  <div class="zj-guidance-heading"><h2>项目下一步</h2><span class="zj-sub">根据当前项目资料提供提示，不影响已有业务操作。</span></div>
  <template v-if="pendingGuidance.length">
    <p class="zj-guidance-intro">以下资料尚待核对，可在日常工作中逐步补充。</p>
    <ul class="zj-guidance-list">
      <li v-for="item in pendingGuidance" :key="item.key" class="zj-guidance-item">
        <span><strong>{{ item.title }}</strong><span class="zj-sub">{{ item.description }}</span></span>
        <el-button text type="primary" @click="openBasic">{{ canEdit ? '前往核对' : '查看资料' }}</el-button>
      </li>
    </ul>
  </template>
  <template v-else><p class="zj-guidance-intro">项目关键资料已登记，可以继续处理现场登记、问题记录或文件。</p></template>
  <div class="zj-guidance-actions">
    <el-button @click="openSection('registration')">现场登记</el-button>
    <el-button @click="openSection('issues')">问题记录</el-button>
    <el-button @click="openSection('documents')">项目文件</el-button>
  </div>
</section>
<section class="zj-card" style="margin-top:16px"><el-tabs v-model="active" :before-leave="beforeTab"><el-tab-pane label="基本信息" name="basic"><div class="zj-summary"><span>正式编号：{{project.projectNo}}</span><el-button v-if="project.numberPending&&canEdit" text type="primary" :loading="saving" @click="applyNumber">申请正式编号</el-button></div><el-form v-if="project.classificationPending" label-position="top" :disabled="!canEdit"><el-form-item label="核实项目类型"><el-select v-model="basic.projectType" @change="basic.contractId=null"><el-option v-for="(label,code) in projectTypes" :key="code" :label="label" :value="code"/></el-select></el-form-item><el-checkbox v-model="basic.classificationPending" :true-value="false" :false-value="true">类型已核实</el-checkbox></el-form><ProjectBasicForm ref="basicRef" v-model="basic" :project-type="basic.projectType" :legacy="!project.contractId" :disabled="!canEdit||saving"/></el-tab-pane>
<el-tab-pane v-if="project.projectType!=='WB'" label="详细信息" name="details"><el-form label-position="top" :disabled="!canEdit||saving"><div class="zj-grid"><el-form-item v-for="f in detailFields" :key="f.key" :label="f.label"><el-input v-model="details[f.key]" :type="f.multi?'textarea':'text'" :rows="f.multi?4:undefined" :maxlength="f.multi?5000:500"/></el-form-item></div></el-form><el-collapse v-if="details.legacySystem"><el-collapse-item title="迁移的原项目系统资料（原样保留）"><pre style="white-space:pre-wrap;overflow-wrap:anywhere;max-height:400px;overflow:auto">{{details.legacySystem}}</pre></el-collapse-item></el-collapse></el-tab-pane>
<el-tab-pane label="登记信息" name="registration"><MaintenanceRegistration v-if="project.projectType==='WB'" v-model="registration" :disabled="!canEdit||saving"/><el-form v-else label-position="top" :disabled="!canEdit||saving"><div class="zj-grid"><el-form-item label="现场情况"><el-select v-model="registration.visitStatus"><el-option label="未登记" value="unknown"/><el-option label="尚未到场" value="not_started"/><el-option label="已经到场" value="visited"/><el-option label="现场工作完成" value="completed"/></el-select></el-form-item><el-form-item label="实际到场日期"><el-date-picker v-model="registration.visitDate" value-format="YYYY-MM-DD"/></el-form-item><el-form-item label="报告情况"><el-select v-model="registration.reportStatus"><el-option label="未登记" value="unknown"/><el-option label="尚未制作" value="not_started"/><el-option label="制作中" value="in_progress"/><el-option label="报告已完成" value="completed"/></el-select></el-form-item><el-form-item label="报告完成日期"><el-date-picker v-model="registration.reportDate" value-format="YYYY-MM-DD"/></el-form-item><el-form-item label="报告编号"><el-input v-model="registration.reportNo" maxlength="255"/></el-form-item><el-form-item label="登记说明"><el-input v-model="registration.notes" type="textarea" :rows="4" maxlength="5000"/></el-form-item></div></el-form></el-tab-pane>
<el-tab-pane label="问题信息" name="issues" lazy><ProjectChildRecords ref="issueRecords" :project-id="id" kind="issues" :can-edit="canEdit" :active="active==='issues'" @busy-change="childSaving=$event"/></el-tab-pane>
<el-tab-pane label="备注信息" name="notes"><el-input v-model="notes" type="textarea" :rows="12" maxlength="10000" show-word-limit :disabled="!canEdit||saving" placeholder="记录项目补充说明、沟通情况和需要注意的事项"/></el-tab-pane>
<el-tab-pane v-if="project.projectType!=='WB'" label="计划信息" name="plans" lazy><ProjectChildRecords ref="planRecords" :project-id="id" kind="plans" :can-edit="canEdit" :active="active==='plans'" @busy-change="childSaving=$event"/></el-tab-pane>
<el-tab-pane label="文件" name="documents" lazy><ProjectFilesPanel :project-id="id" :can-edit="canEdit"/></el-tab-pane><el-tab-pane v-if="project.projectType==='WB'" label="资料" name="materials" lazy><ProjectMaterialsPanel :project="project" :can-edit="canEdit"/></el-tab-pane><el-tab-pane v-if="canExpenses" label="报账信息" name="expenses" lazy><ProjectExpensesPanel :project-id="id"/></el-tab-pane></el-tabs>
<div v-if="canEdit&&['basic','details','registration','notes'].includes(active)" class="zj-project-save"><span class="zj-sub">每页单独保存，避免覆盖其他页面的信息。</span><el-button type="primary" :loading="saving" @click="saveSection">保存{{tabLabels[active]}}</el-button></div></section>

</template></main></template>
<script setup name="ZhongjiProjectDetail">
import MaintenanceRegistration from './components/MaintenanceRegistration.vue';
import ProjectChildRecords from './components/ProjectChildRecords.vue';
import ProjectFilesPanel from './components/ProjectFilesPanel.vue';import ProjectMaterialsPanel from './components/ProjectMaterialsPanel.vue';import ProjectExpensesPanel from './components/ProjectExpensesPanel.vue';
import {onBeforeRouteLeave} from 'vue-router';import {getProject,saveProjectSection,applyProjectNumber,projectTypes} from '@/api/workProject';import ProjectBasicForm from './components/ProjectBasicForm.vue';import {fileUrl} from '@/utils/files';import auth from '@/plugins/auth';import '@/assets/styles/zhongji.scss';
const route=useRoute(),router=useRouter(),{proxy}=getCurrentInstance();const id=computed(()=>route.params.id),project=ref(),basic=ref({}),basicRef=ref(),details=ref({}),registration=ref({}),notes=ref(''),active=ref('basic'),loading=ref(false),error=ref(false),saving=ref(false);const canEdit=computed(()=>auth.hasPermi('management:project:edit')),canExpenses=computed(()=>auth.hasPermi('management:project:expenses'));const childSaving=ref(false),issueRecords=ref(),planRecords=ref();let snapshots={};
const overviewFields=computed(()=>{
  if(!project.value)return [];
  const p=project.value;
  const reg=registration.value||{};
  const text=value=>value===null||value===undefined||value===''?'未登记':String(value);
  const visitLabels={unknown:'未登记',not_started:'尚未到场',visited:'已经到场',completed:'现场工作完成'};
  const reportLabels={unknown:'未登记',not_started:'尚未制作',in_progress:'制作中',completed:'报告已完成'};
  return [
    {key:'contract',label:'关联合同',value:p.contractId?text(p.contractName):'未关联合同'},
    {key:'leader',label:'项目负责人',value:text(p.leaderName)},
    {key:'technical',label:'技术负责人',value:text(p.technicalName)},
    {key:'number',label:'项目编号',value:p.numberPending?'待确认 · '+text(p.projectNo):text(p.projectNo)},
    {key:'visit',label:'现场情况',value:visitLabels[reg.visitStatus]||'状态待核实'},
    {key:'report',label:'报告情况',value:reportLabels[reg.reportStatus]||'状态待核实'}
  ];
});
const pendingGuidance=computed(()=>{
  if(!project.value)return [];
  const items=[];
  if(project.value.classificationPending)items.push({key:'classification',title:'核实项目类型',description:'历史项目的业务类型尚待确认。'});
  if(project.value.numberPending)items.push({key:'number',title:'核对项目编号',description:'当前编号仍待确认，可在基本信息中查看并按权限申请正式编号。'});
  if(!project.value.contractId)items.push({key:'contract',title:'核对关联合同',description:'当前未关联合同；无合同项目也可以继续开展业务。'});
  if(!project.value.leaderUserId)items.push({key:'leader',title:'补充项目负责人',description:'尚未登记项目负责人。'});
  if(!project.value.technicalUserId)items.push({key:'technical',title:'补充技术负责人',description:'尚未登记技术负责人。'});
  return items;
});
async function openSection(section){if(section===active.value)return;if(await beforeTab())active.value=section;}
function openBasic(){openSection('basic');}
const tabLabels={basic:'基本信息',details:'详细信息',registration:'登记信息',notes:'备注信息'};const detailFields=[{key:'constructionType',label:'工程类别（新建 / 扩建 / 改建）'},{key:'constructionUnit',label:'建设单位'},{key:'designUnit',label:'设计单位'},{key:'designQualification',label:'设计单位资质等级'},{key:'contractor',label:'施工单位'},{key:'contractorQualification',label:'施工单位资质等级'},{key:'supervisor',label:'监理单位'},{key:'supervisorQualification',label:'监理单位资质等级'},{key:'fireResistance',label:'耐火等级'},{key:'commissionDate',label:'委托日期（YYYY-MM-DD）'},{key:'buildingOverview',label:'建筑概况',multi:true},{key:'buildingUse',label:'建筑使用性质'},{key:'buildingArea',label:'建筑面积（㎡）'},{key:'buildingHeight',label:'建筑高度（m）'},{key:'aboveFloors',label:'地上层数'},{key:'belowFloors',label:'地下层数'},{key:'structureType',label:'建筑结构类型'},{key:'serviceScope',label:'服务 / 检测范围',multi:true},{key:'fireFacilities',label:'消防设施概况',multi:true},{key:'reportBasis',label:'报告编制依据',multi:true}];
function value(tab){return JSON.stringify(tab==='basic'?basic.value:tab==='details'?details.value:tab==='registration'?registration.value:notes.value);}
async function load(){loading.value=true;error.value=false;try{project.value=(await getProject(id.value)).data;basic.value={...project.value};details.value=JSON.parse(project.value.detailsJson||'{}');registration.value={visitStatus:'unknown',reportStatus:'unknown',...JSON.parse(project.value.registrationJson||'{}')};if(project.value.projectType==='WB'&&!Array.isArray(registration.value.entries))registration.value.entries=[];notes.value=project.value.remark||'';for(const t of Object.keys(tabLabels))snapshots[t]=value(t);}catch{project.value=null;error.value=true;}finally{loading.value=false;}}
async function allowLeave(){if(saving.value||childSaving.value)return false;if(canEdit.value&&snapshots[active.value]!==undefined&&snapshots[active.value]!==value(active.value)){try{await proxy.$modal.confirm('当前页面有未保存的修改，确认放弃？');const old=JSON.parse(snapshots[active.value]);if(active.value==='basic')basic.value=old;else if(active.value==='details')details.value=old;else if(active.value==='registration')registration.value=old;else notes.value=old;}catch{return false;}}return true;}
async function beforeTab(){if(!await allowLeave())return false;const records=active.value==='issues'?issueRecords.value:active.value==='plans'?planRecords.value:null;return records?records.leaveTab():true;}onBeforeRouteLeave(allowLeave);
async function refresh(){if(await allowLeave()){await load();if(active.value==='issues')issueRecords.value?.load();if(active.value==='plans')planRecords.value?.load();}}function back(){const paths={WB:'maintenance',JC:'inspection',PD:'rating',PG:'assessment',QT:'other'};router.push('/zhongji/projects/'+paths[project.value.projectType]);}
async function saveSection(){if(active.value==='basic'&&!await basicRef.value.validate().catch(()=>false))return;const payload=active.value==='basic'?{...basic.value}:active.value==='details'?{details:details.value}:active.value==='registration'?{registration:registration.value}:{remark:notes.value};saving.value=true;try{await saveProjectSection(id.value,active.value,{...payload,version:project.value.version});proxy.$modal.msgSuccess('已保存');await load();}finally{saving.value=false;}}
async function applyNumber(){if(!await allowLeave())return;await proxy.$modal.confirm('为此历史项目申请正式编号？原编号仍保留备查。');saving.value=true;try{await applyProjectNumber(id.value);await load();proxy.$modal.msgSuccess('已申请正式编号');}finally{saving.value=false;}}
watch(()=>route.params.id,()=>{active.value='basic';load();});onMounted(load);
</script>
<style scoped>.zj-project-overview{margin-top:16px}.zj-overview-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px 20px;margin:16px 0 0}.zj-overview-field{min-width:0;padding:12px 0;border-bottom:1px solid var(--zj-color-border)}.zj-overview-field dt{font-size:12px;color:var(--zj-color-muted);margin-bottom:6px}.zj-overview-field dd{margin:0;font-weight:600;overflow-wrap:anywhere}@media(max-width:900px){.zj-overview-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:480px){.zj-overview-grid{grid-template-columns:1fr}}.zj-project-guidance{margin-top:16px}.zj-guidance-heading{display:flex;align-items:baseline;gap:12px;flex-wrap:wrap}.zj-guidance-heading h2{font-size:18px;margin:0}.zj-guidance-intro{margin:12px 0!important}.zj-guidance-list{list-style:none;margin:0;padding:0}.zj-guidance-item{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:12px 0;border-bottom:1px solid var(--zj-color-border)}.zj-guidance-item>span{display:flex;flex-direction:column;gap:4px;min-width:0}.zj-guidance-actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:16px}.zj-guidance-actions .el-button+.el-button{margin-left:0}@media(max-width:600px){.zj-guidance-item{align-items:flex-start}.zj-guidance-item .el-button{flex-shrink:0}.zj-guidance-actions .el-button{flex:1 1 auto}}.zj-project-save{display:flex;justify-content:space-between;align-items:center;gap:16px;border-top:1px solid #e5eaf2;padding-top:20px;margin-top:24px}.zj-workspace :deep(.el-form .el-select),.zj-workspace :deep(.el-form .el-date-editor){width:100%}.zj-workspace :deep(.el-tabs__item){height:48px}@media(max-width:900px){.zj-project-save{align-items:flex-start;flex-direction:column}}</style>
