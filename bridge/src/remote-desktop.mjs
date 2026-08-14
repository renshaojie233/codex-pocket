import { createDecipheriv } from "node:crypto";
import { execFileSync, spawn } from "node:child_process";

const SSH_PATH = "/usr/bin/ssh";
const VNC_FIXED_DES_KEY = Buffer.from("e84ad660c4721ae0", "hex");

export const REMOTE_DESKTOPS = Object.freeze({
  workstation: Object.freeze({
    id: "workstation",
    name: "Workstation",
    description: "Ubuntu 工作站 · 1920 × 1080",
    sshHost: "workstation",
    passwordFile: "/home/ubuntu/.vnc/passwd",
  }),
  agilex: Object.freeze({
    id: "agilex",
    name: "Agilex",
    description: "Agilex 电脑 · GNOME 桌面",
    sshHost: "agilex",
    passwordFile: "/home/agilex/.vnc/passwd",
  }),
});

export function publicRemoteDesktops() {
  return Object.values(REMOTE_DESKTOPS).map(({ id, name, description }) => ({
    id,
    name,
    description,
  }));
}

export function remoteDesktopById(id) {
  return typeof id === "string" ? REMOTE_DESKTOPS[id] || null : null;
}

/**
 * Traditional VNC password files contain one DES block encrypted with a
 * protocol-defined fixed key. Node exposes 3DES rather than single DES;
 * EDE with the same key repeated three times is equivalent to single DES.
 */
export function decryptVncPassword(encrypted) {
  if (!Buffer.isBuffer(encrypted) || encrypted.length !== 8) {
    throw new Error("Invalid VNC password file");
  }
  const tripleKey = Buffer.concat([
    VNC_FIXED_DES_KEY,
    VNC_FIXED_DES_KEY,
    VNC_FIXED_DES_KEY,
  ]);
  const decipher = createDecipheriv("des-ede3", tripleKey, null);
  decipher.setAutoPadding(false);
  return Buffer.concat([decipher.update(encrypted), decipher.final()])
    .toString("latin1")
    .replace(/\0+$/g, "");
}

export function remoteSshArgs(device) {
  if (!device || !Object.values(REMOTE_DESKTOPS).includes(device)) {
    throw new Error("Remote desktop is not allowlisted");
  }
  return [
    "-T",
    "-x",
    "-o", "BatchMode=yes",
    "-o", "ConnectTimeout=8",
    "-o", "ServerAliveInterval=15",
    "-o", "ServerAliveCountMax=3",
    "-o", "ClearAllForwardings=yes",
    device.sshHost,
    "-W", "127.0.0.1:5900",
  ];
}

export function loadRemoteVncPassword(device) {
  const encrypted = execFileSync(SSH_PATH, [
    "-o", "BatchMode=yes",
    "-o", "ConnectTimeout=5",
    device.sshHost,
    "cat", device.passwordFile,
  ], {
    encoding: null,
    timeout: 7_000,
    maxBuffer: 64,
  });
  return decryptVncPassword(encrypted);
}

export function openRemoteVncTunnel(device) {
  return spawn(SSH_PATH, remoteSshArgs(device), {
    stdio: ["pipe", "pipe", "pipe"],
    windowsHide: true,
  });
}

