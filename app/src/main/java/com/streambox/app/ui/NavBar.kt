package com.streambox.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class NavTab(val route: Route, val label: String, val icon: ImageVector)

private val BarTop = Color(0xFF2A2B30)
private val BarBottom = Color(0xFF17181B)
private val Muted2 = Color(0xFFA3A6AD)

/**
 * Yüzen koyu menü: solda yuvarlak logo, sağda yalnızca ikonlar.
 * Seçili sekmenin arkasında, kenarı vurgu rengiyle çizilmiş ve kayarak hareket eden yükseltilmiş bir yuva vardır.
 */
@Composable
fun FloatingNavBar(tabs: List<NavTab>, selected: Route, onSelect: (Route) -> Unit, modifier: Modifier = Modifier) {
    val selIndex = tabs.indexOfFirst { it.route == selected }.coerceAtLeast(0)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .shadow(24.dp, RoundedCornerShape(36.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(36.dp))
                .background(Brush.verticalGradient(listOf(BarTop, BarBottom)))
                .border(1.dp, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x08FFFFFF))), RoundedCornerShape(36.dp))
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo düğmesi (ana sayfa)
            Box(
                Modifier.size(56.dp).shadow(10.dp, CircleShape).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF34363C), Color(0xFF1B1C20))))
                    .border(1.dp, Color(0x22FFFFFF), CircleShape)
                    .clickable { onSelect(tabs.first().route) },
                contentAlignment = Alignment.Center
            ) {
                AppLogo(46.dp)
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(start = 6.dp)) {
                val itemW = maxWidth / tabs.size
                val x by animateDpAsState(itemW * selIndex, spring(dampingRatio = 0.75f, stiffness = 380f), label = "slot")
                // Seçili yuva: yükseltilmiş, kenarı vurgulu, alt kenarda bara kıvrılarak bağlanır
                Canvas(Modifier.offset(x = x).width(itemW).fillMaxHeight()) {
                    val w = size.width; val h = size.height
                    val top = h * 0.10f
                    val r = 22.dp.toPx()
                    val l = 4.dp.toPx(); val rr = w - 4.dp.toPx()
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFF3A3C43), Color(0xFF232429)), startY = top, endY = h),
                        topLeft = Offset(l, top), size = Size(rr - l, h - top + r),
                        cornerRadius = CornerRadius(r, r)
                    )
                    val path = Path().apply {
                        moveTo(l - 14.dp.toPx(), h - 1.dp.toPx())
                        quadraticTo(l, h - 1.dp.toPx(), l, h - 18.dp.toPx())
                        lineTo(l, top + r)
                        quadraticTo(l, top, l + r, top)
                        lineTo(rr - r, top)
                        quadraticTo(rr, top, rr, top + r)
                        lineTo(rr, h - 18.dp.toPx())
                        quadraticTo(rr, h - 1.dp.toPx(), rr + 14.dp.toPx(), h - 1.dp.toPx())
                    }
                    drawPath(
                        path,
                        Brush.horizontalGradient(listOf(Accent.copy(alpha = 0.2f), Accent, Accent, Accent)),
                        style = Stroke(2.dp.toPx())
                    )
                    // Örnekteki gibi: vurgu çizgisi barın alt kenarı boyunca sağa doğru solarak devam eder
                    val y = h - 1.dp.toPx()
                    val from = rr + 14.dp.toPx()
                    val to = from + w * 2.2f
                    drawLine(
                        Brush.horizontalGradient(listOf(Accent, Accent.copy(alpha = 0f)), startX = from, endX = to),
                        Offset(from, y), Offset(to, y), strokeWidth = 2.dp.toPx()
                    )
                }
                Row(Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { i, t ->
                        NavIcon(t, i == selIndex, Modifier.width(itemW).fillMaxHeight()) { onSelect(t.route) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavIcon(tab: NavTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val tint by animateColorAsState(if (selected) Accent else if (focused) Color.White else Muted2, label = "navTint")
    val lift by animateDpAsState(if (selected) (-3).dp else 0.dp, spring(dampingRatio = 0.55f), label = "navLift")
    Column(
        modifier.clickable(interactionSource = src, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (focused && !selected) Color(0x1FFFFFFF) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(if (selected) 27.dp else 25.dp).offset(y = lift))
        }
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) Accent else Color.Transparent))
    }
}

/** TV / tablet: sol kenarda dikey menü (yalnızca ikonlar). */
@Composable
fun SideNavBar(tabs: List<NavTab>, selected: Route, onSelect: (Route) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .padding(12.dp)
            .width(76.dp)
            .clip(RoundedCornerShape(38.dp))
            .background(Brush.horizontalGradient(listOf(BarTop, BarBottom)))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(38.dp))
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppLogo(52.dp)
        tabs.forEach { t ->
            val sel = selected == t.route
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (sel) Color(0xFF34363C) else Color.Transparent)
                    .then(if (sel) Modifier.border(2.dp, Accent, RoundedCornerShape(20.dp)) else Modifier),
                contentAlignment = Alignment.Center
            ) { NavIcon(t, sel, Modifier.fillMaxSize()) { onSelect(t.route) } }
        }
    }
}

/** Seçili uygulama ikonunu gösterir (Klasik ise mavi oynat dairesi). */
@Composable
fun AppLogo(size: androidx.compose.ui.unit.Dp) {
    val res = com.streambox.app.AppIcon.current.logo
    if (res != null) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(res), "StreamBox",
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        Box(Modifier.size(size * 0.66f).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, "StreamBox", tint = Color.White, modifier = Modifier.size(size * 0.45f))
        }
    }
}
