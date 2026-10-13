import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import {installFieldLabels} from './ui/accessibility'

const app = createApp(App)
app.mount('#app')
installFieldLabels(document.body)
