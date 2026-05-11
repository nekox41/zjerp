<template>
  <div class="todo-dashboard">
    <!-- 头部欢迎与操作区 -->
    <div class="header-section">
      <div class="welcome-text">
        <h2>欢迎回来，{{ userName }}！</h2>
        <p class="subtitle">今天也要保持高效哦！</p>
      </div>
      <el-button type="primary" size="large" @click="handleCreateCategory">
        <el-icon class="el-icon--left">
          <Plus />
        </el-icon>
        创建分类
      </el-button>
    </div>

    <el-divider border-style="dashed" />

    <!-- 底部卡片区域 (看板布局) -->
    <div class="category-grid">
      <el-card v-for="category in displayCategoryList" :key="category.id" class="category-card" shadow="hover">

        <template #header>
          <div class="card-header">
            <span class="category-name">{{ category.name }}</span>
            <div class="header-actions">
              <el-tag type="info" size="small" round>{{ category.todos?.length || 0 }} 项</el-tag>

              <el-button v-if="typeof category.id === 'number'" type="danger" size="small" circle text title="删除分类"
                @click="handleDeleteCategory(category)">
                <el-icon>
                  <Delete />
                </el-icon>
              </el-button>
            </div>
          </div>
        </template>

        <div class="todo-list">
          <el-empty v-if="!category.todos || category.todos.length === 0" description="暂无待办事项" :image-size="60" />

          <!-- 引入 VueDraggable 组件实现拖拽 -->
          <draggable 
            v-else 
            v-model="category.todos" 
            item-key="id" 
            handle=".drag-handle" 
            animation="200" 
            :disabled="typeof category.id !== 'number'" 
            @end="handleDragEnd(category)"
          >
            <!-- element 就是单个 todo 对象 -->
            <template #item="{ element: todo }">
              <div class="todo-item">
                <!-- 左侧：拖拽手柄与主要内容 -->
                <div class="todo-left">
                  <!-- 拖拽把手 -->
                  <div class="drag-handle" title="按住拖拽排序">⋮⋮</div>

                  <div class="todo-content">
                    <!-- 标题区域：支持点击编辑 -->
                    <div class="todo-main">
                      <el-input v-if="editingTodoId === todo.id" v-model="editTempTitle" size="small"
                        placeholder="输入待办事项" @blur="saveEditTitle(todo)" @keyup.enter="saveEditTitle(todo)"
                        ref="editInputRef" />
                      <span v-else class="todo-title" :class="{ 'is-completed': todo.todoStatus === 'completed' }"
                        @click="startEditTitle(todo)" title="点击修改标题">
                        {{ todo.title }}
                      </span>
                    </div>

                    <div class="todo-meta">
                      <el-tag :type="getPriorityType(todo.priority)" size="small" effect="light">
                        {{ formatPriority(todo.priority) }}
                      </el-tag>
                      <el-tag :type="getStatusType(todo.todoStatus)" size="small" effect="plain">
                        {{ formatStatus(todo.todoStatus) }}
                      </el-tag>
                    </div>
                  </div>
                </div>

                <div class="todo-actions">
                  <el-popover placement="bottom" trigger="click" width="220">
                    <template #reference>
                      <div class="date-trigger">
                        <span v-if="todo.dueDate" class="todo-date has-date">
                          <el-icon>
                            <Calendar />
                          </el-icon> {{ todo.dueDate }}
                        </span>
                        <el-button v-else size="small" circle title="设置截止日期">
                          <el-icon>
                            <Calendar />
                          </el-icon>
                        </el-button>
                      </div>
                    </template>
                    <el-date-picker v-model="todo.dueDate" type="date" placeholder="选择日期" size="small"
                      value-format="YYYY-MM-DD" @change="handleFieldUpdate(todo, 'dueDate')" style="width: 100%" />
                  </el-popover>

                  <!-- 更多操作菜单 -->
                  <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, todo, category.id)">
                    <el-button size="small" text circle>
                      <el-icon>
                        <More />
                      </el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="status_pending">设为待处理</el-dropdown-item>
                        <el-dropdown-item command="status_in_process">设为进行中</el-dropdown-item>
                        <el-dropdown-item command="status_completed">设为已完成</el-dropdown-item>
                        <el-dropdown-item command="status_cancelled" divided>取消待办</el-dropdown-item>
                        <el-dropdown-item command="archive" class="text-danger">归档 (隐藏)</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
            </template>
          </draggable>
        </div>

        <!-- 卡片底部：添加待办事项 -->
        <div class="card-footer" v-if="typeof category.id === 'number'">
          <el-input v-model="newTodoInputs[category.id]" placeholder="输入新待办，回车保存"
            @keyup.enter="handleAddTodo(category.id)">
            <template #append>
              <el-button @click="handleAddTodo(category.id)">
                <el-icon>
                  <Check />
                </el-icon>
              </el-button>
            </template>
          </el-input>
        </div>

      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted, computed } from 'vue' // 新增了 computed
import { Plus, Calendar, Check, More, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable' // 引入拖拽组件

// 1. 补充引入你的新接口
import {
  listCategoriesWithTodos,
  addCategories,
  delCategories,
  addTodos,
  updateTodos,
  getTodosWithCategoriesId, // 新增局部刷新接口
  updateTodosSort
} from '@/api/index'

const userName = ref('Admin')
const categoryList = ref([]) // 这里存的是后端真实的原始数据
const newTodoInputs = reactive({})

const editingTodoId = ref(null)
const editTempTitle = ref('')
const editInputRef = ref(null)

onMounted(async () => {
  await loadDashboardData()
})

// === 核心数据加载与处理 ===

const loadDashboardData = async () => {
  try {
    const res = await listCategoriesWithTodos()
    const categories = res.data || res

    categories.forEach(category => {
      newTodoInputs[category.id] = ''
      if (category.todos && category.todos.length > 0) {
        category.todos.sort((a, b) => a.sortOrder - b.sortOrder)
      } else {
        category.todos = []
      }
    })
    categoryList.value = categories
  } catch (error) {
    console.error('获取看板数据失败', error)
  }
}

// ✨ 新增：局部刷新某个分类的数据
const reloadCategoryData = async (categoryId) => {
  try {
    const res = await getTodosWithCategoriesId(categoryId)
    // 根据你的接口返回格式，取出那个分类对象
    const updatedCategory = (res.data || res)[0]

    if (updatedCategory) {
      // 找到本地对应的分类并替换它的 todos
      const index = categoryList.value.findIndex(c => c.id === categoryId)
      if (index !== -1) {
        let newTodos = updatedCategory.todos || []
        newTodos.sort((a, b) => a.sortOrder - b.sortOrder)
        categoryList.value[index].todos = newTodos
      }
    }
  } catch (error) {
    console.error('局部刷新失败', error)
  }
}

// ✨ 新增：计算属性 (构建虚拟分类用于渲染)
const displayCategoryList = computed(() => {
  const result = []

  // 定义两个虚拟分类，ID 使用字符串以防和真实的数字 ID 冲突
  const completedCategory = { id: 'virtual_completed', name: '✅ 已完成', todos: [] }
  const cancelledCategory = { id: 'virtual_cancelled', name: '❌ 已取消', todos: [] }

  categoryList.value.forEach(category => {
    // 拷贝真实分类的基础信息，准备重新装载过滤后的待办
    const filteredCategory = { ...category, todos: [] }

    category.todos.forEach(todo => {
      if (todo.todoStatus === 'completed') {
        completedCategory.todos.push(todo) // 归入全局已完成
      } else if (todo.todoStatus === 'cancelled') {
        cancelledCategory.todos.push(todo) // 归入全局已取消
      } else {
        filteredCategory.todos.push(todo)  // 正常待办留在这里
      }
    })
    result.push(filteredCategory)
  })

  // 如果这两个分类有数据，才将它们追加到末尾显示（或者你也可以无论有没有数据都 push 进去）
  if (completedCategory.todos.length > 0) result.push(completedCategory)
  if (cancelledCategory.todos.length > 0) result.push(cancelledCategory)

  return result
})

// === 分类操作 ===

// 1. 创建分类
const handleCreateCategory = () => {
  ElMessageBox.prompt('请输入新分类的名称', '创建分类', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '分类名称不能为空'
  }).then(async ({ value }) => {
    try {
      await addCategories({ name: value.trim() })
      ElMessage.success('分类创建成功')
      await loadDashboardData() // 刷新页面
    } catch (error) {
      console.error(error)
    }
  }).catch(() => { })
}

// 2. 删除分类
const handleDeleteCategory = (category) => {
  if (category.todos && category.todos.length > 0) {
    ElMessage.warning('该分类下还有待办事项，请先清空后再删除！')
    return
  }

  ElMessageBox.confirm(`确定要永久删除分类【${category.name}】吗？`, '删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await delCategories(category.id)
      ElMessage.success('分类已删除')
      await loadDashboardData() // 刷新页面
    } catch (error) {
      console.error(error)
    }
  }).catch(() => { })
}

// === 待办事项操作 ===

// 1. 创建待办事项
const handleAddTodo = async (categoryId) => {
  const title = newTodoInputs[categoryId]?.trim()
  if (!title) return

  const currentCategory = categoryList.value.find(c => c.id === categoryId)
  const currentTodosCount = currentCategory?.todos?.length || 0

  try {
    const payload = {
      title: title,
      categoryId: categoryId,
      priority: 'low',
      todoStatus: 'pending',
      sortOrder: currentTodosCount + 1
    }

    await addTodos(payload)
    ElMessage.success('待办添加成功')
    newTodoInputs[categoryId] = ''

    // ✨ 修改：添加完成后只刷新这一个分类！
    await reloadCategoryData(categoryId)
  } catch (error) {
    console.error('添加待办失败', error)
  }
}

// 2. 通用字段更新 (用于更新状态、修改标题、设置日期)
const handleFieldUpdate = async (todo, field) => {
  try {
    const payload = { id: todo.id, [field]: todo[field] }
    await updateTodos(payload)
    ElMessage.success('更新成功')

    // ✨ 修改：更新完成后只局部刷新这一个分类！
    // 因为虚拟分类里的待办也带有真实的 categoryId，所以这里照样能刷新真实的分类
    await reloadCategoryData(todo.categoryId)
  } catch (error) {
    // 失败则回滚该分类
    await reloadCategoryData(todo.categoryId)
  }
}

// 3. 行内编辑标题相关
const startEditTitle = async (todo) => {
  editingTodoId.value = todo.id
  editTempTitle.value = todo.title
  await nextTick()
  if (editInputRef.value) editInputRef.value.focus()
}

const saveEditTitle = async (todo) => {
  const newTitle = editTempTitle.value.trim()
  if (!newTitle) { editingTodoId.value = null; return }

  if (newTitle !== todo.title) {
    todo.title = newTitle
    await handleFieldUpdate(todo, 'title')
  }
  editingTodoId.value = null
}

// 4. 右侧下拉菜单分发 (处理状态变更及暂未实现的归档)
const handleCommand = async (command, todo, categoryId) => {
  if (command.startsWith('status_')) {
    todo.todoStatus = command.replace('status_', '')
    await handleFieldUpdate(todo, 'todoStatus')
  } else if (command === 'archive') {
    ElMessage.info('归档功能暂未接入')
  }
}

// 5. 拖拽排序结束钩子 (暂时不写)
// === 拖拽排序核心逻辑 ===
// === 拖拽排序核心逻辑 ===
const handleDragEnd = async (draggedCategory) => {
  // 1. 安全拦截：如果是虚拟分类（字符串ID），直接跳过
  if (typeof draggedCategory.id !== 'number') return

  // 2. 此时 draggedCategory.todos 里的顺序，已经是拖拽后的最新顺序了
  // 我们遍历它，重新赋予 sortOrder，并提取出后端需要的 payload
  const updatedOrders = draggedCategory.todos.map((todo, index) => {
    const newSortOrder = index + 1 // 序号从 1 开始
    todo.sortOrder = newSortOrder  // 更新当前对象的本地序号
    return {
      id: todo.id,
      sortOrder: newSortOrder
    }
  })

  // 3. ✨ 关键修复：同步回原始数据源 (categoryList.value)
  // 因为拖拽是在 computed 的临时数组上发生的，必须手动同步回真实源，否则响应式刷新后会弹回原样
  const originalCategory = categoryList.value.find(c => c.id === draggedCategory.id)
  if (originalCategory) {
    // 把当前分类下，状态是“已完成”或“已取消”（即被移到虚拟分类）的原始记录找出来
    const hiddenTodos = originalCategory.todos.filter(t => 
      t.todoStatus === 'completed' || t.todoStatus === 'cancelled'
    )
    // 将【拖拽排好序的待办】和【原本隐藏的待办】重新拼接，覆盖真实的 categoryList
    originalCategory.todos = [...draggedCategory.todos, ...hiddenTodos]
  }

  try {
    // 4. 发送批量更新请求给后端
    await updateTodosSort(updatedOrders)
    ElMessage.success('排序已保存')
  } catch (error) {
    console.error('排序保存失败', error)
    ElMessage.error('排序保存失败，正在恢复原始顺序')
    // 如果后端保存失败，重新请求该分类的数据，把错误的顺序强行纠正回来
    await reloadCategoryData(draggedCategory.id) 
  }
}

// === UI 格式化方法 ===
const formatPriority = (priority) => {
  const map = { high: '高', normal: '中', low: '低' }
  return map[priority] || priority
}
const getPriorityType = (priority) => {
  const map = { high: 'danger', normal: 'warning', low: 'info' }
  return map[priority] || 'info'
}
const formatStatus = (status) => {
  const map = { pending: '待处理', in_process: '进行中', completed: '已完成', cancelled: '已取消' }
  return map[status] || status
}
const getStatusType = (status) => {
  const map = { pending: 'info', in_process: 'primary', completed: 'success', cancelled: 'danger' }
  return map[status] || 'info'
}
</script>

<style scoped>
/* 保持原有基础样式，新增和修改以下部分 */
.todo-dashboard {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
}

.welcome-text h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
}

.welcome-text .subtitle {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 20px;
  align-items: start;
}

.category-card {
  border-radius: 8px;
  display: flex;
  flex-direction: column;
}

:deep(.el-card__body) {
  padding: 0;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
  font-size: 16px;
  gap: 8px;
}

.todo-list {
  padding: 15px;
  max-height: 600px;
  overflow-y: auto;
  background-color: #fafbfc;
}

/* === Todo Item 布局更新 === */
.todo-item {
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  transition: all 0.3s ease;
}

.todo-item:hover {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  border-color: #c0c4cc;
}

/* 左侧内容区：支持拖拽把手 */
.todo-left {
  display: flex;
  align-items: flex-start;
  flex: 1;
  min-width: 0;
  /* 防止内容撑破 flex 容器 */
}

.drag-handle {
  cursor: grab;
  color: #c0c4cc;
  font-size: 18px;
  padding-right: 10px;
  line-height: 1.4;
  user-select: none;
}

.drag-handle:active {
  cursor: grabbing;
}

.todo-content {
  flex: 1;
  min-width: 0;
}

.todo-main {
  margin-bottom: 6px;
}

.todo-title {
  font-size: 14px;
  color: #303133;
  line-height: 1.4;
  cursor: pointer;
  border-bottom: 1px dashed transparent;
  transition: border-color 0.2s;
  word-break: break-all;
}

.todo-title:hover {
  border-bottom-color: #409eff;
  color: #409eff;
}

.todo-title.is-completed {
  text-decoration: line-through;
  color: #909399;
  border-bottom-color: transparent;
}

.todo-meta {
  display: flex;
  gap: 8px;
}

/* 右侧操作区 */
.todo-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 10px;
}

.date-trigger {
  cursor: pointer;
}

.todo-date.has-date {
  font-size: 12px;
  color: #a8abb2;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  border-radius: 4px;
  background-color: #f4f4f5;
  transition: background-color 0.2s;
}

.todo-date.has-date:hover {
  background-color: #e9e9eb;
  color: #409eff;
}

.text-danger {
  color: #f56c6c !important;
}

.card-footer {
  padding: 15px;
  border-top: 1px solid #ebeef5;
  background-color: #fff;
}
</style>