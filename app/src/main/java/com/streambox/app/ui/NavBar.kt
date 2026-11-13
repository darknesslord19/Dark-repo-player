package com.streambox.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NavTab(val route: Route, val label: String, val icon: ImageVector)

private val BarBg = Color(0xFF14161B)
private val BarStroke = Color(0xFF262A33)

/** Telefon: ekranın altında yüzen, yuvarlak köşeli koyu menü. */
@Composable
fun FloatingNavBar(tabs: List<NavTab>, selected: Route, onSelect: (Route) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(18.dp, RoundedCornerShape(34.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(34.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1D23), BarBg)))
                .border(1.dp, BarStroke, RoundedCornerShape(34.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            tabs.forEach { t ->
                NavItem(t, selected == t.route, Modifier.weight(1f)) { onSelect(t.route) }
            }
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val bg by animateColorAsState(
        when { selected -> Accent.copy(alpha = 0.16f); focused -> Color.White.copy(alpha = 0.08f); else -> Color.Transparent },
        label = "navBg"
    )
    val tint by animateColorAsState(if (selected) Accent else Color(0xFF8E939B), label = "navTint")
    val lift by animateDpAsState(if (selected) (-2).dp else 0.dp, spring(dampingRatio = 0.6f), label = "navLift")
    Column(
        modifier
            .fillMaxHeight()
            .padding(vertical = 7.dp, horizontal = 3.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .then(if (focused) Modifier.border(1.5.dp, Accent, RoundedCornerShape(22.dp)) else Modifier)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(24.dp).offset(y = lift))
        Text(
            tab.label, color = if (selected) Color.White else Color(0xFF8E939B), fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
        Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (selected) Accent else Color.Transparent))
    }
}

/** TV / tablet: sol kenarda dikey menü. */
@Composable
fun SideNavBar(tabs: List<NavTab>, selected: Route, onSelect: (Route) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .padding(12.dp)
            .width(92.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(BarBg)
            .border(1.dp, BarStroke, RoundedCornerShape(28.dp))
            .padding(vertical = 18.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        tabs.forEach { t -> NavItem(t, selected == t.route, Modifier.fillMaxWidth().height(76.dp)) { onSelect(t.route) } }
    }
}
