package com.s1607754.user.coinz

import android.content.Context
import android.os.AsyncTask
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class DownloadFileTask(private val caller : DownloadCompleteListener) : AsyncTask<String, Void, String>() {

    override fun doInBackground(vararg urls: String): String = try {
        loadFileFromNetwork(urls[0])
    } catch (e: IOException) {
        "Unable to load content. Check your network connection"
    }
    private fun loadFileFromNetwork(urlString: String): String {
        val stream : InputStream = downloadUrl(urlString)
// Read input from stream, build result as a string
        val resultStream = StringBuilder()
        BufferedReader(InputStreamReader(stream)).forEachLine { resultStream.append(it) }
        val result = resultStream.toString()
        writeJsonLocally(result,"coinzmap.geojson")
        return result
    }

    // Given a string representation of a URL, sets up a connection and gets an input stream.
    @Throws(IOException::class)
    private fun downloadUrl(urlString: String): InputStream {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
    // Also available: HttpsURLConnection
        conn.readTimeout = 10000 // milliseconds
        conn.connectTimeout = 15000 // milliseconds
        conn.requestMethod = "GET"
        conn.doInput = true
        conn.connect() // Starts the query
        return conn.inputStream
    }
    override fun onPostExecute(result: String) {
        super.onPostExecute(result)
        caller.downloadComplete(result)
    }
    private fun writeJsonLocally(data:String, name:String){
        val writePath= "/data/data/com.s1607754.user.coinz/files/$name"
        val file=File(writePath)
        file.writeText(data)
    }


} // end class DownloadFileTask