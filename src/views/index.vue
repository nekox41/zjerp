<template>
    <div class="dashboard-container">
        <!-- 天气信息 -->
        <div class="weather-section">
            <el-card class="weather-card">
                <div class="weather-content">
                    <div class="weather-location">
                        <i class="el-icon-location"></i>
                        <span class="city">杭州市</span>
                    </div>
                    <div class="weather-main">
                        <i class="weather-icon" :class="getWeatherIcon(currentWeather.type)"></i>
                        <div class="temperature">{{ currentWeather.temperature }}°C</div>
                    </div>
                    <div class="weather-desc">{{ currentWeather.description }}</div>
                    <div class="weather-details">
                        <div class="detail-item">
                            <i class="el-icon-wind"></i>
                            <span>{{ currentWeather.wind }}</span>
                        </div>
                        <div class="detail-item">
                            <i class="el-icon-water-cup"></i>
                            <span>{{ currentWeather.humidity }}%</span>
                        </div>
                    </div>
                </div>
            </el-card>
        </div>

        <!-- 信息统计面板 -->
        <div class="stats-section">
            <el-row :gutter="20" class="stats-row">
                <el-col :span="6">
                    <el-card shadow="hover" class="stat-card">
                        <div class="stat-content">
                            <div class="stat-icon bg-blue">
                                <i class="el-icon-document"></i>
                            </div>
                            <div class="stat-info">
                                <div class="stat-number">{{ summary.contractCount }}</div>
                                <div class="stat-title">维保合同数量</div>
                            </div>
                        </div>
                    </el-card>
                </el-col>
<!-- 
                <el-col :span="6">
                    <el-card shadow="hover" class="stat-card">
                        <div class="stat-content">
                            <div class="stat-icon bg-green">
                                <i class="el-icon-money"></i>
                            </div>
                            <div class="stat-info">
                                <div class="stat-number">¥{{ formatNumber(summary.amountSum) }}</div>
                                <div class="stat-title">维保合同金额</div>
                            </div>
                        </div>
                    </el-card>
                </el-col>
 -->

                <el-col :span="6">
                    <el-card shadow="hover" class="stat-card">
                        <div class="stat-content">
                            <div class="stat-icon bg-orange">
                                <i class="el-icon-user"></i>
                            </div>
                            <div class="stat-info">
                                <div class="stat-number">{{ summary.customerCount }}</div>
                                <div class="stat-title">客户数量</div>
                            </div>
                        </div>
                    </el-card>
                </el-col>

                <el-col :span="6">
                    <el-card shadow="hover" class="stat-card">
                        <div class="stat-content">
                            <div class="stat-icon bg-purple">
                                <i class="el-icon-timer"></i>
                            </div>
                            <div class="stat-info">
                                <div class="stat-number">{{ summary.pendingTasks }}</div>
                                <div class="stat-title">待办事项</div>
                            </div>
                        </div>
                    </el-card>
                </el-col>
            </el-row>
        </div>

        <!-- 公告栏和其它信息 -->
        <div class="content-section">
            <el-row :gutter="20">
                <!-- 公告栏 -->
                <el-col :span="16">
                    <el-card class="info-card">
                        <template #header>
                            <div class="card-header">
                                <span class="card-title">系统公告</span>
                                <el-button type="primary" size="small">查看更多</el-button>
                            </div>
                        </template>
                        <div class="notice-list">
                            <div 
                                v-for="(notice, index) in notices" 
                                :key="index" 
                                class="notice-item"
                                @click="viewNotice(notice)"
                            >
                                <div class="notice-content">
                                    <h4>{{ notice.title }}</h4>
                                    <p>{{ notice.content }}</p>
                                </div>
                                <div class="notice-meta">
                                    <span class="notice-time">{{ notice.time }}</span>
                                    <span class="notice-type">{{ notice.type }}</span>
                                </div>
                            </div>
                        </div>
                    </el-card>
                </el-col>

                <!-- 快捷操作 -->
                <el-col :span="8">
                    <el-card class="info-card">
                        <template #header>
                            <div class="card-header">
                                <span class="card-title">快捷操作</span>
                            </div>
                        </template>
                        <div class="quick-actions">
                            <div 
                                v-for="(action, index) in quickActions" 
                                :key="index" 
                                class="action-item"
                                @click="executeAction(action)"
                            >
                                <div class="action-icon" :class="action.iconClass"></div>
                                <div class="action-text">{{ action.name }}</div>
                            </div>
                        </div>
                    </el-card>

                    <!-- 近期活动 -->
                    <el-card class="info-card activity-card">
                        <template #header>
                            <div class="card-header">
                                <span class="card-title">近期活动</span>
                            </div>
                        </template>
                        <div class="activity-list">
                            <div 
                                v-for="(activity, index) in activities" 
                                :key="index" 
                                class="activity-item"
                            >
                                <div class="activity-icon" :class="getActivityIcon(activity.type)"></div>
                                <div class="activity-content">
                                    <h4>{{ activity.title }}</h4>
                                    <p>{{ activity.desc }}</p>
                                    <span class="activity-time">{{ activity.time }}</span>
                                </div>
                            </div>
                        </div>
                    </el-card>
                </el-col>
            </el-row>
        </div>
    </div>
</template>

<script setup name="Index">
import { ref, onMounted } from 'vue'
import { getWbSummary } from '@/api/management/contract'

// 天气数据
const currentWeather = ref({
    temperature: 22,
    type: 'sunny',
    description: '晴朗',
    wind: '3级',
    humidity: 65
});

// 统计数据
const summary = ref({
    contractCount: 0,
    amountSum: 0,
    customerCount: 98,
    pendingTasks: 12
});

// 公告数据
const notices = ref([
    {
        id: 1,
        title: '系统维护通知',
        content: '本周六凌晨2点至4点进行系统维护，届时将暂停服务。',
        time: '2023-05-20',
        type: '重要'
    },
    {
        id: 2,
        title: '新功能上线',
        content: '维保合同管理功能正式上线，欢迎体验！',
        time: '2023-05-18',
        type: '更新'
    },
    {
        id: 3,
        title: '节假日安排',
        content: '劳动节假期安排：5月1日-5月3日放假，4日起正常上班。',
        time: '2023-04-28',
        type: '通知'
    }
]);

// 快捷操作
const quickActions = ref([
    { name: '新建合同', iconClass: 'el-icon-plus' },
    { name: '查看报表', iconClass: 'el-icon-data-analysis' },
    { name: '客户管理', iconClass: 'el-icon-user' },
    { name: '系统设置', iconClass: 'el-icon-setting' }
]);

// 活动数据
const activities = ref([
    {
        type: 'meeting',
        title: '项目启动会议',
        desc: '维保管理系统升级项目启动会',
        time: '今天 14:00'
    },
    {
        type: 'task',
        title: '合同到期提醒',
        desc: '张三公司的维保合同即将到期',
        time: '明天 09:00'
    },
    {
        type: 'event',
        title: '系统培训',
        desc: '新员工系统使用培训',
        time: '后天 10:00'
    }
]);

onMounted(() => {
    getWbSummary().then(res => {
        summary.value.contractCount = res.data.contractCount || 0;
        summary.value.amountSum = res.data.amountSum || 0;
    });
});

// 获取天气图标
const getWeatherIcon = (type) => {
    const weatherIcons = {
        sunny: 'el-icon-sunny',
        cloudy: 'el-icon-cloudy',
        partlyCloudy: 'el-icon-partly-cloudy',
        rain: 'el-icon-heavy-rain',
        snow: 'el-icon-lightning'
    };
    return weatherIcons[type] || 'el-icon-sunny';
};

// 获取活动图标
const getActivityIcon = (type) => {
    const activityIcons = {
        meeting: 'el-icon-meeting',
        task: 'el-icon-tickets',
        event: 'el-icon-date'
    };
    return activityIcons[type] || 'el-icon-tickets';
};

// 格式化数字
const formatNumber = (num) => {
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
};

// 查看公告
const viewNotice = (notice) => {
    console.log('查看公告:', notice);
    // 实现查看公告详情逻辑
};

// 执行快捷操作
const executeAction = (action) => {
    console.log('执行操作:', action.name);
    // 实现快捷操作逻辑
};
</script>

<style scoped>
.dashboard-container {
    padding: 20px;
    background-color: #f5f7fa;
    min-height: 100vh;
}

.weather-section {
    margin-bottom: 20px;
}

.weather-card {
    border-radius: 12px;
    overflow: hidden;
    background: linear-gradient(135deg, #74b9ff, #0984e3);
    color: white;
    box-shadow: 0 4px 12px rgba(116, 185, 255, 0.3);
}

.weather-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20px;
}

.weather-location {
    display: flex;
    flex-direction: column;
    align-items: center;
}

.city {
    font-size: 18px;
    font-weight: bold;
    margin-top: 5px;
}

.weather-main {
    display: flex;
    align-items: center;
    justify-content: center;
}

.weather-icon {
    font-size: 48px;
    margin-right: 15px;
}

.temperature {
    font-size: 36px;
    font-weight: bold;
}

.weather-desc {
    font-size: 16px;
    opacity: 0.9;
}

.weather-details {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
}

.detail-item {
    display: flex;
    align-items: center;
    margin-bottom: 5px;
}

.stats-section {
    margin-bottom: 20px;
}

.stat-card {
    border-radius: 10px;
    transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.stat-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 8px 16px rgba(0,0,0,0.15);
}

.stat-content {
    display: flex;
    align-items: center;
}

.stat-icon {
    width: 60px;
    height: 60px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 15px;
    color: white;
    font-size: 24px;
}

.bg-blue { background-color: #3498db; }
.bg-green { background-color: #2ecc71; }
.bg-orange { background-color: #e67e22; }
.bg-purple { background-color: #9b59b6; }

.stat-info {
    flex: 1;
}

.stat-number {
    font-size: 24px;
    font-weight: bold;
    color: #2c3e50;
}

.stat-title {
    font-size: 14px;
    color: #7f8c8d;
    margin-top: 5px;
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.card-title {
    font-weight: bold;
    color: #2c3e50;
    font-size: 16px;
}

.notice-list {
    max-height: 400px;
    overflow-y: auto;
}

.notice-item {
    padding: 15px 0;
    border-bottom: 1px solid #eee;
    cursor: pointer;
    transition: background-color 0.3s;
}

.notice-item:last-child {
    border-bottom: none;
}

.notice-item:hover {
    background-color: #f8f9fa;
}

.notice-content h4 {
    margin: 0 0 5px 0;
    color: #2c3e50;
    font-size: 15px;
}

.notice-content p {
    margin: 0;
    color: #7f8c8d;
    font-size: 13px;
    line-height: 1.4;
}

.notice-meta {
    display: flex;
    justify-content: space-between;
    margin-top: 8px;
    font-size: 12px;
    color: #95a5a6;
}

.quick-actions {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 15px;
}

.action-item {
    text-align: center;
    padding: 15px;
    border: 1px solid #ebeef5;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.3s;
}

.action-item:hover {
    border-color: #74b9ff;
    box-shadow: 0 2px 8px rgba(116, 185, 255, 0.2);
}

.action-icon {
    font-size: 24px;
    color: #74b9ff;
    margin-bottom: 8px;
}

.action-text {
    font-size: 14px;
    color: #2c3e50;
}

.activity-list {
    max-height: 300px;
    overflow-y: auto;
}

.activity-item {
    display: flex;
    align-items: flex-start;
    padding: 12px 0;
    border-bottom: 1px solid #eee;
}

.activity-item:last-child {
    border-bottom: none;
}

.activity-icon {
    font-size: 20px;
    color: #74b9ff;
    margin-right: 10px;
    margin-top: 2px;
}

.activity-content h4 {
    margin: 0 0 5px 0;
    color: #2c3e50;
    font-size: 14px;
}

.activity-content p {
    margin: 0 0 5px 0;
    color: #7f8c8d;
    font-size: 13px;
}

.activity-time {
    font-size: 12px;
    color: #95a5a6;
}

.info-card {
    margin-bottom: 20px;
    border-radius: 10px;
}

.activity-card {
    margin-bottom: 0;
}
</style>