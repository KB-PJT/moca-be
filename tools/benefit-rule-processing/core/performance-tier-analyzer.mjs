import {parseKoreanAmount} from './korean-amount.mjs';
import {textOf, unique} from './normalization.mjs';

const TIER_PATTERN = /(\d[\d,]*(?:\.\d+)?)\s*(백만|만|천)?원\s*(?:이상)?(?:\s*[~～-]\s*(\d[\d,]*(?:\.\d+)?)\s*(백만|만|천)?원?\s*(?:미만)?)?\s*[:：]\s*(\d[\d,]*(?:\.\d+)?)\s*(백만|만|천)?원/g;

export function analyzePerformanceTiers(benefit) {
  const tiers = [];
  for (const match of textOf(benefit).matchAll(TIER_PATTERN)) {
    const minimumSpend = parseKoreanAmount(match[1], match[2]);
    const maximumSpendExclusive = match[3] ? parseKoreanAmount(match[3], match[4]) : null;
    const limitValue = parseKoreanAmount(match[5], match[6]);
    if (minimumSpend && limitValue) {
      tiers.push({minimumSpend, maximumSpendExclusive, limitValue});
    }
  }
  const deduplicated = unique(tiers.map((tier) => JSON.stringify(tier))).map(JSON.parse);
  return {
    tiers: deduplicated.sort((left, right) => left.minimumSpend - right.minimumSpend),
    complete: deduplicated.length >= 2,
  };
}
