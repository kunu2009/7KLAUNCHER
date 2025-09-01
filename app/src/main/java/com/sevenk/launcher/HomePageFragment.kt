package com.sevenk.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.widget.EditText
import android.widget.TextView
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomePageFragment : Fragment() {
    private var pageIndex: Int = 0
    private lateinit var recycler: RecyclerView
    private var pageWidgetsContainer: FrameLayout? = null
    private lateinit var adapter: HomePageAdapter

    fun getWidgetsContainer(): FrameLayout? = pageWidgetsContainer
    fun getPageIndex(): Int = pageIndex

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageIndex = requireArguments().getInt(ARG_PAGE_INDEX)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.home_page, container, false)
        recycler = view.findViewById(R.id.homePageRecycler)
        pageWidgetsContainer = view.findViewById<FrameLayout>(R.id.pageWidgetsContainer)
        // Apply user-configured home grid columns
        val prefs = requireContext().getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE)
        val homeCols = prefs.getInt("home_columns", 4).coerceIn(3, 6)
        recycler.layoutManager = GridLayoutManager(requireContext(), homeCols)
        recycler.setHasFixedSize(true)
        // Ensure long-press anywhere on the page can open Home Options
        view.isLongClickable = true
        view.setOnLongClickListener {
            (activity as? LauncherActivity)?.showHomeOptions()
            true
        }
        recycler.isLongClickable = true
        recycler.setOnLongClickListener {
            (activity as? LauncherActivity)?.showHomeOptions()
            true
        }
        adapter = HomePageAdapter(onAppClick = { app ->
            (activity as? LauncherActivity)?.launchApp(app)
        }, onAppLongPress = { app ->
            val act = activity as? LauncherActivity ?: return@HomePageAdapter
            val totalPages = act.getNormalHomePages()
            val options = mutableListOf<String>()
            val actions = mutableListOf<() -> Unit>()
            // Remove
            options.add("Remove from this page")
            actions.add {
                act.removeFromHomePage(pageIndex, app.packageName)
                refreshData()
            }
            // Add to new folder
            options.add("Add to New Folder")
            actions.add {
                val input = EditText(requireContext())
                input.hint = "Folder name"
                AlertDialog.Builder(requireContext())
                    .setTitle("New Folder")
                    .setView(input)
                    .setPositiveButton("Create") { d, _ ->
                        val name = input.text?.toString() ?: ""
                        act.createFolder(pageIndex, name, app.packageName)
                        act.removeFromHomePage(pageIndex, app.packageName)
                        refreshData()
                        d.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            // Add to existing folder (if any)
            val existingFolders = act.getFolders(pageIndex)
            if (existingFolders.isNotEmpty()) {
                options.add("Add to Existing Folder")
                actions.add {
                    val names = existingFolders.map { it.name }.toTypedArray()
                    AlertDialog.Builder(requireContext())
                        .setTitle("Choose Folder")
                        .setItems(names) { d, which ->
                            act.addToFolder(pageIndex, which, app.packageName)
                            act.removeFromHomePage(pageIndex, app.packageName)
                            refreshData()
                            d.dismiss()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            // Move left
            if (pageIndex > 0) {
                options.add("Move to Page ${'$'}pageIndex")
                actions.add {
                    act.moveHomeShortcut(pageIndex, pageIndex - 1, app.packageName)
                    act.refreshHomePages()
                }
            }
            // Move right
            if (pageIndex < totalPages - 1) {
                options.add("Move to Page ${'$'}{pageIndex + 2}")
                actions.add {
                    act.moveHomeShortcut(pageIndex, pageIndex + 1, app.packageName)
                    act.refreshHomePages()
                }
            }
            AlertDialog.Builder(requireContext())
                .setTitle(app.name)
                .setItems(options.toTypedArray()) { d, which ->
                    actions[which].invoke()
                    d.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }, onFolderClick = { folderItem ->
            showFolderDialog(folderItem)
        }, onFolderLongPress = { folderItem ->
            val act = activity as? LauncherActivity ?: return@HomePageAdapter
            val options = arrayOf("Rename Folder", "Delete Folder (move apps back)")
            AlertDialog.Builder(requireContext())
                .setTitle(folderItem.folder.name)
                .setItems(options) { d: android.content.DialogInterface, which: Int ->
                    when (which) {
                        0 -> {
                            val input = EditText(requireContext())
                            input.setText(folderItem.folder.name)
                            AlertDialog.Builder(requireContext())
                                .setTitle("Rename Folder")
                                .setView(input)
                                .setPositiveButton("Save") { dd: android.content.DialogInterface, _ ->
                                    act.renameFolder(folderItem.pageIndex, folderItem.folderIndex, input.text?.toString() ?: "")
                                    refreshData()
                                    dd.dismiss()
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                        1 -> {
                            act.deleteFolder(folderItem.pageIndex, folderItem.folderIndex, moveAppsBackToPage = true)
                            refreshData()
                        }
                    }
                    d.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }, iconPackHelper = (activity as? LauncherActivity)?.getIconPackHelper())
        recycler.adapter = adapter
        refreshData()
        return view
    }

    fun refreshData() {
        val act = activity as? LauncherActivity ?: return
        val packages = act.loadHomePageList(pageIndex)

        // Use the getAppList() method instead of directly accessing the private field
        val allApps = act.getAppList()

        // Fix the filter operation to handle null check properly
        val appItems = if (packages.isEmpty()) {
            emptyList()
        } else {
            allApps.filter { app -> app.packageName in packages }
                .map { app -> HomeItem.App(app) }
        }

        val folderItems = act.getFolders(pageIndex).mapIndexed { idx, f -> HomeItem.FolderItem(f, pageIndex, idx) }
        // Combine: folders first, then apps
        val combined = ArrayList<HomeItem>(folderItems.size + appItems.size)
        combined.addAll(folderItems)
        combined.addAll(appItems)
        adapter.submitList(combined)
    }

    private fun showFolderDialog(folderItem: HomeItem.FolderItem) {
        val act = activity as? LauncherActivity ?: return
        val folder = folderItem.folder

        // Use the getAppList() method instead of directly accessing the private field
        val allApps = act.getAppList()

        val folderApps = folder.apps
        val content = layoutInflater.inflate(R.layout.folder_dialog_content, null, false)
        val titleView = content.findViewById<TextView>(R.id.folderTitle)
        val titleEdit = content.findViewById<EditText>(R.id.folderTitleEdit)
        val btnRename = content.findViewById<View>(R.id.btnRenameFolder)
        val btnAdd = content.findViewById<View>(R.id.btnAddToFolder)
        val grid = content.findViewById<RecyclerView>(R.id.folderGrid)
        titleView.text = folder.name
        // Apply user-configured folder grid columns
        val prefs = requireContext().getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE)
        val folderCols = prefs.getInt("folder_columns", 4).coerceIn(3, 6)
        grid.layoutManager = GridLayoutManager(requireContext(), folderCols)
        grid.adapter = FolderGridAdapter(folderApps) { app ->
            (activity as? LauncherActivity)?.launchApp(app)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setView(content)
            .setNegativeButton("Close", null)
            .create()
        btnAdd.setOnClickListener {
            dialog.dismiss()
            act.startAddToFolderMode(folderItem.pageIndex, folderItem.folderIndex)
        }

        val beginInlineEdit: () -> Unit = {
            titleEdit.setText(titleView.text)
            titleView.visibility = View.GONE
            titleEdit.visibility = View.VISIBLE
            titleEdit.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(titleEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            titleEdit.setSelection(titleEdit.text?.length ?: 0)
        }

        val commitInlineEdit: () -> Unit = {
            val newName = titleEdit.text?.toString() ?: ""
            act.renameFolder(folderItem.pageIndex, folderItem.folderIndex, newName)
            titleView.text = newName.ifBlank { "Folder" }
            titleEdit.clearFocus()
            titleEdit.visibility = View.GONE
            titleView.visibility = View.VISIBLE
            refreshData()
        }

        titleView.setOnClickListener { beginInlineEdit() }
        btnRename.setOnClickListener { beginInlineEdit() }
        titleEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                commitInlineEdit(); true
            } else false
        }
        titleEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && titleEdit.visibility == View.VISIBLE) commitInlineEdit()
        }
        dialog.show()
    }

    companion object {
        private const val ARG_PAGE_INDEX = "page_index"
        fun newInstance(index: Int): HomePageFragment {
            val f = HomePageFragment()
            f.arguments = Bundle().apply { putInt(ARG_PAGE_INDEX, index) }
            return f
        }
    }
}
