package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.file.Files
import kotlin.concurrent.thread

class OSIABPdfHelperTest {

    @Test
    fun `isContentTypeApplicationPdf returns true if HEAD is PDF`() {
        mockkObject(OSIABPdfHelper)
        every { OSIABPdfHelper.checkPdfByRequest(any(), "HEAD", any()) } returns true

        val result = OSIABPdfHelper.isContentTypeApplicationPdf("http://example.com")

        assertTrue(result)
        verify { OSIABPdfHelper.checkPdfByRequest(any(), "HEAD", any()) }
        verify(exactly = 0) { OSIABPdfHelper.checkPdfByRequest(any(), "GET", any()) }
        unmockkObject(OSIABPdfHelper)
    }

    @Test
    fun `isContentTypeApplicationPdf falls back to GET if HEAD fails`() {
        mockkObject(OSIABPdfHelper)
        every { OSIABPdfHelper.checkPdfByRequest(any(), "HEAD", any()) } returns false
        every { OSIABPdfHelper.checkPdfByRequest(any(), "GET", any()) } returns true

        val result = OSIABPdfHelper.isContentTypeApplicationPdf("http://example.com")

        assertTrue(result)
        verify { OSIABPdfHelper.checkPdfByRequest(any(), "HEAD", any()) }
        verify { OSIABPdfHelper.checkPdfByRequest(any(), "GET", any()) }
        unmockkObject(OSIABPdfHelper)
    }

    @Test
    fun `isContentTypeApplicationPdf returns false if both HEAD and GET fail`() {
        mockkObject(OSIABPdfHelper)
        every { OSIABPdfHelper.checkPdfByRequest(any(), "HEAD", any()) } returns false
        every { OSIABPdfHelper.checkPdfByRequest(any(), "GET", any()) } returns false

        val result = OSIABPdfHelper.isContentTypeApplicationPdf("http://example.com")

        assertFalse(result)
        verify { OSIABPdfHelper.checkPdfByRequest(any(), "HEAD", any()) }
        verify { OSIABPdfHelper.checkPdfByRequest(any(), "GET", any()) }
        unmockkObject(OSIABPdfHelper)
    }

    @Test
    fun `isContentTypeApplicationPdf returns false if exception occurs`() {
        mockkObject(OSIABPdfHelper)
        every {
            OSIABPdfHelper.checkPdfByRequest(
                any(),
                any(),
                any()
            )
        } throws RuntimeException("Network error")

        val result = OSIABPdfHelper.isContentTypeApplicationPdf("http://example.com")

        assertFalse(result)
        verify { OSIABPdfHelper.checkPdfByRequest(any(), "HEAD", any()) }
        unmockkObject(OSIABPdfHelper)
    }

    @Test
    fun `returns true when content type is application_pdf`() {
        val urlFactory = mockk<OSIABPdfHelper.UrlFactory>()
        val url = mockk<URL>()
        val conn = mockk<HttpURLConnection>(relaxed = true)
        every { urlFactory.create(any()) } returns url
        every { url.openConnection() } returns conn
        every { conn.contentType } returns "application/pdf"
        every { conn.connect() } returns Unit

        val result = OSIABPdfHelper.checkPdfByRequest("http://example.com/test.pdf", "HEAD", urlFactory)

        assertTrue(result)
        verify { conn.connect() }
        verify { conn.disconnect() }
    }

    @Test
    fun `returns true when disposition header contains pdf and content type is empty`() {
        val urlFactory = mockk<OSIABPdfHelper.UrlFactory>()
        val url = mockk<URL>()
        val conn = mockk<HttpURLConnection>(relaxed = true)
        every { urlFactory.create(any()) } returns url
        every { url.openConnection() } returns conn
        every { conn.contentType } returns null
        every { conn.getHeaderField("Content-Disposition") } returns "attachment; filename=test.pdf"
        every { conn.connect() } returns Unit

        val result = OSIABPdfHelper.checkPdfByRequest("http://example.com/test.pdf", "HEAD", urlFactory)

        assertTrue(result)
        verify { conn.connect() }
        verify { conn.disconnect() }
    }

    @Test
    fun `returns false when neither content type nor disposition indicate pdf`() {
        val urlFactory = mockk<OSIABPdfHelper.UrlFactory>()
        val url = mockk<URL>()
        val conn = mockk<HttpURLConnection>(relaxed = true)
        every { urlFactory.create(any()) } returns url
        every { url.openConnection() } returns conn
        every { conn.contentType } returns "text/html"
        every { conn.getHeaderField("Content-Disposition") } returns "inline"
        every { conn.connect() } returns Unit

        val result = OSIABPdfHelper.checkPdfByRequest("http://example.com/test.pdf", "HEAD", urlFactory)

        assertFalse(result)
        verify { conn.connect() }
        verify { conn.disconnect() }
    }

    @Test
    fun `sets Range header for GET method`() {
        val urlFactory = mockk<OSIABPdfHelper.UrlFactory>()
        val url = mockk<URL>()
        val conn = mockk<HttpURLConnection>(relaxed = true)
        every { urlFactory.create(any()) } returns url
        every { url.openConnection() } returns conn
        every { conn.contentType } returns "application/pdf"
        every { conn.connect() } returns Unit

        OSIABPdfHelper.checkPdfByRequest("http://example.com/test.pdf", "GET", urlFactory)

        verify { conn.setRequestProperty("Range", "bytes=0-0") }
        verify { conn.connect() }
        verify { conn.disconnect() }
    }

    @Test
    fun `returns false if connection is null`() {
        val urlFactory = mockk<OSIABPdfHelper.UrlFactory>()
        val url = mockk<URL>()
        every { urlFactory.create(any()) } returns url
        every { url.openConnection() } returns null

        val result = OSIABPdfHelper.checkPdfByRequest("http://example.com/test.pdf", "HEAD", urlFactory)

        assertFalse(result)
    }

    @Test
    fun `returns false if exception is thrown`() {
        val urlFactory = mockk<OSIABPdfHelper.UrlFactory>()
        every { urlFactory.create(any()) } throws RuntimeException("Network error")

        val result = try {
            OSIABPdfHelper.checkPdfByRequest("http://example.com/test.pdf", "HEAD", urlFactory)
        } catch (_: Exception) {
            false
        }

        assertFalse(result)
    }

    @Test
    fun `downloadPdfToCache creates file with content`() {
        val server = ServerSocket(0)
        val port = server.localPort
        val pdfBytes = "%PDF-1.4 test".toByteArray()
        thread {
            val client: Socket = server.accept()
            val out = client.getOutputStream()
            out.write(
                ("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/pdf\r\n" +
                        "Content-Length: ${pdfBytes.size}\r\n" +
                        "\r\n").toByteArray()
            )
            out.write(pdfBytes)
            out.flush()
            client.close()
            server.close()
        }

        val context = mockk<Context>()
        val cacheDir = Files.createTempDirectory("test_cache").toFile()
        every { context.cacheDir } returns cacheDir

        val url = "http://localhost:$port/test.pdf"
        val file = OSIABPdfHelper.downloadPdfToCache(context, url)

        assertTrue(file.exists())
        assertTrue(file.readBytes().copyOfRange(0, 8).contentEquals("%PDF-1.4".toByteArray()))
        file.delete()
        cacheDir.deleteRecursively()
    }
}
