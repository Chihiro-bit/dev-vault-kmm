package com.devvault.ui

import androidx.compose.runtime.Composable

/** Android 返回键 / 返回手势；iOS 无系统返回键，由页面上的返回按钮处理 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
