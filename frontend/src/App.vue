<template>
 <LoginView v-if="!currentUser" @login-success="login" />
 <div v-else :class="['app-shell',{collapsed:sidebarCollapsed}]">
  <button v-if="drawer" class="drawer-scrim" aria-label="关闭导航" @click="drawer=false"></button>
  <aside :class="['app-sidebar',{open:drawer}]" aria-label="平台导航" :role="drawer?'dialog':undefined" :aria-modal="drawer?'true':undefined" @keydown="drawerKey">
   <div class="sidebar-brand"><GraduationCap aria-hidden="true"/><span>爱教学<small>教学质量管理平台</small></span></div>
   <button class="sidebar-collapse" :aria-label="sidebarCollapsed?'展开导航':'折叠导航'" @click="sidebarCollapsed=!sidebarCollapsed"><PanelLeft aria-hidden="true"/></button>
   <nav aria-label="工作台导航"><template v-for="group in navGroups" :key="group.label"><p class="nav-group">{{ group.label }}</p><button v-for="item in group.items" :key="item.key" type="button" :disabled="navigationBusy" :aria-label="item.label" :title="item.label" :aria-current="activeTab===item.key?'page':undefined" :class="['sidebar-link',{active:activeTab===item.key}]" @click="navigate(item.key)"><component :is="item.iconComp" aria-hidden="true"/><span>{{ item.label }}</span></button></template></nav>
   <div class="sidebar-foot">东北大学软件学院</div>
  </aside>
  <div class="app-stage">
   <header class="app-topbar" :aria-busy="navigationBusy"><div class="topbar-title"><button class="mobile-menu" aria-label="打开导航" :aria-expanded="drawer" @click="drawer=true"><Menu aria-hidden="true"/></button><h1>{{ pageTitle }}</h1></div><div class="topbar-tools"><span :class="['connection',{offline:!serviceOnline}]" role="status">{{ serviceOnline===null?'检查连接':serviceOnline?'服务在线':'连接中断' }}</span><button class="account-button" :aria-expanded="accountOpen" @click="accountOpen=!accountOpen"><span class="account-avatar">{{ currentUser.realName?.slice(0,1) }}</span><span>{{ currentUser.realName }}</span><ChevronDown aria-hidden="true"/></button></div></header>
   <div v-if="accountOpen" class="account-menu"><b>{{ currentUser.realName }}</b><p>{{ roleName }} · {{ currentUser.department || '未设置部门' }}</p><p v-if="currentUser.authorizedMajors">授权专业：{{ currentUser.authorizedMajors }}</p><details><summary>系统信息</summary><p>Spring Boot · Vue · MySQL · Redis</p><p>{{ currentTime }}</p></details><button @click="logout">退出登录</button></div>
   <main class="app-content" :key="sessionEpoch">
    <Sprint3Workspace v-if="activeTab==='sprint3'" :role="currentUser.role" />
    <DirectorDeskView v-else-if="activeTab==='director'" :department="currentUser.department" />
    <TeacherDeskView v-else-if="activeTab==='teacher'" :logged-in-user="currentUser" />
    <SupervisorDeskView v-else-if="activeTab==='supervisor'" @jump-to-attendance="jumpToAttendance" />
    <AttendanceDashboardView v-else-if="activeTab==='attendance'" :initial-offering-id="targetOfferingId" :logged-in-user="currentUser" />
    <MicroTeachingView v-else-if="activeTab==='micro'" :logged-in-user="currentUser" />
    <StudentManageView v-else-if="activeTab==='students'" />
   </main>
   <footer class="app-footer">爱教学 · 教学质量持续改进</footer>
  </div>
 </div>
 <div v-if="notice" class="ui-toast" role="status"><span>{{ notice }}</span><button aria-label="关闭提示" @click="notice=''">×</button></div>
 <UiModal :open="!!leaveEditor" title="有未保存的修改" unguarded @close="resolveLeave('cancel')"><p>{{ leaveEditor?.title }}尚未保存，离开前请选择处理方式。</p><template #footer><button @click="resolveLeave('cancel')">继续编辑</button><button @click="resolveLeave('discard')">放弃修改</button><button v-if="leaveEditor?.save" class="ui-primary" @click="resolveLeave('save')">保存后离开</button></template></UiModal>
 <UiModal :open="!!confirmation" title="确认操作" unguarded @close="resolveConfirm(false)"><p>{{ confirmation }}</p><template #footer><button @click="resolveConfirm(false)">取消</button><button class="ui-primary" @click="resolveConfirm(true)">确认</button></template></UiModal>
</template>
<script setup lang="ts">
import {ref,computed,provide,onMounted,onUnmounted,watch,nextTick} from 'vue'
import {GraduationCap,Briefcase,BookOpen,ShieldCheck,Video,Users,PanelLeft,Menu,ChevronDown,BarChart3} from 'lucide-vue-next'
import {authApi,visualApi} from './api'
import {createNavigationCoordinator} from './ui/navigation.mjs'
import {navigationKey,navigationBusyKey,notificationKey,confirmKey,type Editor} from './ui/context'
import LoginView from './views/LoginView.vue'
import DirectorDeskView from './views/DirectorDeskView.vue'
import TeacherDeskView from './views/TeacherDeskView.vue'
import SupervisorDeskView from './views/SupervisorDeskView.vue'
import AttendanceDashboardView from './views/AttendanceDashboardView.vue'
import MicroTeachingView from './views/MicroTeachingView.vue'
import StudentManageView from './views/StudentManageView.vue'
import Sprint3Workspace from './components/Sprint3Workspace.vue'
import UiModal from './components/UiModal.vue'
type TabKey='director'|'teacher'|'supervisor'|'sprint3'|'micro'|'attendance'|'students'
const currentUser=ref<any>(null),activeTab=ref<TabKey>('teacher'),targetOfferingId=ref<number|null>(null),sessionEpoch=ref(0),sidebarCollapsed=ref(false),drawer=ref(false),accountOpen=ref(false),serviceOnline=ref<boolean|null>(null),currentTime=ref(''),notice=ref('')
const leaveEditor=ref<Editor>(),confirmation=ref('');let leaveResolve:(value:string)=>void,confirmResolve:(value:boolean)=>void
const navigationBusy=ref(false)
const nav=createNavigationCoordinator((editor:Editor)=>new Promise<string>(resolve=>{leaveEditor.value=editor;leaveResolve=resolve}),state=>{navigationBusy.value=state.pending||state.saving>0})
provide(navigationKey,nav);provide(navigationBusyKey,navigationBusy);provide(notificationKey,text=>{notice.value=text});provide(confirmKey,text=>new Promise<boolean>(resolve=>{confirmation.value=text;confirmResolve=resolve}))
function resolveLeave(choice:string){leaveEditor.value=undefined;leaveResolve?.(choice)}
function resolveConfirm(value:boolean){confirmation.value='';confirmResolve?.(value)}
const allTabs=[{key:'director',label:'教研室主任工作台',iconComp:Briefcase},{key:'teacher',label:'任课教师工作台',iconComp:BookOpen},{key:'supervisor',label:'教学督导工作台',iconComp:ShieldCheck},{key:'sprint3',label:'统计与反馈',iconComp:BarChart3},{key:'micro',label:'微格教学视频',iconComp:Video},{key:'attendance',label:'课堂智能考勤大屏',iconComp:Video},{key:'students',label:'学生人脸档案库',iconComp:Users}]
const allowed:Record<string,string[]>={DIRECTOR:['director','sprint3','micro','attendance','students'],TEACHER:['teacher','sprint3','micro','attendance'],SUPERVISOR:['supervisor','sprint3','micro','attendance'],ADMIN:['sprint3','micro']}
const visibleTabs=computed(()=>allTabs.filter(t=>allowed[currentUser.value?.role]?.includes(t.key)).map(t=>({...t,label:t.key==='sprint3'&&currentUser.value.role==='ADMIN'?'模板管理':t.label})))
const navGroups=computed(()=>[{label:'工作管理',items:visibleTabs.value.filter(t=>['director','teacher','supervisor','sprint3'].includes(t.key))},{label:'教学支持',items:visibleTabs.value.filter(t=>['micro','attendance','students'].includes(t.key))}])
const pageTitle=computed(()=>visibleTabs.value.find(t=>t.key===activeTab.value)?.label||'爱教学'),roleName=computed(()=>({DIRECTOR:'教研室主任',TEACHER:'任课教师',SUPERVISOR:'教学督导',ADMIN:'管理员'}[currentUser.value?.role]||''))
async function navigate(key:string){if(!visibleTabs.value.some(t=>t.key===key)||key===activeTab.value)return;await nav.request(()=>{activeTab.value=key as TabKey;drawer.value=false;accountOpen.value=false;window.scrollTo(0,0)})}
async function jumpToAttendance(id:number){if(!visibleTabs.value.some(t=>t.key==='attendance'))return;await nav.request(()=>{targetOfferingId.value=id;activeTab.value='attendance';drawer.value=false})}
function login(user:any){nav.setIdentity(user);currentUser.value=user;sessionEpoch.value++;activeTab.value=(allowed[user.role]?.[0]||'sprint3') as TabKey;sidebarCollapsed.value=false;notice.value=''}
function invalidate(){nav.clear();resolveLeave('cancel');resolveConfirm(false);currentUser.value=null;sessionEpoch.value++;accountOpen.value=false;drawer.value=false;targetOfferingId.value=null;window.dispatchEvent(new Event('ui:cancel-requests'));notice.value='登录会话已失效，请重新登录'}
async function logout(){await nav.request(async()=>{await visualApi.stopMonitor().catch(()=>{});await authApi.logout().catch(()=>{});invalidate();notice.value=''})}
function beforeUnload(event:BeforeUnloadEvent){if(nav.dirty){event.preventDefault();event.returnValue=''}}
async function health(){try{await authApi.getCsrf();serviceOnline.value=true}catch{serviceOnline.value=false};currentTime.value=new Date().toLocaleTimeString('zh-CN',{hour12:false})}
let healthTimer:ReturnType<typeof setInterval>
let drawerPrevious:HTMLElement|null=null
watch(drawer,async open=>{await nextTick();const stage=document.querySelector<HTMLElement>('.app-stage');if(stage)stage.inert=open;if(open){drawerPrevious=document.activeElement as HTMLElement;document.querySelector<HTMLElement>('.app-sidebar .sidebar-link')?.focus()}else if(drawerPrevious?.isConnected)drawerPrevious.focus()})
function drawerKey(e:KeyboardEvent){if(!drawer.value)return;if(e.key==='Escape'){e.preventDefault();drawer.value=false}if(e.key==='Tab'){const buttons=[...document.querySelectorAll<HTMLButtonElement>('.app-sidebar button')].filter(b=>b.getClientRects().length>0);const index=buttons.indexOf(document.activeElement as HTMLButtonElement);if(e.shiftKey&&index<=0){e.preventDefault();buttons[buttons.length-1]?.focus()}else if(!e.shiftKey&&index===buttons.length-1){e.preventDefault();buttons[0]?.focus()}}}
onMounted(async()=>{window.addEventListener('beforeunload',beforeUnload);window.addEventListener('ui:auth-expired',invalidate);health();healthTimer=setInterval(health,15000);try{await authApi.getCsrf();login(await authApi.getMe())}catch{}})
onUnmounted(()=>{nav.clear();clearInterval(healthTimer);window.removeEventListener('beforeunload',beforeUnload);window.removeEventListener('ui:auth-expired',invalidate)})
</script>
