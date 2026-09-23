package com.legendpolyfoams.lpplpms

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("lppl_pms_session", Context.MODE_PRIVATE)
    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(value) { prefs.edit().putString("token", value).apply() }
    fun clear() { prefs.edit().clear().apply() }
}
