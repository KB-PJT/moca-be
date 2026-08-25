import {analyzeStacking} from './stacking-analyzer.mjs';
import {buildRuleDefinition} from './rule-definition-builder.mjs';
import {validateRuleDefinition} from './dsl-validator.mjs';

const NON_MONETARY_MODES = new Set(['INFORMATION_ONLY', 'NON_MONETARY']);
const EXCLUDED_MODES = new Set(['ONLINE_OR_INDIRECT_EXCLUDED', 'EXCLUDED']);

export function processBenefit(benefit) {
  const identity = {
    cardId: String(benefit.cardId),
    cardName: benefit.cardName,
    benefitIndex: benefit.benefitIndex,
    benefitTitle: benefit.benefitTitle,
  };
  if (NON_MONETARY_MODES.has(benefit.mode)) {
    return {...identity, status: 'NON_MONETARY', reason: benefit.classificationReason};
  }
  if (EXCLUDED_MODES.has(benefit.mode)) {
    return {...identity, status: 'EXCLUDED', reason: benefit.classificationReason};
  }
  if (benefit.mode !== 'DIRECT_OFFLINE_CALCULABLE') {
    return {...identity, status: 'PARSE_FAILED', reason: benefit.mode ?? 'UNKNOWN_MODE'};
  }

  const stacking = analyzeStacking(benefit);
  const built = buildRuleDefinition(benefit);
  if (!built.ruleDefinition) {
    return {
      ...identity,
      status: 'PARSE_FAILED',
      stackingMode: stacking.mode,
      reason: built.unsupportedConditions.join(','),
    };
  }
  const validationErrors = validateRuleDefinition(built.ruleDefinition);
  if (validationErrors.length > 0) {
    return {
      ...identity,
      status: 'PARSE_FAILED',
      stackingMode: stacking.mode,
      reason: validationErrors.join(','),
    };
  }
  const unsupported = built.unsupportedConditions;
  const pendingRuntime = built.runtimeRequirements;
  return {
    ...identity,
    status: unsupported.length === 0 && pendingRuntime.length === 0 ? 'STRUCTURED' : 'PARTIAL',
    stackingMode: stacking.mode,
    stackingConfidence: stacking.confidence,
    ruleDefinition: built.ruleDefinition,
    unsupportedConditions: unsupported,
    pendingRuntimeRequirements: pendingRuntime,
    policies: built.policies,
    sourceUrl: benefit.sourceUrl,
  };
}

export function summarize(results) {
  const statuses = {};
  const stackingModes = {};
  for (const result of results) {
    statuses[result.status] = (statuses[result.status] ?? 0) + 1;
    if (result.stackingMode) {
      stackingModes[result.stackingMode] = (stackingModes[result.stackingMode] ?? 0) + 1;
    }
  }
  return {total: results.length, statuses, stackingModes};
}
