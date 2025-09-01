package com.sevenk.launcher

/**
 * Data class representing an item on the home screen (app or folder)
 */
sealed class HomeItem {
    data class App(val appInfo: AppInfo) : HomeItem()
    data class FolderItem(val folder: Folder, val pageIndex: Int, val folderIndex: Int) : HomeItem()
}
