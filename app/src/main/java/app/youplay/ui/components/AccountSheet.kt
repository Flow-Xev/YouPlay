package app.youplay.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.youplay.domain.AccountState
import app.youplay.ui.theme.YouPlayRed

/** Account switcher: every saved YouTube account, add another, sync, sign out. Used by Home and the You tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    accounts: List<AccountState>,
    activeId: String?,
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    onSignOutAll: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF141414)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                if (accounts.isEmpty()) "You're browsing as a guest" else "Accounts",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            if (accounts.isEmpty()) Text(
                "Sign in to get your own Home, subscriptions, likes and playlists.",
                color = Color(0xFFA3A3A3), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), color = YouPlayRed)
            accounts.forEach { a ->
                val active = a.id == activeId
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !active && a.id != null) { onSwitch(a.id!!); onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    YpAvatar(a.avatarUrl, a.name ?: "?", 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name ?: "YouTube account", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(a.handle, a.email).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, color = Color(0xFFA3A3A3), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (active) Icon(Icons.Filled.Check, "Active", tint = YouPlayRed)
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).padding(horizontal = 20.dp).background(Color.White.copy(0.08f)))
            SheetAction(Icons.Filled.PersonAdd, if (accounts.isEmpty()) "Sign in" else "Add another account") { onDismiss(); onAdd() }
            if (activeId != null) {
                SheetAction(Icons.Filled.Sync, "Sync with YouTube") { onSync() }
                SheetAction(Icons.Filled.Logout, "Sign out of this account") { onDismiss(); onSignOut() }
                if (accounts.size > 1) SheetAction(Icons.Filled.Logout, "Sign out of all accounts") { onDismiss(); onSignOutAll() }
            }
            message?.let { Text(it, color = YouPlayRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF242424)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(label, color = Color.White, fontSize = 15.sp)
    }
}
