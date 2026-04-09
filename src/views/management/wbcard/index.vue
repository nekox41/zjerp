<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="编号" prop="code">
        <el-input v-model="queryParams.code" placeholder="请输入编号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="queryParams.title" placeholder="请输入标题" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="是否打印" prop="printCheck">
        <el-select v-model="queryParams.printCheck" placeholder="请选择是否打印" clearable>
          <el-option v-for="dict in wbcard_print_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否邮寄" prop="sendCheck">
        <el-select v-model="queryParams.sendCheck" placeholder="请选择是否邮寄" clearable>
          <el-option v-for="dict in wbcard_send_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd"
          v-hasPermi="['management:wbcard:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Plus" @click="handleGen"
          v-hasPermi="['management:wbcard:add']">生成</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
          v-hasPermi="['management:wbcard:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['management:wbcard:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport"
          v-hasPermi="['management:wbcard:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="wbcardList" @selection-change="handleSelectionChange"
      @sort-change="handleSortChange">
      <el-table-column type="selection" width="55" align="center" />
      <!-- <el-table-column label="ID" align="center" prop="id" /> -->
      <el-table-column label="编号" align="center" prop="code" sortable="custom" />
      <el-table-column label="标题" align="center" prop="title" />
      <el-table-column label="是否打印" align="center" prop="printCheck">
        <template #default="scope">
          <dict-tag :options="wbcard_print_status" :value="scope.row.printCheck" />
        </template>
      </el-table-column>
      <el-table-column label="是否邮寄" align="center" prop="sendCheck">
        <template #default="scope">
          <dict-tag :options="wbcard_send_status" :value="scope.row.sendCheck" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['management:wbcard:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"
            v-hasPermi="['management:wbcard:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize" @pagination="getList" />

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
          <file-upload v-model="form.file" />
        </el-form-item>
        <el-form-item label="是否打印" prop="printCheck">
          <el-select v-model="form.printCheck" placeholder="请选择是否打印">
            <el-option v-for="dict in wbcard_print_status" :key="dict.value" :label="dict.label"
              :value="dict.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="是否邮寄" prop="sendCheck">
          <el-select v-model="form.sendCheck" placeholder="请选择是否邮寄">
            <el-option v-for="dict in wbcard_send_status" :key="dict.value" :label="dict.label"
              :value="dict.value"></el-option>
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

    <!-- 生成维保卡对话框 -->
    <el-dialog title="生成维保卡" v-model="openGen" width="800px" append-to-body>
      <el-form :model="genForm" label-width="80px">
        <el-form-item label="维保卡编号" prop="no" label-width="100px">
          <el-input v-model="genForm.no" placeholder="请输入维保卡编号"></el-input>
        </el-form-item>
        <el-form-item label="项目名称" prop="name" label-width="100px">
          <el-input v-model="genForm.name" placeholder="请输入项目名称"></el-input>
        </el-form-item>
        <el-form-item label="公司名称" prop="company" label-width="100px">
          <el-input v-model="genForm.company" disabled placeholder="请输入公司名"></el-input>
        </el-form-item>
        <el-form-item label="项目负责人" prop="leader" label-width="100px">
          <el-input v-model="genForm.leader" placeholder="请输入项目负责人"></el-input>
        </el-form-item>
        <el-form-item label="联系电话" prop="leader" label-width="100px">
          <el-input v-model="genForm.phone" placeholder="请输入联系电话" style="width: 400px;"></el-input>
          <el-select v-model="genForm.phone" filterable remote :remote-method="remotePhoneMethod" remote-show-suffix
            placeholder="选择预设手机号" style="width: 200px;" clearable>
            <el-option v-for="item in selectPhoneList" :key="item.id" :label="item.name" :value="item.phone">
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime" label-width="100px">
          <el-date-picker v-model="genForm.startTime" type="date" placeholder="选择开始日期" value-format="YYYY年MM月DD日" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime" label-width="100px">
          <el-date-picker v-model="genForm.endTime" type="date" placeholder="选择结束日期" value-format="YYYY年MM月DD日" />
        </el-form-item>
        <el-form-item label="项目系统" prop="system" label-width="100px">
          <el-checkbox-group v-model="genForm.system">
            <el-checkbox v-for="(system, index) in systemList" :label="(index + 1) + '.' + system" :value="system" />
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="handleAddPhone">添加手机号</el-button>
          <el-button type="danger" @click="handleOpenDelPhone">删除手机号</el-button>
          <el-button type="primary" @click="submitGen">生 成</el-button>
          <el-button @click="closeGen">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="添加手机号" v-model="openPhone" width="800px" append-to-body>
      <el-form :model="phoneForm" :rules="rules" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="phoneForm.name" placeholder="请输入姓名"></el-input>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="phoneForm.phone" placeholder="请输入手机号"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitPhone">添加</el-button>
          <el-button @click="openPhone = false">取消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="删除手机号" v-model="openDelPhone" width="800px" append-to-body>
      <el-table v-loading="loading" :data="wbcardPhoneList" style="width: 100%">
        <el-table-column label="姓名" align="center" prop="name" />
        <el-table-column label="手机号" align="center" prop="phone" />
        <el-table-column label="操作" align="center" width="80">
          <template #default="scope">
            <el-button link type="danger" icon="Delete" @click="handleDeletePhone(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="phoneTotal > 0" :total="phoneTotal" v-model:page="phoneQueryParams.pageNum"
        v-model:limit="phoneQueryParams.pageSize" @pagination="getPhoneList" />
    </el-dialog>
  </div>
</template>

<script setup name="Wbcard">
import { listWbcard, listWbcardPhone, getWbcard, delWbcard, addWbcard, addWBCardPhone, updateWbcard, delWBCardPhone } from "@/api/management/wbcard";
import { ref } from "vue";
import { generateWbcard } from "../../../api/management/wbcard";

const { proxy } = getCurrentInstance();
const { wbcard_send_status, wbcard_print_status } = proxy.useDict('wbcard_send_status', 'wbcard_print_status');

const wbcardList = ref([]);
const wbcardPhoneList = ref([]);
const selectPhoneList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const phoneTotal = ref(0);
const title = ref("");
const openGen = ref(false)
const openPhone = ref(false)
const openDelPhone = ref(false)
const systemList = ref([
  "火灾自动报警系统",
  "火灾应急照明及疏散指示",
  "消防电话系统",
  "消防广播系统",
  "消防电梯系统",
  "消防供电设施",
  "消防供水系统",
  "自动喷水灭火系统",
  "消火栓系统",
  "机械排烟系统",
  "机械加压送风系统",
  "防火分隔设施",
  "电气火灾监控系统",
  "自动寻的灭火装置",
  "气体灭火系统",
  "泡沫灭火系统",
  "细水雾灭火系统",
  "灭火器（检查）",
  "其他"
])

const data = reactive({
  form: {},
  genForm: {},
  phoneForm: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    code: null,
    title: null,
    printCheck: null,
    sendCheck: null,
    orderByColumn: null,
    isAsc: null
  },
  phoneQueryParams: {
    pageNum: 1,
    pageSize: 10
  },
  rules: {
    name: [
      { required: true, message: "名称不能为空", trigger: "blur" }
    ],
    phone: [
      { required: true, message: "手机号不能为空", trigger: "blur" }
    ]
  }
});

const { queryParams, phoneQueryParams, form, genForm, phoneForm, rules } = toRefs(data);

/** 查询维保卡管理列表 */
function getList() {
  loading.value = true;
  listWbcard(queryParams.value).then(response => {
    wbcardList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 查询手机号列表 */
function getPhoneList() {
  loading.value = true;
  listWbcardPhone(phoneQueryParams.value).then(response => {
    wbcardPhoneList.value = response.rows;
    phoneTotal.value = response.total;
    loading.value = false;
  });
}

// ... existing code ...
function remotePhoneMethod(query) {
  loading.value = true;
  const params = {
    pageNum: 1,
    pageSize: 10
  };
  // 只有当有查询条件时才添加name参数
  if (query && query.trim() !== '') {
    params.name = query;
  }
  listWbcardPhone(params).then(response => {
    selectPhoneList.value = response.rows;
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

/** 重置生成表单 */
function resetGen() {
  genForm.value = {
    name: null,
    company: "河南众基消防技术有限公司",
    leader: null,
    phone: null,
    system: ref([]),
    startTime: null,
    endTime: null,
    no: null
  }
}

/** 重置添加手机号的表单 */
function resetPhone() {
  phoneForm.value = {
    name: null,
    phone: null
  }
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

/** 打开手机号添加 */
function handleAddPhone() {
  resetPhone();
  openPhone.value = true;
}

function handleOpenDelPhone() {
  getPhoneList();
  openDelPhone.value = true;
}

/** 生成维保卡 */
function handleGen() {
  resetGen();
  // 如果手机号列表为空，初始化
  remotePhoneMethod('');
  openGen.value = true;
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

/** 提交生成维保卡 */
function submitGen() {
  console.log(genForm.value)
  let simplifySystem = simplifySystems(genForm.value.system);
  console.log(simplifySystem)
  let system1 = simplifySystem.substring(0, 37);
  let system2 = simplifySystem.substring(37);
  const data = {
    name: genForm.value.name,
    company: genForm.value.company,
    leader: genForm.value.leader,
    phone: `0371-53392119/${genForm.value.phone}`,
    time: `${genForm.value.startTime}到${genForm.value.endTime}`,
    system1: system1,
    system2: system2,
    no: genForm.value.no
  }
  generateWbcard(data).then(res => {
    if (res.code === 200) {
      proxy.$message.success("生成维保卡成功");
      openGen.value = false;
      getList();
    }
  })
}

/** 添加手机号 */
function submitPhone() {
  addWBCardPhone(phoneForm.value).then(response => {
    proxy.$modal.msgSuccess("添加成功");
    openPhone.value = false;
    getPhoneList();
    resetPhone();
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row.id || ids.value;
  proxy.$modal.confirm('是否确认删除维保卡管理编号为"' + _ids + '"的数据项？').then(function () {
    return delWbcard(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => { });
}

/** 删除手机号 */
function handleDeletePhone(row) {
  proxy.$modal.confirm('是否确认删除手机号"' + row.phone + '"？').then(function () {
    return delWBCardPhone(row.id);
  }).then(() => {
    getPhoneList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => { });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('management/wbcard/export', {
    ...queryParams.value
  }, `wbcard_${new Date().getTime()}.xlsx`)
}

/** 自定义的排序功能 */
function handleSortChange(data) {
  queryParams.value.orderByColumn = data.prop;
  queryParams.value.isAsc = data.order === "ascending" ? "asc" : "desc";
  getList();
}

/**
 * 简化系统列表数据
 * @param {Array} systems - 系统列表数组，包含系统信息对象
 * @returns {string} 简化后的系统列表数据
 */
function simplifySystems(systems) {
  // 创建系统副本进行操作
  let simplifiedSystems = [...systems];

  // 检查并合并防排烟系统
  const hasMechanicalSmoke = simplifiedSystems.includes("机械排烟系统");
  const hasPressurizedAir = simplifiedSystems.includes("机械加压送风系统");
  if (hasMechanicalSmoke && hasPressurizedAir) {
    // 移除原有系统
    simplifiedSystems = simplifiedSystems.filter(sys =>
      sys !== "机械排烟系统" && sys !== "机械加压送风系统"
    );
    // 添加合并后的系统
    simplifiedSystems.unshift("防排烟系统");
  }

  let result = simplifiedSystems.join("、");
  if (result.length <= 85) {
    return result;
  }

  // 检查并合并水灭火系统
  const hasWaterSupply = simplifiedSystems.includes("消防供水系统");
  const hasSprinkler = simplifiedSystems.includes("自动喷水灭火系统");
  const hasHydrant = simplifiedSystems.includes("消火栓系统");
  if (hasWaterSupply && hasSprinkler && hasHydrant) {
    // 移除原有系统
    simplifiedSystems = simplifiedSystems.filter(sys =>
      sys !== "消防供水系统" &&
      sys !== "自动喷水灭火系统" &&
      sys !== "消火栓系统"
    );
    // 添加合并后的系统
    simplifiedSystems.unshift("水灭火系统");
  }
  result = simplifiedSystems.join("、");
  if (result.length <= 85) {
    return result;
  }
  // 删除消防广播系统、消防电话系统、消防电梯系统
  const systemsToRemove = [
    "消防广播系统",
    "消防电话系统",
    "消防电梯系统"
  ];
  simplifiedSystems = simplifiedSystems.filter(sys =>
    !systemsToRemove.includes(sys)
  );
  // 重新组合检查长度
  result = simplifiedSystems.join("、");
  // 如果仍然超过85个字符，则删除最后的系统并添加通用描述
  if (result.length > 85) {
    // 逐步移除系统直到满足长度要求
    while (result.length > 70 && simplifiedSystems.length > 1) { // 留出"等现场已设置消防设施"的空间
      simplifiedSystems.pop();
      result = simplifiedSystems.join("、");
    }

    // 添加通用描述
    result = result + "等现场已设置消防设施";
  }

  return result;
}
function closeGen() {
  resetGen();
  openGen.value = false;
}

getList();
</script>
