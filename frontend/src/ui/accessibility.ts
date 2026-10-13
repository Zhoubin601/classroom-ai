// Associate legacy visible labels as well as fields added by conditional panels.
// Components with explicit labels/ARIA retain their own names.
export function installFieldLabels(root:HTMLElement) {
 let serial=0,scheduled=false
 const update=()=>{scheduled=false;root.querySelectorAll<HTMLInputElement|HTMLSelectElement|HTMLTextAreaElement>('input:not([type=hidden]),select,textarea').forEach(field=>{
  if(field.labels?.length||field.hasAttribute('aria-label')||field.hasAttribute('aria-labelledby'))return
  let parent=field.parentElement,label:HTMLLabelElement|undefined
  for(let i=0;i<3&&parent;i++,parent=parent.parentElement){const choices=[...parent.querySelectorAll<HTMLLabelElement>('label:not([for])')].filter(l=>!l.querySelector('input,select,textarea'));if(choices.length===1){label=choices[0];break}if(parent.querySelectorAll('input,select,textarea').length>1)break}
  if(label){field.id ||= `ui-field-${++serial}`;label.htmlFor=field.id}
  else if(field.getAttribute('placeholder'))field.setAttribute('aria-label',field.getAttribute('placeholder')!)
 })}
 const observer=new MutationObserver(()=>{if(!scheduled){scheduled=true;queueMicrotask(update)}});observer.observe(root,{childList:true,subtree:true});update();return ()=>observer.disconnect()
}
