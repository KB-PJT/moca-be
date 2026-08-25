import {sqlString} from './normalization.mjs';

function jsonSql(value) {
  return `CAST(${sqlString(JSON.stringify(value))} AS JSON)`;
}

export function generateSql(results) {
  const calculable = results.filter((result) => result.status === 'STRUCTURED');
  const statements = calculable.map((result) => `UPDATE benefit_rules rule_data
INNER JOIN benefit_offers offer ON offer.offer_id = rule_data.offer_id
INNER JOIN card_benefits benefit ON benefit.benefit_id = offer.benefit_id
INNER JOIN card_content_versions version ON version.content_version_id = benefit.content_version_id
INNER JOIN cards card ON card.card_id = version.card_id
SET rule_data.rule_definition_json = ${jsonSql(result.ruleDefinition)},
    rule_data.rule_schema_version = 1,
    rule_data.rule_support_status = ${sqlString(result.status === 'STRUCTURED' ? 'SUPPORTED' : 'PARTIAL')},
    rule_data.stacking_mode = ${sqlString(result.stackingMode)},
    offer.stacking_mode = ${sqlString(result.stackingMode)},
    rule_data.updated_at = UTC_TIMESTAMP(6),
    offer.updated_at = UTC_TIMESTAMP(6)
WHERE card.gorilla_card_id = ${sqlString(result.cardId)}
  AND benefit.position = ${Number(result.benefitIndex)}
  AND NOT EXISTS (
      SELECT 1 FROM card_content_versions newer
      WHERE newer.card_id = version.card_id
        AND (newer.last_seen_at > version.last_seen_at
             OR (newer.last_seen_at = version.last_seen_at
                 AND newer.content_version_id > version.content_version_id))
  );`);

  return `-- Generated complex benefit rule candidates.
-- Review before promoting to a Flyway migration or seed.
-- Only STRUCTURED rows are emitted. PARTIAL rows remain review-only in the JSON report.

${statements.join('\n\n')}
`;
}
