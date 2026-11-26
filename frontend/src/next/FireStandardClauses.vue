<script setup>
import FormDialog from './FormDialog.vue'
import {ref,onMounted} from 'vue'
import {api} from './api'
const props=defineProps({standard:{type:Object,required:true},canWrite:{type:Boolean,default:false}})
const rows=ref([]),q=ref(''),more=ref(false),busy=ref(false),error=ref(''),form=ref(null)
async function load(reset=true){if(busy.value)return;busy.value=true;error.value='';try{const p=new URLSearchParams({standardId:String(props.standard.id),q:q.value,includeInactive:String(props.canWrite),offset:String(reset?0:rows.value.length),limit:'50'});const r=await api('/fire-standard-clauses?'+p);rows.value=reset?r.rows:[...rows.value,...r.rows];more.value=r.hasMore}catch(e){error.value=e.message}finally{busy.value=false}}
function edit(c=null){form.value={clauseNo:c?.clause_no||'',summary:c?.requirement_summary||'',locator:c?.source_locator||'',confirmed:false}}
async function save(){if(busy.value)return;busy.value=true;error.value='';try{await api('/fire-standard-clauses',{method:'POST',body:JSON.stringify({...form.value,standardId:props.standard.id})});form.value=null}catch(e){error.value=e.message;return}finally{busy.value=false}await load()}
async function toggle(c){if(busy.value)return;busy.value=true;try{await api('/fire-standard-clauses/'+c.id+'/active',{method:'PUT',body:JSON.stringify({active:!c.active})})}catch(e){error.value=e.message;return}finally{busy.value=false}await load()}
onMounted(()=>load())
</script>
<template><section class="clause-panel"><h3>规范条款 · {{standard.standard_no}} {{standard.edition}}</h3><p>记录条款号、人工核验的要求摘要、原文所在章节/页码。不得根据标题猜测内容，或将摘要当作规范全文。</p><a :href="standard.access_url||standard.source_url" target="_blank" rel="noopener noreferrer">查看原文来源</a><p v-if="error" role="alert">{{error}}</p>
<div class="clause-actions"><input v-model="q" placeholder="检索条款号或摘要" @keydown.enter.prevent="load()"><button @click="load()" :disabled="busy">搜索</button><button v-if="canWrite&&standard.active&&standard.verified" @click="edit()">新增条款</button></div>
<FormDialog v-if="form" title="维护规范条款" :steps="['条款资料']" :busy="busy" :error="error" @clear-error="error=''" auto-open @close="form=null" save-label="保存修订">
<template #default="{step,finish}"><form @submit.prevent="finish(()=>save())">
<div v-show="step===0" data-wizard-step="0" class="wizard-fields">
<label>条款号<input v-model="form.clauseNo" required maxlength="80"></label>
<label>要求摘要<textarea v-model="form.summary" required maxlength="4000" rows="4"></textarea></label>
<label>原文定位（章节、页码等）<input v-model="form.locator" required maxlength="500"></label>
<label><span><input v-model="form.confirmed" type="checkbox">已核对本规范版本原文、条款定位和摘要</span></label>
<div class="clause-actions"><button :disabled="busy||!form.confirmed">保存新修订</button><button type="button" @click="form=null">取消</button></div>
</div>
</form></template></FormDialog>
<p v-if="!rows.length">暂无条款记录。</p><article v-for="c in rows" :key="c.id"><b>{{c.clause_no}} · 修订 v{{c.revision_no}} · {{c.active?'启用':'停用'}}</b><p>{{c.requirement_summary}}</p><small>{{c.source_locator}}</small><div v-if="canWrite" class="clause-actions"><button v-if="standard.active&&standard.verified" @click="edit(c)">修订</button><button :disabled="busy" @click="toggle(c)">{{c.active?'停用':'启用'}}</button></div></article><button v-if="more" :disabled="busy" @click="load(false)">更多</button></section></template>
<style scoped>.clause-panel{display:grid;gap:10px;padding:14px;background:var(--erp-bg-fff);border:1px solid var(--erp-border-e2e8f0);border-radius:8px}.clause-panel h3,.clause-panel p{margin:0}.clause-panel form,.clause-panel label,.clause-panel article{display:grid;gap:8px}.clause-panel article{border-top:1px solid var(--erp-border-e2e8f0);padding-top:10px}.clause-panel p,.clause-panel small{color:var(--erp-fg-64748b);font-size:12px}.clause-actions{display:flex;gap:8px;flex-wrap:wrap}.clause-panel input:not([type=checkbox]),.clause-panel textarea,.clause-panel button{border:1px solid var(--erp-border-dfe6ef);border-radius:7px;background:var(--erp-bg-fff);color:inherit;padding:8px;font:inherit}</style>
