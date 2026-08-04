import test from "node:test";
import assert from "node:assert/strict";
import { findTailscalePeer, tailscalePeerRoute } from "../src/tailscale-link.mjs";

const status = {
  Peer: {
    "nodekey:phone": {
      HostName: "Pocket Phone",
      ComputedName: "pocket-phone",
      DNSName: "pocket-phone.example.ts.net.",
      TailscaleIPs: ["100.64.1.2"],
      Online: true,
      Active: true,
      Relay: "example-relay",
      CurAddr: "",
    },
  },
};

test("finds a Tailscale peer by display name, DNS label, or IP", () => {
  assert.equal(findTailscalePeer(status, "Pocket Phone")?.ComputedName, "pocket-phone");
  assert.equal(findTailscalePeer(status, "pocket-phone")?.ComputedName, "pocket-phone");
  assert.equal(findTailscalePeer(status, "100.64.1.2")?.ComputedName, "pocket-phone");
  assert.equal(findTailscalePeer(status, "missing"), null);
});

test("distinguishes direct, relay, idle, and offline peer routes", () => {
  const peer = findTailscalePeer(status, "pocket-phone");
  assert.equal(tailscalePeerRoute(peer), "relay");
  assert.equal(tailscalePeerRoute({ ...peer, CurAddr: "192.0.2.1:41641" }), "direct");
  assert.equal(tailscalePeerRoute({ ...peer, Active: false, Relay: "" }), "idle");
  assert.equal(tailscalePeerRoute({ ...peer, Online: false }), "offline");
});
