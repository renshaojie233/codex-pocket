import { execFileSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const moduleDir = dirname(fileURLToPath(import.meta.url));
const bridgeDir = resolve(moduleDir, "..");
const configPath = resolve(bridgeDir, "data", "config.json");

function tailscaleIpv4() {
  const candidates = ["tailscale", "/Applications/Tailscale.app/Contents/MacOS/Tailscale"];
  for (const candidate of candidates) {
    try {
      const address = execFileSync(candidate, ["ip", "-4"], {
        encoding: "utf8",
        timeout: 2_000,
      }).trim().split(/\s+/)[0];
      if (address) return address;
    } catch {
      // Try the next common Tailscale installation path.
    }
  }
  return "127.0.0.1";
}

function defaultCodexBin() {
  const desktopBin = "/Applications/ChatGPT.app/Contents/Resources/codex";
  return existsSync(desktopBin) ? desktopBin : "codex";
}

function loadSavedConfig() {
  if (!existsSync(configPath)) return {};
  return JSON.parse(readFileSync(configPath, "utf8"));
}

function saveConfig(config) {
  mkdirSync(dirname(configPath), { recursive: true });
  writeFileSync(configPath, `${JSON.stringify(config, null, 2)}\n`, {
    mode: 0o600,
  });
}

export function loadConfig() {
  const saved = loadSavedConfig();
  const config = {
    host: process.env.BRIDGE_HOST || saved.host || tailscaleIpv4(),
    port: Number(process.env.BRIDGE_PORT || saved.port || 8787),
    token: process.env.BRIDGE_TOKEN || saved.token || randomBytes(32).toString("base64url"),
    codexBin: process.env.CODEX_BIN || saved.codexBin || defaultCodexBin(),
  };

  if (!Number.isInteger(config.port) || config.port < 1 || config.port > 65535) {
    throw new Error(`Invalid bridge port: ${config.port}`);
  }

  if (!saved.token || !saved.host || !saved.codexBin || !saved.port) {
    saveConfig(config);
  }
  return { ...config, configPath };
}
