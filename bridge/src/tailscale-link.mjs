function normalized(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replace(/\.$/, "");
}

function peerNames(peer) {
  const dnsName = normalized(peer?.DNSName);
  const shortDnsName = dnsName.split(".")[0];
  return [
    peer?.HostName,
    peer?.ComputedName,
    peer?.ComputedNameWithHost,
    dnsName,
    shortDnsName,
    ...(peer?.TailscaleIPs || []),
  ].map(normalized).filter(Boolean);
}

export function findTailscalePeer(status, target) {
  const wanted = normalized(target);
  if (!wanted) return null;
  return Object.values(status?.Peer || {}).find((peer) => peerNames(peer).includes(wanted)) || null;
}

export function tailscalePeerRoute(peer) {
  if (!peer?.Online) return "offline";
  if (peer.CurAddr) return "direct";
  if (peer.Relay) return "relay";
  return peer.Active ? "negotiating" : "idle";
}
