<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, downloadFile } from './api'
const props=defineProps({permissions:{type:Array,default:()=>[]},destination:{type:Object,default:null}})
const emit=defineEmits(['navigate'])
const can=p=>props.permissions.includes(p)
const visibleTabs=computed(()=>[
  {id:'partners',name:'合作方档案',allowed:can('partner:read')},
  {id:'links',name:'合同与项目关联',allowed:can('contract:read')},
  {id:'leadership',name:'项目人员',allowed:can('project:read')&&can('project:write')},
  {id:'maintenance',name:'维保项目',allowed:can('maintenance:read')&&can('project:read')}
].filter(t=>t.allowed))
const tab=ref('partners'),busy=ref(false),error=ref(''),notice=ref('')
const partners=ref([]),contracts=ref([]),projects=ref([]),employees=ref([])
const partner=ref(null),maintenance=ref(null),contractDetail=ref(null)
const partnerForm=ref({partnerCode:'',name:'',partnerType:'introducer',contactName:'',phone:'',email:'',address:'',notes:''})
const partnerEditOpen=ref(false)
const partnerEditForm=ref({name:'',partnerType:'introducer',contactName:'',phone:'',email:'',address:'',notes:'',status:'active'})
const linkForm=ref({contractId:'',partnerId:'',sharePercent:'',notes:'',fileType:'contract'})
const projectLink=ref({projectId:'',contractId:'',relationType:'service'})
const leadership=ref({projectId:'',managerEmployeeId:'',technicalLeadEmployeeId:''})
const maintenanceForm=ref({projectId:'',currentContractId:'',honeybeeContractUploaded:false,honeybeeProjectCreated:false,projectMaterialsCompleted:false,publicCardStatus:'not_started',notes:''})
const visitForm=ref({visitDate:new Date().toISOString().slice(0,10),contractId:'',employeeIds:[],notes:''})
const renewalForm=ref({contractNo:'',title:'',amount:'0',signedOn:'',startsOn:'',endsOn:'',notes:''})
const renewalHistoryDetail=ref(null)
async function openRenewalHistory(contractId){
  try{renewalHistoryDetail.value=await api('/business/contracts/'+contractId)}catch(e){error.value=e.message}
}
async function createRenewal(){
  if(!maintenanceForm.value.projectId)return
  busy.value=true;error.value=''
  try{
    const contract=await api('/business/projects/'+maintenanceForm.value.projectId+'/maintenance-renewals',{
      method:'POST',body:JSON.stringify(renewalForm.value)
    })
    renewalForm.value={contractNo:'',title:'',amount:'0',signedOn:'',startsOn:'',endsOn:'',notes:''}
    await load()
    await openMaintenance(Number(maintenanceForm.value.projectId))
    notice.value='续签合同草稿已创建并关联原项目，旧合同与维保卡已保留。新合同需要确认后手动指定为当前有效合同。'
    await openRenewalHistory(contract.id)
  }catch(e){error.value=e.message}finally{busy.value=false}
}

const engineers=computed(()=>employees.value.filter(e=>e.employee_type==='engineer'&&e.status==='active'&&e.has_valid_qualification))
async function load(){
  error.value=''
  try{
    const [p,c,pr,e]=await Promise.all([
      can('partner:read')?api('/business/partners'):Promise.resolve([]),
      can('contract:read')?api('/business/contracts'):Promise.resolve([]),
      can('project:read')?api('/business/projects'):Promise.resolve([]),
      can('employee:read')?api('/business/employees'):Promise.resolve([])
    ])
    partners.value=p;contracts.value=c;projects.value=pr;employees.value=e
  }catch(e){error.value=e.message}
}
async function createPartner(){
  busy.value=true;error.value=''
  try{await api('/business/partners',{method:'POST',body:JSON.stringify(partnerForm.value)});partnerForm.value={partnerCode:'',name:'',partnerType:'introducer',contactName:'',phone:'',email:'',address:'',notes:''};await load();notice.value='合作方档案已创建'}
  catch(e){error.value=e.message}finally{busy.value=false}
}
async function openPartner(id){
  try{
    partner.value=await api('/business/partners/'+id)
    partnerEditForm.value={
      name:partner.value.name||'',partnerType:partner.value.partner_type||'introducer',
      contactName:partner.value.contact_name||'',phone:partner.value.phone||'',
      email:partner.value.email||'',address:partner.value.address||'',
      notes:partner.value.notes||'',status:partner.value.status||'active'
    }
    partnerEditOpen.value=false
  }catch(e){error.value=e.message}
}
async function savePartner(){
  if(!partner.value)return
  busy.value=true;error.value=''
  try{
    partner.value=await api('/business/partners/'+partner.value.id,{method:'PUT',body:JSON.stringify(partnerEditForm.value)})
    partnerEditOpen.value=false
    await load()
    await openPartner(partner.value.id)
    notice.value='合作方档案已更新'
  }catch(e){error.value=e.message}finally{busy.value=false}
}
async function openContractDetail(id){
  if(!id){contractDetail.value=null;return}
  try{contractDetail.value=await api('/business/contracts/'+id)}catch(e){error.value=e.message}
}
async function linkPartner(){
  try{await api('/business/contracts/'+linkForm.value.contractId+'/partners',{method:'POST',body:JSON.stringify({partnerId:linkForm.value.partnerId,sharePercent:linkForm.value.sharePercent,notes:linkForm.value.notes})});notice.value='合同合作方及分成比例已保存';await load();await openContractDetail(Number(linkForm.value.contractId));if(partner.value)await openPartner(partner.value.id)}
  catch(e){error.value=e.message}
}
async function unlinkPartner(partnerId){
  const contractId=contractDetail.value?.id
  if(!contractId||!confirm('确定解除此合作方与当前合同的关联？历史审计记录仍会保留。'))return
  busy.value=true;error.value=''
  try{
    await api('/business/contracts/'+contractId+'/partners/'+partnerId,{method:'DELETE'})
    await openContractDetail(contractId)
    if(partner.value)await openPartner(partner.value.id)
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
  <div class="pm-head"><div><span class="pm-kicker">BUSINESS PARTNERS · MAINTENANCE</span><h2>合作方与维护保养</h2><p>独立管理中间人、合同来源与分成，跟踪维护保养项目的合同和资料状态。</p></div><button @click="load">刷新</button></div>
  <p v-if="error" class="pm-msg error">{{error}}</p><p v-if="notice" class="pm-msg success">{{notice}}</p>
  <nav class="pm-tabs"><button v-for="t in visibleTabs" :key="t.id" :class="{active:tab===t.id}" @click="tab=t.id">{{t.name}}</button></nav>

  <div v-if="tab==='partners'" class="pm-layout">
    <div v-if="can('partner:write')" class="pm-panel"><h3>新增合作方</h3><form @submit.prevent="createPartner" class="pm-form">
      <label>合作方编号<input v-model="partnerForm.partnerCode" required></label><label>名称<input v-model="partnerForm.name" required></label>
      <label>类型<select v-model="partnerForm.partnerType"><option value="introducer">业务介绍人</option><option value="agency">合作机构</option><option value="other">其他</option></select></label><label>联系人<input v-model="partnerForm.contactName"></label>
      <label>电话<input v-model="partnerForm.phone"></label><label>邮箱<input v-model="partnerForm.email"></label><label class="wide">地址<input v-model="partnerForm.address"></label><label class="wide">备注<textarea v-model="partnerForm.notes"></textarea></label><button class="primary" :disabled="busy">创建合作方</button>
    </form></div>
    <div class="pm-panel"><h3>合作方列表</h3><div class="pm-list"><button v-for="p in partners" :key="p.id" @click="openPartner(p.id)" :class="{selected:partner?.id===p.id}"><b>{{p.name}}</b><small>{{p.partner_code}} · {{p.contract_count}} 份关联合同</small></button><p v-if="!partners.length" class="pm-empty">暂无合作方</p></div>
      <div v-if="partner" class="pm-detail">
        <div class="pm-detail-head"><h4>{{partner.name}} · 合作方档案</h4><button v-if="can('partner:write')" class="secondary-button" @click="partnerEditOpen=!partnerEditOpen">{{partnerEditOpen?'收起编辑':'编辑档案'}}</button></div>
        <p>编号：{{partner.partner_code}} · 状态：{{partner.status==='active'?'启用':'停用'}}</p>
        <form v-if="partnerEditOpen" class="pm-form" @submit.prevent="savePartner">
          <label>名称<input v-model="partnerEditForm.name" required></label>
          <label>类型<select v-model="partnerEditForm.partnerType"><option value="introducer">业务介绍人</option><option value="agency">合作机构</option><option value="other">其他</option></select></label>
          <label>联系人<input v-model="partnerEditForm.contactName"></label><label>电话<input v-model="partnerEditForm.phone"></label>
          <label>邮箱<input v-model="partnerEditForm.email"></label><label>状态<select v-model="partnerEditForm.status"><option value="active">启用</option><option value="disabled">停用</option></select></label>
          <label class="wide">地址<input v-model="partnerEditForm.address"></label><label class="wide">备注<textarea v-model="partnerEditForm.notes"></textarea></label>
          <button class="primary" :disabled="busy">保存档案</button>
        </form>
        <h4>关联合同</h4><div v-for="c in partner.contracts||[]" :key="c.id" class="pm-contract"><b>{{c.title}}</b><small>{{c.contract_no}} · 分成 {{c.share_percent}}% · {{c.status}}</small><button v-if="can('contract:read')" class="secondary-button" @click="emit('navigate','business','contracts',c.id)">合同档案</button></div><p v-if="!partner.contracts?.length" class="pm-empty">尚未关联合同</p>
      </div>
    </div>
  </div>

  <div v-if="tab==='links'" class="pm-layout">
    <div class="pm-panel"><h3>合同介绍来源与分成</h3><form class="pm-form" @submit.prevent="linkPartner">
      <label>合同<select v-model="linkForm.contractId" @change="openContractDetail(Number(linkForm.contractId))" required><option value="">选择合同</option><option v-for="c in contracts" :key="c.id" :value="c.id">{{c.contract_no}} · {{c.title}}</option></select></label>
      <label>合作方<select v-model="linkForm.partnerId" required><option value="">选择合作方</option><option v-for="p in partners" :key="p.id" :value="p.id">{{p.name}}</option></select></label>
      <label>合同分成比例（%）<input v-model="linkForm.sharePercent" type="number" min="0" max="100" step="0.01" required></label><label>备注<input v-model="linkForm.notes"></label><button v-if="can('contract:write')&&can('partner:read')" class="primary">保存合作关系</button>
    </form><p class="pm-note">一个合作方可以关联多份合同；每份合同可记录多个合作方，分成比例合计不得超过 100%。这里只记录约定比例，不登记实际付款。</p>
    <label>附件类型<select v-model="linkForm.fileType"><option value="contract">合同原件</option><option value="maintenance_card">维保公示卡</option><option value="project_material">项目资料</option><option value="other">其他</option></select></label><label v-if="can('contract:write')&&can('file:upload')" class="pm-upload">上传合同/维保资料<input type="file" hidden accept=".pdf,.docx" @change="uploadContractFile"></label></div>
    <div class="pm-panel"><h3>合同与项目关联</h3><form class="pm-form" @submit.prevent="linkProject">
      <label>合同<select v-model="projectLink.contractId" @change="openContractDetail(Number(projectLink.contractId))" required><option value="">选择合同</option><option v-for="c in contracts" :key="c.id" :value="c.id">{{c.contract_no}} · {{c.title}}</option></select></label>
      <label>项目<select v-model="projectLink.projectId" required><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="p.id">{{p.project_no}} · {{p.name}}</option></select></label>
      <label>关联类型<select v-model="projectLink.relationType"><option value="service">服务合同</option><option value="renewal">年度续签</option><option value="supplement">补充合同</option><option value="other">其他</option></select></label><button v-if="can('contract:write')&&can('project:read')" class="primary">关联项目</button>
    </form><p class="pm-note">项目可以先建立，后续再关联合同；一份合同可以覆盖多个项目，一个项目也可以保留多份年度合同历史。</p>
      <div v-if="contractDetail" class="pm-detail"><h4>{{contractDetail.contract_no}} · 合同关联资料</h4><button class="secondary-button" @click="emit('navigate','business','contracts',contractDetail.id)">返回合同档案</button>
        <div v-if="contractDetail.partners?.length" class="pm-contract"><b>合作方与分成</b><div v-for="p in contractDetail.partners" :key="p.id" class="pm-file"><span>{{p.name}} · {{p.share_percent}}%</span><button v-if="can('contract:write')" type="button" :disabled="busy" @click="unlinkPartner(p.id)">解除关联</button></div></div>
        <div v-if="contractDetail.projects?.length" class="pm-contract"><b>关联合同项目</b><div v-for="p in contractDetail.projects" :key="p.id" class="pm-file"><span>{{p.project_no}} · {{p.name}} · {{p.relation_type}}</span><button v-if="can('project:read')" type="button" @click="emit('navigate','business','projects',p.id)">项目档案</button><button v-if="can('contract:write')&&['draft','pending'].includes(contractDetail.status)" type="button" :disabled="busy" @click="unlinkProject(p.id)">解除关联</button></div></div>
        <div v-if="contractDetail.files?.length" class="pm-contract"><b>合同及项目附件</b><div v-for="f in contractDetail.files" :key="f.id" class="pm-file"><span>{{f.title||f.original_name}} · {{f.file_type}}</span><button v-if="can('file:read')" type="button" @click="downloadFile(f.file_id,f.original_name)">下载</button></div></div>
        <p v-if="!contractDetail.partners?.length&&!contractDetail.projects?.length&&!contractDetail.files?.length" class="pm-empty">暂无关联合作方、项目或附件</p>
      </div>
    </div>
  </div>

  <div v-if="tab==='leadership'" class="pm-panel"><h3>项目负责人及技术负责人</h3><form class="pm-form" @submit.prevent="saveLeadership">
    <label>项目<select v-model="leadership.projectId" @change="loadLeadership(Number(leadership.projectId))" required><option value="">选择项目</option><option v-for="p in projects" :key="p.id" :value="p.id">{{p.project_no}} · {{p.name}}</option></select></label>
    <label>项目负责人<select v-model="leadership.managerEmployeeId" required><option value="">选择工程师</option><option v-for="e in engineers" :key="e.id" :value="e.id">{{e.full_name}}</option></select></label>
    <label>技术负责人<select v-model="leadership.technicalLeadEmployeeId" required><option value="">选择另一位工程师</option><option v-for="e in engineers.filter(x=>String(x.id)!==String(leadership.managerEmployeeId))" :key="e.id" :value="e.id">{{e.full_name}}</option></select></label>
    <button class="primary">保存项目人员</button>
  </form><p class="pm-note">前端只列出在职且具有有效资格记录的工程师；服务端还会校验两人不同并再次核验资格有效期。</p></div>

  <div v-if="tab==='maintenance'" class="pm-layout">
    <div class="pm-panel"><h3>选择维护保养项目</h3><div class="pm-list"><button v-for="p in projects" :key="p.id" @click="openMaintenance(p.id)" :class="{selected:String(maintenanceForm.projectId)===String(p.id)}"><b>{{p.name}}</b><small>{{p.project_no}} · {{p.customer_name}}</small></button></div></div>
    <div class="pm-panel" v-if="maintenance">
      <h3>维保项目状态</h3><button class="secondary-button" @click="emit('navigate','business','projects',Number(maintenanceForm.projectId))">返回项目档案</button><form class="pm-form" @submit.prevent="saveMaintenance"><fieldset :disabled="!can('maintenance:write')" class="wide" style="display:contents">
        <label>当前有效维护合同<select v-model="maintenanceForm.currentContractId"><option value="">未指定</option><option v-for="c in maintenance.contractHistory||[]" :key="c.id" :value="c.id">{{c.contract_no}} · {{c.starts_on||'未开始'}} 至 {{c.ends_on||'未设置'}}</option></select></label>
        <label>公示卡状态<select v-model="maintenanceForm.publicCardStatus"><option value="not_started">未开始</option><option value="preparing">制作中</option><option value="sent">已寄出</option><option value="delivered">已送达</option></select></label>
        <label class="check"><input v-model="maintenanceForm.honeybeeContractUploaded" type="checkbox"> 合同已上传小蜜蜂</label>
        <label class="check"><input v-model="maintenanceForm.honeybeeProjectCreated" type="checkbox"> 小蜜蜂项目已建立</label>
        <label class="check"><input v-model="maintenanceForm.projectMaterialsCompleted" type="checkbox"> 项目资料已完成</label>
        <label class="wide">备注<textarea v-model="maintenanceForm.notes"></textarea></label><button v-if="can('maintenance:write')" class="primary">保存维保状态</button></fieldset>
      </form>
      <div class="pm-divider"></div><h3>年度维保合同续签</h3>
      <p class="pm-note">续签沿用原项目，新合同独立保存合同期限及维保卡附件。新建的是草稿，不会自动替换当前有效合同。</p>
      <form v-if="maintenance.contractHistory?.length&&can('maintenance:write')&&can('contract:write')" class="pm-form" @submit.prevent="createRenewal">
        <label>新合同编号<input v-model="renewalForm.contractNo" required></label>
        <label>合同名称<input v-model="renewalForm.title" required></label>
        <label>金额<input v-model="renewalForm.amount" type="number" min="0" step="0.01" required></label>
        <label>签约日期<input v-model="renewalForm.signedOn" type="date"></label>
        <label>新合同开始日期<input v-model="renewalForm.startsOn" type="date" required></label>
        <label>新合同结束日期<input v-model="renewalForm.endsOn" type="date" required></label>
        <label class="wide">备注<input v-model="renewalForm.notes"></label>
        <button class="primary" :disabled="busy||!can('contract:write')">创建续签合同</button>
      </form>
      <p v-else class="pm-note">此项目没有维保合同。请先在合同管理创建首份合同，再关联合同和项目。</p>
      <div class="pm-detail"><h4>历年维保合同与对应资料</h4>
        <div v-for="c in maintenance.contractHistory||[]" :key="c.id" class="pm-contract">
          <b>{{c.contract_no}} · {{c.title}}</b>
          <small>{{c.starts_on||'未开始'}} 至 {{c.ends_on||'未设置'}} · {{c.status}}</small>
          <button v-if="can('contract:read')" type="button" class="secondary-button" @click="openRenewalHistory(c.id)">查看该合同附件</button>
        </div>
        <div v-if="renewalHistoryDetail" class="pm-contract">
          <b>{{renewalHistoryDetail.contract_no}} · 独立附件记录</b>
          <div v-for="file in renewalHistoryDetail.files||[]" :key="file.id" class="pm-file">
            <span>{{file.title||file.original_name}} · {{file.file_type}}</span>
            <button v-if="can('file:read')" type="button" @click="downloadFile(file.file_id,file.original_name)">下载</button>
          </div>
          <p v-if="!renewalHistoryDetail.files?.length" class="pm-note">此合同暂无附件；请在「合同与项目关联」中上传本年度维保卡。</p>
        </div>
      </div>
      <div class="pm-divider"></div><h3>维护日期与现场人员（按需登记）</h3><form v-if="can('maintenance:write')" class="pm-form" @submit.prevent="addMaintenanceVisit">
        <label>维护日期<input v-model="visitForm.visitDate" type="date" required></label>
        <label>对应合同<select v-model="visitForm.contractId"><option value="">当前项目</option><option v-for="c in maintenance.contractHistory||[]" :key="c.id" :value="c.id">{{c.contract_no}}</option></select></label>
        <label class="wide">现场人员<select v-model="visitForm.employeeIds" multiple><option v-for="e in employees" :key="e.id" :value="e.id">{{e.full_name}}</option></select></label>
        <label class="wide">备注<input v-model="visitForm.notes"></label><button class="primary">登记维护记录</button>
      </form>
      <div class="pm-detail"><h4>维护记录</h4><div v-for="v in maintenance.visits||[]" :key="v.id" class="pm-contract"><b>{{v.visit_date}}</b><small>{{v.staff_names||'未登记现场人员'}} · {{v.notes||'无备注'}}</small></div><p v-if="!maintenance.visits?.length" class="pm-empty">暂无维护日期记录</p></div>
    </div>
    <div v-else class="pm-panel pm-empty">选择一个项目以查看维保状态和合同历史</div>
  </div>
</section>
</template>

<style scoped>
.pm-module{display:grid;gap:16px;color:#293b55}.pm-head{display:flex;justify-content:space-between;align-items:flex-start}.pm-head h2{margin:4px 0;font-size:23px}.pm-head p{font-size:12px;color:#8390a2;margin:0}.pm-kicker{font-size:10px;letter-spacing:.14em;color:#587bb8;font-weight:700}.pm-head button,.pm-tabs button{border:1px solid #dfe6ef;border-radius:8px;padding:8px 12px;background:white;color:#60718a;font-size:12px}.pm-tabs{display:flex;gap:8px;flex-wrap:wrap;border-bottom:1px solid #e6ebf2;padding-bottom:10px}.pm-tabs button.active{background:#edf3ff;border-color:#c8d7fa;color:#315bb3}.pm-layout{display:grid;grid-template-columns:1fr 1fr;gap:14px}.pm-panel{background:#fff;border:1px solid #e6ebf2;border-radius:12px;padding:17px;display:grid;gap:14px;align-content:start}.pm-panel h3{font-size:15px;margin:0;color:#405675}.pm-form{display:grid;grid-template-columns:1fr 1fr;gap:11px}.pm-form label{display:grid;gap:5px;font-size:11px;color:#75849a}.pm-form .wide{grid-column:1/-1}.pm-form input,.pm-form select,.pm-form textarea{border:1px solid #dfe6ef;border-radius:8px;padding:9px;font:inherit;font-size:12px;color:#34465e;background:white;min-width:0}.pm-form select[multiple]{min-height:80px}.pm-form .check{display:flex;align-items:center;gap:8px}.pm-form .check input{width:auto}.primary{background:#315fca;color:white;border:0;border-radius:8px;padding:10px 14px;font-size:12px}.pm-list{display:grid;gap:7px;max-height:360px;overflow:auto}.pm-list button{display:grid;text-align:left;gap:4px;border:1px solid #e8edf4;background:white;border-radius:9px;padding:11px}.pm-list button.selected{border-color:#8da9e3;background:#f5f8ff}.pm-list b,.pm-contract b{font-size:12px}.pm-list small,.pm-contract small{font-size:10px;color:#8491a3}.pm-file{display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:11px;color:#667891}.pm-file button{border:1px solid #dfe6ef;background:#fff;border-radius:6px;padding:5px 8px;color:#4166aa;font-size:10px}.pm-detail{border-top:1px solid #edf0f5;padding-top:12px;display:grid;gap:9px}.pm-detail h4{margin:0;font-size:12px}.pm-contract{display:grid;gap:3px;padding:8px 10px;border:1px solid #edf0f5;border-radius:8px}.pm-note{font-size:11px;color:#8290a3;line-height:1.7}.pm-upload{justify-self:start;background:#eef3fc;color:#4166aa;padding:9px 12px;border-radius:8px;font-size:11px;cursor:pointer}.pm-msg{padding:10px 13px;border-radius:8px;font-size:12px}.pm-msg.error{background:#fff0ed;color:#a33}.pm-msg.success{background:#eaf7ef;color:#287449}.pm-empty{font-size:11px;color:#9aa5b4;text-align:center;padding:18px}.pm-divider{height:1px;background:#edf0f5}.@media(max-width:800px){.pm-layout{grid-template-columns:1fr}}@media(max-width:520px){.pm-form{grid-template-columns:1fr}.pm-form .wide{grid-column:auto}}
</style>
