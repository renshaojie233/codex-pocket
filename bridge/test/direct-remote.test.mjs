import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";
import net from "node:net";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import WebSocket from "ws";

const bridgeRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");

test("keeps the adaptive remote client script syntactically valid", () => {
  const html = readFileSync(resolve(bridgeRoot, "src", "remote-client.html"), "utf8");
  const source = html.match(/<script type="module">([\s\S]*?)<\/script>/)?.[1]
    .replace(/^\s*import RFB[^\n]*$/m, "const RFB = class {};")
    .replace("__DEVICE_JSON__", JSON.stringify({ id: "test", name: "Test" }))
    .replace("__TOKEN_JSON__", JSON.stringify("token"))
    .replace("__PASSWORD_JSON__", JSON.stringify("password"));
  assert.ok(source);
  assert.doesNotThrow(() => Function(source));
  assert.match(source, /low-latency/);
  assert.match(source, /power-save/);
  assert.match(source, /binary-damage/);
  assert.match(source, /0x43505246/);
  assert.match(source, /visibleDisplayDamage/);
});

async function listen(server, port = 0) {
  await new Promise((resolveListen, reject) => {
    server.once("error", reject);
    server.listen(port, "127.0.0.1", resolveListen);
  });
  return server.address().port;
}

async function unusedPort() {
  const server = net.createServer();
  const port = await listen(server);
  await new Promise(resolveClose => server.close(resolveClose));
  return port;
}

async function waitForHealth(url) {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try {
      const response = await fetch(url);
      if (response.ok) return response.json();
    } catch {
      // The child may still be starting.
    }
    await new Promise(resolveDelay => setTimeout(resolveDelay, 25));
  }
  throw new Error("Direct remote gateway did not start");
}

test("proxies an authenticated direct WebSocket to local VNC", async t => {
  const vncServer = net.createServer(socket => socket.write("RFB 003.008\n"));
  const vncPort = await listen(vncServer);
  const gatewayPort = await unusedPort();
  const child = spawn(process.execPath, [resolve(bridgeRoot, "direct-remote", "server.mjs")], {
    env: {
      ...process.env,
      REMOTE_ID: "test-device",
      REMOTE_NAME: "Test Device",
      REMOTE_TOKEN: "test-token",
      REMOTE_BIND_HOST: "127.0.0.1",
      REMOTE_PORT: String(gatewayPort),
      VNC_HOST: "127.0.0.1",
      VNC_PORT: String(vncPort),
      REMOTE_CLIENT_TEMPLATE: resolve(bridgeRoot, "src", "remote-client.html"),
      REMOTE_ASSETS_ROOT: resolve(bridgeRoot, "node_modules", "@novnc", "novnc"),
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  t.after(() => child.kill("SIGTERM"));
  t.after(() => vncServer.close());

  const health = await waitForHealth(`http://127.0.0.1:${gatewayPort}/health`);
  assert.equal(health.route, "tailscale-direct");

  const unauthorized = await fetch(
    `http://127.0.0.1:${gatewayPort}/remote/client?device=test-device&token=wrong`,
  );
  assert.equal(unauthorized.status, 401);

  const extremeUnauthorized = await fetch(
    `http://127.0.0.1:${gatewayPort}/remote/extreme/status?token=wrong`,
  );
  assert.equal(extremeUnauthorized.status, 401);

  const invalidPair = await fetch(
    `http://127.0.0.1:${gatewayPort}/remote/extreme/pair?token=test-token`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ pin: "12" }),
    },
  );
  assert.equal(invalidPair.status, 400);

  const page = await fetch(
    `http://127.0.0.1:${gatewayPort}/remote/client?device=test-device&token=test-token`,
  );
  assert.equal(page.status, 200);
  assert.match(await page.text(), /Codex Pocket Remote Desktop/);

  const handshake = await new Promise((resolveHandshake, reject) => {
    const ws = new WebSocket(
      `ws://127.0.0.1:${gatewayPort}/remote/ws?device=test-device&token=test-token`,
    );
    ws.once("message", data => {
      ws.close();
      resolveHandshake(Buffer.from(data).toString("ascii"));
    });
    ws.once("error", reject);
  });
  assert.equal(handshake, "RFB 003.008\n");
});
