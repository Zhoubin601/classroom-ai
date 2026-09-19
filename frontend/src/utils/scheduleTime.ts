// 高校标准节次与排课时段定义
export interface PeriodSlotDefinition {
  startHour: number
  startMinute: number
  endHour: number
  endMinute: number
  label: string
  slotKey: string
}

export const PERIOD_SLOT_MAP: Record<string, PeriodSlotDefinition> = {
  '1-2': { startHour: 8, startMinute: 0, endHour: 9, endMinute: 35, label: '第1-2节 (08:00 - 09:35)', slotKey: '1-2' },
  '3-4': { startHour: 10, startMinute: 5, endHour: 11, endMinute: 40, label: '第3-4节 (10:05 - 11:40)', slotKey: '3-4' },
  '5-6': { startHour: 13, startMinute: 30, endHour: 15, endMinute: 5, label: '第5-6节 (13:30 - 15:05)', slotKey: '5-6' },
  '7-8': { startHour: 15, startMinute: 35, endHour: 17, endMinute: 10, label: '第7-8节 (15:35 - 17:10)', slotKey: '7-8' },
  '9-10': { startHour: 18, startMinute: 30, endHour: 20, endMinute: 5, label: '第9-10节 (18:30 - 20:05)', slotKey: '9-10' }
}

const WEEKDAY_NAMES = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']

/**
 * 校验排课是否处于当前有效授课时段 (可提前 15 分钟入场督导)
 */
export function isScheduleInSession(
  schedule: { dayOfWeek: number; startPeriod: number; endPeriod: number },
  now = new Date(),
  earlyMinutes = 15
): {
  inSession: boolean
  dayOfWeek: number
  currentDayName: string
  periodRangeText: string
  reason?: string
} {
  if (!schedule) {
    return { inSession: false, dayOfWeek: 0, currentDayName: '', periodRangeText: '', reason: '无排课时段信息' }
  }

  const currentDay = now.getDay() === 0 ? 7 : now.getDay()
  const currentDayName = WEEKDAY_NAMES[currentDay] || ''
  const schedDayName = WEEKDAY_NAMES[schedule.dayOfWeek] || ''
  const periodRangeText = `${schedDayName} 第${schedule.startPeriod}-${schedule.endPeriod}节`

  if (schedule.dayOfWeek !== currentDay) {
    return {
      inSession: false,
      dayOfWeek: currentDay,
      currentDayName,
      periodRangeText,
      reason: `该课程授课日为${schedDayName}，当前为${currentDayName}，非授课日期`
    }
  }

  const slotKey = `${schedule.startPeriod}-${schedule.endPeriod}`
  let startMinutes = 8 * 60
  let endMinutes = 20 * 60

  if (PERIOD_SLOT_MAP[slotKey]) {
    const slot = PERIOD_SLOT_MAP[slotKey]
    startMinutes = slot.startHour * 60 + slot.startMinute
    endMinutes = slot.endHour * 60 + slot.endMinute
  } else {
    // 兜底估算
    if (schedule.startPeriod <= 2) startMinutes = 8 * 60
    else if (schedule.startPeriod <= 4) startMinutes = 10 * 60 + 5
    else if (schedule.startPeriod <= 6) startMinutes = 13 * 60 + 30
    else if (schedule.startPeriod <= 8) startMinutes = 15 * 60 + 35
    else startMinutes = 18 * 60 + 30

    if (schedule.endPeriod <= 2) endMinutes = 9 * 60 + 35
    else if (schedule.endPeriod <= 4) endMinutes = 11 * 60 + 40
    else if (schedule.endPeriod <= 6) endMinutes = 15 * 60 + 5
    else if (schedule.endPeriod <= 8) endMinutes = 17 * 60 + 10
    else endMinutes = 20 * 60 + 5
  }

  const currentMinutes = now.getHours() * 60 + now.getMinutes()
  const inWindow = currentMinutes >= (startMinutes - earlyMinutes) && currentMinutes <= (endMinutes + 10)

  if (!inWindow) {
    const startStr = `${String(Math.floor(startMinutes / 60)).padStart(2, '0')}:${String(startMinutes % 60).padStart(2, '0')}`
    const endStr = `${String(Math.floor(endMinutes / 60)).padStart(2, '0')}:${String(endMinutes % 60).padStart(2, '0')}`
    const nowStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
    return {
      inSession: false,
      dayOfWeek: currentDay,
      currentDayName,
      periodRangeText,
      reason: `该课程排课时段为 ${startStr}-${endStr}，当前时间为 ${nowStr}，尚未开课或已下课`
    }
  }

  return {
    inSession: true,
    dayOfWeek: currentDay,
    currentDayName,
    periodRangeText
  }
}
