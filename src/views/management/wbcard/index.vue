<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="编号" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="请输入编号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入标题"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="是否打印" prop="printCheck">
        <el-select v-model="queryParams.printCheck" placeholder="请选择是否打印" clearable>
          <el-option
            v-for="dict in wbcard_print_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="是否邮寄" prop="sendCheck">
        <el-select v-model="queryParams.sendCheck" placeholder="请选择是否邮寄" clearable>
          <el-option
            v-for="dict in wbcard_send_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['management:wbcard:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['management:wbcard:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['management:wbcard:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['management:wbcard:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="wbcardList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <!-- <el-table-column label="ID" align="center" prop="id" /> -->
      <el-table-column label="编号" align="center" prop="code" />
      <el-table-column label="标题" align="center" prop="title" />
      <el-table-column label="是否打印" align="center" prop="printCheck">
        <template #default="scope">
          <dict-tag :options="wbcard_print_status" :value="scope.row.printCheck"/>
        </template>
      </el-table-column>
      <el-table-column label="是否邮寄" align="center" prop="sendCheck">
        <template #default="scope">
          <dict-tag :options="wbcard_send_status" :value="scope.row.sendCheck"/>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['management:wbcard:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['management:wbcard:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <pagination
      v-show="total>0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改维保卡管理对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="wbcardRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="编号" prop="code">
          <el-input v-model="form.code" placeholder="请输入编号" />
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="文件" prop="file">
          <file-upload v-model="form.file"/>
        </el-form-item>
        <el-form-item label="是否打印" prop="printCheck">
          <el-select v-model="form.printCheck" placeholder="请选择是否打印">
            <el-option
              v-for="dict in wbcard_print_status"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="是否邮寄" prop="sendCheck">
          <el-select v-model="form.sendCheck" placeholder="请选择是否邮寄">
            <el-option
              v-for="dict in wbcard_send_status"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Wbcard">
import { listWbcard, getWbcard, delWbcard, addWbcard, updateWbcard } from "@/api/management/wbcard";

const { proxy } = getCurrentInstance();
const { wbcard_send_status, wbcard_print_status } = proxy.useDict('wbcard_send_status', 'wbcard_print_status');

const wbcardList = ref([]);
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
    code: null,
    title: null,
    printCheck: null,
    sendCheck: null,
  },
  rules: {
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询维保卡管理列表 */
function getList() {
  loading.value = true;
  listWbcard(queryParams.value).then(response => {
    wbcardList.value = response.rows;
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
    title: null,
    file: null,
    printCheck: null,
    sendCheck: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null,
    remark: null
  };
  proxy.resetForm("wbcardRef");
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
  title.value = "添加维保卡管理";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const _id = row.id || ids.value
  getWbcard(_id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改维保卡管理";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["wbcardRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateWbcard(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addWbcard(form.value).then(response => {
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
  proxy.$modal.confirm('是否确认删除维保卡管理编号为"' + _ids + '"的数据项？').then(function() {
    return delWbcard(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('management/wbcard/export', {
    ...queryParams.value
  }, `wbcard_${new Date().getTime()}.xlsx`)
}

getList();
</script>
