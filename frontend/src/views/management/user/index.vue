<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="姓名" prop="userName">
        <el-input
          v-model="queryParams.userName"
          placeholder="请输入姓名"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="人员类别" prop="userType">
        <el-select v-model="queryParams.userType" placeholder="请选择人员类别" clearable style="width: 200px;">
          <el-option
            v-for="dict in zj_user_type"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="手机号" prop="userPhone">
        <el-input
          v-model="queryParams.userPhone"
          placeholder="请输入手机号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <!-- <el-form-item label="出生日期" prop="userBrithday">
        <el-date-picker clearable
          v-model="queryParams.userBrithday"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择出生日期">
        </el-date-picker>
      </el-form-item> -->
      <el-form-item label="年龄" prop="userAge">
        <el-input
          v-model="queryParams.userAge"
          placeholder="请输入年龄"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="身份证号" prop="userChinaid">
        <el-input
          v-model="queryParams.userChinaid"
          placeholder="请输入身份证号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="证书编号" prop="userCert">
        <el-input
          v-model="queryParams.userCert"
          placeholder="请输入证书编号"
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
          v-hasPermi="['management:user:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['management:user:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['management:user:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['management:user:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="userList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <!-- <el-table-column label="${comment}" align="center" prop="id" /> -->
      <el-table-column label="姓名" align="center" prop="userName" />
      <el-table-column label="人员类别" align="center" prop="userType">
        <template #default="scope">
          <dict-tag :options="zj_user_type" :value="scope.row.userType"/>
        </template>
      </el-table-column>
      <el-table-column label="手机号" align="center" prop="userPhone" />
      <el-table-column label="出生日期" align="center" prop="userBrithday" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.userBrithday, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="年龄" align="center" prop="userAge" />
      <el-table-column label="身份证号" align="center" prop="userChinaid" />
      <el-table-column label="证书编号" align="center" prop="userCert" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Search" @click="handleUserinfo(scope.row)" v-hasPermi="['management:userinfo:list']">查阅</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['management:user:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['management:user:remove']">删除</el-button>
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

    <!-- 添加或修改员工资料对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="userRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="姓名" prop="userName">
          <el-input v-model="form.userName" placeholder="请输入姓名" style="width: 150px;" />
        </el-form-item>
        <el-form-item label="人员类别" prop="userType">
          <el-radio-group v-model="form.userType">
            <el-radio
              v-for="dict in zj_user_type"
              :key="dict.value"
              :label="dict.value"
            >{{dict.label}}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号" prop="userPhone">
          <el-input v-model="form.userPhone" placeholder="请输入手机号" style="width: 150px;" />
        </el-form-item>
        <el-form-item label="出生日期" prop="userBrithday">
          <el-date-picker clearable
            v-model="form.userBrithday"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择出生日期">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="年龄" prop="userAge">
          <el-input v-model="form.userAge" placeholder="输入年龄" style="width: 80px;" />
        </el-form-item>
        <el-form-item label="身份证号" prop="userChinaid">
          <el-input v-model="form.userChinaid" placeholder="请输入身份证号" style="width: 250px;"/>
        </el-form-item>
        <el-form-item label="证书编号" prop="userCert">
          <el-input v-model="form.userCert" placeholder="请输入证书编号" style="width: 250px;"/>
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

<script setup name="User">
import FileUpload from "@/components/FileUpload";
import { listUser, getUser, delUser, addUser, updateUser } from "@/api/management/user";

const { proxy } = getCurrentInstance();
const { zj_user_type } = proxy.useDict('zj_user_type');

const userList = ref([]);
const userFileList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const checkedUserFile = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref("");

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    userName: null,
    userType: null,
    userPhone: null,
    userBrithday: null,
    userAge: null,
    userChinaid: null,
    userCert: null
  },
  rules: {
    userName: [
      { required: true, message: "姓名不能为空", trigger: "blur" }
    ],
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询员工资料列表 */
function getList() {
  loading.value = true;
  listUser(queryParams.value).then(response => {
    userList.value = response.rows;
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
    userName: null,
    userType: null,
    userPhone: null,
    userBrithday: null,
    userAge: null,
    userChinaid: null,
    userCert: null
  };
  userFileList.value = [];
  proxy.resetForm("userRef");
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
  title.value = "添加员工资料";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const _id = row.id || ids.value
  getUser(_id).then(response => {
    form.value = response.data;
    userFileList.value = response.data.userFileList;
    open.value = true;
    title.value = "修改员工资料";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["userRef"].validate(valid => {
    if (valid) {
      form.value.userFileList = userFileList.value;
      if (form.value.id != null) {
        updateUser(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addUser(form.value).then(response => {
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
  proxy.$modal.confirm('是否确认删除【' + row.userName + '】的相关数据（包括资料文件）？').then(function() {
    return delUser(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {});
}

/** 用户资料序号 */
function rowUserFileIndex({ row, rowIndex }) {
  row.index = rowIndex + 1;
}

/** 用户资料添加按钮操作 */
function handleAddUserFile() {
  let obj = {};
  obj.tag = "";
  obj.file = "";
  userFileList.value.push(obj);
}

/** 用户资料删除按钮操作 */
function handleDeleteUserFile() {
  if (checkedUserFile.value.length == 0) {
    proxy.$modal.msgError("请先选择要删除的用户资料数据");
  } else {
    const userFiles = userFileList.value;
    const checkedUserFiles = checkedUserFile.value;
    userFileList.value = userFiles.filter(function(item) {
      return checkedUserFiles.indexOf(item.index) == -1
    });
  }
}

/** 复选框选中数据 */
function handleUserFileSelectionChange(selection) {
  checkedUserFile.value = selection.map(item => item.index)
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('management/user/export', {
    ...queryParams.value
  }, `user_${new Date().getTime()}.xlsx`)
}

function handleUserinfo(row) {
  proxy.$cache.session.set("userId", row.id);
  proxy.$router.push({path: "userfile",});
}

getList();
</script>
