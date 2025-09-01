package com.sevenk.launcher

import android.content.Context
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.sevenk.launcher.util.Perf
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.DefaultItemAnimator

class StanHomeFragment : Fragment() {

    private lateinit var chatList: RecyclerView
    private lateinit var chatInput: EditText
    private lateinit var chatSend: Button

    private val messages = mutableListOf<Message>()
    private val adapter = ChatAdapter(messages)

    private val prefs by lazy { requireContext().getSharedPreferences("sevenk_launcher_prefs", Context.MODE_PRIVATE) }
    private val KEY_TODOS_GLOBAL = "todos_global"

    private fun loadTodos(): MutableList<String> {
        val raw = prefs.getString(KEY_TODOS_GLOBAL, "") ?: ""
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveTodos(list: List<String>) {
        prefs.edit().putString(KEY_TODOS_GLOBAL, list.joinToString("\n")).apply()
    }

    private fun broadcastTodoRefresh() {
        try {
            val i = Intent(TodoWidgetProvider.ACTION_REFRESH)
            requireContext().sendBroadcast(i)
        } catch (_: Throwable) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.stan_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Perf.begin("StanHome.onViewCreated")
        chatList = view.findViewById(R.id.chatList)
        chatInput = view.findViewById(R.id.chatInput)
        chatSend = view.findViewById(R.id.chatSend)
        val chatBar = view.findViewById<View>(R.id.chatBar)

        chatList.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        chatList.adapter = adapter
        // Subtle slide/fade animator for new messages
        chatList.itemAnimator = object : DefaultItemAnimator() {
            init {
                addDuration = 140
                removeDuration = 100
                moveDuration = 140
                changeDuration = 100
            }
            override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
                holder.itemView.alpha = 0f
                holder.itemView.translationY = 12f
                val anim = holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(addDuration)
                anim.withEndAction { dispatchAddFinished(holder) }
                anim.start()
                return true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { chatBar?.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)) } catch (_: Throwable) {}
            try { chatList.setRenderEffect(RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP)) } catch (_: Throwable) {}
        }

        chatSend.setOnClickListener { submit() }
        chatInput.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {
                submit(); return@OnEditorActionListener true
            }
            false
        })
        Perf.end()
    }

    private fun submit() {
        Perf.begin("StanHome.submit")
        val text = chatInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        addUser(text)
        chatInput.setText("")
        handleCommand(text)
        // Light haptic feedback to acknowledge send
        try { view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Throwable) {}
        Perf.end()
    }

    private fun handleCommand(input: String) {
        val lower = input.trim().lowercase()
        val act = activity as? LauncherActivity ?: run { addBot("Launcher not available."); return }

        // Help
        if (lower == "help" || lower == "commands") {
            addBot(
                "Commands:\n" +
                "- search <q> | yt <q> | wiki <q> | map <place> | news\n" +
                "- wifi on/off | bt on/off | brightness <0-100> | volume <0-100> | dnd on/off\n" +
                "- call <number> | msg <number> <text> | email <to> <subject> <body>\n" +
                "- todo add <task> | todo show | note <text> | remind <time> <text> (stubs)\n" +
                "- apps | recent | uninstall <app> | weather <city> | date | time\n" +
                "- play music | pause music | next song | prev song (limited)\n" +
                "- 7k life open today | 7k studio new project | 7k polyglot learn <lang> | 7k itihas topic <topic> (stubs)"
            )
            return
        }

        // Web & Search
        when {
            lower.startsWith("search ") -> {
                val q = input.removePrefix("search ")
                openUrl("https://www.google.com/search?q=" + urlEncode(q)); addBot("Searching Google for '$q'…"); return
            }
            lower.startsWith("yt ") -> {
                val q = input.removePrefix("yt ")
                openUrl("https://www.youtube.com/results?search_query=" + urlEncode(q)); addBot("Searching YouTube for '$q'…"); return
            }
            lower.startsWith("wiki ") -> {
                val q = input.removePrefix("wiki ")
                openUrl("https://en.wikipedia.org/w/index.php?search=" + urlEncode(q)); addBot("Searching Wikipedia for '$q'…"); return
            }
            lower.startsWith("map ") -> {
                val place = input.removePrefix("map ")
                openUrl("https://www.google.com/maps/search/" + urlEncode(place)); addBot("Opening map for '$place'…"); return
            }
            lower == "news" -> { openUrl("https://news.google.com"); addBot("Opening News…"); return }
        }

        // Utilities
        when (lower) {
            "apps" -> {
                val names = act.getAppList().take(50).joinToString { it.name }
                addBot(if (names.isEmpty()) "No apps found." else "Apps: $names"); return
            }
            "recent" -> {
                val pkgs = act.getRecentPackages()
                if (pkgs.isEmpty()) { addBot("No recent apps."); return }
                val names = pkgs.mapNotNull { p -> act.getAppList().find { it.packageName == p }?.name }
                addBot("Recent: ${names.joinToString()}"); return
            }
            "date" -> { addBot(java.text.SimpleDateFormat("EEE, MMM d, yyyy").format(java.util.Date())); return }
            "time" -> { addBot(java.text.SimpleDateFormat("h:mm a").format(java.util.Date())); return }
            "news" -> { openUrl("https://news.google.com"); addBot("Opening News…"); return }
        }
        // System toggles (limited: open settings panels as needed)
        when (lower) {
            "wifi on", "wifi off" -> {
                // Open Wi-Fi settings panel (no direct toggle without permissions)
                try { startActivity(android.content.Intent(android.provider.Settings.Panel.ACTION_WIFI)) } catch (_: Throwable) {
                    try { startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) } catch (_: Throwable) {}
                }
                addBot("Opening Wi‑Fi settings…"); return
            }
            "bt on", "bt off" -> {
                try { startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Throwable) {}
                addBot("Opening Bluetooth settings…"); return
            }
            "dnd on", "dnd off" -> {
                if (lower == "dnd on") { try { startActivity(android.content.Intent("android.settings.ZEN_MODE_SETTINGS")); addBot("Open Do Not Disturb settings…") } catch (_: Throwable) { addBot("Cannot open DND settings.") }; return }
            }
            "play music" -> { openUrl("https://music.youtube.com"); addBot("Opening music…"); return }
            "pause music", "next song", "prev song" -> { addBot("Media controls are limited; use your player's UI."); return }
        }
        if (lower.startsWith("brightness ")) {
            addBot("Opening Display settings to adjust brightness…")
            try { startActivity(android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)) } catch (_: Throwable) {}
            return
        }
        if (lower.startsWith("volume ")) {
            val amt = lower.removePrefix("volume ").trim().toIntOrNull()
            val am = requireContext().getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            if (amt != null) {
                val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val set = (amt * max / 100).coerceIn(0, max)
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, set, 0)
                addBot("Set media volume to ${amt}%")
            } else addBot("Invalid volume. Use 0-100.")
            return
        }
        if (lower == "flashlight on" || lower == "flashlight off" || lower == "screenshot") {
            addBot("This action isn't supported by the launcher due to system restrictions. Use quick settings/hardware keys.")
            return
        }
        if (lower.startsWith("weather ")) {
            val city = input.removePrefix("weather ").trim()
            openUrl("https://www.google.com/search?q=" + urlEncode("weather $city")); addBot("Showing weather for '$city'…"); return
        }
        if (lower.startsWith("uninstall ")) {
            val q = input.removePrefix("uninstall ").trim()
            val match = findBestMatch(act.getAppList(), q)
            if (match != null) {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:" + match.packageName))
                    startActivity(intent)
                    addBot("Opening uninstall for ${match.name}…")
                } catch (_: Throwable) { addBot("Cannot open uninstall screen.") }
            } else addBot("App not found.")
            return
        }

        // Communication
        if (lower.startsWith("call ")) {
            val num = input.removePrefix("call ").trim()
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + num))
                startActivity(intent); addBot("Opening dialer…")
            } catch (_: Throwable) { addBot("Cannot open dialer.") }
            return
        }
        if (lower.startsWith("msg ")) {
            val rest = input.removePrefix("msg ").trim()
            val space = rest.indexOf(' ')
            if (space <= 0) { addBot("Usage: msg <number> <text>"); return }
            val num = rest.substring(0, space)
            val body = rest.substring(space + 1)
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("sms:" + num)
                    putExtra("sms_body", body)
                }
                startActivity(intent); addBot("Opening SMS…")
            } catch (_: Throwable) { addBot("Cannot open SMS.") }
            return
        }
        if (lower.startsWith("email ")) {
            val rest = input.removePrefix("email ").trim()
            // naive split: to subject body
            val parts = rest.split(' ', limit = 3)
            if (parts.size < 3) { addBot("Usage: email <to> <subject> <body>"); return }
            val to = parts[0]; val subject = parts[1]; val body = parts[2]
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:" + to)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                    putExtra(android.content.Intent.EXTRA_TEXT, body)
                }
                startActivity(intent); addBot("Opening email compose…")
            } catch (_: Throwable) { addBot("Cannot open email.") }
            return
        }

        // Productivity: simple TODOs backed by SharedPreferences
        if (lower.startsWith("todo add ")) {
            val task = input.removePrefix("todo add ").trim()
            if (task.isBlank()) { addBot("Usage: todo add <task>"); return }
            val list = loadTodos()
            list.add(task)
            saveTodos(list)
            addBot("Added: '$task' (total ${list.size})")
            broadcastTodoRefresh()
            return
        }
        if (lower == "todo show") {
            val list = loadTodos()
            if (list.isEmpty()) { addBot("No TODOs saved yet."); return }
            val preview = list.take(10).mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
            val more = if (list.size > 10) "\n…and ${list.size - 10} more" else ""
            addBot("TODOs:\n$preview$more")
            return
        }
        if (lower.startsWith("note ")) { addBot("Saved note (temporary)."); return }
        if (lower.startsWith("remind ")) { addBot("Reminder set (conceptually). Integrate with alarms next."); return }

        // Pin / Unpin / Hide / Show (basic)
        if (lower.startsWith("pin ")) {
            val q = input.removePrefix("pin ").trim()
            val m = findBestMatch(act.getAppList(), q)
            if (m != null) {
                act.addPackageToListPublic("dock_packages", m.packageName); act.rebuildDockPublic(); addBot("Pinned ${m.name} to Dock.")
            } else addBot("App not found.")
            return
        }
        if (lower.startsWith("unpin ")) {
            val q = input.removePrefix("unpin ").trim()
            val m = findBestMatch(act.getAppList(), q)
            if (m != null) {
                act.removePackageFromListPublic("dock_packages", m.packageName); act.removeFromAllHomePagesPublic(m.packageName); act.rebuildDockPublic(); addBot("Unpinned ${m.name}.")
            } else addBot("App not found.")
            return
        }

        // Open/Launch app fallback
        val verbs = listOf("open", "launch", "start")
        var query = lower
        for (v in verbs) if (lower.startsWith("$v ")) { query = lower.removePrefix("$v ").trim(); break }
        if (query.isBlank()) { addBot("Try 'open whatsapp' or 'search maps'"); return }
        val match = findBestMatch(act.getAppList(), query)
        if (match == null) { addBot("Couldn't find an app matching '$query'."); return }
        addBot("Opening ${match.name}…"); act.launchApp(match)
    }

    private fun urlEncode(s: String): String = try {
        java.net.URLEncoder.encode(s, "UTF-8")
    } catch (_: Throwable) { s }

    private fun openUrl(url: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        } catch (_: Throwable) {
            addBot("Can't open browser.")
        }
    }

    private fun findBestMatch(apps: List<AppInfo>, query: String): AppInfo? {
        val q = query.trim()
        if (q.isEmpty()) return null
        // Exact name match
        apps.firstOrNull { it.name.equals(q, ignoreCase = true) }?.let { return it }
        // Contains in name
        apps.firstOrNull { it.name.contains(q, ignoreCase = true) }?.let { return it }
        // Contains in package
        apps.firstOrNull { it.packageName.contains(q.replace(" ", ""), ignoreCase = true) }?.let { return it }
        // Token-based naive score
        val tokens = q.split(" ", "-", "_", ".").filter { it.isNotBlank() }
        var best: AppInfo? = null
        var bestScore = 0
        for (app in apps) {
            var score = 0
            for (t in tokens) {
                if (app.name.contains(t, ignoreCase = true)) score += 2
                if (app.packageName.contains(t, ignoreCase = true)) score += 1
            }
            if (score > bestScore) {
                bestScore = score
                best = app
            }
        }
        return if (bestScore > 0) best else null
    }

    private fun addUser(text: String) {
        messages.add(Message(text, true))
        adapter.notifyItemInserted(messages.size - 1)
        chatList.smoothScrollToPosition(messages.size - 1)
    }

    private fun addBot(text: String) {
        messages.add(Message(text, false))
        adapter.notifyItemInserted(messages.size - 1)
        chatList.smoothScrollToPosition(messages.size - 1)
    }

    data class Message(val text: String, val fromUser: Boolean)

    class ChatAdapter(private val items: List<Message>) : RecyclerView.Adapter<ChatVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
            val tv = v.findViewById<TextView>(R.id.messageText)
            return ChatVH(v, tv)
        }

        override fun onBindViewHolder(holder: ChatVH, position: Int) {
            val msg = items[position]
            val prefix = if (msg.fromUser) "You: " else "Stan: "
            holder.text.text = prefix + msg.text
            // Choose bubble background and alignment
            val bg = if (msg.fromUser) R.drawable.chat_bubble_user else R.drawable.chat_bubble_bot
            holder.text.background = ContextCompat.getDrawable(holder.text.context, bg)
            val lp = holder.text.layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                // Ensure some horizontal spacing from edges
                lp.leftMargin = holder.text.resources.displayMetrics.density.let { (12f * it).toInt() }
                lp.rightMargin = holder.text.resources.displayMetrics.density.let { (12f * it).toInt() }
                holder.text.layoutParams = lp
            }
            val flp = holder.text.layoutParams
            if (flp is ViewGroup.LayoutParams && holder.itemView is android.widget.FrameLayout) {
                val params = android.widget.FrameLayout.LayoutParams(flp.width, flp.height)
                params.gravity = if (msg.fromUser) Gravity.END else Gravity.START
                holder.text.layoutParams = params
            } else if (holder.itemView is android.widget.FrameLayout) {
                val params = android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.gravity = if (msg.fromUser) Gravity.END else Gravity.START
                holder.text.layoutParams = params
            }
            holder.text.alpha = if (msg.fromUser) 0.97f else 1f
        }

        override fun getItemCount(): Int = items.size
    }

    class ChatVH(itemView: View, val text: TextView) : RecyclerView.ViewHolder(itemView)
}
