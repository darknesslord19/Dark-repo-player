package com.streambox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streambox.app.AppLog
import com.streambox.app.BuildConfig
import com.streambox.app.Settings
import com.streambox.app.restartApp

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var key by remember { mutableStateOf(Settings.tmdbKey) }
    var lang by remember { mutableStateOf(Settings.language) }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            SectionTitle("TMDB")
            OutlinedTextField(key, { key = it }, singleLine = true, label = { Text("TMDB API anahtarı") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp))
            OutlinedTextField(lang, { lang = it }, singleLine = true, label = { Text("Dil (ör: tr-TR, en-US)") },
                modifier = Modifier.fillMaxWidth().padding(14.dp, 8.dp))
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Settings.setTmdbKey(key); Settings.setLanguage(lang); Nav.homeCache.clear(); key = Settings.tmdbKey }) { Text("Kaydet") }
                OutlinedButton(onClick = { key = Settings.DEFAULT_TMDB_KEY }) { Text("Varsayılan") }
            }
            SectionTitle("Uygulama")
            Text("StreamBox ${BuildConfig.VERSION_NAME}", color = Muted, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(14.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { restartApp(context) }) { Text("Yeniden başlat") }
                OutlinedButton(onClick = { AppLog.lines.clear() }) { Text("Günlüğü temizle") }
            }
            SectionTitle("Günlük")
        }
        items(AppLog.lines) { line ->
            Text(
                line, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = if ("  E  " in line || line.contains(" E  ")) Color(0xFFFF8A8A) else Color(0xFFB8BCC2),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
        }
    }
}
