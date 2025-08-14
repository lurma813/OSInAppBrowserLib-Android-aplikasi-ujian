package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object OSIABPdfHelper {
    
    fun isContentTypeApplicationPdf(urlString: String): Boolean {
        return try {
            // Try to identify if the URL is a PDF using a HEAD request.
            // If the server does not implement HEAD correctly or does not return the expected content-type,
            // fall back to a GET request, since some servers only return the correct type for GET.
            if (checkPdfByRequest(urlString, method = "HEAD")) {
                true
            } else {
                checkPdfByRequest(urlString, method = "GET")
            }
        } catch (_: Exception) {
            false
        }
    }

    fun checkPdfByRequest(urlString: String, method: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as? HttpURLConnection)
            conn?.run {
                instanceFollowRedirects = true
                requestMethod = method
                if (method == "GET") {
                    setRequestProperty("Range", "bytes=0-0")
                }
                connect()
                val type = contentType?.lowercase()
                val disposition = getHeaderField("Content-Disposition")?.lowercase()
                type == "application/pdf" ||
                        (type.isNullOrEmpty() && disposition?.contains(".pdf") == true)
            } ?: false
        } finally {
            conn?.disconnect()
        }
    }

    @Throws(IOException::class)
    fun downloadPdfToCache(context: Context, url: String): File {
        val pdfFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.pdf")
        URL(url).openStream().use { input ->
            pdfFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return pdfFile
    }
}
