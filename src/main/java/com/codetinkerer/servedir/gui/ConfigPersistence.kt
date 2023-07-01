package com.codetinkerer.servedir.gui

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.writeText

class ConfigPersistence {
    private val platformConfigDir = System.getenv("XDG_CONFIG_HOME") ?: "${System.getProperty("user.home")}/.config"
    private val configDir = Paths.get(platformConfigDir, "servedir")
    private val configPath = configDir.resolve("config.json")

    fun load(): List<ServerConfig> {
        try {
            val gson = Gson()
            val config = gson.fromJson(configPath.toFile().reader(), ServerListConfig::class.java)
            return config.servers
        } catch (e: IOException) {
            return Collections.emptyList()
        } catch (e: JsonSyntaxException) {
            return Collections.emptyList()
        }
    }

    fun save(config: List<ServerConfig>) {
        if (!configDir.toFile().exists()) {
            configDir.toFile().mkdirs()
        }
        val gson = Gson()
        val config = ServerListConfig(config)
        val serialized = gson.toJson(config)
        configPath.writeText(serialized)
        System.out.println("saved to ${configPath}")
    }
}