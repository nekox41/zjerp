<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from './api'
import BusinessModule from './BusinessModule.vue'
import FinanceModule from './FinanceModule.vue'
import InvoiceModule from './InvoiceModule.vue'
import DashboardModule from './DashboardModule.vue'

const loading = ref(true)
const submitting = ref(false)
const initialized = ref(true)
const user = ref(null)
const roles = ref([])
const identities = ref([])
const audit = ref([])
const error = ref('')
const notice = ref('')
const tab = ref('overview')
const loginForm = ref({ username: '', password: '' })
const bootstrapForm = ref({ token: '', username: 'admin', displayName: '系统管理员', password: '', confirm: '' })
const createForm = ref({ username: '', displayName: '', password: '', roleIds: [] })
const selectedRoleId = ref('')
const overviewCounts = ref({ customers: 0, contracts: 0, projects: 0 })
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
  if (can('customer:read')) requests.push(api('/business/customers').then(v => { overviewCounts.value.customers = v.length }))
  if (can('contract:read')) requests.push(api('/business/contracts').then(v => { overviewCounts.value.contracts = v.length }))
  if (can('project:read')) requests.push(api('/business/projects').then(v => { overviewCounts.value.projects = v.length }))
  await Promise.all(requests)
  if (!selectedRoleId.value) selectedRoleId.value = String(roles.value.find(r => r.code === 'employee')?.id || roles.value[0]?.id || '')
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
  user.value = null; identities.value = []; audit.value = []; tab.value = 'overview'
}
async function createIdentity() {
  error.value = ''; notice.value = ''; submitting.value = true
  try {
    await api('/admin/identities', { method: 'POST', body: JSON.stringify({ ...createForm.value, roleIds: selectedRoleId.value ? [Number(selectedRoleId.value)] : [] }) })
    notice.value = '账号创建成功。'
    createForm.value = { username: '', displayName: '', password: '', roleIds: [] }
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
        <p>合同、项目和服务管理。</p>
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
      <button :class="{active:tab==='overview'}" @click="tab='overview'"><span>⌂</span>工作台</button>
      
      <div class="side-caption">业务管理</div>
      <button v-if="can('customer:read') || can('contract:read') || can('project:read') || can('employee:read')" :class="{active:tab==='business'}" @click="tab='business'"><span>▦</span>业务</button>
      <div class="side-caption">财务</div>
      <button v-if="can('expense:read')" :class="{active:tab==='finance'}" @click="tab='finance'"><span>¥</span>报账</button>
      <button v-if="can('invoice:read')" :class="{active:tab==='invoices'}" @click="tab='invoices'"><span>▤</span>财务台账</button>
      <div class="side-caption">系统管理</div>
      <button v-if="can('identity:read')" :class="{active:tab==='identities'}" @click="tab='identities'"><span>♙</span>账号与角色</button>
      <button v-if="can('audit:read')" :class="{active:tab==='audit'}" @click="tab='audit'"><span>◷</span>操作审计</button>
      <div class="sidebar-bottom"><div class="avatar">{{ initials }}</div><div class="user-meta"><b>{{ user.displayName }}</b><small>{{ user.username }}</small></div><button class="logout" @click="logout" title="退出登录">↗</button></div>
    </aside>
    <section class="workspace-main">
      <header class="topbar"><div><span class="eyebrow">ZJ ERP / NEXT</span><h1>{{ tab==='overview'?'工作台':tab==='identities'?'账号与角色':tab==='audit'?'操作审计':tab==='invoices'?'财务台账':tab==='finance'?'报账':'业务' }}</h1></div><div class="top-user"><span class="online-dot"></span>{{ user.displayName }}</div></header>
      <div class="content">
        <p v-if="error" class="message error" role="alert">{{ error }}</p>
        <p v-if="notice" class="message success" role="status">{{ notice }}</p>
        <template v-if="tab==='overview'">
          <div class="section-heading"><div><h2>业务概况</h2></div><button class="secondary-button" @click="tab='business'">进入业务</button></div>
          <div class="stat-grid"><article class="stat-card"><span>客户</span><b>{{ overviewCounts.customers }}</b></article><article class="stat-card"><span>合同</span><b>{{ overviewCounts.contracts }}</b></article><article class="stat-card"><span>项目</span><b>{{ overviewCounts.projects }}</b></article></div>
        </template>
        <template v-else-if="tab==='identities'">
          <div class="section-heading"><div><h2>账号管理</h2><p>创建账号并分配角色；员工档案将在后续业务模块中独立管理。</p></div></div>
          <section class="panel create-panel"><h3>新建账号</h3><form class="create-grid" @submit.prevent="createIdentity"><label>登录账号<input v-model="createForm.username" required maxlength="100"></label><label>显示名称<input v-model="createForm.displayName" required maxlength="200"></label><label>初始密码<input v-model="createForm.password" type="password" required minlength="12" maxlength="128"></label><label>员工类型 / 权限<select v-model="selectedRoleId" required><option v-for="role in roles" :key="role.id" :value="String(role.id)">{{ role.name }}</option></select></label><button class="primary-button" :disabled="submitting">创建账号</button></form></section>
          <section class="panel"><h3>已有账号 <span>{{ identities.length }}</span></h3><div class="table-wrap"><table><thead><tr><th>账号</th><th>显示名称</th><th>角色</th><th>状态</th></tr></thead><tbody><tr v-for="item in identities" :key="item.id"><td>{{ item.username }}</td><td>{{ item.display_name }}</td><td>{{ item.roles || '未分配' }}</td><td><span class="status-pill"><i></i>{{ item.status }}</span></td></tr></tbody></table><p v-if="!identities.length" class="empty">暂无账号数据</p></div></section>
        </template>
        <template v-else-if="tab==='audit'">
          <section class="panel"><h3>最近操作 <span>最近 30 条</span></h3><div class="table-wrap"><table><thead><tr><th>时间</th><th>操作</th><th>对象</th><th>摘要</th></tr></thead><tbody><tr v-for="item in audit" :key="item.id"><td>{{ item.occurred_at }}</td><td>{{ item.action }}</td><td>{{ item.resource_type }} / {{ item.resource_id }}</td><td>{{ typeof item.summary_json === 'string' ? item.summary_json : JSON.stringify(item.summary_json) }}</td></tr></tbody></table><p v-if="!audit.length" class="empty">暂无审计记录</p></div></section>
        </template>
        
        <InvoiceModule v-else-if="tab==='invoices'" :permissions="user.permissions" />
        <FinanceModule v-else-if="tab==='finance'" :permissions="user.permissions" :user-id="user.id" />
        <BusinessModule v-else />
      </div>
    </section>
  </main>
</template>