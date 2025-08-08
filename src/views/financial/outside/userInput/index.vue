<template>
    <div class="app-container">
        <el-button type="primary" @click="dialogVisible = true">新建报账</el-button>
        <!-- 步骤1. 选择项目 -->
        <el-dialog v-model="dialogVisible" title="选择项目" class="dialog1">
            <div class="dialog1-body">
                <el-autocomplete v-model="projectName" :fetch-suggestions="querySearch" value-key="projectName"
                    placeholder="请输入项目名" />
            </div>
            <template #footer>
                <div class="dialog-footer">
                    <el-button type="primary" @click="handleNext">下一步</el-button>
                </div>
            </template>
        </el-dialog>
        <!-- 步骤2. 添加消费信息 -->
        <el-dialog v-model="costDialog" title="添加消费记录" class="dialog2" width="80%">
            <el-table :data="recordList" style="width: 100%;">
                <el-table-column label="姓名" prop="participant" />
                <el-table-column label="金额（元）" prop="cost" />
                <el-table-column label="日期" prop="date" />
                <el-table-column label="备注" prop="remark" />
                <el-table-column label="附件" prop="attachment" />
                <el-table-column label="操作" />
            </el-table>
            <template #footer>
                <el-button type="success" @click="addRecord">添加</el-button>
                <el-button type="primary" @click="close">完成</el-button>
            </template>
        </el-dialog>
        <!-- 添加弹窗 -->
        <el-dialog v-model="newRecord" title="新建记录" class="dialog3" width="80%">
            <el-form :model="form" label-width="80px">
                <el-form-item prop="projectId" />
                <el-form-item label="姓名" prop="participant">
                    <el-input v-model="form.participant" placeholder="请输入姓名" />
                </el-form-item>
                <el-form-item label="花费" prop="cost">
                    <el-input v-model="form.cost" placeholder="请输入花费（元）" />
                </el-form-item>
                <el-form-item label="日期" prop="date">
                    <el-date-picker clearable v-model="form.date" type="date" value-format="YYYY-MM-DD"
                        placeholder="请选择日期">
                    </el-date-picker>
                </el-form-item>
                <el-form-item label="备注" prop="remark">
                    <el-input v-model="form.remark" placeholder="请输入备注" />
                </el-form-item>
                <el-form-item label="附件" prop="attachment">
                    <file-upload v-model="form.attachment" />
                </el-form-item>
            </el-form>
            <el-alert title="请添加单笔消费记录" type="warning" center show-icon :closable="false" />
            <template #footer>
                <div class="dialog-footer">
                    <el-button type="primary" @click="submitForm">确 定</el-button>
                    <el-button @click="cancel">取 消</el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref } from 'vue'
import { listOutside, addOutside } from "@/api/outside/outside"
import { listCost, addCost } from '@/api/outside/cost'
import { ElMessage } from 'element-plus';

// 变量
let timeout;
const projectList = ref([]);
const projectName = ref('');
const dialogVisible = ref(false);
const costDialog = ref(false);
const newRecord = ref(false);
const recordList = ref([]);
const form = ref({
    projectId: null,
    participant: null,
    cost: null,
    date: null,
    remark: null,
    attachment: null
})

// 函数
function querySearch(queryString, callback) {
    const results = queryString
        ? projectList.value.filter((project) => { project.projectName.includes(queryString) })
        : projectList.value;

    clearTimeout(timeout);
    timeout = setTimeout(() => {
        callback(results);
    }, 500 * Math.random());
};

function handleNext() {
    if (!projectName.value.trim()) {
        ElMessage.error('请输入有效项目名')
        return
    }

    const matchItem = projectList.value.find(item => 
        item.projectName === projectName.value
    )

    if (matchItem) {
        console.log(matchItem)
        form.value.projectId = matchItem.id
    } else {
        addOutside({ projectName: projectName.value }).then(res => {
            if (res.code === 200) {
                return listOutside({ projectName: projectName.value })
            }
            throw new Error('创建项目失败')
        }).then(res2 => {
            if (res2?.rows?.[0]?.id) {
                form.value.projectId = res2.rows[0].id
            } else {
                throw new Error('项目ID获取失败')
            }
        }).catch(error => {
            ElMessage.error('项目初始化失败')
            console.error('Project Error:', error)
            return
        })
    }

    dialogVisible.value = false
    costDialog.value = true
    
    // 使用最新projectId刷新
    if (form.value.projectId) {
        listCost({ projectId: form.value.projectId }).then(res => {
            recordList.value = res.rows
        })
    }
}

function addRecord() {
    reset();
    costDialog.value = false;
    newRecord.value = true;
}

// 重置表单
function reset() {
    const currentProjectId = form.value.projectId;
    form.value = {
    projectId: currentProjectId,
    participant: null,
    cost: null,
    date: null,
    remark: null,
    attachment: null
}
}

// newRecord cancel
function cancel() {
    newRecord.value = false;
    costDialog.value = true;
    reset();
}
// newRecord submit
function submitForm() {
    if (!form.value.projectId) {
        ElMessage({
            message: "项目ID缺失。",
            type: "error",
            duration: 2000
        })
        return
    }
    addCost(form.value).then(response => {
        ElMessage({
            message: '新增成功',
            type: 'success',
            duration: 2000
        })
        newRecord.value = false;
        costDialog.value = true;
        listCost({projectId: form.value.projectId}).then(res => {
            recordList.value = res.rows;
            reset();
        });
    })
}

// close costDialog
function close() {
    costDialog.value = false;
}
// 初始化

listOutside().then(res => { projectList.value = res.rows });
</script>

<style>
.app-container {
    display: flex;
    width: 100vw;
    height: 100vh;
    justify-content: center;
    align-items: center;
}
</style>