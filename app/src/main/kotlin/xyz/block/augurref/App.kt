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

package xyz.block.augurref

import org.slf4j.LoggerFactory
import xyz.block.augur.FeeEstimator
import xyz.block.augurref.bitcoin.BitcoinRpcClient
import xyz.block.augurref.config.ConfigLoader
import xyz.block.augurref.persistence.MempoolPersistence
import xyz.block.augurref.server.HttpServer
import xyz.block.augurref.service.MempoolCollector

/**
 * Main application entry point
 */
fun main() {
  val logger = LoggerFactory.getLogger("xyz.block.augurref.App")

  try {
    logger.info("Starting Augur Reference application")

    // Load configuration
    val config = ConfigLoader.loadConfig()

    // Set up dependencies
    val bitcoinClient = BitcoinRpcClient(config.bitcoinRpc)
    val persistence = MempoolPersistence(config.persistence)
    val feeEstimator = FeeEstimator()

    // Set up mempool collector
    val mempoolCollector = MempoolCollector(
      bitcoinClient = bitcoinClient,
      persistence = persistence,
      feeEstimator = feeEstimator,
    )

    // Set up HTTP server
    val httpServer = HttpServer(
      serverConfig = config.server,
      mempoolCollector = mempoolCollector,
    )

    // Start services
    httpServer.start()
    mempoolCollector.start()

    // Set up periodic cleanup if enabled
    val cleanupTimer = if (config.persistence.cleanupDays > 0) {
      logger.info("Starting periodic mempool data cleanup every 24 hours")
      kotlin.concurrent.fixedRateTimer("mempool-cleanup", period = 24 * 60 * 60 * 1000) { // 24 hours
        try {
          persistence.cleanupOldFiles()
        } catch (e: Exception) {
          logger.error("Error during mempool data cleanup", e)
        }
      }
    } else {
      logger.info("Mempool data cleanup is disabled (cleanupDays=${config.persistence.cleanupDays})")
      null
    }

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(
      Thread {
        logger.info("Shutting down application")
        mempoolCollector.stop()
        httpServer.stop()
        cleanupTimer?.cancel()
        logger.info("Application shutdown completed")
      },
    )

    logger.info("Application startup completed")
  } catch (e: Exception) {
    logger.error("Error starting application", e)
    System.exit(1)
  }
}
