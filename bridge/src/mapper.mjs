function textFromUserInput(content = []) {
  return content
    .filter((item) => item?.type === "text")
    .map((item) => item.text)
    .join("\n");
}

const mediaExtensions = {
  image: new Set(["jpg", "jpeg", "png", "gif", "webp", "avif", "heic", "heif", "bmp"]),
  video: new Set(["mp4", "m4v", "mov", "webm", "mkv"]),
  audio: new Set(["mp3", "m4a", "aac", "wav", "ogg", "flac"]),
};

function sourceExtension(source = "") {
  const clean = source.split(/[?#]/, 1)[0].toLowerCase();
  const match = clean.match(/\.([a-z0-9]+)$/);
  return match?.[1] || "";
}

function mediaKind(source, mimeType = "") {
  const normalizedMime = mimeType.toLowerCase();
  if (normalizedMime.startsWith("image/")) return "image";
  if (normalizedMime.startsWith("video/")) return "video";
  if (normalizedMime.startsWith("audio/")) return "audio";
  const extension = sourceExtension(source);
  return Object.entries(mediaExtensions).find(([, extensions]) => extensions.has(extension))?.[0] || null;
}

function mediaName(source, fallback) {
  if (fallback) return fallback;
  const clean = source.split(/[?#]/, 1)[0].replace(/^file:\/\//, "");
  try {
    return decodeURIComponent(clean.split("/").filter(Boolean).at(-1) || "媒体文件");
  } catch {
    return clean.split("/").filter(Boolean).at(-1) || "媒体文件";
  }
}

function mediaAttachment(source, options = {}) {
  if (typeof source !== "string" || !source) return null;
  const kind = options.kind || mediaKind(source, options.mimeType);
  if (!kind) return null;
  return {
    id: options.id || `${kind}-${source.slice(0, 180)}`,
    kind,
    source,
    name: mediaName(source, options.name),
    mimeType: options.mimeType || "",
    isLocal: options.isLocal ?? (source.startsWith("/") || source.startsWith("file://")),
  };
}

function dedupeAttachments(attachments) {
  const seen = new Set();
  return attachments.filter((attachment) => {
    if (!attachment || seen.has(`${attachment.kind}:${attachment.source}`)) return false;
    seen.add(`${attachment.kind}:${attachment.source}`);
    return true;
  });
}

function attachmentsFromUserInput(content = [], itemId = "user") {
  return dedupeAttachments(content.map((entry, index) => {
    switch (entry?.type) {
      case "image":
        return mediaAttachment(entry.url, { id: `${itemId}-image-${index}`, kind: "image", name: "图片" });
      case "localImage":
        return mediaAttachment(entry.path, {
          id: `${itemId}-image-${index}`,
          kind: "image",
          isLocal: true,
        });
      case "audio":
        return mediaAttachment(entry.url, { id: `${itemId}-audio-${index}`, kind: "audio", name: "音频" });
      case "localAudio":
        return mediaAttachment(entry.path, {
          id: `${itemId}-audio-${index}`,
          kind: "audio",
          isLocal: true,
        });
      default:
        return null;
    }
  }));
}

function attachmentsFromMarkdown(text = "", itemId = "message") {
  const attachments = [];
  const markdownLink = /(!?)\[([^\]]*)\]\((?:<([^>]+)>|([^\s)]+))(?:\s+["'][^"']*["'])?\)/g;
  for (const match of text.matchAll(markdownLink)) {
    const source = match[3] || match[4];
    const attachment = mediaAttachment(source, {
      id: `${itemId}-markdown-${attachments.length}`,
      name: match[2] || undefined,
      kind: match[1] === "!" ? "image" : undefined,
    });
    if (attachment) attachments.push(attachment);
  }
  return dedupeAttachments(attachments);
}

function attachmentsFromToolContent(content = [], itemId = "tool") {
  return dedupeAttachments(content.map((entry, index) => {
    if (entry?.type === "inputImage") {
      return mediaAttachment(entry.imageUrl, { id: `${itemId}-image-${index}`, kind: "image" });
    }
    if (entry?.type === "inputAudio") {
      return mediaAttachment(entry.audioUrl, { id: `${itemId}-audio-${index}`, kind: "audio" });
    }
    if (entry?.type === "image" && entry.data) {
      const mimeType = entry.mimeType || "image/png";
      return mediaAttachment(`data:${mimeType};base64,${entry.data}`, {
        id: `${itemId}-image-${index}`,
        kind: "image",
        mimeType,
        name: "工具返回的图片",
      });
    }
    if (entry?.type === "audio" && entry.data) {
      const mimeType = entry.mimeType || "audio/mpeg";
      return mediaAttachment(`data:${mimeType};base64,${entry.data}`, {
        id: `${itemId}-audio-${index}`,
        kind: "audio",
        mimeType,
        name: "工具返回的音频",
      });
    }
    if (entry?.type === "resource" && entry.resource?.blob) {
      const mimeType = entry.resource.mimeType || "";
      return mediaAttachment(`data:${mimeType};base64,${entry.resource.blob}`, {
        id: `${itemId}-resource-${index}`,
        mimeType,
        name: entry.resource.uri || "工具返回的媒体",
      });
    }
    return null;
  }));
}

export function mapThreadSummary(thread) {
  return {
    id: thread.id,
    title: thread.name || thread.preview || "未命名任务",
    preview: thread.preview || "",
    cwd: thread.cwd || "",
    status: thread.status?.type || "notLoaded",
    activeFlags: thread.status?.activeFlags || [],
    createdAt: thread.createdAt,
    updatedAt: thread.updatedAt,
    recencyAt: thread.recencyAt,
    isPinned: Boolean(thread.isPinned),
  };
}

export function mapModel(model) {
  return {
    id: model.model || model.id,
    displayName: model.displayName || model.model || model.id,
    description: model.description || "",
    efforts: (model.supportedReasoningEfforts || []).map((option) => ({
      id: option.reasoningEffort,
      description: option.description || "",
    })),
    defaultEffort: model.defaultReasoningEffort || "medium",
    isDefault: Boolean(model.isDefault),
    serviceTiers: (model.serviceTiers || []).map((tier) => ({
      id: tier.id,
      name: tier.name,
      description: tier.description || "",
    })),
    defaultServiceTier: model.defaultServiceTier || null,
  };
}

function toolResultText(result) {
  if (!result) return "";
  if (typeof result === "string") return result;
  const content = Array.isArray(result.content) ? result.content : [];
  return content
    .map((item) => item?.text || item?.output_text || "")
    .filter(Boolean)
    .join("\n");
}

export function mapItem(item, turnId) {
  const base = { id: item.id || `${turnId}-${item.type}`, turnId, kind: item.type };
  switch (item.type) {
    case "userMessage":
      return {
        ...base,
        id: item.clientId || base.id,
        role: "user",
        text: textFromUserInput(item.content),
        attachments: attachmentsFromUserInput(item.content, item.id),
      };
    case "agentMessage": {
      const text = item.text || "";
      return {
        ...base,
        role: "assistant",
        text,
        phase: item.phase || null,
        attachments: attachmentsFromMarkdown(text, item.id),
      };
    }
    case "plan":
      return { ...base, role: "assistant", text: item.text || "", kind: "plan" };
    case "reasoning":
      return {
        ...base,
        role: "status",
        text: (item.summary || []).join("\n\n"),
        kind: "reasoning",
        status: "completed",
      };
    case "commandExecution":
      return {
        ...base,
        role: "tool",
        text: item.aggregatedOutput || "",
        command: item.command,
        cwd: item.cwd,
        status: item.status,
        exitCode: item.exitCode,
      };
    case "fileChange":
      return {
        ...base,
        role: "tool",
        text: `${item.changes?.length || 0} 个文件变更`,
        status: item.status,
        changes: item.changes || [],
      };
    case "mcpToolCall":
      return {
        ...base,
        role: "tool",
        text: toolResultText(item.result),
        command: `${item.server || "MCP"} · ${item.tool || "工具"}`,
        status: item.status,
        attachments: attachmentsFromToolContent(item.result?.content || [], item.id),
      };
    case "dynamicToolCall":
      return {
        ...base,
        role: "tool",
        text: toolResultText({ content: item.contentItems }),
        command: [item.namespace, item.tool].filter(Boolean).join(" · ") || "工具调用",
        status: item.status,
        attachments: attachmentsFromToolContent(item.contentItems || [], item.id),
      };
    case "collabAgentToolCall":
      return {
        ...base,
        role: "tool",
        text: item.prompt || "",
        command: `协作代理 · ${item.tool || "任务"}`,
        status: item.status,
      };
    case "webSearch":
      return { ...base, role: "tool", text: "", command: "搜索网络", status: "inProgress" };
    case "imageGeneration": {
      const source = item.savedPath || (item.result ? `data:image/png;base64,${item.result}` : "");
      return {
        ...base,
        role: "tool",
        text: item.revisedPrompt || "",
        command: "生成图片",
        status: item.status || "inProgress",
        attachments: source ? [mediaAttachment(source, {
          id: `${item.id}-generated-image`,
          kind: "image",
          name: "生成的图片",
          isLocal: Boolean(item.savedPath),
        })] : [],
      };
    }
    case "imageView":
      {
        const sources = dedupeAttachments(
          [item.path, ...(Array.isArray(item.paths) ? item.paths : [])]
            .flatMap((source) => Array.isArray(source) ? source : [source])
            .map((source, index) => mediaAttachment(source, {
              id: `${item.id}-viewed-image-${index}`,
              kind: "image",
              isLocal: true,
            })),
        );
      return {
        ...base,
        role: "tool",
        text: "",
        command: "查看图片",
        status: "completed",
        attachments: sources,
      };
      }
    case "contextCompaction":
      return { ...base, role: "status", text: "正在整理较长的上下文", status: "completed" };
    default:
      return null;
  }
}

function activityFromItem(item, phase) {
  const completed = phase === "completed";
  const common = {
    id: item?.id || `activity-${Date.now()}`,
    phase,
    detail: "",
  };
  switch (item?.type) {
    case "reasoning":
      return { ...common, title: completed ? "思考完成" : "正在分析", detail: (item.summary || []).join("\n") };
    case "agentMessage":
      return { ...common, title: completed ? "回复已生成" : "正在组织回复" };
    case "plan":
      return { ...common, title: completed ? "计划已更新" : "正在制定计划", detail: item.text || "" };
    case "commandExecution":
      return {
        ...common,
        title: completed ? "命令执行完成" : "正在执行命令",
        detail: item.command || "",
      };
    case "fileChange":
      return { ...common, title: completed ? "文件修改完成" : "正在修改文件", detail: `${item.changes?.length || 0} 个文件` };
    case "mcpToolCall":
      return {
        ...common,
        title: completed ? "工具调用完成" : "正在使用工具",
        detail: `${item.server || "MCP"} · ${item.tool || "工具"}`,
      };
    case "dynamicToolCall":
      return {
        ...common,
        title: completed ? "工具调用完成" : "正在使用工具",
        detail: [item.namespace, item.tool].filter(Boolean).join(" · "),
      };
    case "collabAgentToolCall":
      return { ...common, title: completed ? "协作任务完成" : "正在调用协作代理", detail: item.tool || "" };
    case "webSearch":
      return { ...common, title: completed ? "网络搜索完成" : "正在搜索网络" };
    case "imageView":
      return { ...common, title: completed ? "图片查看完成" : "正在查看图片", detail: item.path || "" };
    case "imageGeneration":
      return { ...common, title: completed ? "图片生成完成" : "正在生成图片" };
    case "contextCompaction":
      return { ...common, title: completed ? "上下文整理完成" : "正在整理上下文" };
    default:
      return { ...common, title: completed ? "步骤已完成" : "正在处理", detail: item?.type || "" };
  }
}

export function mapThreadDetail(thread, options = {}) {
  const messages = [];
  for (const turn of thread.turns || []) {
    for (const item of turn.items || []) {
      const mapped = mapItem(item, turn.id);
      if (mapped && (mapped.text || mapped.command || mapped.changes?.length || mapped.attachments?.length)) {
        messages.push(mapped);
      }
    }
  }
  const requestedLimit = Number(options.messageLimit);
  const hasLimit = Number.isInteger(requestedLimit) && requestedLimit > 0;
  const beforeMessageId = typeof options.beforeMessageId === "string" && options.beforeMessageId
    ? options.beforeMessageId
    : null;
  const cursorIndex = beforeMessageId
    ? messages.findIndex((message) => message.id === beforeMessageId)
    : messages.length;
  const cursorFound = !beforeMessageId || cursorIndex >= 0;
  const windowEnd = cursorFound ? cursorIndex : messages.length;
  const windowStart = hasLimit ? Math.max(0, windowEnd - requestedLimit) : 0;
  const limitedMessages = cursorFound ? messages.slice(windowStart, windowEnd) : [];
  const requestedClientMessageIds = new Set(
    Array.isArray(options.clientMessageIds)
      ? options.clientMessageIds.filter((id) => typeof id === "string" && id)
      : [],
  );
  return {
    thread: mapThreadSummary(thread),
    messages: limitedMessages,
    totalMessageCount: messages.length,
    messageWindowStart: windowStart,
    messageWindowEnd: windowEnd,
    cursorFound,
    hasOlderMessages: cursorFound && windowStart > 0,
    hasNewerMessages: cursorFound && windowEnd < messages.length,
    activeTurnId: (thread.turns || []).findLast((turn) => turn.status === "inProgress")?.id || null,
    confirmedClientMessageIds: messages
      .filter((message) => message.role === "user" && requestedClientMessageIds.has(message.id))
      .map((message) => message.id),
  };
}

export function mapNotification(message) {
  const params = message.params || {};
  switch (message.method) {
    case "item/agentMessage/delta":
      return { event: "agent.delta", data: params };
    case "item/reasoning/summaryTextDelta":
      return { event: "reasoning.delta", data: params };
    case "item/plan/delta":
      return { event: "plan.delta", data: params };
    case "item/mcpToolCall/progress":
      return { event: "tool.progress", data: params };
    case "item/commandExecution/outputDelta":
      return { event: "tool.output", data: params };
    case "item/started":
      return {
        event: "item.started",
        data: {
          threadId: params.threadId,
          turnId: params.turnId,
          item: mapItem(params.item, params.turnId),
          activity: activityFromItem(params.item, "started"),
        },
      };
    case "item/completed":
      return {
        event: "item.completed",
        data: {
          threadId: params.threadId,
          turnId: params.turnId,
          item: mapItem(params.item, params.turnId),
          activity: activityFromItem(params.item, "completed"),
        },
      };
    case "turn/started":
      return { event: "turn.started", data: params };
    case "turn/completed":
      return { event: "turn.completed", data: params };
    case "thread/status/changed":
      return { event: "thread.status", data: params };
    case "thread/settings/updated":
      return {
        event: "thread.settings",
        data: {
          threadId: params.threadId,
          model: params.threadSettings?.model || null,
          effort: params.threadSettings?.effort || null,
          serviceTier: params.threadSettings?.serviceTier || null,
          mode: params.threadSettings?.collaborationMode?.mode || "default",
          permissionProfile: params.threadSettings?.activePermissionProfile?.id || (() => {
            switch (params.threadSettings?.sandboxPolicy?.type) {
              case "dangerFullAccess": return ":danger-full-access";
              case "workspaceWrite":
              case "externalSandbox": return ":workspace";
              case "readOnly": return ":read-only";
              default: return null;
            }
          })(),
          approvalPolicy: typeof params.threadSettings?.approvalPolicy === "string"
            ? params.threadSettings.approvalPolicy
            : null,
        },
      };
    case "thread/goal/updated":
      return { event: "thread.goal", data: params };
    case "thread/goal/cleared":
      return { event: "thread.goal.cleared", data: params };
    case "thread/started":
      return { event: "thread.catalog", data: { action: "started", threadId: params.thread?.id || null } };
    case "thread/name/updated":
      return { event: "thread.catalog", data: { action: "renamed", ...params } };
    case "thread/archived":
      return { event: "thread.catalog", data: { action: "archived", ...params } };
    case "thread/unarchived":
      return { event: "thread.catalog", data: { action: "unarchived", ...params } };
    case "thread/deleted":
      return { event: "thread.catalog", data: { action: "deleted", ...params } };
    case "turn/diff/updated":
      return { event: "turn.diff", data: params };
    case "turn/plan/updated":
      return { event: "turn.plan", data: params };
    case "warning":
    case "configWarning":
      return { event: "codex.warning", data: params };
    case "model/rerouted":
      return { event: "model.rerouted", data: params };
    case "account/rateLimits/updated":
    case "account/updated":
      return { event: "account.updated", data: params };
    case "error":
      return { event: "codex.error", data: params };
    default:
      return null;
  }
}
