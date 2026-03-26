package com.sevenk.launcher.ecosystem

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class FileForgeActivity : AppCompatActivity() {

    private val vaultDir by lazy { File(filesDir, "file_forge").apply { mkdirs() } }
    private val prefs by lazy { getSharedPreferences("sevenk_file_forge_meta", MODE_PRIVATE) }
    private lateinit var fileListContainer: LinearLayout
    private lateinit var statsText: TextView
    private var query: String = ""
    private var activeFilter: Filter = Filter.ALL
    private val metaByName = mutableMapOf<String, FileMeta>()

    private data class FileMeta(
        val favorite: Boolean,
        val tags: Set<String>
    )

    private enum class Filter { ALL, FAVORITES, TAGGED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "7K File Forge"

        val root = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF171717.toInt())
        }

        content.addView(TextView(this).apply {
            text = "7K File Forge"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
        })

        content.addView(TextView(this).apply {
            text = "Offline internal file workspace (safe app storage)"
            textSize = 13f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(0, 8, 0, 16)
        })

        val searchInput = EditText(this).apply {
            hint = "Search files or tags"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9E9E9E.toInt())
            setBackgroundColor(0xFF202020.toInt())
            setPadding(12, 12, 12, 12)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty().trim()
                    renderFiles()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        content.addView(searchInput)

        statsText = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF80CBC4.toInt())
            setPadding(10, 10, 10, 10)
            setBackgroundColor(0xFF1D2426.toInt())
        }
        content.addView(statsText)

        val allBtn = Button(this).apply {
            text = "All"
            isAllCaps = false
            setOnClickListener { activeFilter = Filter.ALL; renderFiles() }
        }
        val favBtn = Button(this).apply {
            text = "Favorites"
            isAllCaps = false
            setOnClickListener { activeFilter = Filter.FAVORITES; renderFiles() }
        }
        val taggedBtn = Button(this).apply {
            text = "Tagged"
            isAllCaps = false
            setOnClickListener { activeFilter = Filter.TAGGED; renderFiles() }
        }
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(allBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(favBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(taggedBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(filterRow)

        val createBtn = Button(this).apply {
            text = "+ New Note File"
            isAllCaps = false
            setOnClickListener { showCreateFileDialog() }
        }
        content.addView(createBtn)

        val templateBtn = Button(this).apply {
            text = "+ Quick Template File"
            isAllCaps = false
            setOnClickListener {
                val name = "template_${System.currentTimeMillis()}.txt"
                val file = File(vaultDir, name)
                file.writeText("Title:\nSummary:\nAction Items:\n")
                metaByName[file.name] = FileMeta(favorite = false, tags = setOf("template"))
                saveMeta()
                renderFiles()
            }
        }
        content.addView(templateBtn)

        fileListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }
        content.addView(fileListContainer)

        root.addView(content)
        setContentView(root)

        loadMeta()
        renderFiles()
    }

    private fun showCreateFileDialog() {
        val input = EditText(this).apply { hint = "example: project_ideas" }
        AlertDialog.Builder(this)
            .setTitle("Create file")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val base = input.text?.toString()?.trim().orEmpty()
                if (base.isNotBlank()) {
                    val file = File(vaultDir, "$base.txt")
                    if (!file.exists()) {
                        file.writeText("7K File Forge\nCreated: ${System.currentTimeMillis()}\n")
                        metaByName[file.name] = FileMeta(favorite = false, tags = emptySet())
                        saveMeta()
                    }
                    renderFiles()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderFiles() {
        fileListContainer.removeAllViews()
        val allFiles = (vaultDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList())
        val files = allFiles.filter { file ->
            val meta = metaOf(file)
            val queryMatch = if (query.isBlank()) true else {
                file.name.contains(query, ignoreCase = true) || meta.tags.any { it.contains(query, ignoreCase = true) }
            }
            val filterMatch = when (activeFilter) {
                Filter.ALL -> true
                Filter.FAVORITES -> meta.favorite
                Filter.TAGGED -> meta.tags.isNotEmpty()
            }
            queryMatch && filterMatch
        }

        val favCount = allFiles.count { metaOf(it).favorite }
        val taggedCount = allFiles.count { metaOf(it).tags.isNotEmpty() }
        statsText.text = "Files ${allFiles.size} • Showing ${files.size} • Favorites $favCount • Tagged $taggedCount • Filter ${activeFilter.name}"

        if (files.isEmpty()) {
            fileListContainer.addView(TextView(this).apply {
                text = if (query.isBlank()) "No files yet. Create one to start forging." else "No files match '$query'."
                textSize = 14f
                setTextColor(0xFF9E9E9E.toInt())
            })
            return
        }

        files.forEach { file ->
            val meta = metaOf(file)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(0xFF242424.toInt())
            }

            card.addView(TextView(this).apply {
                val star = if (meta.favorite) "★ " else ""
                text = "$star${file.name}"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
            })

            card.addView(TextView(this).apply {
                val tagsText = if (meta.tags.isEmpty()) "no tags" else meta.tags.joinToString(", ")
                text = "${file.length()} bytes • tags: $tagsText"
                setTextColor(0xFF80CBC4.toInt())
                textSize = 11f
            })

            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val openBtn = Button(this).apply {
                text = "Open"
                isAllCaps = false
                setOnClickListener { showFileViewer(file) }
            }
            val renameBtn = Button(this).apply {
                text = "Rename"
                isAllCaps = false
                setOnClickListener { showRenameDialog(file) }
            }
            val duplicateBtn = Button(this).apply {
                text = "Duplicate"
                isAllCaps = false
                setOnClickListener {
                    val dup = File(vaultDir, file.nameWithoutExtension + "_copy.txt")
                    dup.writeText(file.readText())
                    metaByName[dup.name] = meta.copy(favorite = false)
                    saveMeta()
                    renderFiles()
                }
            }
            val favBtn = Button(this).apply {
                text = if (meta.favorite) "Unstar" else "Star"
                isAllCaps = false
                setOnClickListener {
                    toggleFavorite(file)
                    renderFiles()
                }
            }
            val tagBtn = Button(this).apply {
                text = "Tags"
                isAllCaps = false
                setOnClickListener { showTagDialog(file) }
            }
            val deleteBtn = Button(this).apply {
                text = "Delete"
                isAllCaps = false
                setOnClickListener {
                    file.delete()
                    metaByName.remove(file.name)
                    saveMeta()
                    renderFiles()
                }
            }

            actions.addView(openBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(renameBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(duplicateBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(favBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(tagBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(deleteBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(actions)

            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 10
            fileListContainer.addView(card, lp)
        }
    }

    private fun showFileViewer(file: File) {
        val input = EditText(this).apply {
            setText(file.readText())
            minLines = 8
        }
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                file.writeText(input.text?.toString().orEmpty())
                renderFiles()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showRenameDialog(file: File) {
        val input = EditText(this).apply {
            setText(file.nameWithoutExtension)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename file")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val base = input.text?.toString()?.trim().orEmpty()
                if (base.isNotBlank()) {
                    val target = File(vaultDir, "$base.txt")
                    if (!target.exists()) {
                        val oldMeta = metaByName.remove(file.name)
                        file.renameTo(target)
                        if (oldMeta != null) metaByName[target.name] = oldMeta
                        saveMeta()
                        renderFiles()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun metaOf(file: File): FileMeta = metaByName[file.name] ?: FileMeta(false, emptySet())

    private fun toggleFavorite(file: File) {
        val m = metaOf(file)
        metaByName[file.name] = m.copy(favorite = !m.favorite)
        saveMeta()
    }

    private fun showTagDialog(file: File) {
        val input = EditText(this).apply {
            hint = "comma,separated,tags"
            setText(metaOf(file).tags.joinToString(","))
        }
        AlertDialog.Builder(this)
            .setTitle("Edit tags")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val tags = input.text?.toString().orEmpty()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                val old = metaOf(file)
                metaByName[file.name] = old.copy(tags = tags)
                saveMeta()
                renderFiles()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadMeta() {
        val raw = prefs.getString("file_meta", "") ?: ""
        metaByName.clear()
        if (raw.isBlank()) return
        raw.split("||").forEach { token ->
            val p = token.split("\t")
            if (p.size < 3) return@forEach
            val name = p[0]
            val fav = p[1] == "1"
            val tags = p[2].split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
            metaByName[name] = FileMeta(favorite = fav, tags = tags)
        }
    }

    private fun saveMeta() {
        val raw = metaByName.entries.joinToString("||") { (name, meta) ->
            listOf(
                name.replace("\t", " "),
                if (meta.favorite) "1" else "0",
                meta.tags.joinToString(",") { it.replace(",", " ").replace("\t", " ") }
            ).joinToString("\t")
        }
        prefs.edit().putString("file_meta", raw).apply()
    }
}
