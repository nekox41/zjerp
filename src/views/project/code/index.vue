<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="编号" prop="code">
        <el-input v-model="queryParams.code" placeholder="请输入编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="项目名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入项目名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="申请时间" style="width: 308px">
        <el-date-picker v-model="daterangeCreateTime" value-format="YYYY-MM-DD" type="daterange" range-separator="-"
          start-placeholder="开始日期" end-placeholder="结束日期"></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['project:code:add']">申请</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['project:code:edit']">修改</el-button>
      </el-col>
      <!-- <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['project:code:remove']">删除</el-button>
      </el-col> -->
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['project:code:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="codeList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <!-- <el-table-column label="${comment}" align="center" prop="id" /> -->
      <el-table-column label="编号" align="center" prop="code" />
      <el-table-column label="项目名称" align="center" prop="name" />
      <el-table-column label="项目类型" align="center" prop="type">
        <template #default="scope">
          <el-tag type="success" round v-if="scope.row.type == 'JC'">检测</el-tag>
          <el-tag type="success" round v-if="scope.row.type == 'PD'">评定</el-tag>
          <el-tag type="success" round v-if="scope.row.type == 'PG'">评估</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" align="center" prop="createTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="申请人" align="center" prop="createBy" />
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['project:code:edit']">修改</el-button>
          <!-- <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['project:code:remove']">删除</el-button> -->
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize"
      @pagination="getList" />

    <!-- 添加或修改编号申请对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="codeRef" :model="form" :rules="rules" label-width="80px">
        <!-- <el-form-item label="编号" prop="code">
          <el-input v-model="form.code" placeholder="请输入编号" />
        </el-form-item> -->
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择项目类型">
            <el-option label="检测" value="JC"></el-option>
            <el-option label="评定" value="PD"></el-option>
            <el-option label="评估" value="PG"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input type="textarea" v-model="form.remark" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 在模板部分的申请结果对话框中添加表单内容 -->
    <el-dialog title="申请结果" v-model="view" width="500px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="编号">
          {{ viewForm.code }}
        </el-descriptions-item>
        <el-descriptions-item label="项目名称">
          {{ viewForm.name }}
        </el-descriptions-item>
        <el-descriptions-item label="项目类型">
          <el-tag type="success" round v-if="viewForm.type == 'JC'">检测</el-tag>
          <el-tag type="success" round v-if="viewForm.type == 'PD'">评定</el-tag>
          <el-tag type="success" round v-if="viewForm.type == 'PG'">评估</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申请时间">
          {{ parseTime(viewForm.createTime, '{y}-{m}-{d}') }}
        </el-descriptions-item>
        <el-descriptions-item label="申请人">
          {{ viewForm.createBy }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="closeViewDialog">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Code">
import { listCode, getCode, delCode, addCode, updateCode } from "@/api/project/code";

const { proxy } = getCurrentInstance();

const codeList = ref([]);
const open = ref(false);
const view = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref("");
const daterangeCreateTime = ref([]);
const viewForm = ref({
  name: null,
  code: null,
  type: null,
  createTime: null,
  createBy: null
})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    code: null,
    name: null,
    type: null,
    createTime: null,
    createBy: null,
    orderByColumn: "createTime",
    isAsc: "desc"
  },
  rules: {
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询编号申请列表 */
function getList() {
  loading.value = true;
  queryParams.value.params = {};
  if (null != daterangeCreateTime && '' != daterangeCreateTime) {
    queryParams.value.params["beginCreateTime"] = daterangeCreateTime.value[0];
    queryParams.value.params["endCreateTime"] = daterangeCreateTime.value[1];
  }
  listCode(queryParams.value).then(response => {
    codeList.value = response.rows;
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
    code: null,
    name: null,
    type: null,
    index: null,
    createTime: null,
    remark: null
  };
  proxy.resetForm("codeRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  daterangeCreateTime.value = [];
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
  title.value = "申请编号";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const _id = row.id || ids.value
  getCode(_id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改项目名称";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["codeRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateCode(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addCode(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功");
          open.value = false;
          getList();
          viewForm.value = response.data;
          view.value = true;
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row.id || ids.value;
  proxy.$modal.confirm('是否确认删除编号申请编号为"' + _ids + '"的数据项？').then(function () {
    return delCode(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => { });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('project/code/export', {
    ...queryParams.value
  }, `code_${new Date().getTime()}.xlsx`)
}

// 关闭查看对话框
function closeViewDialog() {
  view.value = false;
}

getList();
</script>
