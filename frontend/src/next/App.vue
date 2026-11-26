<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from './api'
import BusinessModule from './BusinessModule.vue'
import FinanceModule from './FinanceModule.vue'
import InvoiceModule from './InvoiceModule.vue'
import ServiceWorkflow from './ServiceWorkflow.vue'
import InspectionTemplateLibrary from './InspectionTemplateLibrary.vue'
import FireStandardLibrary from './FireStandardLibrary.vue'
import PartnerMaintenance from './PartnerMaintenance.vue'
import AccountManagement from './AccountManagement.vue'
import ThemeSwitch from './ThemeSwitch.vue'

const loading = ref(true)
const submitting = ref(false)
const initialized = ref(true)
const user = ref(null)
const identities = ref([])
const error = ref('')
const notice = ref('')
const tab = ref('overview')
const destination = ref(null)
const navigationKey = ref(0)
let navigationRequest=0
const projectTypes = [
  {type:'maintenance',title:'维保项目'},
  {type:'fire_inspection',title:'检测项目'},
  {type:'fire_assessment',title:'评定项目'},
  {type:'fire_evaluation',title:'评估项目'},
  {type:'other',title:'其他技术服务'}
]
const pageTitle = computed(()=>{
  if(tab.value==='business')return destination.value?.section==='projects'
    ? projectTypes.find(p=>p.type===destination.value?.serviceType)?.title||'项目档案'
    : ({employees:'员工档案',customers:'甲方管理',contracts:'合同管理'})[destination.value?.section]||'业务档案'
  if(tab.value==='partner-maintenance')return ({partners:'中间人管理',links:'合同关联与附件',leadership:'项目人员',maintenance:'维保项目详情'})[destination.value?.section]||'中间人管理'
  return ({overview:'工作台',identities:'账户管理','service-workflow':'技术服务交付','inspection-templates':'检测报告模板','fire-standards':'消防规范资料库',invoices:'发票与应收应付',finance:'费用报账与审批'})[tab.value]||'工作台'
})
async function navigate(target, section=null, id=null, serviceType=null) {
  const allowed = {
    business: section ? can(({customers:'customer:read',contracts:'contract:read',projects:'project:read',employees:'employee:read'})[section]) : false,
    'partner-maintenance': section==='maintenance' ? can('maintenance:read')&&can('project:read') : section==='leadership' ? can('project:read')&&can('project:write') : section==='links' ? can('contract:read') : can('partner:read'),
    'service-workflow': can('service:read')
  }
  if (!allowed[target]) return
  const request=++navigationRequest
  if(target==='business'&&section==='projects'&&id&&!serviceType){
    try{serviceType=(await api('/business/projects/'+id)).service_type}catch(e){if(request===navigationRequest)error.value=e.message;return}
  }
  if(request!==navigationRequest)return
  navigationKey.value++
  destination.value = {section,id,serviceType:section==='projects'?(serviceType||'other'):null}
  tab.value = target
}
function sidebar(target) {
  navigationRequest++
  destination.value=null
  navigationKey.value++
  tab.value=target
}
function activeBusiness(section,type=null){
  return tab.value==='business'&&destination.value?.section===section&&(type===null||destination.value?.serviceType===type)
}
const loginForm = ref({ username: '', password: '' })
const bootstrapForm = ref({ token: '', username: 'admin', displayName: '系统管理员', password: '', confirm: '' })
const can = permission => user.value?.permissions?.includes(permission)
const initials = computed(() => (user.value?.displayName || '管').slice(0, 1))

async function refresh() {
  try {
    const status = await api('/bootstrap/status')
    initialized.value = status.initialized
    const token = sessionStorage.getItem('erp-next-token')
    if (token) {
      try {
        user.value = await api('/auth/me')
        await loadWorkspace()
      } catch {
        sessionStorage.removeItem('erp-next-token')
        user.value = null
      }
    }
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
async function loadWorkspace() {
  const requests = []

  if (can('identity:read')) requests.push(api('/admin/identities').then(v => { identities.value = v }))

  const results=await Promise.allSettled(requests)
  const rejected=results.find(r=>r.status==='rejected')
  if(rejected)error.value='部分工作台数据未加载：'+rejected.reason.message
}
async function bootstrap() {
  error.value = ''; notice.value = ''
  if (bootstrapForm.value.password !== bootstrapForm.value.confirm) { error.value = '两次输入的密码不一致'; return }
  submitting.value = true
  try {
    await api('/bootstrap/admin', { method: 'POST', headers: { 'X-Bootstrap-Token': bootstrapForm.value.token }, body: JSON.stringify({
      username: bootstrapForm.value.username, displayName: bootstrapForm.value.displayName, password: bootstrapForm.value.password
    }) })
    initialized.value = true
    notice.value = '管理员已创建，请使用新账号登录。'
    loginForm.value.username = bootstrapForm.value.username
    bootstrapForm.value = { token: '', username: 'admin', displayName: '系统管理员', password: '', confirm: '' }
  } catch (e) { error.value = e.message }
  finally { submitting.value = false }
}
async function login() {
  error.value = ''; notice.value = ''; submitting.value = true
  try {
    const result = await api('/auth/login', { method: 'POST', body: JSON.stringify(loginForm.value) })
    sessionStorage.setItem('erp-next-token', result.token)
    user.value = { id: result.id, username: result.username, displayName: result.displayName, permissions: result.permissions }
    await loadWorkspace()
  } catch (e) { error.value = e.message }
  finally { submitting.value = false }
}
async function logout() {
  try { await api('/auth/logout', { method: 'POST' }) } catch { /* local session is still cleared */ }
  sessionStorage.removeItem('erp-next-token')
  user.value = null; identities.value = []; tab.value = 'overview'; destination.value=null
}
onMounted(refresh)
</script>

<template>
  <main v-if="loading" class="loading-screen"><div class="loader"></div><span>正在准备工作空间…</span></main>
  <main v-else-if="!user" class="auth-shell">
    <section class="auth-aside">
      <div class="brand"><span class="brand-mark">众</span><span>众基 ERP</span></div>
      <div class="aside-copy">
        <span class="eyebrow">A CLEARER WAY TO WORK</span>
        <h1>让业务关系清晰，<br>让每一步都有方向。</h1>
        <p>统一管理客户、合同、项目与技术服务。</p>
      </div>
      <div class="aside-foot">ZJ ERP · NEXT GENERATION</div>
    </section>
    <section class="auth-main"><div class="auth-theme"><ThemeSwitch /></div>
      <div class="auth-card">
        <div class="mobile-brand"><span class="brand-mark">众</span><b>众基 ERP</b></div>
        <template v-if="!initialized">
          <span class="eyebrow">FIRST-TIME SETUP</span>
          <h2>创建系统管理员</h2>
          <p class="muted">这是新系统首次初始化。请向部署管理员获取一次性初始化凭据。</p>
          <form @submit.prevent="bootstrap" class="form-stack">
            <label>初始化凭据<input v-model="bootstrapForm.token" type="password" autocomplete="off" required placeholder="输入部署时配置的初始化凭据"></label>
            <label>管理员账号<input v-model="bootstrapForm.username" readonly autocomplete="username"></label>
            <label>显示名称<input v-model="bootstrapForm.displayName" required maxlength="200"></label>
            <label>设置密码<input v-model="bootstrapForm.password" type="password" required minlength="12" maxlength="128" autocomplete="new-password" placeholder="至少 12 位"></label>
            <label>确认密码<input v-model="bootstrapForm.confirm" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></label>
            <button class="primary-button" :disabled="submitting">{{ submitting ? '正在创建…' : '创建管理员' }}</button>
          </form>
        </template>
        <template v-else>
          <span class="eyebrow">WELCOME BACK</span>
          <h2>登录工作空间</h2>
          <p class="muted">使用管理员或已分配权限的工作账号继续。</p>
          <form @submit.prevent="login" class="form-stack">
            <label>账号<input v-model="loginForm.username" required autocomplete="username" placeholder="输入账号"></label>
            <label>密码<input v-model="loginForm.password" type="password" required autocomplete="current-password" placeholder="输入密码"></label>
            <button class="primary-button" :disabled="submitting">{{ submitting ? '正在登录…' : '登录' }}<span>→</span></button>
          </form>
        </template>
        <p v-if="error" class="message error" role="alert">{{ error }}</p>
        <p v-if="notice" class="message success" role="status">{{ notice }}</p>
        <p class="security-note"><span>●</span> 安全会话 · 权限由服务端验证</p>
      </div>
      <footer>© 2026 众基 ERP · 新版工作空间</footer>
    </section>
  </main>

  <main v-else class="workspace">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">众</span><span>众基 ERP</span></div>
      <div class="side-caption">工作空间</div>
      <button :class="{active:tab==='overview'}" @click="sidebar('overview')"><span>⌂</span>工作台</button>
      <div v-if="['employee:read','partner:read','customer:read','report:read','report:standard:write','report:template:write'].some(can)" class="side-caption">基础资料</div>
      <button v-if="can('employee:read')" :class="{active:activeBusiness('employees')}" @click="navigate('business','employees')"><span>♙</span>员工档案</button>
      <button v-if="can('partner:read')" :class="{active:tab==='partner-maintenance'&&destination?.section==='partners'}" @click="navigate('partner-maintenance','partners')"><span>♧</span>中间人管理</button>
      <button v-if="can('customer:read')" :class="{active:activeBusiness('customers')}" @click="navigate('business','customers')"><span>▦</span>甲方管理</button>
      <button v-if="can('report:read')||can('report:standard:write')" :class="{active:tab==='fire-standards'}" @click="sidebar('fire-standards')"><span>▤</span>消防规范资料库</button>
      <button v-if="can('report:template:write')" :class="{active:tab==='inspection-templates'}" @click="sidebar('inspection-templates')"><span>▤</span>检测报告模板</button>
      <div v-if="can('contract:read')||can('project:read')" class="side-caption">合同与项目</div>
      <button v-if="can('contract:read')" :class="{active:activeBusiness('contracts')||(tab==='partner-maintenance'&&destination?.section==='links')}" @click="navigate('business','contracts')"><span>▤</span>合同管理</button>
      <template v-if="can('project:read')">
        <button v-for="p in projectTypes" :key="p.type" :class="{active:activeBusiness('projects',p.type)||(p.type==='maintenance'&&tab==='partner-maintenance'&&destination?.section==='maintenance')}" @click="navigate('business','projects',null,p.type)"><span>◈</span>{{p.title}}</button>
      </template>
      <div class="side-caption">财务管理</div>
      <button v-if="can('expense:read')" :class="{active:tab==='finance'}" @click="tab='finance'"><span>¥</span>费用报账与审批</button>
      <button v-if="can('invoice:read')" :class="{active:tab==='invoices'}" @click="tab='invoices'"><span>▤</span>发票与应收应付</button>
      <div class="side-caption">系统管理</div>
      <button v-if="can('identity:read')" :class="{active:tab==='identities'}" @click="tab='identities'"><span>♙</span>账户管理</button>
      <div class="sidebar-bottom"><div class="avatar">{{ initials }}</div><div class="user-meta"><b>{{ user.displayName }}</b><small>{{ user.username }}</small></div><button class="logout" @click="logout" title="退出登录">↗</button></div>
    </aside>
    <section class="workspace-main">
      <header class="topbar"><div><span class="eyebrow">ZJ ERP / NEXT</span><h1>{{ pageTitle }}</h1></div><div class="topbar-actions"><ThemeSwitch/><div class="top-user"><span class="online-dot"></span>{{ user.displayName }}</div></div></header>
      <div class="content" :key="tab+':'+navigationKey">
        <p v-if="error" class="message error" role="alert">{{ error }}</p>
        <p v-if="notice" class="message success" role="status">{{ notice }}</p>
        <template v-if="tab==='overview'">
          <section class="welcome-card"><div><span class="eyebrow">YOUR WORKSPACE</span><h2>你好，{{ user.displayName }}</h2><p>从左侧选择业务模块，开始今天的工作。</p></div><div class="welcome-symbol">众</div></section>
          <div class="section-heading"><div><h2>平台状态</h2><p>当前已启用的基础能力</p></div><span class="status-pill"><i></i>运行中</span></div>
          <div class="stat-grid"><article class="stat-card"><span>当前账号</span><b>{{ user.username }}</b><small>身份认证已启用</small></article><article class="stat-card"><span>账户权限</span><b>{{ user.username==='admin'?'超级账户':can('report:review')?'管理人员':'普通员工' }}</b><small>固定账户权限</small></article><article class="stat-card"><span>账号档案</span><b>{{ identities.length || '—' }}</b><small>当前可查看的账号</small></article></div>
          
        </template>
        <AccountManagement v-else-if="tab==='identities'&&can('identity:write')" :user-id="user.id" @self-changed="logout" @updated="loadWorkspace"/>
        <PartnerMaintenance v-else-if="tab==='partner-maintenance'" :key="navigationKey" :permissions="user.permissions" :destination="destination" @navigate="navigate" />
        <FireStandardLibrary v-else-if="tab==='fire-standards'&&(can('report:read')||can('report:standard:write'))" :permissions="user.permissions"/>
        <InspectionTemplateLibrary v-else-if="tab==='inspection-templates'&&can('report:template:write')" :permissions="user.permissions"/>
        <ServiceWorkflow v-else-if="tab==='service-workflow'" :key="navigationKey" :permissions="user.permissions" :user-id="user.id" :destination="destination" @navigate="navigate" />
        <InvoiceModule v-else-if="tab==='invoices'" :permissions="user.permissions" />
        <FinanceModule v-else-if="tab==='finance'" :permissions="user.permissions" :user-id="user.id" />
        <BusinessModule v-else :key="navigationKey" :permissions="user.permissions" :destination="destination" @navigate="navigate" />
      </div>
    </section>
  </main>
</template>