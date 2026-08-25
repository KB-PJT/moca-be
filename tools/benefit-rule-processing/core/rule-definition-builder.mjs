import {decimalString, positiveNumber} from './normalization.mjs';
import {analyzeConditions} from './condition-analyzer.mjs';

const BENEFIT_TYPES = new Set(['DISCOUNT', 'CASHBACK', 'POINT', 'MILEAGE']);
const CALCULATIONS = new Set(['RATE', 'FIXED', 'PER_SPEND_UNIT', 'PER_USAGE_UNIT']);

function rewardUnit(benefit) {
  if (benefit.benefitType === 'POINT') return 'POINT';
  if (benefit.benefitType === 'MILEAGE') return 'MILE';
  return 'KRW';
}

function calculation(benefit) {
  const basis = String(benefit.rewardBasis ?? '').toUpperCase();
  return CALCULATIONS.has(basis) ? basis : null;
}

function rewardFor(benefit) {
  const type = String(benefit.benefitType ?? '').toUpperCase();
  const mode = calculation(benefit);
  if (!BENEFIT_TYPES.has(type) || !mode) return null;
  const rate = mode === 'RATE' ? decimalString(benefit.rewardRate) : null;
  const value = mode !== 'RATE' ? decimalString(benefit.rewardValue) : null;
  const spendUnitAmount = mode === 'PER_SPEND_UNIT'
    ? decimalString(benefit.spendUnitAmount)
    : null;
  if (mode === 'RATE' && !positiveNumber(rate)) return null;
  if (mode !== 'RATE' && !positiveNumber(value)) return null;
  if (mode === 'PER_SPEND_UNIT' && !positiveNumber(spendUnitAmount)) return null;
  return {
    benefitType: type,
    rewardUnit: rewardUnit(benefit),
    calculation: mode,
    rate,
    value,
    spendUnitAmount,
  };
}

export function buildRuleDefinition(benefit) {
  const reward = rewardFor(benefit);
  if (!reward) return {ruleDefinition: null, unsupportedConditions: ['REWARD_NOT_PARSED']};
  const analyzed = analyzeConditions(benefit);
  return {
    ruleDefinition: {
      schemaVersion: 1,
      conditions: analyzed.conditions,
      reward,
      limits: analyzed.limits,
    },
    unsupportedConditions: analyzed.unsupportedConditions,
    policies: analyzed.policies,
    runtimeRequirements: analyzed.runtimeRequirements,
  };
}
