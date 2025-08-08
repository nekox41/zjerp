<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="项目名称" prop="projectName">
        <el-input v-model="queryParams.projectName" placeholder="请输入项目名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <!-- <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['outside:outside:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['outside:outside:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['outside:outside:remove']"
        >删除</el-button>
      </el-col> -->
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['outside:outside:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="outsideList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" align="center" prop="id" />
      <el-table-column label="项目名称" align="center" prop="projectName" />
      <el-table-column label="参与人员" align="center" prop="projectParticipant" />
      <el-table-column label="总共花费" align="center" prop="projectCost" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['outside:outside:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改报账总览对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="outsideRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="参与人员" prop="projectParticipant">
          <el-input v-model="form.projectParticipant" placeholder="请输入参与人员" />
        </el-form-item>
        <el-form-item label="总共花费" prop="projectCost">
          <el-input v-model="form.projectCost" placeholder="请输入总共花费" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查看消费记录 -->
    <el-dialog v-model="costDialog" title="查看消费记录" class="dialog2" width="80%">
      <el-table :data="recordList" style="width: 100%;">
        <el-table-column label="姓名" prop="participant" />
        <el-table-column label="金额（元）" prop="cost" />
        <el-table-column label="日期" prop="date" />
        <el-table-column label="备注" prop="remark" />
        <el-table-column label="附件" prop="attachment" />
        <el-table-column label="操作" />
      </el-table>
      <template #footer>
        <el-button type="success" @click="exportCost">导出</el-button>
        <el-button type="primary" @click="close">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Outside">
import { listOutside, getOutside, delOutside, addOutside, updateOutside } from "@/api/outside/outside";
import { listCost } from "@/api/outside/cost"

const { proxy } = getCurrentInstance();

const recordList = ref([]);
const costDialog = ref(false);
const outsideList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref("");

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    projectName: null,
  },
  rules: {
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询报账总览列表 */
function getList() {
  loading.value = true;
  listOutside(queryParams.value).then(response => {
    outsideList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    projectName: null,
    projectParticipant: null,
    projectCost: null,
    createTime: null,
    updateTime: null
  };
  proxy.resetForm("outsideRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef");
  handleQuery();
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加报账总览";
}

/** 查看按钮操作 */
function handleView(row) {
  costDialog.value = true;
  listCost({projectId: row.id}).then(res => {
    recordList.value = res.rows;
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["outsideRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateOutside(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addOutside(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功");
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row.id || ids.value;
  proxy.$modal.confirm('是否确认删除报账总览编号为"' + _ids + '"的数据项？').then(function () {
    return delOutside(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => { });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('outside/outside/export', {
    ...queryParams.value
  }, `outside_${new Date().getTime()}.xlsx`)
}

/** 关闭查看窗口 */
function close() {
  costDialog.value = false;
}

/** 导出消费记录 */
function exportCost() {
  proxy.download('outside/cost/export')
}
getList();
</script>
