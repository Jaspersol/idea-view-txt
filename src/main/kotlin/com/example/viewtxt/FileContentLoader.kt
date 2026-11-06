package com.example.viewtxt

import com.intellij.openapi.application.ApplicationManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 文本文件读取：磁盘 IO 与解码都在后台线程执行，结果通过 EDT 回调交给调用方更新界面。
 *
 * 解码不再依赖平台默认编码（Windows 上默认 GBK，UTF-8 文件会乱码），顺序如下：
 * 1. 有 BOM 时按 BOM 指定的编码解码（UTF-8 / UTF-16LE / UTF-16BE），并跳过 BOM；
 * 2. 没有 BOM（或按 BOM 解码失败）时先按严格 UTF-8 解码，失败再回退 GB18030；
 *    GBK 是 GB18030 的子集，用 GB18030 能覆盖更多历史编码文件；
 * 3. 两种解码都失败时用 UTF-8 宽松解码兜底，坏字节显示为 U+FFFD，保证界面总有内容。
 */
object FileContentLoader {
    /** GB18030 是 GBK 的超集，优先用它解码历史中文编码文件 */
    private val GB18030: Charset = Charset.forName("GB18030")

    private class Bom(val charset: Charset, val length: Int)

    /**
     * 后台读取并解码 [file]，成功和失败回调都在 EDT 上执行。
     * 读取失败（文件被删、无权限等）交给 [onError]，由调用方决定提示方式。
     */
    fun readTextAsync(file: File, onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
        val application = ApplicationManager.getApplication()
        application.executeOnPooledThread {
            try {
                val content = decode(file.readBytes())
                application.invokeLater { onSuccess(content) }
            } catch (ex: Exception) {
                application.invokeLater { onError(ex) }
            }
        }
    }

    /** 按 BOM → 严格 UTF-8 → GB18030 → 宽松 UTF-8 的顺序解码 */
    fun decode(bytes: ByteArray): String {
        val bom = detectBom(bytes)
        val offset = bom?.length ?: 0
        if (bom != null) {
            decodeStrict(bytes, offset, bom.charset)?.let { return it }
        }
        decodeStrict(bytes, offset, Charsets.UTF_8)?.let { return it }
        decodeStrict(bytes, offset, GB18030)?.let { return it }
        return String(bytes, offset, bytes.size - offset, Charsets.UTF_8)
    }

    /** 严格解码：遇到非法字节序列就返回 null，交给下一种编码尝试 */
    private fun decodeStrict(bytes: ByteArray, offset: Int, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun detectBom(bytes: ByteArray): Bom? {
        fun byteAt(index: Int): Int = bytes[index].toInt() and 0xFF

        return when {
            bytes.size >= 3 && byteAt(0) == 0xEF && byteAt(1) == 0xBB && byteAt(2) == 0xBF ->
                Bom(Charsets.UTF_8, 3)

            bytes.size >= 2 && byteAt(0) == 0xFF && byteAt(1) == 0xFE ->
                Bom(Charsets.UTF_16LE, 2)

            bytes.size >= 2 && byteAt(0) == 0xFE && byteAt(1) == 0xFF ->
                Bom(Charsets.UTF_16BE, 2)

            else -> null
        }
    }
}
