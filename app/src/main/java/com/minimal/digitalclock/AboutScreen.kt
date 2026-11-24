package com.minimal.digitalclock

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val built = Instant.ofEpochMilli(BuildConfig.BUILD_TIME)
    val localBuilt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .format(built.atZone(ZoneId.systemDefault()))
    val utcBuilt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").format(built.atZone(ZoneOffset.UTC))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("About", onBack)

        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("Digital Clock", fontSize = 40.sp, fontWeight = FontWeight.Thin)
            Text(
                "A minimal, full-screen clock that keeps the screen on.",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray,
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        InfoRow("Version", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        InfoRow("Compiled", "$localBuilt\n$utcBuilt")
        InfoRow(
            "Source code",
            BuildConfig.GITHUB_URL.removePrefix("https://"),
            link = true,
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.GITHUB_URL)))
                } catch (_: ActivityNotFoundException) { /* no browser installed */ }
            },
        )
        InfoRow("License", "MIT")
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InfoRow(title: String, value: String, link: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (link) MaterialTheme.colorScheme.primary else Color.Unspecified,
            textDecoration = if (link) TextDecoration.Underline else null,
        )
    }
}
