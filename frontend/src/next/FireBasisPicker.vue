<script setup>
import {computed,onMounted,ref} from 'vue'
import {api,downloadFile} from './api'
const props=defineProps({modelValue:{type:Array,default:()=>[]},referenceDate:{type:String,default:''},disabled:{type:Boolean,default:false},canDownload:{type:Boolean,default:false}})
const emit=defineEmits(['update:modelValue'])
const q=ref(''),category=ref(''),categories=ref([]),rows=ref([]),more=ref(false),busy=ref(false),error=ref('')
const selected=computed(()=>props.modelValue)
async function search(reset=true){
 if(busy.value)return
 busy.value=true;error.value=''
 try{const params=new URLSearchParams({q:q.value,category:category.value,offset:String(reset?0:rows.value.length),limit:'50'});const page=await api('/fire-standards?'+params);rows.value=reset?page.rows:[...rows.value,...page.rows];more.value=page.hasMore}catch(e){error.value=e.message}finally{busy.value=false}
}
function add(s){if(props.disabled||!s.verified||selected.value.some(x=>x.id===s.id))return;emit('update:modelValue',[...selected.value,{id:s.id,standard_no:s.standard_no,edition:s.edition,title:s.title,source_url:s.source_url,access_url:s.access_url,effective_on:s.effective_on,withdrawn_on:s.withdrawn_on,validity:s.validity,revision_no:s.revision_no,file_id:s.file_id,scope:'',reason:''}])}
function change(i,k,v){emit('update:modelValue',selected.value.map((x,j)=>j===i?{...x,[k]:v}:x))}
function remove(i){emit('update:modelValue',selected.value.filter((x,j)=>j!==i))}
function move(i,d){const j=i+d;if(j<0||j>=selected.value.length)return;const list=[...selected.value];[list[i],list[j]]=[list[j],list[i]];emit('update:modelValue',list)}
function hint(s){const d=props.referenceDate;if(!d)return '未录入参考日期';if(!s.effective_on)return '实施日期待核';if(d<s.effective_on)return '参考日期早于实施日期';if(s.withdrawn_on&&d>=s.withdrawn_on)return '参考日期已达废止日期';return '日期在已知区间内（非适用性结论）'}
async function download(s){try{await downloadFile(s.file_id,s.standard_no+'.pdf')}catch(e){error.value=e.message}}
onMounted(async()=>{try{categories.value=await api('/fire-standards/categories')}catch(e){error.value=e.message}await search()})
</script>
<template>
<section class="basis-picker"><p>以设计审查、验收、改造及检测事实为依据，由工程师确认适用版本。日期提示不是适用性判断；历史标准可以引用，但必须填写理由。</p>
<p v-if="error" role="alert">{{error}}</p>
<div v-for="(s,i) in selected" :key="s.id" class="basis-item"><b>{{i+1}}. {{s.title}} · {{s.standard_no}} {{s.edition}}</b><small>资料库修订 v{{s.revision_no}} · {{hint(s)}}</small><div class="basis-links"><a :href="s.source_url" target="_blank" rel="noopener noreferrer">来源</a><a v-if="s.access_url" :href="s.access_url" target="_blank" rel="noopener noreferrer">原文入口</a><button v-if="s.file_id&&canDownload" type="button" @click="download(s)">PDF原件</button></div><label>适用楼栋 / 系统 / 条款范围<input :value="s.scope" :disabled="disabled" maxlength="500" @input="change(i,'scope',$event.target.value)"></label><label>选用理由及适用性说明<textarea :value="s.reason" :disabled="disabled" maxlength="2000" rows="2" @input="change(i,'reason',$event.target.value)"></textarea></label><div v-if="!disabled" class="basis-links"><button type="button" :disabled="i===0" @click="move(i,-1)">上移</button><button type="button" :disabled="i===selected.length-1" @click="move(i,1)">下移</button><button type="button" @click="remove(i)">移除</button></div></div>
<template v-if="!disabled"><div class="basis-links"><input v-model="q" placeholder="名称、编号、旧编号" @keydown.enter.prevent="search()"><select v-model="category" @change="search()"><option value="">全部类别</option><option v-for="c in categories" :key="c">{{c}}</option></select><button type="button" :disabled="busy" @click="search()">搜索</button></div>
<div v-for="s in rows" :key="s.id" class="basis-item"><b>{{s.title}} · {{s.standard_no}} {{s.edition}}</b><small>{{s.verified?'已人工核验':'待核验，暂不可选'}} · {{hint(s)}} · {{s.validity}}</small><div class="basis-links"><a :href="s.source_url" target="_blank" rel="noopener noreferrer">来源</a><a v-if="s.access_url" :href="s.access_url" target="_blank" rel="noopener noreferrer">原文入口</a><button type="button" :disabled="!s.verified||selected.some(x=>x.id===s.id)" @click="add(s)">选用版本</button></div></div><button v-if="more" type="button" :disabled="busy" @click="search(false)">加载更多</button></template></section>
</template>
<style scoped>.basis-picker{display:grid;gap:10px}.basis-picker p,.basis-picker small{color:#64748b;font-size:12px}.basis-item{border:1px solid #e2e8f0;border-radius:8px;padding:12px;display:grid;gap:8px}.basis-links{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.basis-picker label{display:grid;gap:5px}.basis-picker input,.basis-picker textarea,.basis-picker select,.basis-picker button{padding:8px;border:1px solid #dfe6ef;border-radius:6px;background:white;color:inherit;font:inherit}.basis-picker button:disabled{opacity:.5}</style>