<template>
    <el-row>
        <el-col :span="12">
            <el-card>
                <template #header>
                    <h1>欢迎回来！</h1>
                </template>
                <p>{{ currentTime }}</p>
            </el-card>
        </el-col>
    </el-row>
</template>

<script setup name="Index">
import { ref, onMounted } from 'vue'

// 动态时间
const currentTime = ref(new Date().toLocaleString())

const updateTime = () => {
    currentTime.value = new Date().toLocaleString()
}

// 每秒更新时间
setInterval(updateTime, 1000)

// 位置与天气信息
const locationInfo = ref(null)

const fetchLocationAndWeather = async () => {
    try {
        // 获取 IP 和地理位置（示例使用 ip-api.com）
        const ipResponse = await fetch('http://ip-api.com/json')
        const ipData = await ipResponse.json()

        const city = ipData.city || '未知城市'
        const region = ipData.regionName || '未知地区'
        const country = ipData.country || '未知国家'
        const ip = ipData.query

        // 获取天气（示例使用 weatherapi.com，请替换为你的 API Key）
        // const weatherResponse = await fetch(`https://api.weatherapi.com/v1/current.json?key=YOUR_API_KEY&q=${city}`)
        // const weatherData = await weatherResponse.json()
        // const weather = weatherData.current.condition[0].text || '未知天气'

        // 示例模拟天气信息
        const weather = '晴朗' // 替换为真实数据后可删除此行

        locationInfo.value = {
            city,
            region,
            country,
            ip,
            weather
        }
    } catch (error) {
        console.error('获取位置或天气信息失败:', error)
        locationInfo.value = {
            city: '获取失败',
            region: '',
            country: '',
            ip: '获取失败',
            weather: ''
        }
    }
}

onMounted(() => {
    // fetchLocationAndWeather()
})
</script>

<style scoped lang="scss"></style>
