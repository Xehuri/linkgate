package com.xehuri.linkgate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCodecTest {

    private val samples = listOf(
        DownloadRecord(
            id = "dl:1",
            name = "导出数据.csv",
            mime = "text/csv",
            size = 32,
            uri = "content://media/external/downloads/1001",
            pageUrl = "https://example.com/export",
            siteTitle = "示例站点",
            createdAt = 1_700_000_000_000,
            ok = true,
        ),
        DownloadRecord(
            id = "dm:42",
            name = "report.txt",
            mime = "text/plain",
            size = 51,
            uri = "content://media/external/downloads/1002",
            pageUrl = "",
            siteTitle = "",
            createdAt = 1_700_000_100_000,
            ok = false,
        ),
    )

    @Test
    fun `编码后可完整解码回来`() {
        val decoded = DownloadCodec.decode(DownloadCodec.encode(samples))
        assertEquals(samples, decoded)
    }

    @Test
    fun `空列表与脏数据安全`() {
        assertTrue(DownloadCodec.decode(null).isEmpty())
        assertTrue(DownloadCodec.decode("").isEmpty())
        assertTrue(DownloadCodec.decode("随便一段文字").isEmpty())
        assertTrue(DownloadCodec.decode("LINKGATE-DOWNLOADS/9\na\tb\tc\t1\td\te\tf\t1\t1").isEmpty())
        val empty = DownloadCodec.encode(emptyList())
        assertTrue(empty.startsWith("LINKGATE-DOWNLOADS/1"))
        assertTrue(DownloadCodec.decode(empty).isEmpty())
    }

    @Test
    fun `损坏的行被跳过而不是整份失效`() {
        val good = DownloadCodec.encode(samples.take(1))
        val broken = "$good\n字段不够\t只有两个\nbad\tline\there\tnotanumber\ta\tb\tc\td\te"
        val decoded = DownloadCodec.decode(broken)
        assertEquals(1, decoded.size)
        assertEquals("dl:1", decoded.first().id)
    }

    @Test
    fun `大小格式化可读`() {
        assertEquals("0 B", DownloadRecord.formatSize(0))
        assertEquals("512 B", DownloadRecord.formatSize(512))
        assertEquals("1.0 KB", DownloadRecord.formatSize(1024))
        assertEquals("1.5 MB", DownloadRecord.formatSize(1024L * 1536))
        assertTrue(DownloadRecord.formatSize(3L * 1024 * 1024 * 1024).endsWith("GB"))
    }

    @Test
    fun `文件类型分类`() {
        assertEquals(DownloadRecord.Kind.IMAGE, rec("a.png", "image/png").kind())
        assertEquals(DownloadRecord.Kind.VIDEO, rec("a.mp4", "video/mp4").kind())
        assertEquals(DownloadRecord.Kind.AUDIO, rec("a.mp3", "audio/mpeg").kind())
        assertEquals(DownloadRecord.Kind.PDF, rec("a.pdf", "application/pdf").kind())
        assertEquals(DownloadRecord.Kind.ARCHIVE, rec("a.zip", "application/zip").kind())
        assertEquals(DownloadRecord.Kind.ARCHIVE, rec("a.apk", "application/vnd.android.package-archive").kind())
        assertEquals(DownloadRecord.Kind.FILE, rec("a.bin", "").kind())
    }

    @Test
    fun `副标题包含站点与大小`() {
        val record = samples.first()
        assertTrue(record.summary().contains("示例站点"))
        assertTrue(record.summary().contains("32 B"))
        val failed = samples.last().copy(ok = false)
        assertTrue(failed.summary().contains("未完成"))
    }

    private fun rec(name: String, mime: String) = DownloadRecord(
        id = "x", name = name, mime = mime, size = 1, uri = "content://a", createdAt = 1, ok = true,
    )
}
