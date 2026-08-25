import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {processBenefit, summarize} from './core/benefit-processor.mjs';
import {generateSql} from './core/sql-generator.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const input = path.resolve(
  process.argv.find((value) => value.startsWith('--input='))?.slice('--input='.length)
    ?? path.join(root, 'src/test/resources/benefit/card-benefit-detail-cases-1206.json'),
);
const outputDirectory = path.resolve(
  process.argv.find((value) => value.startsWith('--output-dir='))?.slice('--output-dir='.length)
    ?? path.join(root, 'tmp/benefit-rule-processing'),
);

const fixture = JSON.parse(fs.readFileSync(input, 'utf8'));
if (!Array.isArray(fixture.benefits)) throw new Error('fixture.benefits 배열이 필요합니다.');

const results = fixture.benefits.map(processBenefit);
const report = {
  generatedAt: new Date().toISOString(),
  source: path.relative(root, input),
  summary: summarize(results),
  results,
};

fs.mkdirSync(outputDirectory, {recursive: true});
fs.writeFileSync(
  path.join(outputDirectory, 'complex-benefit-rules.json'),
  `${JSON.stringify(report, null, 2)}\n`,
);
fs.writeFileSync(
  path.join(outputDirectory, 'complex-benefit-rules.sql'),
  generateSql(results),
);

console.log(JSON.stringify(report.summary, null, 2));
