<script setup>
import FormDialog from './FormDialog.vue'
import FileDropzone from './FileDropzone.vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, downloadFile } from './api'
import MaintenanceDocuments from './MaintenanceDocuments.vue'
import PartnerDirectory from './PartnerDirectory.vue'
const props=defineProps({permissions:{type:Array,default:()=>[]},destination:{type:Object,default:null}})
const emit=defineEmits(['navigate'])
const can=p=>props.permissions.includes(p)
const visibleTabs=computed(()=>[
  {id:'partners',name:'中间人档案',allowed:can('partner:read')},
  {id:'links',name:'合同与项目关联',allowed:can('contract:read')},
  {id:'leadership',name:'项目人员',allowed:can('project:read')&&can('project:write')},
  {id:'maintenance',name:'维保项目',allowed:can('maintenance:read')&&can('project:read')}
].filter(t=>t.allowed))
const tab=ref(props.destination?.section||'partners'),busy=ref(false),error=ref(''),notice=ref('')
const partners=ref([]),contracts=ref([]),projects=ref([]),employees=ref([])
const maintenance=ref(null),contractDetail=ref(null)
const linkForm=ref({contractId:'',partnerId:'',sharePercent:'',notes:'',fileType:'contract'})
const projectLink=ref({projectId:'',contractId:'',relationType:'service'})
const leadership=ref({projectId:'',managerEmployeeId:'',technicalLeadEmployeeId:''})
const maintenanceForm=ref({projectId:'',currentContractId:'',honeybeeContractUploaded:false,honeybeeProjectCreated:false,projectMaterialsCompleted:false,publicCardStatus:'not_started',notes:''})
const visitForm=ref({visitDate:new Date().toISOString().slice(0,10),contractId:'',employeeIds:[],notes:''})
const renewalForm=ref({externalContractNo:'',title:'',amount:'0',signedOn:'',startsOn:'',endsOn:'',notes:''})
const renewalFiles=ref([])
const renewalReservation=ref(null),renewalOpen=ref(false),renewalReserving=ref(false)
let disposed=false
const renewalHistoryDetail=ref(null)
const documentContractId=ref(null)
async function openRenewalHistory(contractId){
  try{renewalHistoryDetail.value=await api('/business/contracts/'+contractId)}catch(e){error.value=e.message}
}
async function releaseRenewalReservation(reservation=renewalReservation.value){
  if(!reservation?.id)return
  try{await api('/business/number-reservations/'+encodeURIComponent(reservation.id),{method:'DELETE'})}catch{}
}
async function beginRenewal(){
  if(!maintenanceForm.value.projectId||renewalReserving.value||busy.value||renewalOpen.value)return
  error.value=''
  renewalForm.value={externalContractNo:'',title:'',amount:'0',signedOn:'',startsOn:'',endsOn:'',notes:''}
  renewalFiles.value=[]
  renewalReserving.value=true
  try{
    const reservation=await api('/business/number-reservations',{method:'POST',body:JSON.stringify({serviceType:'maintenance',purpose:'maintenance_renewal'})})
    if(disposed){await releaseRenewalReservation(reservation);return}
    renewalReservation.value=reservation
    renewalOpen.value=true
  }catch(e){if(!disposed)error.value=e.message}
  finally{renewalReserving.value=false}
}
async function closeRenewal(){
  if(busy.value)return
  renewalOpen.value=false
  const reservation=renewalReservation.value
  renewalReservation.value=null
  await releaseRenewalReservation(reservation)
  renewalForm.value={externalContractNo:'',title:'',amount:'0',signedOn:'',startsOn:'',endsOn:'',notes:''}
  renewalFiles.value=[]
}
async function createRenewal(){
  if(!maintenanceForm.value.projectId)return
  if(!renewalReservation.value?.id)throw new Error('编号预留已失效，请关闭窗口后重新新建续签合同')
  busy.value=true;error.value=''
  try{
    const fileIds=[]
    for(const file of renewalFiles.value){
      const fd=new FormData();fd.append('file',file)
      fileIds.push((await api('/files',{method:'POST',body:fd})).id)
    }
    const contract=await api('/business/projects/'+maintenanceForm.value.projectId+'/maintenance-renewals',{
      method:'POST',body:JSON.stringify({...renewalForm.value,fileIds,numberReservationId:renewalReservation.value.id})
    })
    renewalReservation.value=null
    renewalForm.value={externalContractNo:'',title:'',amount:'0',signedOn:'',startsOn:'',endsOn:'',notes:''}
    renewalFiles.value=[]
    await load()
    await openMaintenance(Number(maintenanceForm.value.projectId))
    notice.value='续签合同草稿已创建并关联原项目，旧合同与维保卡已保留。新合同需要确认后手动指定为当前有效合同。'
    await openRenewalHistory(contract.id)
  }catch(e){error.value=e.message}finally{busy.value=false}
}

const maintenanceProjects=computed(()=>projects.value.filter(p=>p.service_type==='maintenance'))
const engineers=computed(()=>employees.value.filter(e=>e.employee_type==='engineer'&&e.status==='active'&&e.has_certificate))
async function load(){
  error.value=''
  try{
    const [p,c,pr,e]=await Promise.all([
      (can('partner:read')&&tab.value!=='partners')?api('/business/partners'):Promise.resolve([]),
      can('contract:read')?api('/business/contracts'):Promise.resolve([]),
      can('project:read')?api('/business/projects'):Promise.resolve([]),
      can('employee:read')?api('/business/employees'):Promise.resolve([])
    ])
    partners.value=p;contracts.value=c;projects.value=pr;employees.value=e
  }catch(e){error.value=e.message}
}
async function openContractDetail(id){
  if(!id){contractDetail.value=null;return}
  try{contractDetail.value=await api('/business/contracts/'+id)}catch(e){error.value=e.message}
}
async function linkPartner(){
  try{await api('/business/contracts/'+linkForm.value.contractId+'/partners',{method:'POST',body:JSON.stringify({partnerId:linkForm.value.partnerId,sharePercent:linkForm.value.sharePercent,notes:linkForm.value.notes})});notice.value='合同合作方及分成比例已保存';await load();await openContractDetail(Number(linkForm.value.contractId))}
  catch(e){error.value=e.message}
}
async function unlinkPartner(partnerId){
  const contractId=contractDetail.value?.id
  if(!contractId||!confirm('确定解除此合作方与当前合同的关联？'))return
  busy.value=true;error.value=''
  try{
    await api('/business/contracts/'+contractId+'/partners/'+partnerId,{method:'DELETE'})
    await openContractDetail(contractId)
    await load()
    notice.value='已解除合作关系，并重新计算合同分成'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function unlinkProject(projectId){
  const contractId=contractDetail.value?.id
  if(!contractId||!confirm('确认解除草稿合同与此项目的关联？项目本身不会被删除。'))return
  busy.value=true;error.value=''
  try{
    await api('/business/contracts/'+contractId+'/projects/'+projectId,{method:'DELETE'})
    await load();await openContractDetail(contractId)
    notice.value='合同项目关联已解除'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function linkProject(){
  try{await api('/business/contracts/'+projectLink.value.contractId+'/projects',{method:'POST',body:JSON.stringify({projectId:projectLink.value.projectId,relationType:projectLink.value.relationType})});notice.value='合同与项目关联已保存';await openContractDetail(Number(projectLink.value.contractId))}
  catch(e){error.value=e.message}
}
async function loadLeadership(id){
  if(!id){leadership.value.managerEmployeeId='';leadership.value.technicalLeadEmployeeId='';return}
  try{
    const p=await api('/business/projects/'+id)
    leadership.value.managerEmployeeId=p.manager_employee_id||''
    leadership.value.technicalLeadEmployeeId=p.technical_lead_employee_id||''
  }catch(e){error.value=e.message}
}
async function saveLeadership(){
  try{await api('/business/projects/'+leadership.value.projectId+'/leadership',{method:'PUT',body:JSON.stringify({managerEmployeeId:leadership.value.managerEmployeeId,technicalLeadEmployeeId:leadership.value.technicalLeadEmployeeId})});notice.value='项目负责人和技术负责人已保存'}
  catch(e){error.value=e.message}
}
async function openMaintenance(id){
  maintenanceForm.value.projectId=String(id)
  documentContractId.value=null
  visitForm.value.contractId=''
  visitForm.value.employeeIds=[]
  try{
    renewalHistoryDetail.value=null
    maintenance.value=await api('/business/projects/'+id+'/maintenance')
    Object.assign(maintenanceForm.value,{
      currentContractId:maintenance.value.current_contract_id||'',
      honeybeeContractUploaded:!!maintenance.value.honeybee_contract_uploaded,
      honeybeeProjectCreated:!!maintenance.value.honeybee_project_created,
      projectMaterialsCompleted:!!maintenance.value.project_materials_completed,
      publicCardStatus:maintenance.value.public_card_status||'not_started',
      notes:maintenance.value.notes||''
    })
  }catch(e){error.value=e.message}
}
function selectAnnualState(){
  const id=maintenanceForm.value.currentContractId
  const s=maintenance.value?.contractHistory?.find(c=>String(c.id)===String(id))||{}
  Object.assign(maintenanceForm.value,{honeybeeContractUploaded:!!s.honeybee_contract_uploaded,honeybeeProjectCreated:!!s.honeybee_project_created,projectMaterialsCompleted:!!s.project_materials_completed,publicCardStatus:s.public_card_status||'not_started',notes:s.notes||''})
}
async function saveMaintenance(){
  try{maintenance.value=await api('/business/projects/'+maintenanceForm.value.projectId+'/maintenance',{method:'PUT',body:JSON.stringify(maintenanceForm.value)});notice.value='维保项目状态已保存'}
  catch(e){error.value=e.message}
}
async function addMaintenanceVisit(){
  try{await api('/business/projects/'+maintenanceForm.value.projectId+'/maintenance-visits',{method:'POST',body:JSON.stringify(visitForm.value)});await openMaintenance(Number(maintenanceForm.value.projectId));notice.value='维护日期及现场人员已登记'}
  catch(e){error.value=e.message}
}
async function uploadContractFile(event){
  const file=event.target.files?.[0];event.target.value='';if(!file||!linkForm.value.contractId)return
  try{const fd=new FormData();fd.append('file',file);const uploaded=await api('/files',{method:'POST',body:fd});await api('/business/contracts/'+linkForm.value.contractId+'/files',{method:'POST',body:JSON.stringify({fileId:uploaded.id,fileType:linkForm.value.fileType})});notice.value='合同附件已保存';await openContractDetail(Number(linkForm.value.contractId))}
  catch(e){error.value=e.message}
}
onBeforeUnmount(()=>{disposed=true;if(renewalReservation.value?.id)void releaseRenewalReservation(renewalReservation.value)})
onMounted(async ()=>{
  const target=props.destination
  tab.value=visibleTabs.value.some(t=>t.id===target?.section)?target.section:(visibleTabs.value[0]?.id||'')
  await load()
  if(!target?.id||tab.value!==target.section)return
  if(tab.value==='maintenance')await openMaintenance(target.id)
  else if(tab.value==='leadership'){leadership.value.projectId=target.id;await loadLeadership(target.id)}
  else if(tab.value==='links'){linkForm.value.contractId=target.id;projectLink.value.contractId=target.id;await openContractDetail(target.id)}
})
</script>

<template>
<section class="pm-module">
  <div v-if="tab!=='partners'" class="pm-head"><div><h2>{{({links:"合同关联与附件",leadership:"项目人员",maintenance:"维保项目详情"})[tab]}}</h2></div><button @click="load">刷新</button></div>
  <p v-if="error" class="pm-msg error">{{error}}</p><p v-if="notice" class="pm-msg success">{{notice}}</p>

  <PartnerDirectory v-if="tab==='partners'" :permissions="props.permissions" @navigate="id=>emit('navigate','business','contracts',id)" />

  <div v-if="tab==='links'" class="pm-layout">
    <div class="pm-panel"><h3>合同介绍来源与分成</h3><FormDialog v-if="(can('contract:write')&&can('partner:read'))" title="关联合同中间人" :steps="['合同与分成']" :busy="busy" :error="error" @clear-error="error=''" save-label="保存">
<template #default="{step,finish}"><form class="pm-form" @submit.prevent="finish(()=>linkPartner())">
<div v-show="step===0" data-wizard-step="0" class="wizard-fields">
<label>合同<select v-model="linkForm.contractId" @change="openContractDetail(Number(linkForm.contractId))" required><option value="">选择合同</option><option v-for="c in contracts" :key="c.id" :value="c.id">{{c.contract_no}} · {{c.title}}</option></select></label>
<label>合作方<select v-model="linkForm.partnerId" required><option value="">选择合作方</option><option v-for="p in partners" :key="p.id" :value="p.id">{{p.name}}</option></select></label>
<label>合同分成比例（%）<input v-model="linkForm.sharePercent" type="number" min="0" max="100" step="0.01" required></label>
<label>备注<input v-model="linkForm.notes"></label>
<button v-if="can('contract:write')&&can('partner:read')" class="primary">保存合作关系</button>
</div>
</form></template></FormDialog><p class="pm-note">一个合作方可以关联多份合同；每份合同可记录多个合作方，分成比例合计不得超过 100%。这里只记录约定比例，不登记实际付款。</p>
    <label>附件类型<select v-model="linkForm.fileType"><option value="contract">合同原件</option><option value="maintenance_card">维保公示卡</option><option value="project_material">项目资料</option><option value="other">其他</option></select></label><label v-if="can('contract:write')&&can('file:upload')" class="pm-upload">上传合同/维保资料<input type="file" hidden accept=".pdf,.docx" @change="uploadContractFile"></label></div>
    <div class="pm-panel"><h3>合同与项目关联</h3><FormDialog v-if="(can('contract:write')&&can('project:read'))" title="关联合同项目" :steps="['合同与项目']" :busy="busy" :error="error" @clear-error="error=''" save-label="关联">
<template #default="{step,finish}"><form class="pm-form" @submit.prevent="finish(()=>linkProject())">
<div v-show="step===0" data-wizard-step="0" class="wizard-fields">
<label>合同<select v-model="projectLink.contractId" @change="openContractDetail(Number(projectLink.contractId))" required><option value="">选择合同</option><option v-for="c in contracts" :key="c.id" :value="c.id">{{c.contract_no}} · {{c.title}}</option></select></label>
<label>项目<select v-model="projectLink.projectId" required><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="p.id">{{p.project_no}} · {{p.name}}</option></select></label>
<label>关联类型<select v-model="projectLink.relationType"><option value="service">服务合同</option><option value="renewal">年度续签</option><option value="supplement">补充合同</option><option value="other">其他</option></select></label>
<button v-if="can('contract:write')&&can('project:read')" class="primary">关联项目</button>
</div>
</form></template></FormDialog><p class="pm-note">项目可以先建立，后续再关联合同；一份合同可以覆盖多个项目，一个项目也可以保留多份年度合同历史。</p>
      <div v-if="contractDetail" class="pm-detail"><h4>{{contractDetail.contract_no}} · 合同关联资料</h4><button class="secondary-button" @click="emit('navigate','business','contracts',contractDetail.id)">返回合同档案</button>
        <div v-if="contractDetail.partners?.length" class="pm-contract"><b>合作方与分成</b><div v-for="p in contractDetail.partners" :key="p.id" class="pm-file"><span>{{p.name}} · {{p.share_percent}}%</span><button v-if="can('contract:write')" type="button" :disabled="busy" @click="unlinkPartner(p.id)">解除关联</button></div></div>
        <div v-if="contractDetail.projects?.length" class="pm-contract"><b>关联合同项目</b><div v-for="p in contractDetail.projects" :key="p.id" class="pm-file"><span>{{p.project_no}} · {{p.name}} · {{p.relation_type}}</span><button v-if="can('project:read')" type="button" @click="emit('navigate','business','projects',p.id)">项目档案</button><button v-if="can('contract:write')&&['draft','pending'].includes(contractDetail.status)" type="button" :disabled="busy" @click="unlinkProject(p.id)">解除关联</button></div></div>
        <div v-if="contractDetail.files?.length" class="pm-contract"><b>合同及项目附件</b><div v-for="f in contractDetail.files" :key="f.id" class="pm-file"><span>{{f.title||f.original_name}} · {{f.file_type}}</span><button v-if="can('file:read')" type="button" @click="downloadFile(f.file_id,f.original_name)">下载</button></div></div>
        <p v-if="!contractDetail.partners?.length&&!contractDetail.projects?.length&&!contractDetail.files?.length" class="pm-empty">暂无关联合作方、项目或附件</p>
      </div>
    </div>
  </div>

  <div v-if="tab==='leadership'" class="pm-panel"><h3>项目负责人及技术负责人</h3><FormDialog title="分配项目人员" :steps="['负责人']" :busy="busy" :error="error" @clear-error="error=''">
<template #default="{step,finish}"><form class="pm-form" @submit.prevent="finish(()=>saveLeadership())">
<div v-show="step===0" data-wizard-step="0" class="wizard-fields">
<label>项目<select v-model="leadership.projectId" @change="loadLeadership(Number(leadership.projectId))" required><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="p.id">{{p.project_no}} · {{p.name}}</option></select></label>
<label>项目负责人<select v-model="leadership.managerEmployeeId" required><option value="">选择工程师</option><option v-for="e in engineers" :key="e.id" :value="e.id">{{e.full_name}}</option></select></label>
<label>技术负责人<select v-model="leadership.technicalLeadEmployeeId" required><option value="">选择另一位工程师</option><option v-for="e in engineers.filter(x=>String(x.id)!==String(leadership.managerEmployeeId))" :key="e.id" :value="e.id">{{e.full_name}}</option></select></label>
<button class="primary">保存项目人员</button>
</div>
</form></template></FormDialog><p class="pm-note">前端只列出在职且具有有效资格记录的工程师；服务端还会校验两人不同并再次核验资格有效期。</p></div>

  <div v-if="tab==='maintenance'" class="pm-layout">
    <div class="pm-panel"><h3>选择维护保养项目</h3><div class="pm-list"><button v-for="p in maintenanceProjects" :key="p.id" @click="openMaintenance(p.id)" :class="{selected:String(maintenanceForm.projectId)===String(p.id)}"><b>{{p.name}}</b><small>{{p.project_no}} · {{p.customer_name}}</small></button></div></div>
    <div class="pm-panel" v-if="maintenance">
      <h3>维保项目状态</h3><div class="pm-contract"><b>当前合同：{{maintenance.current_contract_no||'未指定'}}</b><small>合同上传：{{maintenance.honeybee_contract_uploaded?'已完成':'未完成'}} · 小蜜蜂项目：{{maintenance.honeybee_project_created?'已建立':'未建立'}}</small><small>项目资料：{{maintenance.project_materials_completed?'已完成':'未完成'}} · 公示卡：{{({not_started:'未开始',preparing:'制作中',sent:'已寄出',delivered:'已送达'})[maintenance.public_card_status]||'未开始'}}</small><small v-if="maintenance.notes">{{maintenance.notes}}</small></div><button class="secondary-button" @click="emit('navigate','business','projects',Number(maintenanceForm.projectId))">返回项目档案</button><FormDialog v-if="(can('maintenance:write'))" title="编辑维保状态" :steps="['维保状态']" :busy="busy" :error="error" @clear-error="error=''" save-label="保存">
<template #default="{step,finish}"><form class="pm-form" @submit.prevent="finish(()=>saveMaintenance())">
<div v-show="step===0" data-wizard-step="0" class="wizard-fields">
<fieldset :disabled="!can('maintenance:write')" class="wide" style="display:contents">
        <label>当前有效维护合同<select v-model="maintenanceForm.currentContractId" @change="selectAnnualState"><option value="">未指定</option><option v-for="c in maintenance.contractHistory||[]" :key="c.id" :value="c.id">{{c.contract_no}} · {{c.starts_on||'未开始'}} 至 {{c.ends_on||'未设置'}}</option></select></label>
        <label>公示卡状态<select v-model="maintenanceForm.publicCardStatus"><option value="not_started">未开始</option><option value="preparing">制作中</option><option value="sent">已寄出</option><option value="delivered">已送达</option></select></label>
        <label class="check"><input v-model="maintenanceForm.honeybeeContractUploaded" type="checkbox"> 合同已上传小蜜蜂</label>
        <label class="check"><input v-model="maintenanceForm.honeybeeProjectCreated" type="checkbox"> 小蜜蜂项目已建立</label>
        <label class="check"><input v-model="maintenanceForm.projectMaterialsCompleted" type="checkbox"> 项目资料已完成</label>
        <label class="wide">备注<textarea v-model="maintenanceForm.notes"></textarea></label><button v-if="can('maintenance:write')" class="primary">保存维保状态</button></fieldset>
</div>
</form></template></FormDialog>
      <div class="pm-divider"></div><h3>年度维保合同续签</h3>
      <p class="pm-note">续签沿用原项目，新合同独立保存合同期限及维保卡附件。新建的是草稿，不会自动替换当前有效合同。</p>
      <button v-if="maintenance.contractHistory?.length&&can('maintenance:write')&&can('contract:write')" type="button" class="primary" :disabled="busy||renewalReserving" @click="beginRenewal">{{renewalReserving?'正在预留编号…':'＋ 新建续签合同'}}</button>
      <FormDialog v-if="renewalOpen" title="新建续签合同" :steps="['合同信息','合同日期与备注']" :busy="busy" :error="error" @clear-error="error=''" save-label="创建续签" auto-open @close="closeRenewal">
<template #default="{step,finish}"><form class="pm-form" @submit.prevent="finish(()=>createRenewal())">
<div v-show="step===0" data-wizard-step="0" class="wizard-fields">
<label>系统合同编号<input :value="renewalReservation?.number||''" disabled></label>
<label>其他编号（选填）<input v-model.trim="renewalForm.externalContractNo" maxlength="160" placeholder="甲方指定的合同编号"></label>
<label>合同名称<input v-model.trim="renewalForm.title" required></label>
<label>金额<input v-model="renewalForm.amount" type="number" min="0" step="0.01" required></label>
</div>
<div v-show="step===1" data-wizard-step="1" class="wizard-fields">
<label>签约日期<input v-model="renewalForm.signedOn" type="date"></label>
<label>新合同开始日期<input v-model="renewalForm.startsOn" type="date" required></label>
<label>新合同结束日期<input v-model="renewalForm.endsOn" type="date" required></label>
<label class="wide">备注<input v-model="renewalForm.notes"></label>
<FileDropzone v-if="can('file:upload')" class="wide" label="续签合同附件" :model-value="renewalFiles" :multiple="true" :max-files="10" accept=".pdf,.doc,.docx,.jpg,.jpeg,.png" hint="最多10个文件；单文件默认25 MB，DOCX最大100 MB" :max-bytes="25*1024*1024" :file-limit="file=>file.name.toLowerCase().endsWith('.docx')?100*1024*1024:25*1024*1024" :disabled="busy" @update:model-value="renewalFiles=$event"/>
<button class="primary" :disabled="busy||!can('contract:write')">创建续签合同</button>
</div>
</form></template></FormDialog>
      <p v-else-if="!maintenance.contractHistory?.length" class="pm-note">此项目没有维保合同。请先在合同管理创建首份合同，再关联合同和项目。</p>
      <div class="pm-detail"><h4>历年维保合同与对应资料</h4>
        <div v-for="c in maintenance.contractHistory||[]" :key="c.id" class="pm-contract">
          <b>{{c.contract_no}} · {{c.title}}</b>
          <small>{{c.starts_on||'未开始'}} 至 {{c.ends_on||'未设置'}} · {{c.status}}</small><small v-if="c.state_updated_at">小蜜蜂合同：{{c.honeybee_contract_uploaded?'已上传':'未上传'}} · 项目：{{c.honeybee_project_created?'已建立':'未建立'}} · 资料：{{c.project_materials_completed?'已完成':'未完成'}} · 公示卡：{{({not_started:'未开始',preparing:'制作中',sent:'已寄出',delivered:'已送达'})[c.public_card_status]}}</small><small v-else>暂无该年度状态记录</small>
          <button type="button" class="secondary-button" @click="documentContractId=documentContractId===c.id?null:c.id">{{documentContractId===c.id?'收起年度资料':'维保卡 / 附带资料'}}</button>
          <button v-if="can('contract:read')" type="button" class="secondary-button" @click="openRenewalHistory(c.id)">查看该合同附件</button>
        </div>
        <MaintenanceDocuments v-if="documentContractId" :key="maintenanceForm.projectId+'-'+documentContractId" :project-id="Number(maintenanceForm.projectId)" :contract-id="Number(documentContractId)" :permissions="props.permissions" />
        <div v-if="renewalHistoryDetail" class="pm-contract">
          <b>{{renewalHistoryDetail.contract_no}} · 独立附件记录</b>
          <div v-for="file in renewalHistoryDetail.files||[]" :key="file.id" class="pm-file">
            <span>{{file.title||file.original_name}} · {{file.file_type}}</span>
            <button v-if="can('file:read')" type="button" @click="downloadFile(file.file_id,file.original_name)">下载</button>
          </div>
          <p v-if="!renewalHistoryDetail.files?.length" class="pm-note">此合同暂无附件；请在「合同与项目关联」中上传本年度维保卡。</p>
        </div>
      </div>
      <div class="pm-divider"></div><h3>维护日期与现场人员（按需登记）</h3><FormDialog v-if="can('maintenance:write')" title="登记维护记录" :steps="['维护记录']" :busy="busy" :error="error" @clear-error="error=''">
<template #default="{step,finish}"><form class="pm-form" @submit.prevent="finish(()=>addMaintenanceVisit())">
<div v-show="step===0" data-wizard-step="0" class="wizard-fields">
<label>维护日期<input v-model="visitForm.visitDate" type="date" required></label>
<label>对应合同<select v-model="visitForm.contractId"><option value="">当前项目</option><option v-for="c in maintenance.contractHistory||[]" :key="c.id" :value="c.id">{{c.contract_no}}</option></select></label>
<label class="wide">现场人员<select v-model="visitForm.employeeIds" multiple><option v-for="e in employees" :key="e.id" :value="e.id">{{e.full_name}}</option></select></label>
<label class="wide">备注<input v-model="visitForm.notes"></label>
<button class="primary">登记维护记录</button>
</div>
</form></template></FormDialog>
      <div class="pm-detail"><h4>维护记录</h4><div v-for="v in maintenance.visits||[]" :key="v.id" class="pm-contract"><b>{{v.visit_date}}</b><small>{{v.staff_names||'未登记现场人员'}} · {{v.notes||'无备注'}}</small></div><p v-if="!maintenance.visits?.length" class="pm-empty">暂无维护日期记录</p></div>
    </div>
    <div v-else class="pm-panel pm-empty">选择一个项目以查看维保状态和合同历史</div>
  </div>
</section>
</template>

<style scoped>
.pm-module{display:grid;gap:16px;color:var(--erp-fg-293b55)}.pm-head{display:flex;justify-content:space-between;align-items:flex-start}.pm-head h2{margin:4px 0;font-size:23px}.pm-head p{font-size:12px;color:var(--erp-fg-8390a2);margin:0}.pm-kicker{font-size:10px;letter-spacing:.14em;color:var(--erp-fg-587bb8);font-weight:700}.pm-head button,.pm-tabs button{border:1px solid var(--erp-border-dfe6ef);border-radius:8px;padding:8px 12px;background:var(--erp-bg-fff);color:var(--erp-fg-60718a);font-size:12px}.pm-tabs{display:flex;gap:8px;flex-wrap:wrap;border-bottom:1px solid var(--erp-border-e6ebf2);padding-bottom:10px}.pm-tabs button.active{background:var(--erp-bg-edf3ff);border-color:var(--erp-border-c8d7fa);color:var(--erp-fg-315bb3)}.pm-layout{display:grid;grid-template-columns:1fr 1fr;gap:14px}.pm-panel{background:var(--erp-bg-fff);border:1px solid var(--erp-border-e6ebf2);border-radius:12px;padding:17px;display:grid;gap:14px;align-content:start}.pm-panel h3{font-size:15px;margin:0;color:var(--erp-fg-405675)}.pm-form{display:grid;grid-template-columns:1fr 1fr;gap:11px}.pm-form label{display:grid;gap:5px;font-size:11px;color:var(--erp-fg-75849a)}.pm-form .wide{grid-column:1/-1}.pm-form input,.pm-form select,.pm-form textarea{border:1px solid var(--erp-border-dfe6ef);border-radius:8px;padding:9px;font:inherit;font-size:12px;color:var(--erp-fg-34465e);background:var(--erp-bg-fff);min-width:0}.pm-form select[multiple]{min-height:80px}.pm-form .check{display:flex;align-items:center;gap:8px}.pm-form .check input{width:auto}.primary{background:var(--erp-bg-315fca);color:white;border:0;border-radius:8px;padding:10px 14px;font-size:12px}.pm-list{display:grid;gap:7px;max-height:360px;overflow:auto}.pm-list button{display:grid;text-align:left;gap:4px;border:1px solid var(--erp-border-e8edf4);background:var(--erp-bg-fff);border-radius:9px;padding:11px}.pm-list button.selected{border-color:var(--erp-border-8da9e3);background:var(--erp-bg-f5f8ff)}.pm-list b,.pm-contract b{font-size:12px}.pm-list small,.pm-contract small{font-size:10px;color:var(--erp-fg-8491a3)}.pm-file{display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:11px;color:var(--erp-fg-667891)}.pm-file button{border:1px solid var(--erp-border-dfe6ef);background:var(--erp-bg-fff);border-radius:6px;padding:5px 8px;color:var(--erp-fg-4166aa);font-size:10px}.pm-detail{border-top:1px solid var(--erp-border-edf0f5);padding-top:12px;display:grid;gap:9px}.pm-detail h4{margin:0;font-size:12px}.pm-contract{display:grid;gap:3px;padding:8px 10px;border:1px solid var(--erp-border-edf0f5);border-radius:8px}.pm-note{font-size:11px;color:var(--erp-fg-8290a3);line-height:1.7}.pm-upload{justify-self:start;background:var(--erp-bg-eef3fc);color:var(--erp-fg-4166aa);padding:9px 12px;border-radius:8px;font-size:11px;cursor:pointer}.pm-msg{padding:10px 13px;border-radius:8px;font-size:12px}.pm-msg.error{background:var(--erp-bg-fff0ed);color:var(--erp-fg-a33)}.pm-msg.success{background:var(--erp-bg-eaf7ef);color:var(--erp-fg-287449)}.pm-empty{font-size:11px;color:var(--erp-fg-9aa5b4);text-align:center;padding:18px}.pm-divider{height:1px;background:var(--erp-bg-edf0f5)}.@media(max-width:800px){.pm-layout{grid-template-columns:1fr}}@media(max-width:520px){.pm-form{grid-template-columns:1fr}.pm-form .wide{grid-column:auto}}
</style>
