<template>
 <el-dialog v-model="open" :title="`用户资料：${name}`" width="780px" append-to-body>
  <el-table :data="items"><el-table-column prop="materialKey" label="资料 key"/><el-table-column prop="valueType" label="类型" width="90"/><el-table-column label="内容"><template #default="s"><span v-if="s.row.valueType==='string'">{{s.row.content}}</span><a v-else :href="fileUrl(s.row.content)" target="_blank" rel="noopener">查看文件</a></template></el-table-column><el-table-column label="操作" width="130"><template #default="s"><el-button link @click="edit(s.row)">编辑</el-button><el-button link type="danger" @click="remove(s.row)">删除</el-button></template></el-table-column></el-table>
  <el-divider>新增或更新资料</el-divider>
  <el-form label-width="100px"><el-form-item label="资料 key"><el-select v-model="form.materialKey" filterable allow-create default-first-option><el-option v-for="k in presets" :key="k" :label="k" :value="k"/></el-select></el-form-item><el-form-item label="类型"><el-select v-model="form.valueType" @change="form.content='' "><el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value"/></el-select></el-form-item><el-form-item label="内容"><el-input v-if="form.valueType==='string'" v-model="form.content" maxlength="4000"/><FileUpload v-else :key="form.valueType" v-model="form.content" :limit="1" :file-type="extensions"/></el-form-item></el-form>
  <template #footer><el-button @click="open=false">关闭</el-button><el-button type="primary" :loading="saving" @click="save">保存资料</el-button></template>
 </el-dialog>
</template>
<script setup>
import request from '@/utils/request';
import {fileUrl} from '@/utils/files';
import FileUpload from '@/components/FileUpload/index.vue';
const {proxy}=getCurrentInstance();
const open=ref(false),name=ref(''),userId=ref(),items=ref([]),saving=ref(false);
const presets=['资格证书','执业证书','执业证书编号','操作员证书'];
const types=[{value:'string',label:'字符串'},{value:'png',label:'PNG 图片'},{value:'jpg',label:'JPG 图片'},{value:'pdf',label:'PDF 文件'},{value:'file',label:'文件'}];
const form=ref({materialKey:'资格证书',valueType:'png',content:''});
const extensions=computed(()=>form.value.valueType==='file'?['png','jpg','jpeg','pdf','doc','docx']:form.value.valueType==='jpg'?['jpg','jpeg']:[form.value.valueType]);
const url=()=>`/system/user/${userId.value}/materials`;
async function load(){items.value=(await request({url:url()})).data;}
async function show(row){userId.value=row.userId;name.value=row.nickName||row.userName;items.value=[];form.value={materialKey:'资格证书',valueType:'png',content:''};open.value=true;await load();}
function edit(row){form.value={...row};}
async function save(){if(!form.value.materialKey.trim()||!form.value.content){proxy.$modal.msgError('请填写资料 key 和内容');return;}saving.value=true;try{await request({url:url(),method:'put',data:form.value});await load();proxy.$modal.msgSuccess('已保存');}finally{saving.value=false;}}
async function remove(row){await proxy.$modal.confirm(`删除资料“${row.materialKey}”？`);await request({url:url(),method:'delete',params:{key:row.materialKey}});await load();}
defineExpose({show});
</script>
