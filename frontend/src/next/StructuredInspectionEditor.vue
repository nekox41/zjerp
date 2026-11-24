<script setup>
import {ref} from 'vue'
import {api} from './api'
const props=defineProps({systems:{type:Array,default:()=>[]}})
const emit=defineEmits(['saved'])
const form=ref({system:'',name:'',notes:'',cleanConfirmed:false}),items=ref([]),q=ref(''),clauses=ref([]),more=ref(false),busy=ref(false),error=ref(''),notice=ref('')
async function search(reset=true){
 if(busy.value)return
 busy.value=true;error.value=''
 try{const p=new URLSearchParams({q:q.value,offset:String(reset?0:clauses.value.length),limit:'50'});const r=await api('/fire-standard-clauses?'+p);clauses.value=reset?r.rows:[...clauses.value,...r.rows];more.value=r.hasMore}catch(e){error.value=e.message}finally{busy.value=false}
}
function add(c){items.value.push({item:'',clauseId:c.id,clause:c});form.value.cleanConfirmed=false}
function move(i,d){const j=i+d;if(j<0||j>=items.value.length)return;[items.value[i],items.value[j]]=[items.value[j],items.value[i]];form.value.cleanConfirmed=false}
async function clone(t){
 if(busy.value)return
 if(items.value.length&&!confirm('放弃当前未保存的模板？'))return
 busy.value=true;error.value=''
 try{const rows=await api('/inspection-templates/'+t.id+'/items');form.value={system:t.system_name,name:t.template_name,notes:t.notes||'',cleanConfirmed:false};items.value=rows.map(i=>({item:i.item_name,clauseId:i.clause_id,clause:{...i,id:i.clause_id}}));notice.value='已载入旧版本，保存将建立新修订；停用条款须替换。'}catch(e){error.value=e.message}finally{busy.value=false}
}
defineExpose({clone})
async function save(){
 if(busy.value||!form.value.cleanConfirmed||!items.value.length)return
 busy.value=true;error.value='';notice.value=''
 try{const r=await api('/inspection-templates/structured',{method:'POST',body:JSON.stringify({...form.value,items:items.value.map(i=>({item:i.item,clauseId:i.clauseId}))})});notice.value='已建立 v'+r.revisionNo+'，共 '+r.itemCount+' 项；Word 章节由后端生成。';items.value=[];form.value.cleanConfirmed=false;emit('saved')}catch(e){error.value=e.message}finally{busy.value=false}
}
</script>
<template>
<section class="structured-editor">
 <h3>网页建立系统检测模板</h3><p>直接维护项目及已核验的条款摘要，保存后自动生成六列原生 Word 检测明细。所有检测结果和判定留空。</p>
 <p v-if="error" role="alert">{{error}}</p><p v-if="notice" role="status">{{notice}}</p>
 <form @submit.prevent="save" class="editor-grid">
 <label>消防系统<input v-model="form.system" list="structured-systems" required maxlength="80" @input="form.cleanConfirmed=false"><datalist id="structured-systems"><option v-for="s in systems" :key="s" :value="s"/></datalist></label>
 <label>模板名称<input v-model="form.name" required maxlength="150"></label>
 <label class="wide">修订备注<textarea v-model="form.notes" maxlength="1000" rows="2"></textarea></label>
 <div class="wide"><h4>选择规范条款</h4><div class="editor-actions"><input v-model="q" placeholder="编号、条款号、要求摘要" @keydown.enter.prevent="search()"><button type="button" :disabled="busy" @click="search()">查找条款</button></div><p v-if="!clauses.length">暂无候选，先到消防规范资料库建立已核验条款。</p><article v-for="c in clauses" :key="c.id"><b>{{c.standard_no}} {{c.edition}} · {{c.clause_no}}</b><p>{{c.requirement_summary}}</p><small>{{c.source_locator}}</small><button type="button" @click="add(c)">加入检测项目</button></article><button v-if="more" type="button" @click="search(false)">更多条款</button></div>
 <div class="wide"><h4>检测项目（{{items.length}}）</h4><article v-for="(i,n) in items" :key="n"><label>{{n+1}}. 检验项目<input v-model="i.item" required maxlength="240" @input="form.cleanConfirmed=false"></label><small>{{i.clause.standard_no}} {{i.clause.edition}} · {{i.clause.clause_no}}</small><p>{{i.clause.requirement_summary}}</p><small v-if="i.clause.clause_active===false||i.clause.standard_active===false">已停用，请移除并选择有效条款</small><div class="editor-actions"><button type="button" :disabled="n===0" @click="move(n,-1)">上移</button><button type="button" :disabled="n===items.length-1" @click="move(n,1)">下移</button><button type="button" @click="items.splice(n,1);form.cleanConfirmed=false">移除</button></div></article></div>
 <label class="wide"><span><input type="checkbox" v-model="form.cleanConfirmed">已人工核对检测项目和对应条款内容及版本。</span></label><button class="primary" :disabled="busy||!items.length||!form.cleanConfirmed">保存新版本并生成章节</button>
 </form></section>
</template>
<style scoped>.structured-editor{display:grid;gap:10px}.structured-editor h3,.structured-editor h4,.structured-editor p{margin:0}.structured-editor p,.structured-editor small{font-size:12px;color:#64748b;line-height:1.7}.editor-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.editor-grid .wide{grid-column:1/-1}.editor-grid label{display:grid;gap:5px}.editor-grid input:not([type=checkbox]),.editor-grid textarea{box-sizing:border-box;width:100%;border:1px solid #dfe6ef;border-radius:7px;padding:8px;background:white;font:inherit}.editor-grid article{display:grid;gap:5px;border:1px solid #e2e8f0;padding:10px;margin:7px 0;border-radius:8px}.editor-actions{display:flex;gap:8px;flex-wrap:wrap}.structured-editor button{padding:8px;border:1px solid #dfe6ef;border-radius:7px;background:white;color:inherit;cursor:pointer}.structured-editor button.primary{background:#315fca;color:white}.structured-editor button:disabled{opacity:.5}@media(max-width:700px){.editor-grid{grid-template-columns:1fr}}</style>
