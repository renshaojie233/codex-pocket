#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { findTailscalePeer, tailscalePeerRoute } from "../src/tailscale-link.mjs";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const bridgeDir = resolve(scriptDir, "..");
const statePath = resolve(bridgeDir, "data", "tailscale-link-repair.json");
const target = String(process.env.CODEX_POCKET_TAILSCALE_PEER || "").trim();
const cooldownMs = Number(process.env.CODEX_POCKET_TAILSCALE_REPAIR_COOLDOWN_MS || 10 * 60 * 1_000);
const probeIntervalMs = Number(process.env.CODEX_POCKET_TAILSCALE_PROBE_INTERVAL_MS || 2 * 60 * 1_000);
const discoveryKeyCooldownMs = Number(
  process.env.CODEX_POCKET_TAILSCALE_DISCOVERY_KEY_COOLDOWN_MS || 60 * 60 * 1_000,
);

function timestamped(message) {
  console.log(`[${new Date().toISOString()}] ${message}`);
}

function findTailscaleBin() {
  const candidates = [
    "/usr/local/bin/tailscale",
    "/opt/homebrew/bin/tailscale",
    "/Applications/Tailscale.app/Contents/MacOS/Tailscale",
  ];
  return candidates.find(existsSync) || "tailscale";
}

const tailscaleBin = findTailscaleBin();

function run(args, timeout = 15_000) {
  return execFileSync(tailscaleBin, args, {
    encoding: "utf8",
    timeout,
    stdio: ["ignore", "pipe", "pipe"],
  });
}

function readStatus() {
  return JSON.parse(run(["status", "--json"], 5_000));
}

function readState() {
  try {
    return JSON.parse(readFileSync(statePath, "utf8"));
  } catch {
    return {};
  }
}

function saveState(state) {
  mkdirSync(dirname(statePath), { recursive: true });
  writeFileSync(statePath, `${JSON.stringify(state, null, 2)}\n`, { mode: 0o600 });
}

function inspectPeer() {
  const peer = findTailscalePeer(readStatus(), target);
  return { peer, route: tailscalePeerRoute(peer) };
}

function attemptPing() {
  try {
    run(["ping", "--c", "5", "--timeout", "2s", target], 13_000);
  } catch {
    // A failed probe is expected while a carrier or NAT path is unavailable.
  }
}

if (!target) process.exit(0);

const now = Date.now();
const state = readState();
let { peer, route } = inspectPeer();

if (!peer) {
  if (state.route !== "missing") timestamped(`Tailscale peer '${target}' was not found; repair paused.`);
  saveState({ ...state, route: "missing", checkedAt: now });
  process.exit(0);
}

if (route === "direct") {
  if (state.route !== "direct") timestamped(`Direct Tailscale path restored via ${peer.CurAddr}.`);
  saveState({ ...state, route, checkedAt: now, directAddress: peer.CurAddr });
  process.exit(0);
}

if (!peer.Online || !peer.Active) {
  saveState({ ...state, route, checkedAt: now });
  process.exit(0);
}

if (now - Number(state.lastProbeAt || 0) < probeIntervalMs) process.exit(0);
state.lastProbeAt = now;
attemptPing();
({ peer, route } = inspectPeer());
if (route === "direct") {
  timestamped(`Direct Tailscale path recovered by peer probe via ${peer.CurAddr}.`);
  saveState({ ...state, route, checkedAt: Date.now(), directAddress: peer.CurAddr });
  process.exit(0);
}

if (now - Number(state.lastRepairAt || 0) < cooldownMs) {
  saveState({ ...state, route, checkedAt: Date.now() });
  process.exit(0);
}

state.lastRepairAt = now;
saveState({ ...state, route, checkedAt: now });
timestamped(`Peer '${target}' is using DERP; refreshing local UDP discovery.`);

const repairCommands = [
  ["debug", "rebind"],
  ["debug", "restun"],
];
if (now - Number(state.lastDiscoveryKeyRefreshAt || 0) >= discoveryKeyCooldownMs) {
  repairCommands.push(["debug", "rotate-disco-key"]);
  state.lastDiscoveryKeyRefreshAt = now;
}

for (const args of repairCommands) {
  try {
    run(args, 5_000);
  } catch (error) {
    timestamped(`Repair command '${args.join(" ")}' failed: ${error.message}`);
  }
}

attemptPing();
({ peer, route } = inspectPeer());
if (route === "direct") {
  timestamped(`Direct Tailscale path restored via ${peer.CurAddr}.`);
} else {
  timestamped(`Direct path is still unavailable (${route}); next repair waits for cooldown.`);
}
saveState({
  ...state,
  route,
  checkedAt: Date.now(),
  directAddress: route === "direct" ? peer.CurAddr : "",
});
