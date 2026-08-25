const VALUES = {
  benefitTypes: new Set(['DISCOUNT', 'CASHBACK', 'POINT', 'MILEAGE']),
  rewardUnits: new Set(['KRW', 'POINT', 'MILE']),
  calculations: new Set(['RATE', 'FIXED', 'PER_SPEND_UNIT', 'PER_USAGE_UNIT']),
  limits: new Set(['TRANSACTION_BENEFIT_BASE', 'DAILY_USAGE_COUNT', 'MONTHLY_USAGE_COUNT']),
  numericConditions: new Set([
    'PAYMENT_AMOUNT', 'PREVIOUS_MONTH_SPEND', 'USED_DAILY_COUNT', 'USED_MONTHLY_COUNT',
  ]),
  booleanConditions: new Set([
    'FOREIGN_TRANSACTION', 'NEW_MEMBER_GRACE', 'MERCHANT_ELIGIBLE',
    'PAYMENT_CHANNEL_ELIGIBLE',
  ]),
  targetConditions: new Set(['MERCHANT', 'MERCHANT_CATEGORY', 'TRANSACTION_TYPE']),
};

function presentNumber(value) {
  return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value));
}

function validateCondition(condition) {
  if (VALUES.numericConditions.has(condition.type)) {
    return ['GT', 'GTE', 'LT', 'LTE', 'EQ'].includes(condition.operator)
      && presentNumber(condition.value);
  }
  if (VALUES.booleanConditions.has(condition.type)) {
    return condition.operator === 'EQ' && ['true', 'false'].includes(condition.value);
  }
  if (VALUES.targetConditions.has(condition.type)) {
    return condition.operator === 'EQ'
      ? typeof condition.value === 'string' && condition.value.length > 0
      : condition.operator === 'IN' && condition.values.length > 0;
  }
  if (condition.type === 'DAY_OF_WEEK') {
    return condition.operator === 'IN' && condition.values.length > 0
      && condition.values.every((value) => [
        'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
      ].includes(value));
  }
  if (condition.type === 'APPROVED_TIME') {
    return condition.operator === 'BETWEEN' && condition.values.length === 2
      && condition.values.every((value) => /^([01]\d|2[0-3]):[0-5]\d$/.test(value));
  }
  return false;
}

export function validateRuleDefinition(definition) {
  const errors = [];
  const reward = definition?.reward;
  if (definition?.schemaVersion !== 1) errors.push('SCHEMA_VERSION');
  if (!VALUES.benefitTypes.has(reward?.benefitType)) errors.push('BENEFIT_TYPE');
  if (!VALUES.rewardUnits.has(reward?.rewardUnit)) errors.push('REWARD_UNIT');
  if (!VALUES.calculations.has(reward?.calculation)) errors.push('CALCULATION');
  if (reward?.calculation === 'RATE' && !presentNumber(reward.rate)) errors.push('REWARD_RATE');
  if (reward?.calculation !== 'RATE' && !presentNumber(reward?.value)) errors.push('REWARD_VALUE');
  if (reward?.calculation === 'PER_SPEND_UNIT' && Number(reward.spendUnitAmount) <= 0) {
    errors.push('SPEND_UNIT_AMOUNT');
  }
  for (const group of ['all', 'any', 'none']) {
    if (!Array.isArray(definition?.conditions?.[group])) {
      errors.push(`CONDITIONS_${group.toUpperCase()}`);
      continue;
    }
    definition.conditions[group].forEach((item) => {
      if (!validateCondition(item)) errors.push(`CONDITION_${item.type ?? 'UNKNOWN'}`);
    });
  }
  if (!Array.isArray(definition?.limits)) {
    errors.push('LIMITS');
  } else {
    definition.limits.forEach((limit) => {
      if (!VALUES.limits.has(limit.type) || !presentNumber(limit.value)) {
        errors.push(`LIMIT_${limit.type ?? 'UNKNOWN'}`);
      }
    });
  }
  return [...new Set(errors)];
}
