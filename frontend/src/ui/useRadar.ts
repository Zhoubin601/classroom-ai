import {watch,nextTick,onUnmounted,type Ref} from 'vue'
import * as echarts from 'echarts'
export function useRadar(container: Ref<HTMLDivElement | null | undefined>, draw:(chart:echarts.ECharts)=>void) {
 let chart:echarts.ECharts|undefined,observer:ResizeObserver|undefined,epoch=0
 const render=async()=>{const current=++epoch;await nextTick();if(current!==epoch||!container.value||!container.value.clientWidth)return;chart ||= echarts.init(container.value);draw(chart);const option=chart.getOption() as any,values=option.series?.[0]?.data?.[0]?.value,names=option.radar?.[0]?.indicator;container.value.setAttribute('role','img');container.value.setAttribute('aria-label',values?'四维雷达图：'+values.map((value:number,index:number)=>`${names?.[index]?.name} ${value} / ${names?.[index]?.max}`).join('，'):'雷达图缺少有效维度，请查看可用数据表')}
 watch(container,async element=>{epoch++;observer?.disconnect();chart?.dispose();chart=undefined;if(element){observer=new ResizeObserver(()=>{if(element.clientWidth){if(chart)chart.resize();else render()}});observer.observe(element);await render()}},{flush:'post'})
 onUnmounted(()=>{epoch++;observer?.disconnect();chart?.dispose();chart=undefined})
 return {render}
}
