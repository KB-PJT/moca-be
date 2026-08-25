import {positiveNumber, textOf, unique} from './normalization.mjs';
import {analyzeExclusions} from './exclusion-analyzer.mjs';
import {analyzePaymentChannel} from './payment-channel-analyzer.mjs';
import {analyzePerformanceTiers} from './performance-tier-analyzer.mjs';

const KNOWN = new Set([
  'REWARD_FORMULA',
  'PREVIOUS_MONTH_SPEND',
  'MINIMUM_PAYMENT',
  'MAXIMUM_BENEFIT_BASE',
  'DAILY_USAGE_LIMIT',
  'MONTHLY_USAGE_LIMIT',
  'NEW_MEMBER_GRACE_PERIOD',
  'WEEKEND',
  'PERFORMANCE_TIER',
  'EXCLUSIONS',
  'CAPTURE_ORDER',
]);

const REASONS = {
  PAYMENT_AMOUNT: 'MIN_PAYMENT_NOT_MET',
  PREVIOUS_MONTH_SPEND: 'PERFORMANCE_NOT_MET',
  USED_DAILY_COUNT: 'FREQUENCY_LIMIT_EXHAUSTED',
  USED_MONTHLY_COUNT: 'FREQUENCY_LIMIT_EXHAUSTED',
  DAY_OF_WEEK: 'CONDITION_NOT_MET',
  APPROVED_TIME: 'CONDITION_NOT_MET',
  NEW_MEMBER_GRACE: 'PERFORMANCE_NOT_MET',
};

function condition(type, operator, value = null, values = []) {
  return {type, operator, value, values, rejectionReason: REASONS[type] ?? 'CONDITION_NOT_MET'};
}

function parseHour(hour, meridiem) {
  let value = Number(hour);
  if (!Number.isInteger(value) || value < 0 || value > 24) return null;
  if (meridiem === '오후' && value < 12) value += 12;
  if (meridiem === '오전' && value === 12) value = 0;
  if (value === 24) value = 0;
  return `${String(value).padStart(2, '0')}:00`;
}

function approvedTime(text) {
  const match = text.match(/(오전|오후)?\s*(\d{1,2})시(?:부터|\s*[~～-]\s*)(오전|오후)?\s*(\d{1,2})시(?:까지)?/);
  if (!match) return null;
  const start = parseHour(match[2], match[1]);
  const end = parseHour(match[4], match[3] ?? match[1]);
  return start && end ? [start, end] : null;
}

function weekendOnly(text) {
  if (/주중\s*\/\s*주말\s*상관없이|평일.{0,8}주말.{0,8}(?:모두|동일)/.test(text)) return false;
  return /주말\s*(?:에만|한정|이용|결제)|토요일.{0,8}일요일|토\/일/.test(text);
}

export function analyzeConditions(benefit) {
  const all = [];
  const any = [];
  const none = [];
  const limits = [];
  const unsupported = [];
  const recognized = new Set(benefit.recognizedConditions ?? []);
  const text = textOf(benefit);

  const minimum = positiveNumber(benefit.minimumPaymentAmount);
  if (minimum) all.push(condition('PAYMENT_AMOUNT', 'GTE', String(minimum)));

  const previousSpend = positiveNumber(benefit.requiredPreviousMonthSpend);
  if (previousSpend) {
    const spendCondition = condition('PREVIOUS_MONTH_SPEND', 'GTE', String(previousSpend));
    if (benefit.newMemberGracePeriod || recognized.has('NEW_MEMBER_GRACE_PERIOD')) {
      any.push(spendCondition, condition('NEW_MEMBER_GRACE', 'EQ', 'true'));
    } else {
      all.push(spendCondition);
    }
  }

  const transactionBase = positiveNumber(benefit.maximumBenefitBaseAmount);
  if (transactionBase) limits.push({type: 'TRANSACTION_BENEFIT_BASE', value: String(transactionBase)});

  const daily = positiveNumber(benefit.dailyUsageLimit);
  if (daily) limits.push({type: 'DAILY_USAGE_COUNT', value: String(Math.trunc(daily))});

  const monthly = positiveNumber(benefit.monthlyUsageLimit);
  if (monthly) limits.push({type: 'MONTHLY_USAGE_COUNT', value: String(Math.trunc(monthly))});

  const time = approvedTime(text);
  if (time) all.push(condition('APPROVED_TIME', 'BETWEEN', null, time));

  if (recognized.has('WEEKEND') && weekendOnly(text)) {
    all.push(condition('DAY_OF_WEEK', 'IN', null, ['SATURDAY', 'SUNDAY']));
  }

  const exclusions = recognized.has('EXCLUSIONS')
    ? analyzeExclusions(benefit)
    : {transactionTypes: [], complete: true};
  const paymentChannel = analyzePaymentChannel(benefit);
  const performanceTiers = recognized.has('PERFORMANCE_TIER')
    ? analyzePerformanceTiers(benefit)
    : {tiers: [], complete: true};
  if (benefit.paymentChannelEligibilityRequired) {
    all.push(condition('PAYMENT_CHANNEL_ELIGIBLE', 'EQ', 'true'));
  }
  if (exclusions.transactionTypes.length > 0) {
    none.push({
      type: 'TRANSACTION_TYPE',
      operator: 'IN',
      value: null,
      values: exclusions.transactionTypes,
      rejectionReason: 'MERCHANT_NOT_ELIGIBLE',
    });
  }

  for (const name of recognized) {
    if (KNOWN.has(name)) continue;
    if (name === 'TIME_WINDOW' && time) continue;
    unsupported.push(name);
  }

  if (recognized.has('PERFORMANCE_TIER') && !performanceTiers.complete) {
    unsupported.push('PERFORMANCE_TIER_NOT_PARSED');
  }
  if (recognized.has('EXCLUSIONS') && !exclusions.complete) {
    unsupported.push('EXCLUSIONS_NOT_PARSED');
  }
  if (recognized.has('CAPTURE_ORDER')) unsupported.push('CAPTURE_ORDER');
  if (benefit.paymentChannelEligibilityRequired && !paymentChannel.complete) {
    unsupported.push('PAYMENT_CHANNEL_NOT_PARSED');
  }

  const runtimeRequirements = [];
  if (exclusions.transactionTypes.length > 0) runtimeRequirements.push('TRANSACTION_TYPE');
  if (benefit.paymentChannelEligibilityRequired) runtimeRequirements.push('PAYMENT_CHANNEL');
  if (performanceTiers.tiers.length > 0) runtimeRequirements.push('LIMIT_TIER_POLICY');

  return {
    conditions: {all, any, none},
    limits,
    unsupportedConditions: unique(unsupported),
    policies: {exclusions, paymentChannel, performanceTiers},
    runtimeRequirements: unique(runtimeRequirements),
  };
}
