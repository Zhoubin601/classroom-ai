<template>
  <div class="w-full h-full flex flex-col">
    <div class="flex items-center justify-between mb-3 px-1">
      <div class="flex items-center gap-2.5">
        <div class="w-2.5 h-2.5 rounded-full bg-indigo-600 ring-4 ring-indigo-50"></div>
        <h3 class="text-sm font-bold text-slate-900 tracking-tight">课堂抬头率与注意力流动趋势</h3>
      </div>
      <span class="text-[11px] text-slate-500 font-mono bg-slate-100/80 px-2 py-0.5 rounded-md border border-slate-200/60">
        单位: 抬头率(%) / 在座人数(人)
      </span>
    </div>
    <div ref="chartRef" class="w-full flex-1 min-h-[260px]"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import type { FocusTrendPointVO } from '../api/types'

const props = defineProps<{
  data: FocusTrendPointVO[]
}>()

const chartRef = ref<HTMLDivElement | null>(null)
let myChart: echarts.ECharts | null = null

const initChart = () => {
  if (!chartRef.value) return
  myChart = echarts.init(chartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!myChart) return

  const times = props.data.map(d => d.time)
  const rates = props.data.map(d => d.lookupRate)
  const counts = props.data.map(d => d.presentCount)

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e2e8f0',
      borderWidth: 1,
      padding: [10, 14],
      textStyle: {
        color: '#0f172a',
        fontSize: 12
      },
      extraCssText: 'box-shadow: 0 10px 25px -5px rgba(15, 23, 42, 0.1); border-radius: 12px; backdrop-filter: blur(8px);',
      axisPointer: {
        type: 'cross',
        lineStyle: {
          color: '#cbd5e1',
          type: 'dashed'
        },
        label: {
          backgroundColor: '#334155',
          color: '#ffffff',
          borderRadius: 6
        }
      }
    },
    legend: {
      data: ['抬头率 (%)', '实到人数 (人)'],
      right: '2%',
      top: '0%',
      textStyle: {
        color: '#64748b',
        fontSize: 12,
        fontWeight: 500
      },
      itemWidth: 14,
      itemHeight: 6,
      itemGap: 16
    },
    grid: {
      left: '2%',
      right: '2%',
      bottom: '3%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: times,
      axisLine: {
        lineStyle: {
          color: '#e2e8f0'
        }
      },
      axisTick: { show: false },
      axisLabel: {
        color: '#64748b',
        fontSize: 11,
        fontFamily: 'monospace'
      }
    },
    yAxis: [
      {
        type: 'value',
        name: '抬头率',
        min: 0,
        max: 100,
        position: 'left',
        nameTextStyle: {
          color: '#64748b',
          fontSize: 11,
          align: 'right',
          padding: [0, 8, 0, 0]
        },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: {
          lineStyle: {
            color: '#f1f5f9',
            type: 'dashed'
          }
        },
        axisLabel: {
          color: '#64748b',
          formatter: '{value}%',
          fontSize: 11,
          fontFamily: 'monospace'
        }
      },
      {
        type: 'value',
        name: '在座人数',
        min: 0,
        position: 'right',
        nameTextStyle: {
          color: '#64748b',
          fontSize: 11,
          align: 'left',
          padding: [0, 0, 0, 8]
        },
        splitLine: { show: false },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          color: '#64748b',
          formatter: '{value}人',
          fontSize: 11,
          fontFamily: 'monospace'
        }
      }
    ],
    series: [
      {
        name: '抬头率 (%)',
        type: 'line',
        smooth: true,
        showSymbol: false,
        symbolSize: 6,
        itemStyle: {
          color: '#4f46e5'
        },
        lineStyle: {
          width: 3,
          color: '#4f46e5',
          shadowColor: 'rgba(79, 70, 229, 0.25)',
          shadowBlur: 8,
          shadowOffsetY: 4
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(79, 70, 229, 0.22)' },
            { offset: 1, color: 'rgba(79, 70, 229, 0.0)' }
          ])
        },
        data: rates
      },
      {
        name: '实到人数 (人)',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        showSymbol: false,
        itemStyle: {
          color: '#0284c7'
        },
        lineStyle: {
          width: 2,
          type: 'dashed',
          color: '#0284c7'
        },
        data: counts
      }
    ]
  }

  myChart.setOption(option)
}

watch(() => props.data, () => {
  updateChart()
}, { deep: true })

const handleResize = () => {
  myChart?.resize()
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  myChart?.dispose()
})
</script>
