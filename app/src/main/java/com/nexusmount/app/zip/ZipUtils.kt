package com.nexusmount.app.zip

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {

    fun zip(sources: List<File>, destZip: File): Result<File> = runCatching {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destZip))).use { zos ->
            sources.forEach { file ->
                if (file.isDirectory) addDir(zos, file, file.name)
                else addFile(zos, file, file.name)
            }
        }
        destZip
    }

    fun unzip(zipFile: File, destDir: File): Result<File> = runCatching {
        if (!destDir.exists()) destDir.mkdirs()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val out = File(destDir, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        destDir
    }

    private fun addFile(zos: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            zos.putNextEntry(ZipEntry(entryName))
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }

    private fun addDir(zos: ZipOutputStream, dir: File, base: String) {
        dir.listFiles()?.forEach { child ->
            val name = "$base/${child.name}"
            if (child.isDirectory) addDir(zos, child, name)
            else addFile(zos, child, name)
        }
    }
}
