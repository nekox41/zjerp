<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { getProjectSystem, updateProjectSystem } from "@/api/management/projectSystem";

const props = defineProps(
  {
    id: String
  }
)
const projectData = ref()
const treeData = ref()
const defaultProps = {
  children: "children",
  label: "label"
}
const defaultChecked = ref()

function fetchData() {
  getProjectSystem(props.id).then(res => {
    projectData.value = res.data
    treeData.value = JSON.parse(res.data.projectSystem)
    defaultChecked.value = getChecked(treeData.value)
  })
}
function getChecked(nodes){
  let result = [];
  // check children node
  console.log(nodes)
  for (let i = 0; i < nodes.length; i++) {
    // first, check all current node's checked tag
    if (nodes[i].checked) {
      result.push(nodes[i].label);
    }
    // second, if current node has children, check children node
    if (nodes[i].children) {
      result = result.concat(getChecked(nodes[i].children));
    }
  }
  // return checked node's label
  return result;
}
function handleCheck(node, selected) {
  node.checked = !node.checked;
}

function handleSubmit() {
  updateProjectSystem({
      id: projectData.value.id,
      projectName: projectData.value.name,
      projectSystem: JSON.stringify(treeData.value)
    }).then(res => { 
      ElMessage.success(res.msg)
    })
}
console.log(props.id)
fetchData()
</script>

<template>
  <div class="tree-container">
    <h3>{{ projectData.name }}</h3>
    <el-tree
    :data="treeData"
    :props="defaultProps"
    show-checkbox
    default-expand-all
    node-key="label"
    :default-checked-keys="defaultChecked"
    @check="handleCheck"
    style="width: 100%;"
    />
    <el-button type="success" @click="handleSubmit">提交</el-button>
  </div>
</template>

<style scoped>
.tree-container {
  display: flex;
  flex-direction: column;
  align-items: center;
}
</style>
