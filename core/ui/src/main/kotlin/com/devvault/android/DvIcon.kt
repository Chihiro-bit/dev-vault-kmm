package com.devvault.android

import androidx.annotation.DrawableRes
import com.devvault.core.ui.R

/** 图标为 res/drawable/ic_*.xml 矢量图，路径与桌面端 Icon.tsx 相同（24×24，2dp 圆头描边） */
enum class DvIcon(@DrawableRes val res: Int) {
    Copy(R.drawable.ic_copy),
    Clone(R.drawable.ic_clone),
    Eye(R.drawable.ic_eye),
    EyeOff(R.drawable.ic_eye_off),
    Star(R.drawable.ic_star),
    Edit(R.drawable.ic_edit),
    Trash(R.drawable.ic_trash),
    Plus(R.drawable.ic_plus),
    Search(R.drawable.ic_search),
    External(R.drawable.ic_external),
    X(R.drawable.ic_x),
    Up(R.drawable.ic_up),
    Down(R.drawable.ic_down),
    Right(R.drawable.ic_right),
    Message(R.drawable.ic_message),
    ListIcon(R.drawable.ic_list_icon),
    Back(R.drawable.ic_back),
    Repeat(R.drawable.ic_repeat),
    Subtask(R.drawable.ic_subtask),
    Send(R.drawable.ic_send),
    Gear(R.drawable.ic_gear),
    Cloud(R.drawable.ic_cloud),
    Logout(R.drawable.ic_logout),
    Sliders(R.drawable.ic_sliders),
    Layers(R.drawable.ic_layers),
    Inbox(R.drawable.ic_inbox),
    Key(R.drawable.ic_key),
    Check(R.drawable.ic_check),
    Tick(R.drawable.ic_tick),
    Refresh(R.drawable.ic_refresh),
    Calendar(R.drawable.ic_calendar),
    Lock(R.drawable.ic_lock),
    User(R.drawable.ic_user),
    Git(R.drawable.ic_git),
    Shield(R.drawable.ic_shield),
    Server(R.drawable.ic_server),
    Database(R.drawable.ic_database),
    Bell(R.drawable.ic_bell),
    Wand(R.drawable.ic_wand);
}
