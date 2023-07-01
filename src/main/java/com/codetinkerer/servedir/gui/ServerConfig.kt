package com.codetinkerer.servedir.gui

data class ServerConfig(var directoryPath: String, var port: Int?, var desiredState: ServerState)