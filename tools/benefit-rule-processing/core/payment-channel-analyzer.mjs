import {textOf} from './normalization.mjs';

export function analyzePaymentChannel(benefit) {
  const text = textOf(benefit);
  if (/오프라인\s*(?:결제|이용)?건?에\s*한/.test(text)) return {mode: 'OFFLINE_ONLY', complete: true};
  if (/공식\s*(?:홈페이지|웹|앱).{0,18}(?:결제|접속).{0,8}한/.test(text)) {
    return {mode: 'OFFICIAL_WEB_OR_APP_ONLY', complete: true};
  }
  if (/온라인|앱\s*결제|간편결제|자동납부/.test(text)) return {mode: 'SPECIAL_CHANNEL', complete: false};
  return {mode: null, complete: !benefit.paymentChannelEligibilityRequired};
}
