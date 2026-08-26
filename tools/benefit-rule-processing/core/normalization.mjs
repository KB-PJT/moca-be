export function textOf(benefit) {
  return [benefit.benefitTitle, benefit.benefitDescription, benefit.detailText]
    .filter(Boolean)
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function positiveNumber(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

export function decimalString(value) {
  if (value === null || value === undefined || value === '') return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return null;
  return String(Math.round(parsed * 1000000) / 1000000);
}

export function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

export function sqlString(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}
