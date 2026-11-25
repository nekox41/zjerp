<script setup>
import {computed,onMounted,ref} from 'vue'
import {api} from './api'
import ModalDialog from './ModalDialog.vue'
const props=defineProps({userId:Number})
const emit=defineEmits(['self-changed','updated'])
const rows=ref([]),q=ref(''),loading=ref(false),busy=ref(false),error=ref(''),notice=ref(''),dialog=ref(null),form=ref({}),formError=ref('')
const labels={employee:'普通员工',manager:'管理人员',admin:'超级账户'}
const shown=computed(()=>rows.value.filter(x=>[x.username,x.display_name,labels[x.role]].join(' ').toLowerCase().includes(q.value.trim().toLowerCase())))
const superAccount=computed(()=>dialog.value?.item?.username==='admin')
async function load(){loading.value=true;error.value='';try{rows.value=await api('/admin/identities')}catch(e){error.value=e.message}finally{loading.value=false}}
function open(item=null){formError.value='';dialog.value={item};form.value={username:item?.username||'',displayName:item?.display_name||'',password:'',role:item?.role||'employee',status:item?.status==='active'?'active':item?'disabled':'active'}}
function close(){if(busy.value)return;dialog.value=null;form.value={};formError.value=''}
async function save(){
 if(busy.value)return
 const item=dialog.value.item
 busy.value=true;formError.value='';notice.value=''
 try{
  const {username,displayName,password,role,status}=form.value
  const body=item?{displayName,role,status,...(password?{password}:{})}:{username,displayName,password,role}
  await api(item?'/admin/identities/'+item.id:'/admin/identities',{method:item?'PUT':'POST',body:JSON.stringify(body)})
  const self=item&&Number(item.id)===Number(props.userId)&&password
  dialog.value=null;form.value={}
  if(self){emit('self-changed');return}
  notice.value=item?'账户已更新':'账户已创建'
  await load();emit('updated')
 }catch(e){formError.value=e.message}finally{busy.value=false}
}
onMounted(load)
</script>
<template><section class="account-module">
 <div class="section-heading"><div><h2>账户列表</h2><p>管理员维护所有登录账户。权限仅分为三类。</p></div><button class="primary-button" @click="open()">＋ 新建账户</button></div>
 <p v-if="error" class="message error" role="alert">{{error}}</p><p v-if="notice" class="message success" role="status">{{notice}}</p>
 <section class="panel"><div class="account-toolbar"><input v-model="q" placeholder="搜索姓名、账号或权限" aria-label="搜索账户"><span class="muted">共 {{shown.length}} 个账户</span><button class="secondary-button" :disabled="loading" @click="load">刷新</button></div>
 <div class="table-wrap"><table><thead><tr><th>姓名</th><th>账号</th><th>权限</th><th>状态</th><th class="account-right">操作</th></tr></thead><tbody><tr v-for="item in shown" :key="item.id"><td><b>{{item.display_name}}</b></td><td>{{item.username}}</td><td><span class="account-tier" :class="item.role">{{labels[item.role]||'普通员工'}}</span></td><td>{{item.status==='active'?'启用':'停用'}}</td><td class="account-right"><button class="secondary-button" @click="open(item)">编辑</button></td></tr></tbody></table><p v-if="!shown.length" class="empty">{{loading?'正在加载…':'暂无符合条件的账户'}}</p></div></section>
 <ModalDialog v-if="dialog" :title="dialog.item?'编辑账户':'新建账户'" :description="dialog.item?'修改账户资料；留空密码则保持原密码。':'输入账户信息并选择固定权限。'" :busy="busy" @close="close">
 <form class="account-form" @submit.prevent="save">
 <label>姓名<input v-model.trim="form.displayName" required maxlength="200" autocomplete="name" autofocus></label>
 <label>账号<input v-model.trim="form.username" :readonly="!!dialog.item" required maxlength="100" autocomplete="off"></label>
 <label>{{dialog.item?'新密码（可选）':'密码'}}<input v-model="form.password" type="password" :required="!dialog.item" minlength="12" maxlength="72" autocomplete="new-password" placeholder="至少 12 个字符"></label>
 <label>权限<select v-model="form.role" :disabled="superAccount"><option v-if="superAccount" value="admin">超级账户（admin）</option><option value="employee">普通员工</option><option value="manager">管理人员</option></select></label>
 <p class="muted account-role-help">{{form.role==='employee'?'日常业务、现场记录、报告编制与本人报账；不可审核签发或管理账户。':form.role==='manager'?'业务管理、审核签发与财务管理；不可管理账户或迁移。':'唯一超级账户，不可停用或降级。'}}</p>
 <label v-if="dialog.item&&!superAccount">状态<select v-model="form.status"><option value="active">启用</option><option value="disabled">停用</option></select></label>
 <p v-if="formError" class="message error" role="alert">{{formError}}</p>
 <div class="account-footer"><button type="button" class="secondary-button" :disabled="busy" @click="close">取消</button><button class="primary-button" :disabled="busy">{{busy?'保存中…':dialog.item?'保存':'创建账户'}}</button></div>
 </form></ModalDialog></section></template>
<style scoped>.account-module{display:grid;gap:16px}.account-toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:16px}.account-toolbar input{max-width:320px;flex:1;min-width:160px}.account-toolbar .muted{margin-left:auto}.account-right{text-align:right}.account-tier{display:inline-block;padding:3px 9px;border-radius:5px;background:#f0f3f7;color:#50627a;font-size:12px;white-space:nowrap}.account-tier.manager{background:#eaf0ff;color:#3558a0}.account-tier.admin{background:#e8edf5;color:#223e66}.account-form{padding:20px 24px;display:grid;gap:15px}.account-form label{display:grid;gap:7px;font-size:13px;font-weight:600}.account-form input,.account-form select,.account-toolbar input{width:100%;box-sizing:border-box;border:1px solid #dbe3ed;border-radius:8px;padding:10px 12px;font:inherit;background:white;color:#172b4d}.account-form input[readonly],.account-form select:disabled{background:#f4f6f9;color:#65758a}.account-role-help{margin:-8px 0 0;font-size:12px;line-height:1.6}.account-footer{display:flex;justify-content:flex-end;gap:8px;border-top:1px solid #e9eef4;padding-top:16px;margin-top:6px}</style>