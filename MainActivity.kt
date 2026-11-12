package com.example.dmc3launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("dmc3", MODE_PRIVATE) }
    private val ui = Handler(Looper.getMainLooper())
    private var pendingBoot: Runnable? = null

    private lateinit var status: TextView
    private lateinit var isoLabel: TextView
    private lateinit var play: Button
    private lateinit var pkgInput: EditText

    private val RED = Color.parseColor("#C8102E")
    private val GREY = Color.parseColor("#9A9A9A")
    private val PICK_ISO = 41

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(buildUi())
        refresh()
        // "Boot straight in": only on a cold start, and cancellable.
        if (b == null && prefs.getBoolean("autoboot", false) && isoUri() != null) {
            var left = 3
            pendingBoot = object : Runnable {
                override fun run() {
                    if (left == 0) { pendingBoot = null; startGame(); return }
                    status.text = "Booting in ${left--}s… tap anything to cancel"
                    ui.postDelayed(this, 1000)
                }
            }
            ui.post(pendingBoot!!)
        }
    }

    private fun cancelBoot() {
        pendingBoot?.let { ui.removeCallbacks(it); pendingBoot = null; refresh() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = ScrollView(this).apply { setBackgroundColor(Color.BLACK) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        fun btn(s: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
            text = s
            setTextColor(Color.WHITE)
            setBackgroundColor(if (primary) RED else Color.parseColor("#222222"))
            setOnClickListener { cancelBoot(); onClick() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(12) }
        }

        col.addView(text("DEVIL MAY CRY 3", 28f, Color.WHITE, true))
        col.addView(text("PS2 launcher for ARMSX2", 14f, RED))
        status = text("", 13f, GREY).apply { setPadding(0, dp(24), 0, dp(8)) }
        col.addView(status)
        isoLabel = text("", 15f, Color.WHITE).apply { setPadding(0, 0, 0, dp(8)) }
        col.addView(isoLabel)

        col.addView(btn("Select game image (ISO / CHD)", false) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, PICK_ISO)
        })
        play = btn("PLAY", true) { startGame() }
        col.addView(play)

        col.addView(Switch(this).apply {
            text = "Boot straight into the game on open"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("autoboot", false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("autoboot", on).apply() }
            setPadding(0, dp(24), 0, dp(8))
        })

        col.addView(text("Emulator package (leave blank to auto-detect)", 12f, GREY).apply {
            setPadding(0, dp(16), 0, 0)
        })
        pkgInput = EditText(this).apply {
            setText(prefs.getString("pkg", ""))
            setTextColor(Color.WHITE); setHintTextColor(GREY)
            hint = "e.g. com.armsx2.app"
            setSingleLine()
            setOnFocusChangeListener { _, focus ->
                if (!focus) { prefs.edit().putString("pkg", text.toString().trim()).apply(); refresh() }
            }
        }
        col.addView(pkgInput)

        root.addView(col)
        return root
    }

    private fun isoUri(): Uri? = prefs.getString("iso", null)?.let(Uri::parse)

    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "selected file"
    } catch (e: Exception) { "selected file" }

    private fun refresh() {
        val iso = isoUri()
        val target = EmulatorLauncher.findTarget(this, prefs.getString("pkg", null))
        isoLabel.text = iso?.let { displayName(it) } ?: "No game selected"
        play.isEnabled = iso != null && target != null
        play.alpha = if (play.isEnabled) 1f else 0.4f
        status.text = when {
            target == null -> "ARMSX2 not found. Install it, or enter its package name below."
            iso == null -> "Found ${target.pkg}. Pick your DMC3 image to continue."
            else -> "Ready · ${target.pkg}"
        }
    }

    private fun startGame() {
        val iso = isoUri() ?: return
        val target = EmulatorLauncher.findTarget(this, prefs.getString("pkg", null))
        if (target == null) { refresh(); return }
        when (val r = EmulatorLauncher.launch(this, target, iso)) {
            is EmulatorLauncher.Result.Ok -> {}
            is EmulatorLauncher.Result.Fail -> status.text = "Launch failed: ${r.reason}"
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == PICK_ISO && res == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { /* provider doesn't offer persistable grants */ }
            prefs.edit().putString("iso", uri.toString()).apply()
            refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        pendingBoot?.let { ui.removeCallbacks(it); pendingBoot = null }
    }
}
