package com.commontongue.spike.device

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal object ResearchFiles {
    fun store(context: Context): PackStore {
        val raw =
            JSONObject(
                context.assets.open("test-pack-contract.json").bufferedReader().use {
                    it.readText()
                }
            )
        val manifest = raw.getJSONObject("manifest")
        check(raw.getInt("schema_version") == 1 && manifest.getInt("schema_version") == 1)
        check(
            manifest.getString("app_package") == context.packageName &&
                manifest.getBoolean("research_only")
        )
        val files = manifest.getJSONArray("files")
        val contract =
            PackContract(
                manifest.getString("pack_id"),
                raw.getLong("pack_bytes"),
                raw.getString("pack_sha256"),
                raw.getString("manifest_sha256"),
                (0 until files.length()).map { index ->
                    files.getJSONObject(index).let {
                        PackFile(it.getString("path"), it.getLong("bytes"), it.getString("sha256"))
                    }
                },
            )
        return PackStore(File(context.filesDir, "test-packs"), contract)
    }

    fun root(context: Context): File {
        val store = store(context)
        if (store.isInstalled()) return store.root()
        // Never silently fall back to developer paths when a local import is incomplete.
        if (File(context.filesDir, "test-packs/installed").exists())
            throw PackProblem(
                "The installed test pack is incomplete. Export Research Results so we can check it."
            )
        if (hasLegacyPack(context)) return context.filesDir
        throw PackProblem("Install the Common Tongue test pack first.")
    }

    fun hasPack(context: Context): Boolean = store(context).isInstalled()

    fun hasLegacyPack(context: Context): Boolean =
        try {
            JSONObject(File(context.filesDir, "provision.json").readText())
                .optBoolean("verified_on_device")
        } catch (_: Exception) {
            false
        }

    /** Presence/size/readability only; actual destination hashes are checked in the worker. */
    fun diagnostics(context: Context, translator: String, asr: String): JSONObject {
        val installed = File(context.filesDir, "test-packs/installed")
        val modern = hasPack(context)
        val legacy = !installed.exists() && hasLegacyPack(context)
        val root = if (modern || installed.exists()) installed else context.filesDir
        val raw =
            JSONObject(
                context.assets.open("test-pack-contract.json").bufferedReader().use {
                    it.readText()
                }
            )
        val expected = raw.getJSONObject("manifest").getJSONArray("files")
        val sizes =
            (0 until expected.length()).associate { index ->
                expected.getJSONObject(index).let { it.getString("path") to it.getLong("bytes") }
            }
        val files =
            ModelPaths(root).required(translator, asr).map { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                JSONObject()
                    .put("relative_path", relative)
                    .put("present", file.isFile)
                    .put("readable", file.canRead())
                    .put("bytes", if (file.isFile) file.length() else JSONObject.NULL)
                    .put("expected_bytes", sizes[relative] ?: JSONObject.NULL)
                    .put("matches_expected_size", file.isFile && file.length() == sizes[relative])
            }
        return JSONObject()
            .put("installed_state_persists", modern || legacy)
            .put(
                "source",
                if (modern || installed.exists()) "installed_test_pack"
                else if (legacy) "developer_provisioning" else "missing",
            )
            .put(
                "private_storage",
                root.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator) ||
                    root == context.filesDir,
            )
            .put("receipt_present", File(root, "provision.json").isFile)
            .put("complete_marker_matches", modern)
            .put("required_files", JSONArray(files))
    }
}
