package me.rerere.rikkahub.data.db.fts

import android.content.Context
import java.io.File

object SimpleDictManager {

    private const val DICT_ASSET_DIR = "simple_dict"
    private const val VERSION_FILE = "version.txt"

    // 与 assets 中的词典版本对齐，更新词典时递增此值
    private const val CURRENT_VERSION = 1

    /**
     * 将 assets/simple_dict 解压到 files/simple_dict，返回词典目录。
     * 已是最新版本时直接返回，无需重复拷贝。
     * 可在任意线程调用。
     */
    fun extractDict(context: Context): File {
        val destDir = File(context.filesDir, DICT_ASSET_DIR)
        val versionFile = File(destDir, VERSION_FILE)

        if (versionFile.exists() && versionFile.readText().trim().toIntOrNull() == CURRENT_VERSION) {
            return destDir
        }

        destDir.deleteRecursively()
        destDir.mkdirs()
        copyAssetDir(context, DICT_ASSET_DIR, destDir)
        versionFile.writeText(CURRENT_VERSION.toString())
        return destDir
    }

    /** 安全模式用：删除已解压词典（若解压中途进程被杀可能损坏，损坏会让每次 DB 打开挂死）。
     *  下次 DB 打开时 extractDict 会因版本文件缺失而重新解压。 */
    fun resetExtractedDict(context: Context): Boolean {
        val destDir = File(context.filesDir, DICT_ASSET_DIR)
        return destDir.deleteRecursively()
    }

    private fun copyAssetDir(context: Context, assetPath: String, destDir: File) {        val assets = context.assets.list(assetPath) ?: return
        for (name in assets) {
            val childAsset = "$assetPath/$name"
            val destFile = File(destDir, name)
            val children = context.assets.list(childAsset)
            if (!children.isNullOrEmpty()) {
                destFile.mkdirs()
                copyAssetDir(context, childAsset, destFile)
            } else {
                context.assets.open(childAsset).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
