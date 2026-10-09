package com.galaxyrio.gracelauncher.data

/**
 * Focus Mode is a launcher-only filter: it never disables, suspends or blocks an app,
 * and it leaves Android's own task switcher and notifications untouched.
 */
fun isFocusHidden(appKey: String, focusActive: Boolean, focusAppKeys: Set<String>): Boolean =
    focusActive && appKey in focusAppKeys
