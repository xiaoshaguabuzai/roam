/**
 * 侧栏：会话列表与导航。
 */
package roam.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import roam.core.model.SessionInfo

@Composable
fun Sidebar(store: RoamStore, now: Long, modifier: Modifier = Modifier) {
    Column(modifier.background(Dsh.bg).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Brand()
        Spacer(Modifier.height(14.dp))
        NewSessionButton { store.newSession() }
        Spacer(Modifier.height(14.dp))

        NavItem(Icons.Outlined.Description, "记忆", selected = store.showMemory) { store.toggleMemory() }
        NavItem(Icons.Outlined.Refresh, "轨迹", selected = store.tab == 1) { store.selectTab(1) }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("会话", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Dsh.textTertiary, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.Search, null, tint = Dsh.textTertiary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Outlined.Add, null, tint = Dsh.textTertiary, modifier = Modifier.size(16.dp))
        }

        Spacer(Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(store.sessions, key = { it.id }) { session ->
                SessionRow(session, store.currentId == session.id, store.counts[session.id] ?: 0, now) {
                    store.select(session.id)
                }
            }
        }

        UserRow()
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Dsh.textPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text("R", color = Dsh.surface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text("roam", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dsh.textPrimary)
        Spacer(Modifier.width(6.dp))
        Text("AGENT", fontSize = 9.sp, letterSpacing = 1.4.sp, color = Dsh.textTertiary)
    }
}

@Composable
private fun NewSessionButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Dsh.surface,
        border = BorderStroke(1.dp, Dsh.border),
        modifier = Modifier.fillMaxWidth().height(38.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Add, null, tint = Dsh.textPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("新会话", fontSize = 13.sp, color = Dsh.textPrimary)
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Dsh.selected else Dsh.bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Dsh.textPrimary else Dsh.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = if (selected) Dsh.textPrimary else Dsh.textSecondary)
    }
}

@Composable
private fun SessionRow(session: SessionInfo, selected: Boolean, count: Int, now: Long, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Dsh.selected else Dsh.bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            session.title,
            fontSize = 13.sp,
            color = Dsh.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text("$count 条 · ${relativeTime(now, session.updatedAt)}", fontSize = 11.sp, color = Dsh.textTertiary)
    }
}

@Composable
private fun UserRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Dsh.selected), contentAlignment = Alignment.Center) {
            Text("R", fontSize = 11.sp, color = Dsh.textSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text("本地用户", fontSize = 12.sp, color = Dsh.textPrimary)
            Text("侧载 · 离线优先", fontSize = 10.sp, color = Dsh.textTertiary)
        }
    }
}
