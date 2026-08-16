package com.codexpocket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class AutomationHeartbeat(
    val automationId: String,
    val decision: String,
    val message: String,
)

internal fun parseAutomationHeartbeat(source: String): AutomationHeartbeat? {
    val trimmed = source.trim()
    if (!trimmed.startsWith("<heartbeat", ignoreCase = true) ||
        !trimmed.endsWith("</heartbeat>", ignoreCase = true)
    ) return null

    fun tag(name: String): String = Regex(
        "<$name(?:\\s[^>]*)?>\\s*([\\s\\S]*?)\\s*</$name>",
        RegexOption.IGNORE_CASE,
    ).find(trimmed)?.groupValues?.getOrNull(1).orEmpty().xmlUnescape().trim()

    val message = tag("message")
    if (message.isBlank()) return null
    return AutomationHeartbeat(
        automationId = tag("automation_id"),
        decision = tag("decision").ifBlank { "NOTIFY" },
        message = message,
    )
}

private fun String.xmlUnescape(): String = replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")

@Composable
internal fun AutomationHeartbeatCard(
    heartbeat: AutomationHeartbeat,
    fontSizeSp: Float,
) {
    var expanded by rememberSaveable(heartbeat.automationId, heartbeat.message) { mutableStateOf(false) }
    val active = heartbeat.decision.uppercase() !in setOf("NO_ACTION", "IGNORE", "NONE")
    val accent = if (active) Color(0xFF2F9B70) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.MonitorHeart,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "自动化状态",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(7.dp).background(accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (active) "有更新" else "正常",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                heartbeat.message,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.5f).sp,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (expanded && heartbeat.automationId.isNotBlank()) {
                Text(
                    heartbeat.automationId,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (fontSizeSp - 3f).coerceAtLeast(10f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
        }
    }
}
