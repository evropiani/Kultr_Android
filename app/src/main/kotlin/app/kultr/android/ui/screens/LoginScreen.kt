package app.kultr.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.AppGraph
import app.kultr.android.R
import app.kultr.android.data.AuthRepository
import app.kultr.android.ui.components.ArtworkBackdropPlain
import app.kultr.android.ui.components.GlassPanel
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.AuthMode
import kotlinx.coroutines.launch

/**
 * One screen to connect: the server's address, a username and a password.
 * Also used to add a second server, or to sign back in to a saved one.
 */
@Composable
fun LoginScreen(
    graph: AppGraph,
    prefillUrl: String = "",
    prefillUser: String = "",
    onCancel: (() -> Unit)? = null,
    onDone: () -> Unit = {},
) {
    val colors = Kultr.colors
    val scope = rememberCoroutineScope()
    val profiles by graph.auth.profiles.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(prefillUrl) }
    var user by rememberSaveable { mutableStateOf(prefillUser) }
    var password by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var plain by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val result = graph.auth.login(
                AuthRepository.LoginInput(url, user, password, label, if (plain) AuthMode.PLAIN else AuthMode.TOKEN),
            )
            busy = false
            if (result == null) {
                graph.sync.startFirstSyncIfNeeded()
                onDone()
            } else {
                error = result
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ArtworkBackdropPlain()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Image(painterResource(R.drawable.kultr_logo), contentDescription = null, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(12.dp))
            Text("Kultr", style = MaterialTheme.typography.headlineLarge, color = colors.ink)
            Text(
                "Connect to your Navidrome server. Kultr mirrors your library on this phone, crossfades properly and mixes like a DJ.",
                color = colors.ink2,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).widthIn(max = 420.dp),
            )
            GlassPanel(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Server address") },
                        placeholder = { Text("https://music.example.com") },
                        leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Username") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { advanced = !advanced }) {
                        Text(if (advanced) "Hide advanced options" else "Advanced options")
                    }
                    if (advanced) {
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("Name for this server (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Plain password", color = colors.ink)
                                Text(
                                    "Only for servers or proxies that reject token authentication. Use HTTPS.",
                                    color = colors.ink3,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(checked = plain, onCheckedChange = { plain = it })
                        }
                    }
                    error?.let { Text(it, color = colors.danger, style = MaterialTheme.typography.bodyMedium) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill(
                            if (busy) "Connecting…" else "Connect",
                            onClick = { submit() },
                            modifier = Modifier.weight(1f),
                            accent = true,
                            enabled = !busy && url.isNotBlank() && user.isNotBlank(),
                        )
                        if (onCancel != null) Pill("Cancel", onClick = onCancel)
                    }
                }
            }
            val saved = profiles.filter { it.hasCredentials && it.enabled }
            if (saved.isNotEmpty() && onCancel == null) {
                Spacer(Modifier.height(24.dp))
                Text("Or pick a saved server", color = colors.ink3, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                saved.forEach { profile ->
                    Pill(
                        "${profile.label} · ${profile.username}",
                        onClick = { if (graph.auth.switchTo(profile.id)) onDone() },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Your password stays on this phone, sealed by the Android keystore, and is only ever sent to your own server.",
                color = colors.ink3,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }
}
