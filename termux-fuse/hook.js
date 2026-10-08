// Acode Cordova `after_prepare` hook.
// Runs last (appended after Acode's own hooks) and fuses the Termux terminal + simple editor
// into the generated Android project.
const { execSync } = require("child_process");
const path = require("path");

const here = __dirname;
const root = path.resolve(here, "..");

try {
  console.log("[termux-fuse] applying patch to " + root);
  execSync(`python3 "${path.join(here, "patch.py")}" "${root}" post`, {
    stdio: "inherit",
  });
} catch (e) {
  console.error("[termux-fuse] FAILED:", e.message);
  process.exit(1);
}
