import { spawnSync } from "node:child_process";
import { runGradle } from "./run-gradle.mjs";

const result = spawnSync("git", ["diff", "--cached", "--check"], {
    cwd: process.cwd(),
    stdio: "inherit"
});

if (result.error) {
    console.error(`[pre-commit] staged diff 검사를 실행할 수 없습니다: ${result.error.message}`);
    process.exit(1);
}

if (result.status !== 0) {
    process.exit(result.status ?? 1);
}

runGradle(["checkstyleMain", "checkstyleTest"], "pre-commit");
