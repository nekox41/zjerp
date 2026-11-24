<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from './api'
import BusinessModule from './BusinessModule.vue'
import FinanceModule from './FinanceModule.vue'
import InvoiceModule from './InvoiceModule.vue'
import DashboardModule from './DashboardModule.vue'
import ServiceWorkflow from './ServiceWorkflow.vue'
import PartnerMaintenance from './PartnerMaintenance.vue'

const loading = ref(true)
const submitting = ref(false)
const initialized = ref(true)
const user = ref(null)
const roles = ref([])
const permissionCatalog=ref([])
const roleEditor=ref(false)
const roleForm=ref({id:null,name:'',permissionIds:[]})
const permissionGroups=computed(()=>{
  const groups={}
  for(const p of permissionCatalog.value.filter(x=>x.code!=='identity:write')){
    const key=p.code.split(':')[0]
    ;(groups[key]??=[]).push(p)
  }
  return groups
})
const roleLabels={workspace:'工作台',identity:'账号',audit:'审计',employee:'员工',customer:'甲方',contract:'合同',partner:'中间人',project:'项目',maintenance:'维护保养',service:'技术服务',report:'报告',delivery:'交付',finance:'财务',expense:'费用',invoice:'发票',organization:'组织',file:'文件'}
async function editRole(role=null){
  error.value='';notice.value=''
  try{
    permissionCatalog.value=await api('/admin/permissions')
    roleForm.value=role?{id:role.id,name:role.name,permissionIds:await api('/admin/roles/'+role.id+'/permissions')}:{id:null,name:'',permissionIds:[]}
    roleEditor.value=true
  }catch(e){error.value=e.message}
}
async function saveRole(){
  submitting.value=true;error.value='';notice.value=''
  try{
    const {id,name,permissionIds}=roleForm.value
    await api(id?'/admin/roles/'+id:'/admin/roles',{method:id?'PUT':'POST',body:JSON.stringify({name,permissionIds})})
    roleEditor.value=false
    roles.value=await api('/admin/roles')
    notice.value='角色已保存。已关联账户的权限将在重新登录后更新。'
  }catch(e){error.value=e.message}finally{submitting.value=false}
}
const identities = ref([])
const audit = ref([])
const error = ref('')
const notice = ref('')
const tab = ref('overview')
const destination = ref(null)
const navigationKey = ref(0)
function navigate(target, section=null, id=null) {
  const allowed = {
    business: section ? can(({customers:'customer:read',contracts:'contract:read',projects:'project:read',employees:'employee:read'})[section]) : ['customer:read','contract:read','project:read','employee:read'].some(can),
    'partner-maintenance': section==='maintenance' ? can('maintenance:read')&&can('project:read') : section==='leadership' ? can('project:read')&&can('project:write') : section==='links' ? can('contract:read') : can('partner:read')||can('maintenance:read')||can('contract:read')||can('project:read'),
    'service-workflow': can('service:read')
  }
  if (!allowed[target]) return
  destination.value = {section,id}
  navigationKey.value++
  tab.value = target
}
function sidebar(target) {
  destination.value=null
  navigationKey.value++
  tab.value=target
}
const loginForm = ref({ username: '', password: '' })
const registering=ref(false)
const registerForm=ref({username:'',displayName:'',password:'',confirm:''})
async function register(){
  error.value='';notice.value=''
  if(registerForm.value.password!==registerForm.value.confirm){error.value='两次输入的密码不一致';return}
  submitting.value=true
  try{
    const {username,displayName,password}=registerForm.value
    await api('/auth/register',{method:'POST',body:JSON.stringify({username,displayName,password})})
    loginForm.value.username=username
    loginForm.value.password=''
    registerForm.value={username:'',displayName:'',password:'',confirm:''}
    registering.value=false
    notice.value='注册成功，请等待管理员启用账户并分配权限。'
  }catch(e){error.value=e.message}finally{submitting.value=false}
}
function toggleRegister(){
  registering.value=!registering.value;error.value='';notice.value=''
  registerForm.value={username:'',displayName:'',password:'',confirm:''}
}
const bootstrapForm = ref({ token: '', username: 'admin', displayName: '系统管理员', password: '', confirm: '' })
const createForm = ref({ username: '', displayName: '', password: '', roleIds: [] })
const identityEdit = ref(null)
const identityEditForm = ref({ displayName: '', status: 'active', roleIds: [], password: '' })
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
  if (can('identity:read')) requests.push(api('/admin/roles').then(v => { roles.value = v }))
  if (can('identity:read')) requests.push(api('/admin/identities').then(v => { identities.value = v }))
  if (can('audit:read')) requests.push(api('/admin/audit?limit=30').then(v => { audit.value = v }))
  await Promise.all(requests)
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
  user.value = null; identities.value = []; roles.value=[]; permissionCatalog.value=[]; roleEditor.value=false; identityEdit.value=null; audit.value = []; tab.value = 'overview'; destination.value=null
}
async function createIdentity() {
  error.value = ''; notice.value = ''; submitting.value = true
  try {
    await api('/admin/identities', { method: 'POST', body: JSON.stringify(createForm.value) })
    notice.value = '账号创建成功。'
    createForm.value = { username: '', displayName: '', password: '', roleIds: [] }
    identities.value = await api('/admin/identities')
  } catch (e) { error.value = e.message }
  finally { submitting.value = false }
}

function openIdentityEditor(item) {
  identityEdit.value = item
  const roleCodes = (item.roles || '').split(',').filter(Boolean)
  identityEditForm.value = {
    displayName: item.display_name || '',
    status: item.status || 'active',
    roleIds: roles.value.filter(role => roleCodes.includes(role.code)).map(role => role.id),
    password: ''
  }
}
async function saveIdentity() {
  if (!identityEdit.value) return
  error.value = ''; notice.value = ''; submitting.value = true
  try {
    const body = {
      displayName: identityEditForm.value.displayName,
      status: identityEditForm.value.status,
      roleIds: identityEditForm.value.roleIds
    }
    if (identityEditForm.value.password) body.password = identityEditForm.value.password
    const editingSelf=identityEdit.value.id===user.value.id
    await api('/admin/identities/' + identityEdit.value.id, { method: 'PUT', body: JSON.stringify(body) })
    if(editingSelf){await logout();identityEdit.value=null;notice.value='账号已更新，请重新登录。';return}
    notice.value = '账号资料、角色和状态已更新。'
    identityEdit.value = null
    identityEditForm.value = { displayName: '', status: 'active', roleIds: [], password: '' }
    identities.value = await api('/admin/identities')
  } catch (e) { error.value = e.message }
  finally { submitting.value = false }
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
        <p>新版 ERP 正在逐步构建。先从安全、清晰的工作空间开始。</p>
      </div>
      <div class="aside-foot">ZJ ERP · NEXT GENERATION</div>
    </section>
    <section class="auth-main">
      <div class="auth-card">
        <div class="mobile-brand"><span class="brand-mark">众</span><b>众基 ERP</b></div>
        <template v-if="!initialized">
          <span class="eyebrow">FIRST-TIME SETUP</span>
          <h2>创建系统管理员</h2>
          <p class="muted">这是新系统首次初始化。请向部署管理员获取一次性初始化凭据。</p>
          <form @submit.prevent="bootstrap" class="form-stack">
            <label>初始化凭据<input v-model="bootstrapForm.token" type="password" autocomplete="off" required placeholder="输入部署时配置的初始化凭据"></label>
            <label>管理员账号<input v-model="bootstrapForm.username" required maxlength="100" autocomplete="username"></label>
            <label>显示名称<input v-model="bootstrapForm.displayName" required maxlength="200"></label>
            <label>设置密码<input v-model="bootstrapForm.password" type="password" required minlength="12" maxlength="128" autocomplete="new-password" placeholder="至少 12 位"></label>
            <label>确认密码<input v-model="bootstrapForm.confirm" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></label>
            <button class="primary-button" :disabled="submitting">{{ submitting ? '正在创建…' : '创建管理员' }}</button>
          </form>
        </template>
        <template v-else-if="registering">
          <span class="eyebrow">CREATE ACCOUNT</span>
          <h2>注册工作账号</h2>
          <p class="muted">注册后由管理员核实身份、启用账户并分配权限。</p>
          <form @submit.prevent="register" class="form-stack">
            <label>账号<input v-model="registerForm.username" required maxlength="100" autocomplete="username"></label>
            <label>姓名<input v-model="registerForm.displayName" required maxlength="200" autocomplete="name"></label>
            <label>密码<input v-model="registerForm.password" type="password" required minlength="12" maxlength="72" autocomplete="new-password"></label>
            <label>确认密码<input v-model="registerForm.confirm" type="password" required minlength="12" maxlength="72" autocomplete="new-password"></label>
            <button class="primary-button" :disabled="submitting">提交注册申请</button>
            <button type="button" class="secondary-button" @click="toggleRegister" :disabled="submitting">返回登录</button>
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
          <button type="button" class="secondary-button" @click="toggleRegister" :disabled="submitting">注册普通账户</button>
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
      <button :class="{active:tab==='overview'}" @click="tab='overview'"><span>⌂</span>工作台</button>
      <button v-if="can('project:read')" :class="{active:tab==='dashboard'}" @click="tab='dashboard'"><span>◫</span>经营驾驶舱</button>
      <div class="side-caption">业务管理</div>
      <button v-if="can('customer:read') || can('contract:read') || can('project:read') || can('employee:read')" :class="{active:tab==='business'}" @click="sidebar('business')"><span>▦</span>业务全生命周期</button>
      <button v-if="can('partner:read') || can('contract:read') || (can('project:read')&&(can('maintenance:read')||can('project:write')))" :class="{active:tab==='partner-maintenance'}" @click="sidebar('partner-maintenance')"><span>♧</span>合作方与维保</button>
      <button v-if="can('service:read')" :class="{active:tab==='service-workflow'}" @click="sidebar('service-workflow')"><span>◈</span>技术服务交付</button>
      <div class="side-caption">财务管理</div>
      <button v-if="can('expense:read')" :class="{active:tab==='finance'}" @click="tab='finance'"><span>¥</span>费用报账与审批</button>
      <button v-if="can('invoice:read')" :class="{active:tab==='invoices'}" @click="tab='invoices'"><span>▤</span>发票与应收应付</button>
      <div class="side-caption">系统管理</div>
      <button v-if="can('identity:read')" :class="{active:tab==='identities'}" @click="tab='identities'"><span>♙</span>账号与角色</button>
      <button v-if="can('audit:read')" :class="{active:tab==='audit'}" @click="tab='audit'"><span>◷</span>操作审计</button>
      <div class="sidebar-bottom"><div class="avatar">{{ initials }}</div><div class="user-meta"><b>{{ user.displayName }}</b><small>{{ user.username }}</small></div><button class="logout" @click="logout" title="退出登录">↗</button></div>
    </aside>
    <section class="workspace-main">
      <header class="topbar"><div><span class="eyebrow">ZJ ERP / NEXT</span><h1>{{ tab==='overview'?'工作台':tab==='identities'?'账号与角色':tab==='audit'?'操作审计':tab==='partner-maintenance'?'合作方与维保':tab==='service-workflow'?'技术服务交付':tab==='dashboard'?'经营驾驶舱':tab==='invoices'?'发票与应收应付':tab==='finance'?'费用报账与审批':'业务全生命周期' }}</h1></div><div class="top-user"><span class="online-dot"></span>{{ user.displayName }}</div></header>
      <div class="content">
        <p v-if="error" class="message error" role="alert">{{ error }}</p>
        <p v-if="notice" class="message success" role="status">{{ notice }}</p>
        <template v-if="tab==='overview'">
          <section class="welcome-card"><div><span class="eyebrow">YOUR WORKSPACE</span><h2>你好，{{ user.displayName }}</h2><p>平台基础已经就绪。接下来将逐步开放客户、合同、项目与交付流程。</p></div><div class="welcome-symbol">众</div></section>
          <div class="section-heading"><div><h2>平台状态</h2><p>当前已启用的基础能力</p></div><span class="status-pill"><i></i>运行中</span></div>
          <div class="stat-grid"><article class="stat-card"><span>当前账号</span><b>{{ user.username }}</b><small>身份认证已启用</small></article><article class="stat-card"><span>可用权限</span><b>{{ user.permissions?.length || 0 }}</b><small>由服务端角色授权</small></article><article class="stat-card"><span>账号档案</span><b>{{ identities.length || '—' }}</b><small>当前可查看的账号</small></article></div>
          <section class="next-card"><div class="next-icon">✦</div><div><b>接下来会发生什么？</b><p>新版业务模块将按真实工作流程逐步上线。现阶段不会展示尚未实现的功能入口，也不会将旧系统数据误当作新系统数据。</p></div></section>
        </template>
        <template v-else-if="tab==='identities'">
          <div class="section-heading"><div><h2>账号管理</h2><p>系统账号独立于员工档案；在这里维护登录状态、角色权限和密码。</p></div></div>
          <section v-if="can('identity:write')" class="panel">
            <div class="section-heading"><div><h3>角色与权限</h3><p>预设角色不可修改，可按实际工作需要创建自定义角色。</p></div><button type="button" class="secondary-button" @click="editRole()">新建角色</button></div>
            <div class="table-wrap"><table><thead><tr><th>角色</th><th>类型</th><th>操作</th></tr></thead><tbody><tr v-for="r in roles" :key="r.id"><td>{{r.name}}</td><td>{{r.code.startsWith('custom_')?'自定义':'系统预设'}}</td><td><button v-if="r.code.startsWith('custom_')" type="button" class="secondary-button" @click="editRole(r)">编辑权限</button></td></tr></tbody></table></div>
            <form v-if="roleEditor" class="form-stack" @submit.prevent="saveRole">
              <label>角色名称<input v-model="roleForm.name" required maxlength="150"></label>
              <div v-for="(items,key) in permissionGroups" :key="key" class="related-card">
                <h4>{{roleLabels[key]||key}}</h4>
                <div style="display:flex;flex-wrap:wrap;gap:12px">
                  <label v-for="p in items" :key="p.id" style="display:flex;align-items:center;gap:5px;flex-direction:row"><input type="checkbox" v-model="roleForm.permissionIds" :value="p.id" style="width:auto">{{p.name}}</label>
                </div>
              </div>
              <div class="report-actions"><button class="primary-button" :disabled="submitting">保存角色</button><button type="button" class="secondary-button" @click="roleEditor=false">取消</button></div>
            </form>
          </section>
          <section v-if="can('identity:write')" class="panel create-panel"><h3>新建账号</h3><form class="create-grid" @submit.prevent="createIdentity"><label>登录账号<input v-model="createForm.username" required maxlength="100"></label><label>显示名称<input v-model="createForm.displayName" required maxlength="200"></label><label>初始密码<input v-model="createForm.password" type="password" required minlength="12" maxlength="128"></label><label>分配角色<select v-model="createForm.roleIds" multiple><option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</option></select></label><button class="primary-button" :disabled="submitting">创建账号</button></form></section>
          <section v-if="identityEdit&&can('identity:write')" class="panel create-panel"><h3>编辑账号 · {{ identityEdit.username }}</h3><form class="create-grid" @submit.prevent="saveIdentity"><label>显示名称<input v-model="identityEditForm.displayName" required maxlength="200"></label><label>账号状态<select v-model="identityEditForm.status"><option value="active">启用</option><option value="disabled">停用</option></select></label><label>角色权限<select v-model="identityEditForm.roleIds" multiple><option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</option></select></label><label>重置密码（留空则不修改）<input v-model="identityEditForm.password" type="password" minlength="12" maxlength="128" autocomplete="new-password"></label><div class="report-actions"><button class="primary-button" :disabled="submitting">保存账号</button><button type="button" class="secondary-button" @click="identityEdit=null">取消</button></div></form><p class="muted">停用账号会立即阻止后续请求；重置密码会撤销该账号已有会话。系统至少保留一个启用的系统管理员。</p></section>
          <section class="panel"><h3>已有账号 <span>{{ identities.length }}</span></h3><div class="table-wrap"><table><thead><tr><th>账号</th><th>显示名称</th><th>角色</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in identities" :key="item.id"><td>{{ item.username }}</td><td>{{ item.display_name }}</td><td>{{ (item.roles||'').split(',').filter(Boolean).map(code=>roles.find(r=>r.code===code)?.name||code).join('、')||'未分配' }}</td><td><span class="status-pill"><i></i>{{ item.status==='active'?'启用':'停用' }}</span></td><td><button v-if="can('identity:write')" class="secondary-button" @click="openIdentityEditor(item)">编辑</button></td></tr></tbody></table><p v-if="!identities.length" class="empty">暂无账号数据</p></div></section>
        </template>
        <template v-else-if="tab==='audit'">
          <section class="panel"><h3>最近操作 <span>最近 30 条</span></h3><div class="table-wrap"><table><thead><tr><th>时间</th><th>操作</th><th>对象</th><th>摘要</th></tr></thead><tbody><tr v-for="item in audit" :key="item.id"><td>{{ item.occurred_at }}</td><td>{{ item.action }}</td><td>{{ item.resource_type }} / {{ item.resource_id }}</td><td>{{ typeof item.summary_json === 'string' ? item.summary_json : JSON.stringify(item.summary_json) }}</td></tr></tbody></table><p v-if="!audit.length" class="empty">暂无审计记录</p></div></section>
        </template>
        <PartnerMaintenance v-else-if="tab==='partner-maintenance'" :key="navigationKey" :permissions="user.permissions" :destination="destination" @navigate="navigate" />
        <ServiceWorkflow v-else-if="tab==='service-workflow'" :key="navigationKey" :permissions="user.permissions" :user-id="user.id" :destination="destination" @navigate="navigate" />
        <DashboardModule v-else-if="tab==='dashboard'" />
        <InvoiceModule v-else-if="tab==='invoices'" :permissions="user.permissions" />
        <FinanceModule v-else-if="tab==='finance'" :permissions="user.permissions" :user-id="user.id" />
        <BusinessModule v-else :key="navigationKey" :permissions="user.permissions" :destination="destination" @navigate="navigate" />
      </div>
    </section>
  </main>
</template>