<template>
  <div class="w-full h-full flex flex-col">
    <div class="flex items-center justify-between mb-3">
      <div class="flex items-center gap-2">
        <div class="w-2 h-2 rounded-full bg-indigo-600"></div>
        <h3 class="text-sm font-semibold text-slate-800 tracking-normal">课堂抬头率与注意力流动趋势</h3>
      </div>
      <span class="text-xs text-slate-500 font-mono">单位: 抬头率(%) / 在座人数(人)</span>
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
      backgroundColor: '#ffffff',
      borderColor: '#e2e8f0',
      borderWidth: 1,
      padding: [8, 12],
      textStyle: {
        color: '#0f172a',
        fontSize: 12
      },
      extraCssText: 'box-shadow: 0 4px 12px -2px rgba(0, 0, 0, 0.08); border-radius: 8px;',
      axisPointer: {
        type: 'cross',
        lineStyle: {
          color: '#cbd5e1',
          type: 'dashed'
        },
        label: {
          backgroundColor: '#475569',
          color: '#ffffff'
        }
      }
    },
    legend: {
      data: ['抬头率 (%)', '实到人数 (人)'],
      right: '4%',
      top: '0%',
      textStyle: {
        color: '#64748b',
        fontSize: 12
      },
      itemWidth: 12,
      itemHeight: 8
    },
    grid: {
      left: '3%',
      right: '4%',
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
      axisLabel: {
        color: '#64748b',
        fontSize: 11
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
          fontSize: 11
        },
        axisLine: {
          show: false
        },
        splitLine: {
          lineStyle: {
            color: '#f1f5f9',
            type: 'dashed'
          }
        },
        axisLabel: {
          color: '#64748b',
          formatter: '{value}%',
          fontSize: 11
        }
      },
      {
        type: 'value',
        name: '在座人数',
        min: 0,
        position: 'right',
        nameTextStyle: {
          color: '#64748b',
          fontSize: 11
        },
        splitLine: {
          show: false
        },
        axisLabel: {
          color: '#64748b',
          formatter: '{value}人',
          fontSize: 11
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
          width: 2.5,
          color: '#4f46e5'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(79, 70, 229, 0.15)' },
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

