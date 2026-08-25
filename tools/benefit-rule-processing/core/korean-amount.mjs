const UNITS = new Map([['천', 1_000], ['만', 10_000], ['백만', 1_000_000]]);

export function parseKoreanAmount(numberText, unitText = '') {
  const value = Number(String(numberText).replaceAll(',', ''));
  if (!Number.isFinite(value)) return null;
  return Math.round(value * (UNITS.get(unitText) ?? 1));
}
