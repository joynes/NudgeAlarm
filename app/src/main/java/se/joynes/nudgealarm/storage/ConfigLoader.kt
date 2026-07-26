package se.joynes.nudgealarm.storage

import android.content.Context
import android.net.Uri
import se.joynes.nudgealarm.core.config.AppConfig
import se.joynes.nudgealarm.core.config.ConfigParser
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

class ConfigLoader(private val context: Context) {

    fun loadFromUri(uri: Uri): Result<AppConfig> = runCatching {
        val content = when (uri.scheme) {
            "file" -> {
                val file = File(uri.path!!)
                file.readText()
            }
            else -> {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open file: $uri")
                inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        reader.readText()
                    }
                }
            }
        }

        ConfigParser.parse(content).getOrThrow()
    }

    fun loadFromString(yamlContent: String): Result<AppConfig> {
        return ConfigParser.parse(yamlContent)
    }
}
