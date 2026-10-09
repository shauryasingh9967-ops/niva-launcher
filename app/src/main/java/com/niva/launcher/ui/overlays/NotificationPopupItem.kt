package com.niva.launcher.ui.overlays

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.niva.launcher.data.LauncherApp
import com.niva.launcher.data.notifications.AppNotification
import com.niva.launcher.ui.components.AppIcon
import com.niva.launcher.ui.components.SwipeDismissContainer
import com.niva.launcher.ui.components.notificationAge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun NotificationPopupItem(
    app: LauncherApp,
    notification: AppNotification,
    onOpen: () -> Unit,
    onDismiss: () -> Boolean,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, notification.key, notification.icon) {
        value = withContext(Dispatchers.IO) {
            runCatching { notification.icon?.loadDrawable(context)?.toBitmap(120, 120)?.asImageBitmap() }.getOrNull()
        }
    }
    SwipeDismissContainer(
        itemKey = notification.key to notification.revision,
        onDismiss = onDismiss,
        modifier = modifier.fillMaxWidth().testTag("notification:${notification.key}"),
        enabled = notification.canDismiss && gesturesEnabled,
    ) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = notification.canOpen && gesturesEnabled, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val image = icon
                if (image != null) Image(image, null, Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                else AppIcon(app, size = 36.dp)
                Spacer(Modifier.width(22.dp))
                Text(
                    "${notification.title.ifBlank { app.label }} · ${notificationAge(notification.postedAt)}",
                    style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (notification.text.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(notification.text, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
