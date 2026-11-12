package com.example.dmc3launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager

/** Finds ARMSX2 and asks it to open a disc image. */
object EmulatorLauncher {

    data class Target(val pkg: String, val activities: List<String>)

    /**
     * ARMSX2's own VIEW filters (content:// and file://, no MIME type) are used as a probe, so we
     * learn the real package and class names instead of guessing them. [override] wins if set.
     */
    fun findTarget(ctx: Context, override: String?): Target? {
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, MutableList<String>>()
        for (scheme in listOf("content", "file")) {
            val probe = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://probe/game.iso"))
            val hits = if (android.os.Build.VERSION.SDK_INT >= 33)
                pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
            else @Suppress("DEPRECATION") pm.queryIntentActivities(probe, 0)
            for (ri in hits) {
                val ai = ri.activityInfo ?: continue
                found.getOrPut(ai.packageName) { mutableListOf() }.let {
                    if (ai.name !in it) it.add(ai.name)
                }
            }
        }
        val pkg = override?.takeIf { it.isNotBlank() }
            ?: found.keys.firstOrNull { it.contains("armsx2", true) || it.contains("pasx2", true) }
            ?: return null
        val acts = found[pkg].orEmpty()
        // BootSplashActivity is the LAUNCHER entry; Main is the emulator host. Try splash first.
        val ordered = acts.sortedBy { if (it.endsWith("BootSplashActivity")) 0 else 1 }
        return Target(pkg, ordered)
    }

    sealed class Result {
        object Ok : Result()
        data class Fail(val reason: String) : Result()
    }

    fun launch(ctx: Context, target: Target, iso: Uri): Result {
        val classes = target.activities.ifEmpty { return Result.Fail("No launchable activity found in ${target.pkg}") }
        var last = "unknown error"
        for (cls in classes) {
            // Explicit component: skips intent-filter MIME matching, which a scheme-only filter
            // can fail once Android resolves a type for a content:// URI.
            val i = Intent(Intent.ACTION_VIEW).apply {
                component = ComponentName(target.pkg, cls)
                data = iso
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            try {
                ctx.startActivity(i)
                return Result.Ok
            } catch (e: ActivityNotFoundException) {
                last = "$cls not found"
            } catch (e: SecurityException) {
                last = "$cls refused the launch (${e.message})"
            }
        }
        return Result.Fail(last)
    }
}
