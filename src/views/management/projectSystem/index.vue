<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="项目名称" prop="projectName">
        <el-input
          v-model="queryParams.projectName"
          placeholder="请输入项目名称"
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
          v-hasPermi="['projectSystem:projectSystem:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['projectSystem:projectSystem:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['projectSystem:projectSystem:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['projectSystem:projectSystem:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="projectSystemList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" align="center" prop="id" />
      <el-table-column label="项目名称" align="center" prop="projectName" />
      <el-table-column label="地址" align="center" >
        <template #default="scope">
          <el-button type="success" @click="handleCopyURL(scope.row)">
            复制链接
          </el-button>
        </template>
      </el-table-column>
      <!-- <el-table-column label="二维码" align="center" prop="qrcode" width="100">
        <template #default="scope">
          <image-preview :src="scope.row.qrcode" :width="50" :height="50"/>
        </template>
      </el-table-column> -->
      <!-- <el-table-column label="系统" align="center" prop="system" /> -->
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handlePreview(scope.row)" v-hasPermi="['projectSystem:projectSystem:edit']">预览</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['projectSystem:projectSystem:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['projectSystem:projectSystem:remove']">删除</el-button>
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

    <!-- 添加或修改项目系统对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body :close-on-click-modal="false">
      <el-form ref="projectSystemRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="二维码" prop="qrcode">
          <image-upload v-model="form.qrcode"/>
        </el-form-item>
        <el-form-item label="系统" prop="projectSystem">
          <el-input v-model="form.projectSystem" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="预览系统" v-model="previewOpen" width="600px" append-to-body>
      <el-scrollbar height="600px">
              <el-tree
      :data="treeData"
      :props="defaultProps"
      show-checkbox
      default-expand-all
      node-key="label"
      :default-checked-keys="defaultChecked"
      style="width: 100%;"
      />
      </el-scrollbar>
    </el-dialog>
  </div>
</template>

<script setup name="ProjectSystem">
import { listProjectSystem, getProjectSystem, delProjectSystem, addProjectSystem, updateProjectSystem } from "@/api/management/projectSystem";

const { proxy } = getCurrentInstance();

const projectSystemList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref("");
const treeData = ref()
const defaultProps = {
  children: "children",
  label: "label"
}
const previewOpen = ref(false)
const defaultChecked = ref([])
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

/** 查询项目系统列表 */
function getList() {
  loading.value = true;
  listProjectSystem(queryParams.value).then(response => {
    projectSystemList.value = response.rows;
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
    qrcode: null,
    projectSystem: null
  };
  proxy.resetForm("projectSystemRef");
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
  title.value = "添加项目系统";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const _id = row.id || ids.value
  getProjectSystem(_id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改项目系统";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["projectSystemRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateProjectSystem(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addProjectSystem(form.value).then(response => {
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
  proxy.$modal.confirm('是否确认删除项目系统编号为"' + _ids + '"的数据项？').then(function() {
    return delProjectSystem(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('projectSystem/projectSystem/export', {
    ...queryParams.value
  }, `projectSystem_${new Date().getTime()}.xlsx`)
}

function getChecked(nodes) {
  let result = []
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
function handlePreview(row) {
  treeData.value = JSON.parse(row.projectSystem);
  previewOpen.value = true
  defaultChecked.value = getChecked(treeData.value)
}

function handleCopyURL(row) {
  const url = `${window.location.origin}/systemcheck/${row.id}`
  // url to clipboard
  navigator.clipboard.writeText(url).then(() => {
    proxy.$message.success("复制成功");
  }).catch(() => {
    proxy.$message.error("复制失败");
  });
}
getList();
</script>
