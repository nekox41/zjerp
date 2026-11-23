<script setup>
import {onMounted,ref} from 'vue'
import {api} from './api'
const data=ref(null),customerId=ref(''),projectId=ref(''),error=ref(''),busy=ref(false)
async function load(){busy.value=true;error.value='';try{const params=new URLSearchParams();if(customerId.value)params.set('customerId',customerId.value);if(projectId.value)params.set('projectId',projectId.value);data.value=await api('/business/dashboard'+(params.size?'?'+params:''))}catch(e){error.value=e.message}finally{busy.value=false}}
onMounted(load)
</script>
<template>
<section class="panel"><div class="section-heading"><div><h2>经营驾驶舱</h2><p>按客户、项目查看合同、执行进度及财务汇总</p></div><button class="secondary-button" @click="load" :disabled="busy">刷新指标</button></div>
<form class="create-grid compact" @submit.prevent="load"><label>客户 ID（可选）<input v-model="customerId" type="number" min="1"></label><label>项目 ID（可选）<input v-model="projectId" type="number" min="1"></label><button class="primary-button">应用筛选</button></form>
<p v-if="error" class="message error">{{error}}</p>
<template v-if="data"><div class="stat-grid"><article class="stat-card"><span>合同数量</span><b>{{data.contracts.count}}</b><small>合同总额 {{data.contracts.total_amount}}</small></article><article class="stat-card"><span>项目数量</span><b>{{data.projects.count}}</b><small>执行中 {{data.projects.active_count||0}}</small></article><article class="stat-card"><span>项目平均进度</span><b>{{Number(data.projects.avg_progress||0).toFixed(1)}}%</b><small>按项目平均</small></article></div>
<h3>应收与应付</h3><div class="table-wrap"><table><thead><tr><th>类型</th><th>币种</th><th>票据数</th><th>总额</th><th>已核销</th><th>未结</th><th>逾期</th></tr></thead><tbody><tr v-for="x in data.invoices||[]" :key="x.direction+x.currency"><td>{{x.direction==='receivable'?'应收':'应付'}}</td><td>{{x.currency}}</td><td>{{x.invoice_count}}</td><td>{{x.total_amount}}</td><td>{{x.settled_amount}}</td><td>{{x.outstanding_amount}}</td><td>{{x.overdue_count}}</td></tr><tr v-if="!data.invoices?.length"><td colspan="7">暂无可查看的财务指标</td></tr></tbody></table></div>
<h3>费用报账</h3><div class="table-wrap"><table><thead><tr><th>币种</th><th>已批准报账数</th><th>费用总额</th><th>已支付</th></tr></thead><tbody><tr v-for="x in data.expenses||[]" :key="x.currency"><td>{{x.currency}}</td><td>{{x.claim_count}}</td><td>{{x.total_amount}}</td><td>{{x.paid_amount}}</td></tr><tr v-if="!data.expenses?.length"><td colspan="4">暂无可查看的费用指标</td></tr></tbody></table></div>
<h3>项目状态</h3><p v-for="x in data.projectsByStatus" :key="x.status" class="log-line">{{x.status}}：{{x.count}} 个项目</p>
</template></section>
</template>
