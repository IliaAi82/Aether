package studio.cluvex.aether.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.cluvex.aether.R
import studio.cluvex.aether.core.UpdateChecker

/**
 * Telegram-style update banner.
 *
 * Renders nothing until a newer GitHub release is found. Then it shows a card
 * with the new version and an Update button; tapping it downloads the right
 * APK for this device (with a progress bar) and opens the system installer.
 * Because the signing key is stable, the update installs in place.
 */
@Composable
fun UpdatePrompt(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (UpdateChecker.shouldAutoCheck(context)) {
            info = withContext(Dispatchers.IO) { UpdateChecker.check(context) }
        }
    }

    val update = info
    if (update == null || dismissed) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.update_available, update.versionName),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.update_downloading, progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        // Android requires a one-time approval before an app may
                        // install packages; send the user there first if needed.
                        if (UpdateChecker.needsInstallPermission(context)) {
                            Toast.makeText(
                                context,
                                R.string.update_allow_install,
                                Toast.LENGTH_LONG,
                            ).show()
                            UpdateChecker.requestInstallPermission(context)
                            return@Button
                        }
                        downloading = true
                        progress = 0
                        scope.launch {
                            try {
                                val apk = withContext(Dispatchers.IO) {
                                    UpdateChecker.download(context, update) { progress = it }
                                }
                                UpdateChecker.install(context, apk)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    R.string.update_failed,
                                    Toast.LENGTH_LONG,
                                ).show()
                            } finally {
                                downloading = false
                            }
                        }
                    }) {
                        Text(stringResource(R.string.update_action))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { dismissed = true }) {
                        Text(stringResource(R.string.update_dismiss))
                    }
                }
            }
        }
    }
}
