import assert from 'node:assert/strict';
import test from 'node:test';
import {processBenefit} from '../core/benefit-processor.mjs';

function benefit(overrides = {}) {
  return {
    cardId: '360',
    cardName: '테스트 카드',
    benefitIndex: 1,
    benefitTitle: '생활 영역 추가적립',
    benefitDescription: '전월 30만원 이상 생활 영역 추가적립 0.3%',
    detailText: '일 1회, 월 5회, 1회 이용금액 10,000원까지 추가적립',
    mode: 'DIRECT_OFFLINE_CALCULABLE',
    benefitType: 'POINT',
    rewardBasis: 'RATE',
    rewardRate: '0.003',
    requiredPreviousMonthSpend: '300000',
    maximumBenefitBaseAmount: '10000',
    dailyUsageLimit: 1,
    monthlyUsageLimit: 5,
    recognizedConditions: [
      'REWARD_FORMULA', 'PREVIOUS_MONTH_SPEND', 'MAXIMUM_BENEFIT_BASE',
      'DAILY_USAGE_LIMIT', 'MONTHLY_USAGE_LIMIT',
    ],
    ...overrides,
  };
}

test('추가 적립과 횟수 한도를 JSON DSL과 additive로 구조화한다', () => {
  const result = processBenefit(benefit());
  assert.equal(result.status, 'STRUCTURED');
  assert.equal(result.stackingMode, 'additive');
  assert.equal(result.ruleDefinition.reward.rate, '0.003');
  assert.deepEqual(result.ruleDefinition.limits, [
    {type: 'TRANSACTION_BENEFIT_BASE', value: '10000'},
    {type: 'DAILY_USAGE_COUNT', value: '1'},
    {type: 'MONTHLY_USAGE_COUNT', value: '5'},
  ]);
});

test('신규 발급 유예는 전월 실적과 OR 조건으로 구조화한다', () => {
  const result = processBenefit(benefit({newMemberGracePeriod: true}));
  assert.deepEqual(result.ruleDefinition.conditions.any.map((item) => item.type), [
    'PREVIOUS_MONTH_SPEND', 'NEW_MEMBER_GRACE',
  ]);
});

test('높은 혜택 적용 문구는 highest_only로 판정한다', () => {
  const result = processBenefit(benefit({
    benefitDescription: '기본 적립과 특별 적립 중 높은 혜택을 적용',
    detailText: '',
  }));
  assert.equal(result.stackingMode, 'highest_only');
});

test('제외 조건은 거래 유형 정책으로 추출하고 런타임 입력 대기로 보존한다', () => {
  const result = processBenefit(benefit({recognizedConditions: ['REWARD_FORMULA', 'EXCLUSIONS']}));
  assert.equal(result.status, 'PARTIAL');
  assert.deepEqual(result.unsupportedConditions, ['EXCLUSIONS_NOT_PARSED']);
});

test('실적 구간은 단일 룰로 축약하지 않고 PARTIAL로 보존한다', () => {
  const result = processBenefit(benefit({
    recognizedConditions: ['REWARD_FORMULA', 'PERFORMANCE_TIER'],
  }));
  assert.equal(result.status, 'PARTIAL');
  assert.deepEqual(result.unsupportedConditions, ['PERFORMANCE_TIER_NOT_PARSED']);
});

test('결제 채널 조건은 DSL과 채널 정책을 함께 생성한다', () => {
  const result = processBenefit(benefit({
    detailText: '공식 홈페이지/앱을 통한 결제건에 한함',
    paymentChannelEligibilityRequired: true,
  }));
  assert.equal(result.status, 'PARTIAL');
  assert.equal(result.policies.paymentChannel.mode, 'OFFICIAL_WEB_OR_APP_ONLY');
  assert.equal(result.ruleDefinition.conditions.all.at(-1).type, 'PAYMENT_CHANNEL_ELIGIBLE');
  assert.deepEqual(result.pendingRuntimeRequirements, ['PAYMENT_CHANNEL']);
});

test('실적별 월 한도 구간을 관계형 tier 후보로 추출한다', () => {
  const result = processBenefit(benefit({
    detailText: '전월실적 30만원~50만원 : 3천원\n전월실적 50만원~100만원 : 7천원\n전월실적 100만원 이상 : 1만원',
    recognizedConditions: ['REWARD_FORMULA', 'PERFORMANCE_TIER'],
  }));
  assert.deepEqual(result.policies.performanceTiers.tiers, [
    {minimumSpend: 300000, maximumSpendExclusive: 500000, limitValue: 3000},
    {minimumSpend: 500000, maximumSpendExclusive: 1000000, limitValue: 7000},
    {minimumSpend: 1000000, maximumSpendExclusive: null, limitValue: 10000},
  ]);
  assert.deepEqual(result.pendingRuntimeRequirements, ['LIMIT_TIER_POLICY']);
});

test('주말 전용 문구는 토요일과 일요일 조건으로 구조화한다', () => {
  const result = processBenefit(benefit({
    benefitDescription: '주말에만 생활 영역 추가적립 0.3%',
    detailText: '',
    recognizedConditions: ['REWARD_FORMULA', 'WEEKEND'],
  }));
  assert.deepEqual(result.ruleDefinition.conditions.all.at(-1), {
    type: 'DAY_OF_WEEK',
    operator: 'IN',
    value: null,
    values: ['SATURDAY', 'SUNDAY'],
    rejectionReason: 'CONDITION_NOT_MET',
  });
});

test('금전 산식을 파싱하지 못하면 PARSE_FAILED로 분류한다', () => {
  const result = processBenefit(benefit({rewardRate: '0'}));
  assert.equal(result.status, 'PARSE_FAILED');
});

test('정보성 혜택은 NON_MONETARY로 분류한다', () => {
  const result = processBenefit(benefit({mode: 'INFORMATION_ONLY'}));
  assert.equal(result.status, 'NON_MONETARY');
});
