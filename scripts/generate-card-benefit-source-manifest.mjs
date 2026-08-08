import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const sourceArgument = process.argv[2];
if (!sourceArgument) {
    throw new Error("사용법: node scripts/generate-card-benefit-source-manifest.mjs <card-gorilla-json>");
}

const sourcePath = path.resolve(sourceArgument);
const sourceBuffer = fs.readFileSync(sourcePath);
const sourceCards = JSON.parse(sourceBuffer.toString("utf8"));
if (!Array.isArray(sourceCards)) {
    throw new Error("카드고릴라 원본의 최상위 값은 배열이어야 합니다.");
}

const cards = sourceCards.map((card) => ({
    cardId: String(card.card_id),
    cardType: card.card_type,
    ranking: card.ranking,
    name: card.name,
    benefitCount: card.benefits.length,
    benefitsSha256: sha256(JSON.stringify(card.benefits)),
}));
const benefits = sourceCards.flatMap((card) => card.benefits);
const manifest = {
    metadata: {
        sourceFileName: path.basename(sourcePath),
        sourceSha256: sha256(sourceBuffer),
        sourceCardCount: sourceCards.length,
        sourceBenefitCount: benefits.length,
        creditCardCount: sourceCards.filter((card) => card.card_type === "credit").length,
        checkCardCount: sourceCards.filter((card) => card.card_type === "check").length,
        missingDetailTextCount: benefits.filter((benefit) => !(benefit.detail_text ?? "").trim()).length,
        summaryBenefitsExcluded: true,
    },
    cards,
};

const outputPath = path.join(
    process.cwd(),
    "src/test/resources/benefit/card-gorilla-without-summary-manifest.json",
);
fs.writeFileSync(outputPath, `${JSON.stringify(manifest, null, 2)}\n`);

function sha256(value) {
    return crypto.createHash("sha256").update(value).digest("hex");
}
