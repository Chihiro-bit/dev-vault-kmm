package com.devvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devvault.AppStore
import com.devvault.ui.components.BtnKind
import com.devvault.ui.components.DvButton
import com.devvault.ui.components.noRippleClick
import com.devvault.ui.theme.Dv
import com.devvault.ui.theme.DvIcon
import com.devvault.ui.theme.DvIconView

/** 应用锁界面（对应桌面端的锁屏页），出现时自动弹出系统验证 */
@Composable
fun LockScreen() {
    val store = remember { AppStore.get() }
    val c = Dv.colors
    LaunchedEffect(Unit) { store.unlock() }
    Box(Modifier.fillMaxSize().background(c.bg).noRippleClick {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(c.primary), contentAlignment = Alignment.Center) {
                DvIconView(DvIcon.Key, Color.White, 34.dp)
            }
            Spacer(Modifier.height(20.dp))
            Text("DevVault 已锁定", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text("使用指纹、面容或锁屏密码解锁", fontSize = 14.sp, color = c.muted)
            Spacer(Modifier.height(28.dp))
            DvButton("解锁", { store.unlock() }, kind = BtnKind.Primary, icon = DvIcon.Lock, modifier = Modifier.width(200.dp).padding(horizontal = 8.dp))
        }
    }
}
