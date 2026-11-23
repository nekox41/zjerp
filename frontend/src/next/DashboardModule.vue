<script setup>
import {onMounted,ref} from 'vue'
import {api} from './api'
const data=ref(null),busy=ref(false),error=ref('')
const labels={planned:'计划中',in_progress:'进行中',paused:'暂停',completed:'已完成',cancelled:'已取消',open:'待整改',resolved:'待复核',closed:'已关闭',submitted:'待审核',reviewing:'审核中',returned:'已退回',approved:'已通过',issued:'已签发',archived:'已归档',draft:'草稿'}
async function load(){busy.value=true;error.value='';try{data.value=await api('/business/dashboard')}catch(e){error.value=e.message}finally{busy.value=false}}
onMounted(load)
</script>
<template>
<section class="operations-dashboard">
  <div class="dash-head"><div><span class="eyebrow">OPERATIONS OVERVIEW</span><h2>业务工作台</h2><p>关注项目执行、现场服务、整改、报告审核和维保资料。</p></div><button class="secondary-button" @click="load" :disabled="busy">刷新</button></div>
  <p v-if="error" class="message error">{{error}}</p>
  <template v-if="data">
    <div class="stat-grid">
      <article class="stat-card"><span>有效客户</span><b>{{data.summary.customers}}</b><small>客户档案</small></article>
      <article class="stat-card"><span>合作方</span><b>{{data.summary.partners}}</b><small>独立合作方档案</small></article>
      <article class="stat-card"><span>在职员工</span><b>{{data.summary.employees}}</b><small>员工与账号分离</small></article>
      <article class="stat-card"><span>履行中合同</span><b>{{data.summary.active_contracts}}</b><small>合同期限独立保留</small></article>
      <article class="stat-card"><span>进行中项目</span><b>{{data.summary.active_projects}}</b><small>跨合同持续跟踪</small></article>
      <article class="stat-card"><span>计划现场服务</span><b>{{data.summary.planned_visits}}</b><small>等待执行</small></article>
      <article class="stat-card"><span>未关闭整改</span><b>{{data.summary.open_issues}}</b><small>待整改或整改中</small></article>
      <article class="stat-card"><span>报告审核队列</span><b>{{data.summary.reports_in_review}}</b><small>待审核或审核中</small></article>
      <article class="stat-card"><span>待寄维保卡</span><b>{{data.summary.maintenance_cards_pending}}</b><small>需核对项目资料</small></article>
    </div>
    <div class="dash-grid">
      <section class="panel"><h3>待处理整改</h3><div v-for="x in data.pendingIssues" :key="x.id" class="dash-row"><div><b>{{x.title}}</b><small>{{x.project_no}} · {{x.project_name}} · {{x.owner_name||'未分配'}}</small></div><span class="dash-severity" :class="x.severity">{{x.severity}} · {{labels[x.status]}}</span></div><p v-if="!data.pendingIssues.length" class="empty">没有待处理整改</p></section>
      <section class="panel"><h3>报告审核与退回</h3><div v-for="x in data.reportsInQueue" :key="x.id" class="dash-row"><div><b>{{x.report_no}} · {{x.title}}</b><small>{{x.customer_name}} · {{x.project_name}}</small></div><span class="tag">{{labels[x.status]}}</span></div><p v-if="!data.reportsInQueue.length" class="empty">报告队列为空</p></section>
      <section class="panel"><h3>计划现场服务</h3><div v-for="x in data.upcomingVisits" :key="x.id" class="dash-row"><div><b>{{x.title}}</b><small>{{x.project_no}} · {{x.project_name}}</small></div><time>{{x.performed_on}}</time></div><p v-if="!data.upcomingVisits.length" class="empty">没有待执行现场服务</p></section>
      <section class="panel"><h3>维保资料提醒</h3><div v-for="x in data.maintenanceAttention" :key="x.id" class="dash-row"><div><b>{{x.project_no}} · {{x.name}}</b><small>{{x.customer_name}} · 合同结束 {{x.current_contract_end||'未关联'}}</small></div><span class="tag">{{!x.honeybee_uploaded?'小蜜蜂待上传':!x.documents_ready?'资料待完善':!x.maintenance_card_sent?'公示卡待寄':'合同临期'}}</span></div><p v-if="!data.maintenanceAttention.length" class="empty">没有待处理维保资料</p></section>
    </div>
    <div class="panel"><h3>业务状态分布</h3><div class="status-distribution"><div><b>项目</b><span v-for="x in data.projectStatuses" :key="x.status">{{labels[x.status]||x.status}}：{{x.count}}</span></div><div><b>问题整改</b><span v-for="x in data.issueStatuses" :key="x.status">{{labels[x.status]||x.status}}：{{x.count}}</span></div><div><b>报告</b><span v-for="x in data.reportStatuses" :key="x.status>{{labels[x.status]||x.status}}：{{x.count}}</span></div></div></div>
  </template>
</section>
</template>
<style scoped>
.operations-dashboard{display:grid;gap:18px}.dash-head{display:flex;align-items:end;justify-content:space-between}.dash-head h2{margin:5px 0;font-size:23px;color:#203149}.dash-head p{font-size:12px;color:#7d889a;margin:0}.stat-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.dash-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:15px}.panel h3{font-size:14px;margin:0 0 12px}.dash-row{display:flex;justify-content:space-between;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid #edf0f5}.dash-row>div{display:grid;gap:5px;min-width:0}.dash-row b{font-size:11px;color:#3e4e66}.dash-row small,.dash-row time{font-size:10px;color:#929daf}.tag{font-size:10px;padding:5px 8px;border-radius:20px;background:#edf2fd;color:#315fca;white-space:nowrap}.dash-severity{font-size:10px;white-space:nowrap}.dash-severity.critical,.dash-severity.high{color:#b44343}.dash-severity.medium{color:#9a742e}.dash-severity.low{color:#468568}.status-distribution{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:15px}.status-distribution>div{display:grid;gap:8px}.status-distribution b{font-size:12px}.status-distribution span{font-size:11px;color:#7c889a}.empty{font-size:11px;color:#a0a9b6;text-align:center;padding:18px}@media(max-width:850px){.stat-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.dash-grid{grid-template-columns:1fr}.status-distribution{grid-template-columns:1fr}}
</style>
