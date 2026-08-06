import { spawnSync } from "node:child_process";

export function runGradle(args, hookName) {
    const isWindows = process.platform === "win32";
    const command = isWindows ? ".\\gradlew.bat" : "./gradlew";
    const result = spawnSync(command, args, {
        cwd: process.cwd(),
        stdio: "inherit",
        shell: isWindows,
        timeout: 600000,
        killSignal: "SIGTERM"
    });

    if (result.error) {
        console.error(`[${hookName}] Gradle을 실행할 수 없습니다: ${result.error.message}`);
        process.exit(1);
    }

    if (result.status !== 0) {
        process.exit(result.status ?? 1);
    }
}
