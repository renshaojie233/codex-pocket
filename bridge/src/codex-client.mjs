import { spawnSync } from "node:child_process";
import { EventEmitter } from "node:events";
import { existsSync } from "node:fs";
import { createConnection } from "node:net";
import { homedir } from "node:os";
import { resolve } from "node:path";
import WebSocket from "ws";

const REQUEST_TIMEOUT_MS = 30_000;
const CONNECT_TIMEOUT_MS = 8_000;

export class CodexClient extends EventEmitter {
  constructor({ codexBin }) {
    super();
    this.codexBin = codexBin;
    this.socket = null;
    this.socketPath = resolve(homedir(), ".codex", "app-server-control", "app-server-control.sock");
    this.nextId = 1;
    this.pending = new Map();
    this.started = false;
    this.exiting = false;
  }

  async start() {
    if (this.started) return;
    const managedCodex = resolve(homedir(), ".codex", "packages", "standalone", "current", "codex");
    const daemonCodex = existsSync(managedCodex) ? managedCodex : this.codexBin;
    const daemonStart = spawnSync(daemonCodex, ["app-server", "daemon", "start"], {
      encoding: "utf8",
      env: process.env,
    });
    if (daemonStart.status !== 0) {
      throw new Error(
        `Unable to start Codex shared App Server: ${daemonStart.stderr?.trim() || daemonStart.error?.message || "unknown error"}`,
      );
    }

    // This is the same local-daemon transport used by Codex desktop when
    // CODEX_APP_SERVER_USE_LOCAL_DAEMON=1. Both clients therefore subscribe to
    // one runtime and receive the same live thread notifications.
    this.socket = new WebSocket("ws://localhost/rpc", {
      createConnection: () => createConnection(this.socketPath),
      handshakeTimeout: CONNECT_TIMEOUT_MS,
      perMessageDeflate: false,
    });
    await new Promise((resolveConnection, rejectConnection) => {
      const timeout = setTimeout(() => {
        rejectConnection(new Error("Timed out connecting to Codex shared App Server"));
        this.socket?.terminate();
      }, CONNECT_TIMEOUT_MS);
      this.socket.once("open", () => {
        clearTimeout(timeout);
        resolveConnection();
      });
      this.socket.once("error", (error) => {
        clearTimeout(timeout);
        rejectConnection(error);
      });
    });

    this.socket.on("message", (data) => this.#handleMessage(data.toString("utf8")));
    this.socket.on("error", (error) => this.emit("log", error.message));
    this.socket.on("close", (code, reason) => this.#handleExit(code, reason.toString("utf8")));

    const initialized = await this.request("initialize", {
      clientInfo: {
        name: "codex_pocket_bridge",
        title: "Codex Pocket Bridge",
        version: "0.16.6",
      },
      capabilities: {
        experimentalApi: true,
        requestAttestation: false,
      },
    });
    this.notify("initialized", {});
    this.started = true;
    this.emit("ready", initialized);
  }

  request(method, params = {}) {
    if (this.socket?.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error("Codex shared App Server is not connected"));
    }
    const id = this.nextId++;
    return new Promise((resolveRequest, rejectRequest) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id);
        rejectRequest(new Error(`Codex request timed out: ${method}`));
      }, REQUEST_TIMEOUT_MS);
      this.pending.set(id, { method, resolve: resolveRequest, reject: rejectRequest, timeout });
      this.#write({ method, id, params });
    });
  }

  notify(method, params = {}) {
    this.#write({ method, params });
  }

  respond(id, result) {
    this.#write({ id, result });
  }

  respondError(id, code, message) {
    this.#write({ id, error: { code, message } });
  }

  stop() {
    if (!this.socket) return;
    this.exiting = true;
    this.socket.close(1000, "Bridge shutting down");
    this.socket = null;
    this.started = false;
  }

  #write(message) {
    if (this.socket?.readyState !== WebSocket.OPEN) {
      throw new Error("Codex shared App Server connection is closed");
    }
    this.socket.send(JSON.stringify(message));
  }

  #handleMessage(raw) {
    let message;
    try {
      message = JSON.parse(raw);
    } catch {
      this.emit("log", `Ignored invalid JSON from Codex: ${raw.slice(0, 200)}`);
      return;
    }

    if (message.id !== undefined && !message.method && this.pending.has(message.id)) {
      const pending = this.pending.get(message.id);
      this.pending.delete(message.id);
      clearTimeout(pending.timeout);
      if (message.error) {
        pending.reject(new Error(message.error.message || JSON.stringify(message.error)));
      } else {
        pending.resolve(message.result);
      }
      return;
    }

    if (message.id !== undefined && message.method) {
      this.emit("serverRequest", message);
      return;
    }

    if (message.method) this.emit("notification", message);
  }

  #handleExit(code, reason) {
    const error = new Error(`Codex shared App Server connection closed (code=${code}, reason=${reason || "none"})`);
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timeout);
      pending.reject(error);
    }
    this.pending.clear();
    this.socket = null;
    this.started = false;
    if (!this.exiting) this.emit("exit", error);
  }
}
