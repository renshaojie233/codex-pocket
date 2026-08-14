import assert from "node:assert/strict";
import { createCipheriv } from "node:crypto";
import test from "node:test";
import {
  decryptVncPassword,
  publicRemoteDesktops,
  remoteDesktopById,
  remoteSshArgs,
} from "../src/remote-desktop.mjs";

const fixedKey = Buffer.from("e84ad660c4721ae0", "hex");

test("exposes only the two allowlisted remote desktops", () => {
  assert.deepEqual(publicRemoteDesktops().map((device) => device.id), ["workstation", "agilex"]);
  assert.equal(remoteDesktopById("../../etc/passwd"), null);
});

test("builds a fixed SSH stdio tunnel without accepting arbitrary hosts", () => {
  const workstation = remoteDesktopById("workstation");
  const args = remoteSshArgs(workstation);
  assert.deepEqual(args.slice(-3), ["workstation", "-W", "127.0.0.1:5900"]);
  assert.throws(() => remoteSshArgs({ sshHost: "attacker" }), /allowlisted/);
});

test("decrypts the traditional eight-byte VNC password file", () => {
  const key = Buffer.concat([fixedKey, fixedKey, fixedKey]);
  const cipher = createCipheriv("des-ede3", key, null);
  cipher.setAutoPadding(false);
  const encrypted = Buffer.concat([
    cipher.update(Buffer.from("pocket\0\0", "latin1")),
    cipher.final(),
  ]);
  assert.equal(decryptVncPassword(encrypted), "pocket");
  assert.throws(() => decryptVncPassword(Buffer.alloc(7)), /Invalid/);
});

