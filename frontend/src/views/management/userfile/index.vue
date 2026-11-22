<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
        <el-form-item label="用户" prop="userId">
          <el-select placeholder="选择用户" v-model="queryParams.userId" style="width: 200px;">
            <el-option v-for="user in userList" :key="user.id" :label="user.userName" :value="user.id"/>
          </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="queryParams.remark"
          placeholder="请输入备注"
          clearable
          @keyup.enter="handleQuery"
        />
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
          v-hasPermi="['management:userfile:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['management:userfile:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['management:userfile:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['management:userfile:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="userfileList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <!-- <el-table-column label="${comment}" align="center" prop="id" /> -->
      <el-table-column label="用户" align="center" prop="userName" />
      <el-table-column label="名称" align="center" prop="name" />
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="创建者" align="center" prop="createBy" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="更新者" align="center" prop="updateBy" />
      <el-table-column label="更新时间" align="center" prop="updateTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.updateTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200">
        <template #default="scope">
          <el-button link type="primary" icon="Download" @click="handleDownload(scope.row)">下载</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['management:userfile:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['management:userfile:remove']">删除</el-button>
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

    <!-- 添加或修改资料附件对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="userfileRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户" prop="userId">
          <el-select placeholder="选择用户" v-model="form.userId">
            <el-option v-for="user in userList" :key="user.id" :label="user.userName" :value="user.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入标签" style="width: 200px;" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item label="文件" prop="file">
          <file-upload v-model="form.file" :fileSize="50" :fileType="['png', 'jpg', 'pdf']"/>
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

<script setup name="Userfile">
import { listUserfile, getUserfile, delUserfile, addUserfile, updateUserfile } from "@/api/management/userfile";
import { listUser } from "@/api/management/user";
import download from "@/plugins/download"
import { useRoute } from "vue-router";
import { onActivated, onMounted } from "vue";

const { proxy } = getCurrentInstance();

const userfileList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref("");
const userList = ref([])
const lastUserId = ref(null);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    userId: null,
    name: null,
    remark: null,
  },
  rules: {
  }
});

const { queryParams, form, rules } = toRefs(data);

function getUserList() {
  loading.value = true;
  listUser().then(response => {
    userList.value = response.rows;
    loading.value = false;
  })
}

/** 查询资料附件列表 */
function getList() {
  loading.value = true;
  listUserfile(queryParams.value).then(response => {
    userfileList.value = response.rows;
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
    userId: null,
    name: null,
    file: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  };
  proxy.resetForm("userfileRef");
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
  getUserList();
  open.value = true;
  title.value = "添加资料附件";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  getUserList();
  const _id = row.id || ids.value
  getUserfile(_id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改资料附件";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["userfileRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateUserfile(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addUserfile(form.value).then(response => {
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
  proxy.$modal.confirm('是否确认删除资料附件编号为"' + _ids + '"的数据项？').then(function() {
    return delUserfile(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {});
}

// 推荐方式：使用 lastIndexOf 获取最后一个点号后的内容
function getFileExtension(fileName) {
  if (!fileName) return '';
  const lastDotIndex = fileName.lastIndexOf('.');
  return lastDotIndex !== -1 ? fileName.substring(lastDotIndex + 1) : '';
}

// 在 handleDownload 函数中的应用
function handleDownload(row) {
  const fileExtension = getFileExtension(row.file);
  const fileName = fileExtension 
    ? `${row.userName}_${row.name}.${fileExtension}`
    : `${row.userName}_${row.name}`;
  download.resourceWithName(row.file, fileName);
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('management/userfile/export', {
    ...queryParams.value
  }, `userfile_${new Date().getTime()}.xlsx`)
}
onActivated(() => {
  const currentUserId = proxy.$cache.session.get("userId");
  if (currentUserId != null) {
    queryParams.value.userId = parseInt(currentUserId);
    getList();
    proxy.$cache.session.remove("userId");
  }
})
getUserList();
getList()
</script>
