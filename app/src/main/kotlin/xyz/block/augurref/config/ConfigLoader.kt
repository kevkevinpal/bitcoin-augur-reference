/*
 * Copyright (c) 2025 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.block.augurref.config

import com.charleskorn.kaml.Yaml
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Loads application configuration from YAML files and environment variables
 */
object ConfigLoader {
  private val logger = LoggerFactory.getLogger(ConfigLoader::class.java)
  private val yaml = Yaml.default

  fun loadConfig(): AppConfig {
    // Load base config from sources
    val config = loadConfigFromSources()

    // Override with environment variables if present
    return config.copy(
      server = config.server.copy(
        host = System.getenv("AUGUR_SERVER_HOST") ?: config.server.host,
        port = System.getenv("AUGUR_SERVER_PORT")?.toIntOrNull() ?: config.server.port,
      ),
      bitcoinRpc = config.bitcoinRpc.copy(
        url = System.getenv("BITCOIN_RPC_URL") ?: config.bitcoinRpc.url,
        username = System.getenv("BITCOIN_RPC_USERNAME") ?: config.bitcoinRpc.username,
        password = System.getenv("BITCOIN_RPC_PASSWORD") ?: config.bitcoinRpc.password,
      ),
      persistence = config.persistence.copy(
        dataDirectory = System.getenv("AUGUR_DATA_DIR") ?: config.persistence.dataDirectory,
        cleanupDays = System.getenv("AUGUR_CLEANUP_DAYS")?.toIntOrNull() ?: config.persistence.cleanupDays,
      ),
    ).also { finalConfig ->
      logger.info(
        "Loaded configuration: " +
          "server.port=${finalConfig.server.port}, " +
          "bitcoinRpc.url=${finalConfig.bitcoinRpc.url}, " +
          "persistence.dataDirectory=${finalConfig.persistence.dataDirectory}, " +
          "persistence.cleanupDays=${finalConfig.persistence.cleanupDays}",
      )
    }
  }

  private fun loadConfigFromSources(): AppConfig {
    // First try loading from external config file if specified
    val configFromFile = System.getenv("AUGUR_CONFIG_FILE")?.let { path ->
      val file = File(path)
      if (file.exists()) {
        try {
          yaml.decodeFromString(AppConfig.serializer(), file.readText())
        } catch (e: Exception) {
          logger.warn("Failed to load external config file: ${e.message}")
          null
        }
      } else {
        logger.warn("Specified config file does not exist: $path")
        null
      }
    }

    if (configFromFile != null) {
      return configFromFile
    }

    // Fall back to default config from resources
    return try {
      val configStream = ConfigLoader::class.java.classLoader
        .getResourceAsStream("config.yaml")
        ?: throw IllegalStateException("Default config.yaml not found in resources")

      configStream.use { stream ->
        yaml.decodeFromStream(AppConfig.serializer(), stream)
      }
    } catch (e: Exception) {
      logger.error("Failed to load default config, using fallback values", e)
      AppConfig()
    }
  }
}
