import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
import { applyTheme, initialTheme } from './utils/theme'

// 挂载前确定主题（index.html 里的内联脚本已先加上 dark 类，这里补齐 color-scheme 等属性）
applyTheme(initialTheme())

createApp(App).use(ElementPlus, { locale: zhCn }).mount('#app')
