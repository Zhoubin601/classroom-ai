export function validateWeights(values) {
  const scaled = values.map(value => {
    const text = String(value ?? '').trim();
    if (!/^(0|[1-9]\d*)(\.\d{1,3})?$/.test(text)) return null;
    const [whole, fraction = ''] = text.split('.');
    const units = Number(whole) * 1000 + Number(fraction.padEnd(3, '0'));
    return Number.isSafeInteger(units) && units <= 100000 ? units : null;
  });
  if (values.length !== 4 || scaled.some(value => value === null))
    return { valid: false, total: null, message: '四维权重须为0～100，最多三位小数' };
  const units = scaled.reduce((sum, value) => sum + value, 0);
  return { valid: units === 100000, total: units / 1000, message: units === 100000 ? '' : '四维权重必须精确合计100%' };
}
