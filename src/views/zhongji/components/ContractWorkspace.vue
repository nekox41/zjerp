<template>
 <main class="zj-workspace">
  <header class="zj-heading"><div><div class="zj-eyebrow">众基消防 / 合同管理</div><h1>{{title}}</h1><p>归集合同信息、付款安排与文件，让每一笔业务清晰可查。</p></div><el-button type="primary" icon="Plus" @click="create" v-hasPermi="['management:contract:add']">新建合同</el-button></header>
  <section class="zj-card"><div class="zj-filters"><el-input v-model="query.contractName" aria-label="合同名称" placeholder="搜索合同名称" clearable @keyup.enter="search"/><el-input v-model="query.contractId" aria-label="合同编号" placeholder="合同编号" clearable @keyup.enter="search"/><el-select v-model="query.ownership" placeholder="全部归属" aria-label="合同归属" clearable><el-option label="待确认" value="pending"/><el-option label="公司" value="company"/><el-option label="中间人" value="intermediary"/></el-select><el-button type="primary" icon="Search" @click="search">查询</el-button><el-button @click="reset">清空</el-button><el-button icon="Refresh" aria-label="刷新列表" @click="load"/></div></section>
  <section class="zj-card"><div class="zj-summary"><span>共 {{total}} 份合同</span><span>金额单位：元</span></div><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon/>
   <el-table v-loading="loading" :data="rows" empty-text="暂无符合条件的合同，请调整筛选或新建合同">
    <el-table-column label="合同" min-width="280"><template #default="{row}"><button class="zj-name" @click="show(row,false)">{{row.contractName||'未填写'}}</button><div class="zj-sub">{{row.contractId||'未填写编号'}} <el-tag v-if="row.classificationPending" size="small" type="warning">分类待确认</el-tag></div></template></el-table-column>
    <el-table-column label="合同金额" width="155" align="right"><template #default="{row}"><span class="zj-money">{{money(row.amount)}}</span></template></el-table-column>
    <el-table-column label="合同归属" min-width="150"><template #default="{row}"><el-tag :type="row.ownership==='pending'?'warning':'info'">{{owner(row)}}</el-tag></template></el-table-column>
    <el-table-column label="合同期限" width="200"><template #default="{row}"><div>{{row.startTime||'开始日期未填写'}}</div><div class="zj-sub">至 {{row.endTime||'未填写'}}</div></template></el-table-column>
    <el-table-column prop="manager" label="负责人" min-width="100" show-overflow-tooltip/>
    <el-table-column label="操作" width="150" fixed="right"><template #default="{row}"><el-button link type="primary" @click="show(row,true)" v-hasPermi="['management:contract:edit']">编辑</el-button><el-button link type="danger" @click="remove(row)" v-hasPermi="['management:contract:remove']">删除</el-button></template></el-table-column>
   </el-table><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @size-change="search" @current-change="load"/>
  </section>
  <el-drawer v-model="open" :title="editing?(form.id?'编辑合同':'新建合同'):'合同详情'" :size="drawerSize" class="zj-drawer" :before-close="beforeClose" :close-on-click-modal="false">
   <el-alert v-if="form.classificationPending" title="此历史合同原类型为验收或未填写，已归入其他技术服务合同，分类待确认。" type="warning" :closable="false"/>
   <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="!editing || saving">
    <h3>基本信息</h3><div class="zj-grid"><el-form-item label="合同编号" prop="contractId"><el-input v-model="form.contractId" maxlength="255"/></el-form-item><el-form-item label="合同名称" prop="contractName"><el-input v-model="form.contractName" maxlength="255"/></el-form-item><el-form-item label="合同金额（元）" prop="amount"><el-input v-model="form.amount" inputmode="decimal" placeholder="例如 12000.00"/></el-form-item><el-form-item label="合同负责人"><el-input v-model="form.manager" maxlength="255"/></el-form-item><el-form-item label="开始日期"><el-date-picker v-model="form.startTime" value-format="YYYY-MM-DD"/></el-form-item><el-form-item label="结束日期"><el-date-picker v-model="form.endTime" value-format="YYYY-MM-DD"/></el-form-item></div>
    <h3>合同归属</h3><div class="zj-grid"><el-form-item label="归属"><el-select v-model="form.ownership" @change="form.intermediaryId=null"><el-option label="待确认" value="pending"/><el-option label="公司" value="company"/><el-option label="中间人" value="intermediary"/></el-select></el-form-item><el-form-item v-if="form.ownership==='intermediary'" label="中间人" prop="intermediaryId"><el-select v-model="form.intermediaryId" filterable remote :remote-method="searchPeople" :loading="peopleLoading" placeholder="输入姓名搜索"><el-option v-for="p in people" :key="p.id" :label="p.name" :value="p.id"/></el-select></el-form-item></div>
    <h3>付款计划</h3><p v-if="!plans.length" class="zj-sub">尚未填写付款安排</p><div v-for="(plan,index) in plans" :key="index" class="zj-plan"><el-input v-model="plan.name" aria-label="付款阶段名称" placeholder="阶段名称" maxlength="100"/><el-input v-model="plan.amount" inputmode="decimal" aria-label="计划付款金额" placeholder="金额（元）"/><el-date-picker v-model="plan.dueDate" aria-label="计划付款日期" value-format="YYYY-MM-DD" placeholder="计划日期"/><el-button v-if="editing" text type="danger" icon="Delete" aria-label="移除此付款阶段" @click="plans.splice(index,1)"/></div><el-button v-if="editing" text type="primary" icon="Plus" @click="plans.push({name:'',amount:'',dueDate:null})">添加付款阶段</el-button><p class="zj-sub">计划合计：{{money(planTotal)}} 元</p>
    <h3>附件与备注</h3><el-form-item label="合同附件"><FileUpload v-model="form.contractFile" :disabled="!editing" :limit="20" :file-type="['pdf','png','jpg','jpeg','doc','docx','zip']" :file-size="200"/></el-form-item><el-form-item label="合同备注"><el-input v-model="form.remark" type="textarea" :rows="4" maxlength="5000" show-word-limit/></el-form-item>
   </el-form>
   <template #footer><el-button @click="close">{{editing?'取消':'关闭'}}</el-button><el-button v-if="editing" type="primary" :loading="saving" @click="save">保存合同</el-button></template>
  </el-drawer>
 </main>
</template>
<script setup>
import {contracts,contract,saveContract,removeContract,intermediaryOptions} from '@/api/zhongji';
import FileUpload from '@/components/FileUpload/index.vue';
import '@/assets/styles/zhongji.scss';
const props=defineProps({contractType:{type:String,required:true}});
const {proxy}=getCurrentInstance();const title=computed(()=>({WB:'维护保养合同',JC:'消防检测合同',PD:'消防评定合同',PG:'消防评估合同',QT:'其他技术服务合同'}[props.contractType]));
const query=reactive({pageNum:1,pageSize:20,contractName:'',contractId:'',ownership:''});const rows=ref([]),total=ref(0),loading=ref(false),error=ref(''),open=ref(false),editing=ref(false),saving=ref(false),form=ref({}),plans=ref([]),people=ref([]),peopleLoading=ref(false),formRef=ref();let snapshot='',sequence=0,originalAmount=null,originalAmountInput='';
const drawerSize='min(780px, 96vw)';const money=value=>value===null||value===undefined||value===''?'未填写':Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2});
const owner=r=>r.ownership==='company'?'公司':r.ownership==='intermediary'?(r.intermediaryName||'中间人'):'待确认';const planTotal=computed(()=>plans.value.reduce((sum,p)=>sum+(Number(p.amount)||0),0));
const rules={contractId:[{required:true,message:'请填写合同编号',trigger:'blur'}],contractName:[{required:true,message:'请填写合同名称',trigger:'blur'}],intermediaryId:[{validator:(_r,v,cb)=>form.value.ownership==='intermediary'&&!v?cb(new Error('请选择中间人')):cb(),trigger:'change'}]};
function current(){return JSON.stringify([form.value,plans.value]);}
async function load(){const seq=++sequence;loading.value=true;error.value='';try{const r=await contracts({...query,contractType:props.contractType});if(seq===sequence){rows.value=r.rows;total.value=r.total;}}catch(e){if(seq===sequence){rows.value=[];total.value=0;error.value='合同加载失败，请点击刷新重试';}}finally{if(seq===sequence)loading.value=false;}}
function search(){query.pageNum=1;load();}function reset(){Object.assign(query,{pageNum:1,contractName:'',contractId:'',ownership:''});load();}
function create(){originalAmount=null;originalAmountInput='';form.value={contractType:props.contractType,contractId:'',contractName:'',amount:null,ownership:'pending',intermediaryId:null,contractFile:'',remark:'',startTime:null,endTime:null};plans.value=[];people.value=[];editing.value=true;snapshot=current();open.value=true;}
async function show(row,edit){try{const r=await contract(row.id);form.value=r.data;originalAmount=form.value.amount;originalAmountInput=originalAmount===null?null:Number(originalAmount).toFixed(2);form.value.amount=originalAmountInput;plans.value=form.value.paymentPlan?JSON.parse(form.value.paymentPlan):[];people.value=form.value.intermediaryId?[{id:form.value.intermediaryId,name:form.value.intermediaryName}]:[];editing.value=edit;snapshot=current();open.value=true;}catch(e){proxy.$modal.msgError('无法加载合同详情');}}
let personSequence=0;async function searchPeople(name){const n=++personSequence;peopleLoading.value=true;try{const r=await intermediaryOptions({name,pageNum:1,pageSize:20});if(n===personSequence)people.value=r.rows;}finally{if(n===personSequence)peopleLoading.value=false;}}
async function beforeClose(done){if(saving.value)return;if(editing.value&&current()!==snapshot){try{await proxy.$modal.confirm('有未保存的修改，确认放弃？');}catch{return;}}done();}function close(){beforeClose(()=>open.value=false);}
async function save(){if(!await formRef.value.validate().catch(()=>false))return;
 const validAmount=v=>v===null||v===''||/^\d{1,13}(\.\d{1,2})?$/.test(String(v));
 if(form.value.amount!==null && !validAmount(form.value.amount)){proxy.$modal.msgError('金额应为非负数，最多两位小数');return;}
 if(plans.value.some(p=>!p.name.trim()||p.amount===''||!validAmount(p.amount))){proxy.$modal.msgError('请填写付款阶段名称及有效金额');return;}
 if(form.value.amount!==null&&planTotal.value>Number(form.value.amount)+0.000001){proxy.$modal.msgError('计划合计不能超过合同金额');return;}
 saving.value=true;try{await saveContract({...form.value,contractType:props.contractType,amount:form.value.id&&form.value.amount===originalAmountInput?originalAmount:form.value.amount,paymentPlan:JSON.stringify(plans.value)});open.value=false;proxy.$modal.msgSuccess('合同已保存');await load();}finally{saving.value=false;}}
async function remove(row){await proxy.$modal.confirm(`确认删除合同“${row.contractName}”？附件原件按系统保留策略保存。`);await removeContract(row.id);proxy.$modal.msgSuccess('已删除');if(rows.value.length===1&&query.pageNum>1)query.pageNum--;load();}
onMounted(load);
</script>
