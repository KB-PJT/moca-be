import {textOf, unique} from './normalization.mjs';

const TYPES = [
  ['GIFT_CARD', /상품권|선불카드|선불전자지급수단/],
  ['TAX', /국세|지방세|관세|각종\s*세금/],
  ['PUBLIC_FEE', /공과금/],
  ['APARTMENT_MANAGEMENT_FEE', /아파트\s*관리비/],
  ['TUITION', /대학(?:원)?\s*(?:등록금|납입금)/],
  ['INTEREST_FREE_INSTALLMENT', /무이자\s*할부/],
  ['FEE', /각종\s*수수료|할부수수료/],
  ['CASH_ADVANCE', /현금서비스|카드론|단기카드대출|장기카드대출/],
];

export function analyzeExclusions(benefit) {
  const text = textOf(benefit);
  const exclusionContexts = [...text.matchAll(
    /.{0,120}(?:제외|적용\s*불가|미적용|대상(?:이|에서)?\s*아님|적립되지\s*않|할인되지\s*않).{0,300}/g,
  )].map((match) => match[0]).join(' ');
  const transactionTypes = unique(
    TYPES.filter(([, pattern]) => pattern.test(exclusionContexts)).map(([type]) => type),
  );
  const hasUnboundedLanguage =
    /일부\s*(?:매장|가맹점|임대매장)|등의?\s*이용금액|제외\s*상세/.test(exclusionContexts);
  return {
    transactionTypes,
    complete: transactionTypes.length > 0 && !hasUnboundedLanguage,
  };
}
