package com.sevenk.launcher

/**
 * Sealed class representing an item in a sectioned list (app drawer sections)
 */
sealed class SectionedItem {
    data class Header(val name: String) : SectionedItem()
    data class App(val app: AppInfo) : SectionedItem()
}
