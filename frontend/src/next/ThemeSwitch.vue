<script setup>
import {ref,onMounted,onBeforeUnmount} from 'vue'
const media=window.matchMedia('(prefers-color-scheme: dark)')
const key='erp-next-theme'
function saved(){try{return localStorage.getItem(key)}catch{return null}}
const dark=ref(saved()==='dark'||(saved()!=='light'&&media.matches))
function apply(){document.documentElement.dataset.erpTheme=dark.value?'dark':'light'}
function toggle(){dark.value=!dark.value;try{localStorage.setItem(key,dark.value?'dark':'light')}catch{}apply()}
function systemChange(e){if(!saved()){dark.value=e.matches;apply()}}
apply()
onMounted(()=>media.addEventListener('change',systemChange))
onBeforeUnmount(()=>media.removeEventListener('change',systemChange))
</script>
<template><button type="button" class="theme-switch" role="switch" :aria-checked="dark" :aria-label="dark?'切换浅色主题':'切换深色主题'" :title="dark?'切换浅色主题':'切换深色主题'" :class="{dark}" @click="toggle"><span class="switch-sun" aria-hidden="true">☀</span><span class="switch-moon" aria-hidden="true">☾</span><span class="switch-thumb" aria-hidden="true"><span>{{dark?'☾':'☀'}}</span></span></button></template>
<style>
.theme-switch{position:relative;isolation:isolate;display:flex;align-items:center;justify-content:space-between;width:72px;height:37px;border:1px solid var(--erp-border);border-radius:999px;padding:0 9px;background:var(--erp-subtle);color:var(--erp-muted);flex-shrink:0;box-shadow:inset 0 2px 5px #101a3012;transition:background .24s,border-color .24s}.switch-sun,.switch-moon{font-size:17px;line-height:1}.switch-thumb{position:absolute;display:grid;place-items:center;left:3px;top:3px;width:29px;height:29px;border-radius:50%;background:#fff;color:#e49b27;box-shadow:0 3px 9px #101a3030;transition:transform .44s cubic-bezier(.26,1.65,.47,1),background .25s,color .25s}.switch-thumb span{font-size:20px;line-height:1;transition:transform .44s cubic-bezier(.26,1.65,.47,1)}.theme-switch.dark .switch-thumb{transform:translateX(35px);background:#91b2ff;color:#192947}.theme-switch.dark .switch-thumb span{transform:rotate(-25deg)}.theme-switch:active .switch-thumb{scale:1.16 .86}.theme-switch:focus-visible{outline:2px solid var(--erp-accent);outline-offset:4px}.theme-switch:hover{border-color:var(--erp-accent)}@media(prefers-reduced-motion:reduce){.switch-thumb,.switch-thumb span,.theme-switch{transition:none!important}}
</style>
