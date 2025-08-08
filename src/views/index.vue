<template>
    <div class="index-body">
        <el-row :gutter="20">
            <el-col :span="4">
                <el-card shadow="hover">
                    <el-statistic :value="summary.contractCount">
                        <template #title>
                            <div>维保合同数量</div>
                        </template>
                    </el-statistic>
                </el-card>
            </el-col>

            <el-col :span="4">
                <el-card shadow="hover">
                    <el-statistic :value="summary.amountSum">
                        <template #title>
                            <div style="display: inline-flex; align-items: center">
                                维保合同金额
                                <el-tooltip content="统计所有记录在系统中的维保合同。"><el-icon>
                                        <Warning />
                                    </el-icon>
                                </el-tooltip>
                            </div>
                        </template>
                        <template #suffix>
                            <div>元</div>
                        </template>
                    </el-statistic>
                </el-card>
            </el-col>
        </el-row>
    </div>
</template>

<script setup name="Index">
import { ref, onMounted } from 'vue'
import { getWbSummary } from '@/api/management/contract'

const summary = ref({});

onMounted(() => {
    getWbSummary().then(res => {
        summary.value.contractCount = res.data.contractCount;
        summary.value.amountSum = res.data.amountSum;
    })
})
</script>

<style scoped>
    .index-body {
        margin-top: 50px;
        margin-left: 50px;
    }
</style>