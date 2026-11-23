<script setup>
import { onMounted,ref } from 'vue'
import { api } from './api'
const props=defineProps({permissions:{type:Array,default:()=>[]}})
const can=p=>props.permissions.includes(p)
const options=ref({customers:[],contracts:[],projects:[]}),rows=ref([]),summary=ref([]),selected=ref(null),error=ref(''),notice=ref(''),busy=ref(false),filter=ref('')
const today=new Date().toISOString().slice(0,10)
const form=ref({invoiceNo:'',direction:'receivable',counterpartyName:'',title:'',amount:'',issuedOn:today,dueOn:'',customerId:'',contractId:'',projectId:'',currency:'CNY'})
const payment=ref({paymentReference:'',amount:'',settledOn:today})
async function refresh(){try{rows.value=await api('/finance/invoices'+(filter.value?'?direction='+filter.value:''));summary.value=await api('/finance/invoices/summary')}catch(e){error.value=e.message}}
async function open(id){try{selected.value=await api('/finance/invoices/'+id);payment.value.amount=selected.value.outstanding_amount}catch(e){error.value=e.message}}
function reset(){selected.value=null;form.value={invoiceNo:'',direction:'receivable',counterpartyName:'',title:'',amount:'',issuedOn:today,dueOn:'',customerId:'',contractId:'',projectId:'',currency:'CNY'}}
async function create(){busy.value=true;error.value='';try{const b={...form.value};for(const k of ['customerId','contractId','projectId'])b[k]=b[k]?Number(b[k]):null;b.dueOn=b.dueOn||null;const r=await api('/finance/invoices',{method:'POST',body:JSON.stringify(b)});await open(r.id);await refresh();notice.value='发票已登记'}catch(e){error.value=e.message}finally{busy.value=false}}
async function action(name){busy.value=true;error.value='';try{const id=selected.value.id,body=name==='settlements'?payment.value:{};await api('/finance/invoices/'+id+'/'+name,{method:'POST',body:JSON.stringify(body)});await open(id);await refresh();notice.value='操作已完成'}catch(e){error.value=e.message}finally{busy.value=false}}
onMounted(async()=>{await Promise.all([refresh(),api('/finance/options').then(v=>{options.value=v}).catch(e=>{error.value=e.message})])})
</script>
<template>
<section class="panel"><div class="section-heading"><div><h2>发票与应收应付</h2><p>销项/进项发票、待收待付余额与分次核销</p></div><button v-if="can('invoice:write')" class="primary-button" @click="reset">登记发票</button></div>
<p v-if="error" class="message error" role="alert">{{error}}</p><p v-if="notice" class="message success" role="status">{{notice}}</p>
<div class="stat-grid"><article v-for="s in summary" :key="s.direction+s.currency" class="stat-card"><span>{{s.direction==='receivable'?'应收':'应付'}} · {{s.currency}}</span><b>{{s.outstanding_amount}}</b><small>已核销 {{s.settled_amount}} / 总额 {{s.total_amount}}</small></article></div>
<div class="finance-toolbar"><select v-model="filter" @change="refresh"><option value="">全部类型</option><option value="receivable">应收</option><option value="payable">应付</option></select><button class="secondary-button" @click="refresh">刷新</button></div>
<div class="table-wrap"><table><thead><tr><th>发票号</th><th>类型</th><th>往来方</th><th>金额</th><th>未结余额</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="r in rows" :key="r.id"><td>{{r.invoice_no}}</td><td>{{r.direction==='receivable'?'应收':'应付'}}</td><td>{{r.counterparty_name}}</td><td>{{r.amount}} {{r.currency}}</td><td>{{r.outstanding_amount}}</td><td>{{r.status}}</td><td><button class="secondary-button" @click="open(r.id)">详情</button></td></tr><tr v-if="!rows.length"><td colspan="7">暂无发票</td></tr></tbody></table></div>
</section>
<section v-if="!selected&&can('invoice:write')" class="panel"><h3>新建发票草稿</h3><form class="form-stack" @submit.prevent="create"><div class="create-grid">
<label>发票号码<input v-model="form.invoiceNo" required maxlength="100"></label><label>类型<select v-model="form.direction"><option value="receivable">应收（销项）</option><option value="payable">应付（进项）</option></select></label>
<label>往来单位<input v-model="form.counterpartyName" required maxlength="240"></label><label>业务标题<input v-model="form.title" required maxlength="240"></label>
<label>金额<input v-model="form.amount" type="number" step="0.01" min="0.01" required></label><label>币种<input v-model="form.currency" maxlength="3" required></label>
<label>开票日期<input v-model="form.issuedOn" type="date" required></label><label>到期日期<input v-model="form.dueOn" type="date"></label>
<label>客户（可选）<select v-model="form.customerId"><option value="">不关联</option><option v-for="x in options.customers" :key="x.id" :value="x.id">{{x.name} · {{x.id}}</option></select></label><label>合同（可选）<select v-model="form.contractId"><option value="">不关联</option><option v-for="x in options.contracts.filter(x=>!form.customerId||x.customer_id===Number(form.customerId))" :key="x.id" :value="x.id">{{x.title} · {{x.id}}</option></select></label><label>项目（可选）<select v-model="form.projectId"><option value="">不关联</option><option v-for="x in options.projects.filter(x=>(!form.customerId||x.customer_id===Number(form.customerId))&&(!form.contractId||x.contract_id===Number(form.contractId)))" :key="x.id" :value="x.id">{{x.name} · {{x.id}}</option></select></label></div><button class="primary-button" :disabled="busy">登记发票</button></form></section>
<section v-if="selected" class="panel"><div class="section-heading"><h3>{{selected.invoice_no}} · {{selected.status}}</h3><button class="secondary-button" @click="selected=null">关闭详情</button></div>
<p>总金额 {{selected.amount}} {{selected.currency}} · 已核销 {{selected.settled_amount}} · 未结 {{selected.outstanding_amount}}</p>
<div class="report-actions"><button v-if="can('invoice:approve')&&selected.status==='draft'" class="primary-button" :disabled="busy" @click="action('approve')">审核入账</button><button v-if="can('invoice:approve')&&['draft','open'].includes(selected.status)" class="secondary-button" :disabled="busy" @click="action('void')">作废</button></div>
<form v-if="can('invoice:settle')&&['open','partial'].includes(selected.status)" class="form-stack" @submit.prevent="action('settlements')"><h4>登记收付款核销</h4><div class="create-grid"><label>流水号<input v-model="payment.paymentReference" required maxlength="120"></label><label>核销金额<input v-model="payment.amount" type="number" step="0.01" min="0.01" required></label><label>收付款日期<input v-model="payment.settledOn" type="date" required></label></div><button class="primary-button" :disabled="busy">确认核销</button></form>
<h4>核销明细</h4><p v-for="x in selected.settlements" :key="x.id" class="log-line">{{x.settled_on}} · {{x.payment_reference}} · {{x.amount}}</p><h4>操作记录</h4><p v-for="x in selected.events" :key="x.id" class="log-line">{{x.created_at}} · {{x.action}} · {{x.details}}</p></section>
</template>
