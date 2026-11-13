package com.streambox.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streambox.app.Profile
import com.streambox.app.Profiles
import java.io.File

/** Hazır avatarlar: renk geçişi + simge (10 adet). */
val Avatars: List<Triple<Color, Color, ImageVector>> = listOf(
    Triple(Color(0xFFFF3B30), Color(0xFFFF9500), Icons.Default.LocalFireDepartment),
    Triple(Color(0xFF4F8CFF), Color(0xFF00C6FF), Icons.Default.RocketLaunch),
    Triple(Color(0xFF34C759), Color(0xFF00E0A4), Icons.Default.Eco),
    Triple(Color(0xFFAF52DE), Color(0xFFFF2D92), Icons.Default.AutoAwesome),
    Triple(Color(0xFFFFCC00), Color(0xFFFF9500), Icons.Default.EmojiEmotions),
    Triple(Color(0xFF5856D6), Color(0xFF8E7CFF), Icons.Default.SportsEsports),
    Triple(Color(0xFFFF2D55), Color(0xFFFF6B9A), Icons.Default.Favorite),
    Triple(Color(0xFF00B4D8), Color(0xFF48CAE4), Icons.Default.Pets),
    Triple(Color(0xFF1C1C1E), Color(0xFF636366), Icons.Default.Movie),
    Triple(Color(0xFFFF6B00), Color(0xFFE53935), Icons.Default.SportsSoccer),
)

/** Profil resmi: seçilen fotoğraf ya da hazır avatar. */
@Composable
fun ProfileAvatar(p: Profile, size: Dp, corner: Dp = size / 4) {
    AvatarImage(p.avatar, p.image, size, corner)
}

@Composable
fun AvatarImage(avatar: Int, image: String?, size: Dp, corner: Dp = size / 4) {
    val shape = RoundedCornerShape(corner)
    if (image != null && File(image).exists()) {
        AsyncImage(File(image), null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        val (a, b, icon) = Avatars[avatar.mod(Avatars.size)]
        Box(Modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
        }
    }
}

/** Açılıştaki "Kim izliyor?" ekranı. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePickerScreen(manage: Boolean = false, onDone: () -> Unit = {}) {
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var manageMode by remember { mutableStateOf(manage) }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF151824), Bg))).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(30.dp))
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.streambox.app.R.drawable.logo_dl), null,
            modifier = Modifier.size(64.dp).clip(CircleShape)
        )
        Spacer(Modifier.height(18.dp))
        Text(if (manageMode) "Profilleri yönet" else "Kim izliyor?", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(if (manageMode) "Düzenlemek için bir profile dokun" else "Profilini seç", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(30.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.widthIn(max = 900.dp),
        ) {
            Profiles.list.forEach { p ->
                ProfileTile(p.name, selected = p.id == Profiles.currentId && manageMode.not(), editBadge = manageMode, content = { ProfileAvatar(p, 104.dp, 26.dp) }) {
                    if (manageMode) editing = p else { Profiles.select(p); onDone() }
                }
            }
            if (Profiles.list.size < Profiles.MAX) ProfileTile("Profil ekle", false, false, content = {
                Box(Modifier.size(104.dp).clip(RoundedCornerShape(26.dp)).border(2.dp, Color(0x55FFFFFF), RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(46.dp))
                }
            }) { creating = true }
        }
        Spacer(Modifier.height(34.dp))
        OutlinedButton(onClick = { if (manage) onDone() else manageMode = !manageMode }, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFFFFF))) {
            Icon(if (manageMode) Icons.Default.Check else Icons.Default.Edit, null, tint = Color.White)
            Text(if (manageMode) "Bitti" else "Profilleri yönet", color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(120.dp))
    }
    if (creating) ProfileEditor(null) { creating = false }
    editing?.let { e -> ProfileEditor(e) { editing = null } }
}

@Composable
private fun ProfileTile(name: String, selected: Boolean, editBadge: Boolean, content: @Composable () -> Unit, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Column(
        Modifier.width(120.dp).scale(if (focused) 1.1f else 1f).clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.border(3.dp, if (focused || selected) Color.White else Color.Transparent, RoundedCornerShape(28.dp)).padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
            if (editBadge) Box(Modifier.matchParentSize().clip(RoundedCornerShape(26.dp)).background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
        Text(name, color = if (focused || selected) Color.White else Color(0xFFB8BCC4), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Profil oluşturma / düzenleme penceresi: ad, 10 hazır avatar ya da galeriden fotoğraf. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditor(profile: Profile?, onClose: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var avatar by remember { mutableStateOf(profile?.avatar ?: Profiles.list.size % Avatars.size) }
    var image by remember { mutableStateOf(profile?.image) }
    val tempId = remember { profile?.id ?: System.currentTimeMillis().toString(36) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) Profiles.importImage(context, uri, tempId)?.let { image = it }
    }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF171A21),
        title = { Text(if (profile == null) "Yeni profil" else "Profili düzenle", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(avatar, image, 72.dp, 18.dp)
                    OutlinedTextField(name, { name = it.take(20) }, label = { Text("Profil adı") }, singleLine = true,
                        modifier = Modifier.padding(start = 14.dp).weight(1f))
                }
                Text("Avatar seç", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatars.indices.forEach { i ->
                        val sel = image == null && avatar == i
                        val src = remember { MutableInteractionSource() }
                        val focused by src.collectIsFocusedAsState()
                        Box(
                            Modifier.border(2.5.dp, if (sel || focused) Color.White else Color.Transparent, RoundedCornerShape(16.dp)).padding(3.dp)
                                .clickable(interactionSource = src, indication = LocalIndication.current) { avatar = i; image = null }
                        ) { AvatarImage(i, null, 50.dp, 13.dp) }
                    }
                }
                OutlinedButton(onClick = { pick.launch("image/*") }, modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                    Icon(Icons.Default.PhotoLibrary, null); Text("Galeriden resim seç", Modifier.padding(start = 8.dp))
                }
                if (profile != null && Profiles.list.size > 1) TextButton(
                    onClick = { Profiles.remove(profile); onClose() },
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF453A))
                    Text("Profili sil", color = Color(0xFFFF453A), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (profile == null) {
                    if (Profiles.add(name, avatar, image) == null) Toast.makeText(context, "En fazla ${Profiles.MAX} profil", Toast.LENGTH_SHORT).show()
                } else Profiles.update(profile.copy(name = name.trim().ifBlank { profile.name }, avatar = avatar, image = image))
                onClose()
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Vazgeç") } },
    )
}
