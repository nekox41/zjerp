<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, downloadFile } from './api'

const props=defineProps({permissions:{type:Array,default:()=>[]},userId:{type:Number,required:true}})
const can=(p)=>props.permissions.includes(p)
const items=ref([]),selected=ref(null),error=ref(''),notice=ref(''),busy=ref(false),filter=ref('')
const form=ref({claimNo:'',title:'',projectId:'',lines:[{description:'',category:'travel',incurredOn:new Date().toISOString().slice(0,10),amount:''}]})
const reason=ref(''),payment=ref({paymentReference:'',paidAmount:''})
const own=computed(()=>selected.value?.applicant_identity_id===props.userId)
const editable=computed(()=>own.value&&['draft','returned'].includes(selected.value?.status))
const money=(n)=>Number(n||0).toFixed(2)
async function refresh(){try{items.value=await api('/finance/expenses'+(filter.value?'?status='+encodeURIComponent(filter.value):''))}catch(e){error.value=e.message}}
async function open(id){try{selected.value=await api('/finance/expenses/'+id);form.value={claimNo:selected.value.claim_no,title:selected.value.title,projectId:selected.value.project_id||'',lines:selected.value.lines.map(x=>({description:x.description,category:x.category,incurredOn:x.incurred_on,amount:x.amount}))};payment.value.paidAmount=selected.value.total_amount}catch(e){error.value=e.message}}
function addLine(){form.value.lines.push({description:'',category:'other',incurredOn:new Date().toISOString().slice(0,10),amount:''})}
function reset(){selected.value=null;form.value={claimNo:'',title:'',projectId:'',lines:[{description:'',category:'travel',incurredOn:new Date().toISOString().slice(0,10),amount:''}]}}
async function save(){busy.value=true;error.value='';try{const f=form.value,body={title:f.title,lines:f.lines.map(x=>({...x,amount:String(x.amount)}))};if(selected.value){body.expectedVersionNo=selected.value.version_no;await api('/finance/expenses/'+selected.value.id,{method:'PUT',body:JSON.stringify(body)});await open(selected.value.id)}else{body.claimNo=f.claimNo;if(f.projectId)body.projectId=Number(f.projectId);const result=await api('/finance/expenses',{method:'POST',body:JSON.stringify(body)});await open(result.id)}await refresh();notice.value='报账单已保存'}catch(e){error.value=e.message}finally{busy.value=false}}
async function action(name){if(!selected.value)return;busy.value=true;error.value='';try{const id=selected.value.id,body=name==='return'?{reason:reason.value}:name==='pay'?{paymentReference:payment.value.paymentReference,paidAmount:payment.value.paidAmount}:{};await api('/finance/expenses/'+id+'/'+name,{method:'POST',body:JSON.stringify(body)});await open(id);await refresh();reason.value='';notice.value='状态更新成功'}catch(e){error.value=e.message}finally{busy.value=false}}
async function upload(e){const file=e.target.files?.[0];if(!file||!selected.value)return;busy.value=true;try{const data=new FormData();data.append('file',file);const f=await api('/files',{method:'POST',body:data});await api('/finance/expenses/'+selected.value.id+'/attachments',{method:'POST',body:JSON.stringify({fileId:f.id})});await open(selected.value.id);notice.value='附件已关联'}catch(x){error.value=x.message}finally{busy.value=false;e.target.value=''}}
onMounted(refresh)
</script>
<template>
<section class="panel finance-panel">
  <div class="section-heading"><div><h2>费用报账与财务审批</h2><p>按项目归集费用，保留明细、附件与付款流水</p></div><button v-if="can('expense:write')" class="primary-button" @click="reset">新建报账</button></div>
  <p v-if="error" class="message error" role="alert">{{error}}</p><p v-if="notice" class="message success" role="status">{{notice}}</p>
  <div class="finance-toolbar"><select v-model="filter" @change="refresh"><option value="">全部状态</option><option v-for="s in ['draft','submitted','returned','approved','paid']" :key="s">{{s}}</option></select><button class="secondary-button" @click="refresh">刷新</button></div>
  <div class="table-wrap"><table><thead><tr><th>报账编号</th><th>标题</th><th>金额</th><th>状态</th><th>申请人</th><th>操作</th></tr></thead><tbody><tr v-for="x in items" :key="x.id"><td>{{x.claim_no}}</td><td>{{x.title}}</td><td>{{money(x.total_amount)}} {{x.currency}}</td><td>{{x.status}}</td><td>{{x.applicant_identity_id}}</td><td><button class="secondary-button" @click="open(x.id)">查看</button></td></tr><tr v-if="!items.length"><td colspan="6">暂无报账单</td></tr></tbody></table></div>
</section>
<section v-if="selected || can('expense:write')" class="panel finance-panel">
  <h3>{{selected?selected.claim_no+' · '+selected.status:'创建费用报账单'}}</h3>
  <p v-if="selected">总额：{{money(selected.total_amount)}} {{selected.currency}} · 版本：{{selected.version_no}}</p>
  <form v-if="!selected || editable" class="form-stack" @submit.prevent="save">
    <div class="create-grid compact"><label v-if="!selected">报账编号<input v-model="form.claimNo" required maxlength="80"></label><label>标题<input v-model="form.title" required maxlength="240"></label><label v-if="!selected">项目 ID（可选）<input v-model="form.projectId" type="number" min="1"></label></div>
    <h4>费用明细</h4><div v-for="(line,i) in form.lines" :key="i" class="create-grid compact"><label>费用说明<input v-model="line.description" required maxlength="500"></label><label>类别<select v-model="line.category"><option value="travel">差旅</option><option value="transport">交通</option><option value="materials">材料</option><option value="other">其他</option></select></label><label>发生日期<input v-model="line.incurredOn" type="date" required></label><label>金额<input v-model="line.amount" type="number" min="0.01" step="0.01" required></label><button type="button" class="secondary-button" :disabled="form.lines.length===1" @click="form.lines.splice(i,1)">删除明细</button></div>
    <div class="report-actions"><button type="button" class="secondary-button" @click="addLine">添加明细</button><button class="primary-button" :disabled="busy">保存报账</button></div>
  </form>
  <div v-if="selected">
    <h4>附件</h4><input v-if="editable" type="file" accept=".pdf,.png,.jpg,.jpeg,.txt,.docx" @change="upload"><p v-for="a in selected.attachments" :key="a.id" class="log-line">{{a.original_name}} · {{a.sha256}} <button class="secondary-button" @click="downloadFile(a.file_id,a.original_name)">下载</button></p>
    <div class="report-actions"><button v-if="own&&selected.status==='draft'" class="primary-button" :disabled="busy" @click="action('submit')">提交审批</button><button v-if="can('expense:approve')&&selected.status==='submitted'&&!own" class="primary-button" :disabled="busy" @click="action('approve')">批准</button><label v-if="can('expense:approve')&&selected.status==='submitted'&&!own">退回原因<input v-model="reason"></label><button v-if="can('expense:approve')&&selected.status==='submitted'&&!own" class="secondary-button" :disabled="busy||!reason.trim()" @click="action('return')">退回修改</button><label v-if="can('expense:pay')&&selected.status==='approved'&&!own">付款流水号<input v-model="payment.paymentReference"></label><label v-if="can('expense:pay')&&selected.status==='approved'&&!own">付款金额<input v-model="payment.paidAmount" type="number" min="0.01" step="0.01"></label><button v-if="can('expense:pay')&&selected.status==='approved'&&!own" class="primary-button" :disabled="busy||!payment.paymentReference.trim()" @click="action('pay')">登记全额付款</button></div>
    <p v-if="selected.return_reason" class="message error">退回原因：{{selected.return_reason}}</p><h4>审批与付款记录</h4><p v-for="e in selected.events" :key="e.id" class="log-line">{{e.created_at}} · {{e.actor_name}} · {{e.from_status||'新建'}} → {{e.to_status}} · {{e.reason||''}}</p>
  </div>
</section>
</template>
