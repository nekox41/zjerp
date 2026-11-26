import { createApp } from 'vue'
import App from './App.vue'
import './style.css'

let theme
try{theme=localStorage.getItem('erp-next-theme')}catch{}
document.documentElement.dataset.erpTheme=theme==='dark'||(theme!=='light'&&matchMedia('(prefers-color-scheme: dark)').matches)?'dark':'light'

createApp(App).mount('#next-app')