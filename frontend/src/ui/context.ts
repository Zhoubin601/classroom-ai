import { inject, onUnmounted, ref, watch, type InjectionKey, type Ref } from 'vue'
import { createNavigationCoordinator } from './navigation.mjs'

export type SaveResult = { status: 'success' | 'invalid' | 'conflict' | 'failed' | 'stale' }
export interface Editor { title: string; dirty: () => boolean; discard: () => void; save?: () => Promise<SaveResult> }
export const navigationKey: InjectionKey<ReturnType<typeof createNavigationCoordinator>> = Symbol('navigation')
export const navigationBusyKey: InjectionKey<Ref<boolean>> = Symbol('navigation-busy')
export const notificationKey: InjectionKey<(text: string) => void> = Symbol('notification')
export const confirmKey: InjectionKey<(text: string) => Promise<boolean>> = Symbol('confirmation')
export function useNavigation() { const nav = inject(navigationKey); if (!nav) throw new Error('Navigation provider missing'); return nav }
export function useNavigationBusy() { return inject(navigationBusyKey,ref(false)) }
export function useFeedback() {
  return { notify: inject(notificationKey, (text: string) => console.info(text)), confirm: inject(confirmKey, async () => false) }
}
let editorId = 0
export function useEditGuard(title: string, snapshot: () => any, restore: (value: any) => void, enabled: () => boolean = () => true, save?: () => Promise<SaveResult>) {
  const nav = useNavigation(), baseline = ref(''), initialized = ref(false)
  const stable = (value:any) => JSON.stringify(value,(_key,item)=>item && typeof item==='object' && !Array.isArray(item) ? Object.fromEntries(Object.keys(item).sort().map(key=>[key,item[key]])) : item)
  const serialize = () => stable(snapshot())
  const reset = () => { baseline.value = serialize(); initialized.value = true }
  const dirty = () => enabled() && initialized.value && baseline.value !== serialize()
  const discard = () => { if (initialized.value) restore(JSON.parse(baseline.value)); reset() }
  const unregister = nav.register(`editor-${++editorId}`, {title, dirty, discard, save})
  watch(enabled, active => { if (active) reset(); else initialized.value = false }, {flush:'post', immediate:true})
  onUnmounted(unregister)
  // A successful old request must not mark newer edits as saved.
  const saved = (submitted: string) => { baseline.value = stable(JSON.parse(submitted)); initialized.value = true }
  return {reset, dirty, discard, capture: serialize, saved}
}
export function usePreference<T>(page: string, key: string, fallback: T): Ref<T> {
  const nav = useNavigation(), value = ref(nav.read(page,key,fallback)) as Ref<T>
  watch(value, next => nav.write(page,key,next), {deep:true})
  return value
}

// Safe fallback for forms without a draft endpoint; only discard/cancel are offered.
// Controlled editors are tagged data-edit-managed and use explicit save contracts.
export function useDomEdits(root: Ref<HTMLElement | undefined>, title: string) {
  const nav = useNavigation(); const controls = new Map<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement, {value:string;checked:boolean}>()
  const state = (e:any) => ({value:e.value, checked:!!e.checked})
  const same = (a:any,b:any) => a.value === b.value && a.checked === b.checked
  const dirty = () => [...controls].some(([e,original]) => e.isConnected && !same(state(e),original))
  const capture = (event:Event) => {
    const e = event.target as HTMLInputElement
    if (!e?.matches('input,textarea,select') || e.closest('[data-edit-managed]') || e.closest('[data-filter]') || /搜索|筛选|关键词|历史学期|查看大纲版本|阈值/.test(e.getAttribute('aria-label')||e.getAttribute('placeholder')||'') || e.disabled || e.readOnly) return
    if (!controls.has(e)) controls.set(e,state(e))
  }
  const discard = () => { for (const [e,s] of controls) { if (!e.isConnected) continue; if(e.type==='file') e.value=''; else e.value=s.value; if(e instanceof HTMLInputElement)e.checked=s.checked; e.dispatchEvent(new Event(e.matches('select,input[type=checkbox],input[type=radio]')?'change':'input',{bubbles:true})) } controls.clear() }
  const unregister = nav.register(`dom-${++editorId}`,{title,dirty,discard})
  watch(root,(node,previous) => { for(const event of ['focusin','pointerdown','beforeinput']) {previous?.removeEventListener(event,capture,true);node?.addEventListener(event,capture,true)} },{flush:'post'})
  onUnmounted(()=>{unregister();for(const event of ['focusin','pointerdown','beforeinput'])root.value?.removeEventListener(event,capture,true)})
  return { reset:()=>controls.clear() }
}
