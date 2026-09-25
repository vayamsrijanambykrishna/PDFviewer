package `in`.krishna.pdfviewer

import android.graphics.Bitmap

internal class PageCache(
    private val maxBytes: Int
) {

    private val entries = LinkedHashMap<Int, Bitmap>(16, 0.75f, true)
    private var currentBytes = 0L

    @Synchronized
    fun get(pageIndex: Int): Bitmap? {
        return entries[pageIndex]?.takeUnless { it.isRecycled }
    }

    @Synchronized
    fun contains(pageIndex: Int): Boolean {
        return entries[pageIndex]?.let { !it.isRecycled } == true
    }

    @Synchronized
    fun put(pageIndex: Int, bitmap: Bitmap) {
        if (bitmap.isRecycled) return

        entries.remove(pageIndex)?.let {
            currentBytes -= it.byteCount.toLong()
        }

        entries[pageIndex] = bitmap
        currentBytes += bitmap.byteCount.toLong()
        trimToSize()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        currentBytes = 0L
    }

    @Synchronized
    fun trimForMemoryPressure() {
        while (currentBytes > maxBytes / 2 && entries.isNotEmpty()) {
            removeOldest()
        }
    }

    @Synchronized
    fun trimToBytes(targetBytes: Long) {
        val target = targetBytes.coerceAtLeast(0L)
        while (currentBytes > target && entries.isNotEmpty()) {
            removeOldest()
        }
    }

    private fun trimToSize() {
        while (currentBytes > maxBytes && entries.isNotEmpty()) {
            removeOldest()
        }
    }

    private fun removeOldest() {
        val iterator = entries.entries.iterator()
        if (iterator.hasNext()) {
            val entry = iterator.next()
            currentBytes -= entry.value.byteCount.toLong()
            iterator.remove()
        }
    }
}
