const BACKGROUND_EVENTS = new Set([
  "bridge.error",
  "item.completed",
  "thread.catalog",
  "thread.goal",
  "thread.goal.cleared",
  "thread.settings",
  "thread.status",
  "turn.completed",
]);

export function socketClientMode(requestUrl = "") {
  try {
    const url = new URL(requestUrl, "http://bridge.local");
    return url.searchParams.get("client") === "background" ? "background" : "foreground";
  } catch {
    return "foreground";
  }
}

export function shouldDeliverSocketPayload(clientMode, payload) {
  if (clientMode !== "background" || payload?.type !== "event") return true;
  return BACKGROUND_EVENTS.has(payload.event);
}

export function filterReplayForClient(clientMode, method, result) {
  if (clientMode !== "background" || method !== "events.replay" || !Array.isArray(result?.events)) {
    return result;
  }
  return {
    ...result,
    events: result.events.filter((event) => shouldDeliverSocketPayload(clientMode, event)),
  };
}
