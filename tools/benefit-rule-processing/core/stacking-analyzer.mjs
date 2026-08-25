import {textOf} from './normalization.mjs';

const HIGHEST_ONLY = [
  /높은\s*(?:혜택|적립률|할인율|금액).{0,12}(?:적용|제공)/,
  /(?:혜택|적립|할인).{0,12}중\s*(?:큰|높은)\s*(?:금액|혜택)/,
];
const NOT_STACKABLE = [
  /중복\s*(?:적용|할인|적립)\s*(?:불가|제외|되지\s*않)/,
  /다른\s*(?:할인|적립|혜택).{0,12}(?:중복|동시).{0,8}(?:불가|제외)/,
];
const REPLACE = [
  /기본\s*(?:혜택|적립|할인).{0,16}(?:대신|대체)/,
  /(?:대신|대체).{0,12}(?:적립|할인|제공)/,
];
const ADDITIVE = [
  /추가\s*(?:적립|할인|캐시백|마일리지)/,
  /기본\s*(?:적립|할인).{0,24}\+.{0,24}(?:추가|우대)/,
  /중복\s*(?:적용|할인|적립)\s*(?:가능|제공|됩니다)/,
];

function matchesAny(text, patterns) {
  return patterns.some((pattern) => pattern.test(text));
}

export function analyzeStacking(benefit) {
  const text = textOf(benefit);
  if (matchesAny(text, HIGHEST_ONLY)) return {mode: 'highest_only', confidence: 'HIGH'};
  if (matchesAny(text, NOT_STACKABLE)) return {mode: 'not_stackable', confidence: 'HIGH'};
  if (matchesAny(text, REPLACE)) return {mode: 'replace', confidence: 'MEDIUM'};
  if (matchesAny(text, ADDITIVE)) return {mode: 'additive', confidence: 'HIGH'};
  return {mode: 'standalone', confidence: 'MEDIUM'};
}
