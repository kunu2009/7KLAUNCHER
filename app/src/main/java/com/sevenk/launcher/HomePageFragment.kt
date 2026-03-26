package com.sevenk.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.widget.EditText
import android.widget.TextView
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

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
            val actions = mutableListOf<() -> Unit>()
            val labels = mutableListOf<String>()
            // Remove
            labels.add("Remove from this page")
            actions.add {
                act.removeFromHomePage(pageIndex, app.packageName)
                refreshData()
            }
            // Add to new folder
            labels.add("Add to New Folder")
            actions.add {
                showGlassInputSheet(
                    title = "New Folder",
                    hint = "Folder name",
                    submitLabel = "Create"
                ) { name ->
                    act.createFolder(pageIndex, name, app.packageName)
                    act.removeFromHomePage(pageIndex, app.packageName)
                    refreshData()
                }
            }
            // Add to existing folder (if any)
            val existingFolders = act.getFolders(pageIndex)
            if (existingFolders.isNotEmpty()) {
                labels.add("Add to Existing Folder")
                actions.add {
                    val folderActions = existingFolders.mapIndexed { which, folder ->
                        folder.name to {
                            act.addToFolder(pageIndex, which, app.packageName)
                            act.removeFromHomePage(pageIndex, app.packageName)
                            refreshData()
                        }
                    }
                    act.showLauncherActionSheet("Choose Folder", folderActions)
                }
            }
            // Move left
            if (pageIndex > 0) {
                labels.add("Move to Page ${'$'}pageIndex")
                actions.add {
                    act.moveHomeShortcut(pageIndex, pageIndex - 1, app.packageName)
                    act.refreshHomePages()
                }
            }
            // Move right
            if (pageIndex < totalPages - 1) {
                labels.add("Move to Page ${'$'}{pageIndex + 2}")
                actions.add {
                    act.moveHomeShortcut(pageIndex, pageIndex + 1, app.packageName)
                    act.refreshHomePages()
                }
            }
            val sheetActions = labels.mapIndexed { index, label -> label to { actions[index].invoke() } }
            act.showLauncherActionSheet(app.name, sheetActions)
        }, onFolderClick = { folderItem ->
            showFolderDialog(folderItem)
        }, onFolderLongPress = { folderItem ->
            val act = activity as? LauncherActivity ?: return@HomePageAdapter
            val actions = listOf(
                "Rename Folder" to {
                    showGlassInputSheet(
                        title = "Rename Folder",
                        hint = "Folder name",
                        initialValue = folderItem.folder.name,
                        submitLabel = "Save"
                    ) { newName ->
                        act.renameFolder(folderItem.pageIndex, folderItem.folderIndex, newName)
                        refreshData()
                    }
                },
                "Delete Folder (move apps back)" to {
                    act.deleteFolder(folderItem.pageIndex, folderItem.folderIndex, moveAppsBackToPage = true)
                    refreshData()
                }
            )
            act.showLauncherActionSheet(folderItem.folder.name, actions)
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
        val dialog = BottomSheetDialog(requireContext())
        content.setBackgroundResource(R.drawable.dialog_background)
        dialog.setContentView(content)
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

    private fun showGlassInputSheet(
        title: String,
        hint: String,
        initialValue: String = "",
        submitLabel: String = "Save",
        onSubmit: (String) -> Unit
    ) {
        val ctx = requireContext()
        val sheet = BottomSheetDialog(ctx)
        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
            setBackgroundResource(R.drawable.dialog_background)
        }

        val titleView = TextView(ctx).apply {
            text = title
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(12))
        }
        root.addView(titleView)

        val input = EditText(ctx).apply {
            this.hint = hint
            setText(initialValue)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x99FFFFFF.toInt())
            setBackgroundResource(R.drawable.glass_panel)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    onSubmit(text?.toString().orEmpty())
                    sheet.dismiss()
                    true
                } else {
                    false
                }
            }
        }
        val inputLp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
        root.addView(input, inputLp)

        val submit = TextView(ctx).apply {
            text = submitLabel
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.glass_panel)
            foreground = AppCompatResources.getDrawable(ctx, android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onSubmit(input.text?.toString().orEmpty())
                sheet.dismiss()
            }
        }
        val submitLp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        root.addView(submit, submitLp)

        val cancel = TextView(ctx).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(0xFF80CBC4.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = android.view.Gravity.CENTER
            setOnClickListener { sheet.dismiss() }
        }
        root.addView(cancel)

        sheet.setContentView(root)
        sheet.setOnShowListener {
            input.requestFocus()
            input.setSelection(input.text?.length ?: 0)
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        sheet.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_PAGE_INDEX = "page_index"
        fun newInstance(index: Int): HomePageFragment {
            val f = HomePageFragment()
            f.arguments = Bundle().apply { putInt(ARG_PAGE_INDEX, index) }
            return f
        }
    }
}
