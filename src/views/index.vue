<template>
  <div class="home">
    <!-- 欢迎横幅 -->
    <div class="hero">
      <h2 class="hero-title">{{ greeting }}，{{ userName }} 👋</h2>
      <div class="hero-meta">
        <span class="hero-date">{{ todayText }}</span>
        <el-tag size="small" effect="dark" round>今日 6 项待处理</el-tag>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col v-for="item in stats" :key="item.label" :xs="12" :md="6">
        <el-card shadow="hover" class="stat-card" :body-style="{ padding: '18px' }">
          <div class="stat">
            <div class="stat-icon" :style="{ background: item.bg, color: item.color }">
              <el-icon :size="20">
                <component :is="item.icon" />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ item.value }}</div>
              <div class="stat-label">{{ item.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图 + 目标完成度 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="16">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-header">
              <span>本周完成趋势</span>
              <el-tag size="small" type="success" effect="light">较上周 +12%</el-tag>
            </div>
          </template>
          <div class="chart">
            <div v-for="item in weekTrend" :key="item.day" class="chart-col">
              <div class="bar-area">
                <div class="bar" :title="`${item.day}：${item.value} 项`"
                  :style="{ height: (item.value / maxTrend * 100) + '%' }"></div>
              </div>
              <div class="bar-label">{{ item.day }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-header">
              <span>本月目标</span>
            </div>
          </template>
          <div class="ring-wrap">
            <el-progress type="circle" :percentage="72" :width="150" :stroke-width="10">
              <template #default="{ percentage }">
                <div class="ring-value">{{ percentage }}%</div>
                <div class="ring-label">完成度</div>
              </template>
            </el-progress>
            <p class="ring-tip">已完成 36 / 50 项，继续保持</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 员工统计 + 系统公告 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-header">
              <span>员工统计</span>
              <el-tag size="small" effect="plain">共 {{ userStat.total }} 人</el-tag>
            </div>
          </template>
          <div v-if="userStat.posts.length" class="post-grid">
            <div v-for="p in userStat.posts" :key="p.postId" class="post-item">
              <div class="post-count">{{ p.userCount }}</div>
              <div class="post-name">{{ p.postName }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无岗位数据" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :xs="24" :md="10">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-header">
              <span>系统公告</span>
              <el-tag size="small" effect="plain">{{ notices.length }} 条</el-tag>
            </div>
          </template>
          <div v-if="notices.length" class="notice-list">
            <div v-for="item in notices" :key="item.noticeId" class="notice-row">
              <el-tag size="small" :type="item.noticeType === '1' ? 'warning' : 'primary'" effect="light">
                {{ item.noticeType === '1' ? '通知' : '公告' }}
              </el-tag>
              <span class="notice-title" :title="item.noticeContent">{{ item.noticeTitle }}</span>
              <span class="notice-time">{{ item.createTime }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无公告" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { List, Clock, CircleCheck, Flag } from '@element-plus/icons-vue'
import { listNotice } from '@/api/system/notice'
import { getUserStat } from '@/api/system/dashboard'

/* ---------------- 顶部信息 ---------------- */
const userName = ref('Admin')

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() => {
  const d = new Date()
  const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 ${week}`
})

/* ---------------- 静态展示数据 ---------------- */
const stats = [
  { label: '全部任务', value: 28, icon: List, color: '#409eff', bg: '#ecf5ff' },
  { label: '进行中', value: 9, icon: Clock, color: '#e6a23c', bg: '#fdf6ec' },
  { label: '已完成', value: 16, icon: CircleCheck, color: '#67c23a', bg: '#f0f9eb' },
  { label: '高优先级', value: 3, icon: Flag, color: '#f56c6c', bg: '#fef0f0' }
]

const weekTrend = [
  { day: '周一', value: 3 },
  { day: '周二', value: 5 },
  { day: '周三', value: 2 },
  { day: '周四', value: 6 },
  { day: '周五', value: 4 },
  { day: '周六', value: 7 },
  { day: '周日', value: 5 }
]
const maxTrend = Math.max(...weekTrend.map(i => i.value), 1)

/* ---------------- 系统公告（接入若依接口） ---------------- */
const notices = ref([])

const loadNotices = async () => {
  try {
    const res = await listNotice({ pageNum: 1, pageSize: 6, status: '0' })
    notices.value = res.rows || []
  } catch (e) {
    // 静默失败，不干扰首页展示
    notices.value = []
  }
}

/* ---------------- 员工统计（接入自定义接口） ---------------- */
const userStat = ref({ total: 0, posts: [] })

const loadUserStat = async () => {
  try {
    const res = await getUserStat()
    userStat.value = res.data || { total: 0, posts: [] }
  } catch (e) {
    // 静默失败，不干扰首页展示
    userStat.value = { total: 0, posts: [] }
  }
}

onMounted(() => {
  loadNotices()
  loadUserStat()
})
</script>

<style scoped>
.home {
  padding: 16px;
  min-height: 100vh;
  box-sizing: border-box;
  background-color: #f5f7fa;
}

/* 欢迎横幅 */
.hero {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px;
  margin-bottom: 16px;
  border-radius: 10px;
  color: #fff;
  background: linear-gradient(135deg, #409eff 0%, #79bbff 100%);
}

.hero-title {
  margin: 0;
  font-size: 22px;
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.hero-date {
  font-size: 13px;
  opacity: 0.85;
}

/* 统计卡片 */
.stat-card {
  margin-bottom: 16px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
  color: #303133;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

/* 面板通用 */
.panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  margin-bottom: 16px;
}

.panel :deep(.el-card__body) {
  flex: 1;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 15px;
}

/* 柱状图 */
.chart {
  display: flex;
  gap: 10px;
  height: 220px;
  padding-top: 8px;
}

.chart-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.bar-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
}

.bar {
  width: 55%;
  max-width: 34px;
  min-height: 4px;
  border-radius: 6px 6px 2px 2px;
  background: linear-gradient(180deg, #a0cfff, #409eff);
  transition: height 0.4s ease;
}

.bar:hover {
  background: linear-gradient(180deg, #79bbff, #337ecc);
}

.bar-label {
  margin-top: 8px;
  font-size: 12px;
  text-align: center;
  color: #909399;
}

/* 环形进度 */
.ring-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 10px 0;
}

.ring-value {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.ring-label {
  font-size: 12px;
  color: #909399;
}

.ring-tip {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

/* 员工统计 */
.post-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 12px;
  padding: 4px 0;
}

.post-item {
  padding: 16px 8px;
  text-align: center;
  border-radius: 8px;
  background-color: #f5f7fa;
  transition: background-color 0.2s;
}

.post-item:hover {
  background-color: #ecf5ff;
}

.post-count {
  font-size: 22px;
  font-weight: 600;
  color: #409eff;
  line-height: 1.2;
}

.post-name {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
}

/* 系统公告 */
.notice-list {
  display: flex;
  flex-direction: column;
}

.notice-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px dashed #ebeef5;
}

.notice-row:last-child {
  border-bottom: none;
}

.notice-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-time {
  flex-shrink: 0;
  font-size: 12px;
  color: #c0c4cc;
}
</style>