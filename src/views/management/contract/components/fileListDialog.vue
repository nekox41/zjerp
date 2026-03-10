<template>
    <el-dialog v-model="dialogVisible" title="文件列表" @close="handleClose">
        <div v-if="fileList.length">
            <ul>
                <li v-for="(file, index) in fileList" :key="index">
                    <el-link :href="`${baseUrl}${file.url}`" :underline="true" target="_blank">
                        <span class="el-icon-document"> {{ file.name }} </span>
                    </el-link>
                </li>
            </ul>
        </div>
        <template #footer>
            <el-button @click="handleClose">关闭</el-button>
        </template>
    </el-dialog>
</template>

<script setup>
import { computed } from 'vue';

const baseUrl = import.meta.env.VITE_APP_BASE_API;

// 定义 props
const props = defineProps({
    visible: Boolean,
    contractFile: String
})

const emit = defineEmits(['update:visible', "close"])

const dialogVisible = computed({
    get: () => props.visible,
    set: (val) => emit('update:visible', val)
})

const handleClose = () => {
    emit("update:visible", false);
}

const fileList = computed(() => {
    let temp = 1;
    const list = Array.isArray(props.contractFile) ? props.contractFile : props.contractFile.split(',');
    return list.map(item => {
        if (typeof item === "string") {
            item = { name: item.split('/').at(-1), url: item };
        }
        item.uid = item.uid || new Date().getTime() + temp++;
        return item;
    })
})

</script>