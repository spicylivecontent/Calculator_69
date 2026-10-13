package com.calculator.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calculator.app.data.AppPreferences
import com.calculator.app.data.AutoLock
import com.calculator.app.data.PreferredQuality
import com.calculator.app.ui.components.SectionHeader
import com.calculator.app.ui.components.TagChip
import com.calculator.app.ui.web.AdBlockLevel
import com.calculator.app.ui.theme.VaultAccent
import com.calculator.app.ui.theme.VaultBorder
import com.calculator.app.ui.theme.VaultSurface
import com.calculator.app.ui.theme.VaultTextPrimary
import com.calculator.app.ui.theme.VaultTextSecondary

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    supportedSiteLabels: List<String>,
    historySize: Int,
    onlyPlayable: Boolean,
    onOnlyPlayableChange: (Boolean) -> Unit,
    adBlockLevel: AdBlockLevel = preferences.adBlockLevel,
    onAdBlockLevelChange: (AdBlockLevel) -> Unit = {},
    onLock: () -> Unit,
    onOpenChangePin: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    rulesEnabled: Boolean = preferences.remoteRulesEnabled,
    rulesStatus: String = "Inbyggda regler (10 sajter)",
    rulesMessage: String? = null,
    onRulesEnabledChange: (Boolean) -> Unit = {},
    onRefreshRules: () -> Unit = {},
    onResetRules: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var preferredQuality by remember { mutableStateOf(preferences.preferredQuality) }
    var skipSeconds by remember { mutableStateOf(preferences.skipSeconds) }
    var keepScreenOn by remember { mutableStateOf(preferences.keepScreenOn) }
    var gestureSeeking by remember { mutableStateOf(preferences.gestureSeeking) }
    var resumePlayback by remember { mutableStateOf(preferences.resumePlayback) }
    var autoLock by remember { mutableStateOf(preferences.autoLock) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Inställningar", color = VaultTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Spelare, sökning, sajter och säkerhet", color = VaultTextSecondary, fontSize = 12.sp)
            }
        }

        item { SectionHeader(title = "Spelare") }
        item {
            VaultSettingCard(title = "Standardkvalitet", subtitle = preferredQuality.label) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreferredQuality.entries.take(3).forEach { quality ->
                            TagChip(
                                text = quality.label.substringBefore(" ("),
                                selected = preferredQuality == quality,
                                onClick = {
                                    preferredQuality = quality
                                    preferences.preferredQuality = quality
                                }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreferredQuality.entries.drop(3).forEach { quality ->
                            TagChip(
                                text = quality.label.substringBefore(" ("),
                                selected = preferredQuality == quality,
                                onClick = {
                                    preferredQuality = quality
                                    preferences.preferredQuality = quality
                                }
                            )
                        }
                    }
                }
            }
        }
        item {
            VaultSettingCard(title = "Spolintervall", subtitle = "$skipSeconds sekunder vid dubbeltryck och med knapparna") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SKIP_OPTIONS.forEach { seconds ->
                        TagChip(
                            text = "${seconds}s",
                            selected = skipSeconds == seconds,
                            onClick = {
                                skipSeconds = seconds
                                preferences.skipSeconds = seconds
                            }
                        )
                    }
                }
            }
        }
        item {
            VaultToggleCard(
                title = "Håll skärmen tänd",
                subtitle = "Skärmen släcks inte medan en video spelas",
                checked = keepScreenOn,
                onCheckedChange = {
                    keepScreenOn = it
                    preferences.keepScreenOn = it
                }
            )
        }
        item {
            VaultToggleCard(
                title = "Spola med drag",
                subtitle = "Dra horisontellt för att skrubba, vertikalt för volym och ljus",
                checked = gestureSeeking,
                onCheckedChange = {
                    gestureSeeking = it
                    preferences.gestureSeeking = it
                }
            )
        }
        item {
            VaultToggleCard(
                title = "Fortsätt där du slutade",
                subtitle = "Spelaren startar automatiskt på senaste positionen",
                checked = resumePlayback,
                onCheckedChange = {
                    resumePlayback = it
                    preferences.resumePlayback = it
                }
            )
        }

        item { SectionHeader(title = "WebView-spelaren") }
        item {
            VaultSettingCard(
                title = "Annonsblockering",
                subtitle = adBlockLevel.label
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdBlockLevel.entries.forEach { level ->
                        TagChip(
                            text = level.label,
                            selected = adBlockLevel == level,
                            onClick = {
                                preferences.adBlockLevel = level
                                onAdBlockLevelChange(level)
                            }
                        )
                    }
                    Text(
                        "Blockerar annons-, popunder- och trackingdomäner, stoppar popup-fönster och " +
                            "omdirigeringar samt gömmer annonsytor på sidan. Antalet blockerade förfrågningar " +
                            "visas i spelaren.",
                        color = VaultTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
        item {
            VaultSettingCard(
                title = "Uppspelning",
                subtitle = "Videorna spelas på sajten i en inbyggd webbläsare med annonsblockering"
            ) {
                Text(
                    "Välj \"Direktuppspelning (beta)\" i spelarens meny om du hellre vill spela upp en " +
                        "extraherad ström i appens egen spelare.",
                    color = VaultTextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        item { SectionHeader(title = "Sökning") }
        item {
            VaultToggleCard(
                title = "Visa bara sidor som svarar",
                subtitle = "Träffar vars detaljsida inte kan läsas in döljs",
                checked = onlyPlayable,
                onCheckedChange = onOnlyPlayableChange
            )
        }
        item {
            VaultSettingCard(
                title = "Sökhistorik",
                subtitle = if (historySize == 0) "Ingen historik" else "$historySize sparade sökningar"
            ) {
                TagChip(text = "Rensa historiken", selected = false, onClick = onClearSearchHistory)
            }
        }

        item { SectionHeader(title = "Sajter") }
        item {
            VaultSettingCard(
                title = "${supportedSiteLabels.size} sajter",
                subtitle = supportedSiteLabels.joinToString(", ")
            ) {
                Text(
                    "Appen blockerar annons- och trackingdomäner, läser inga inloggningar och skickar inga uppgifter till tredje part.",
                    color = VaultTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        item { SectionHeader(title = "Skrapregler") }
        item {
            VaultToggleCard(
                title = "Hämta uppdaterade regler",
                subtitle = "Hämtar högst en gång i dygnet från appens eget repo. Bara adresser och mönster – aldrig kod.",
                checked = rulesEnabled,
                onCheckedChange = onRulesEnabledChange
            )
        }
        item {
            VaultSettingCard(title = "Regler i bruk", subtitle = rulesStatus) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagChip(text = "Uppdatera nu", selected = false, onClick = onRefreshRules)
                    TagChip(text = "Använd inbyggda", selected = false, onClick = onResetRules)
                }
                if (rulesMessage != null) {
                    Text(
                        rulesMessage,
                        color = VaultTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        item { SectionHeader(title = "Säkerhet") }
        item {
            VaultSettingCard(title = "Byt huvudkod", subtitle = "Koden som öppnar vaulten") {
                TagChip(text = "Byt huvudkod", selected = false, onClick = { onOpenChangePin("real") })
            }
        }
        item {
            VaultSettingCard(title = "Byt reservkod", subtitle = "Koden som öppnar decoy-vyn") {
                TagChip(text = "Byt reservkod", selected = false, onClick = { onOpenChangePin("decoy") })
            }
        }
        item {
            VaultSettingCard(
                title = "Biometrisk låsning",
                subtitle = "Krävs för att öppna vaulten. Saknas biometri förblir vaulten låst."
            )
        }
        item {
            VaultSettingCard(title = "Autolås", subtitle = autoLock.label) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoLock.entries.forEach { option ->
                        TagChip(
                            text = option.shortLabel,
                            selected = autoLock == option,
                            onClick = {
                                autoLock = option
                                preferences.autoLock = option
                            }
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onLock,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5A)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Lås vaulten (Panic)", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        item {
            Text(
                "All data ligger lokalt i appens privata lagring. Endast för personligt bruk – kontrollera att du följer lokal lagstiftning och sajternas villkor.",
                color = VaultTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun VaultSettingCard(
    title: String,
    subtitle: String,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = VaultTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = VaultTextSecondary, fontSize = 12.sp)
            }
            if (content != null) {
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

@Composable
private fun VaultToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = VaultTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = VaultTextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = VaultAccent,
                    uncheckedThumbColor = VaultTextSecondary,
                    uncheckedTrackColor = Color(0xFF2A2A3A)
                )
            )
        }
    }
}

private val SKIP_OPTIONS = listOf(5, 10, 15, 30)
