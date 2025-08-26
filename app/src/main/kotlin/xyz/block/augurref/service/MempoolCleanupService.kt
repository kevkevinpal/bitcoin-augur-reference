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

package xyz.block.augurref.service

import org.slf4j.LoggerFactory
import xyz.block.augurref.config.PersistenceConfig
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

/**
 * Service for cleaning up old mempool data files based on configured retention period
 */
class MempoolCleanupService(
  private val persistenceConfig: PersistenceConfig,
  private val cleanupIntervalMs: Long = 3600000, // 1 hour
) {
  private val logger = LoggerFactory.getLogger(MempoolCleanupService::class.java)
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private var cleanupTimer: Timer? = null

  /**
   * Start the cleanup service if retention is enabled
   */
  fun start() {
    if (persistenceConfig.retentionDays <= 0) {
      logger.info("Mempool data cleanup disabled (retentionDays=${persistenceConfig.retentionDays})")
      return
    }

    logger.info("Starting mempool data cleanup service with ${persistenceConfig.retentionDays} day retention, checking every ${cleanupIntervalMs}ms")

    cleanupTimer = fixedRateTimer("mempool-cleanup", period = cleanupIntervalMs) {
      try {
        performCleanup()
      } catch (e: Exception) {
        logger.error("Error during mempool data cleanup", e)
      }
    }
  }

  /**
   * Stop the cleanup service
   */
  fun stop() {
    logger.info("Stopping mempool data cleanup service")
    cleanupTimer?.cancel()
    cleanupTimer = null
  }

  /**
   * Perform cleanup of old mempool data directories
   */
  private fun performCleanup() {
    val startTime = System.currentTimeMillis()
    logger.debug("Starting mempool data cleanup")

    val dataDirectory = persistenceConfig.getDataDirectoryFile()
    if (!dataDirectory.exists()) {
      logger.debug("Data directory does not exist: ${dataDirectory.absolutePath}")
      return
    }

    val cutoffDate = LocalDate.now().minusDays(persistenceConfig.retentionDays.toLong())
    logger.debug("Cleaning up directories older than $cutoffDate")

    var directoriesDeleted = 0
    var filesDeleted = 0
    var errors = 0

    try {
      dataDirectory.listFiles()?.forEach { file ->
        if (file.isDirectory && isDateDirectory(file.name)) {
          try {
            val directoryDate = LocalDate.parse(file.name, dateFormatter)
            if (directoryDate.isBefore(cutoffDate)) {
              logger.debug("Deleting directory: ${file.absolutePath}")
              val fileCount = countFilesInDirectory(file)
              
              if (file.deleteRecursively()) {
                directoriesDeleted++
                filesDeleted += fileCount
                logger.info("Deleted directory: ${file.name} (contained $fileCount files)")
              } else {
                errors++
                logger.error("Failed to delete directory: ${file.absolutePath}")
              }
            }
          } catch (e: Exception) {
            errors++
            logger.error("Error processing directory: ${file.absolutePath}", e)
          }
        }
      }
    } catch (e: Exception) {
      errors++
      logger.error("Error listing data directory: ${dataDirectory.absolutePath}", e)
    }

    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
    logger.info("Mempool cleanup completed in %.2f seconds: deleted %d directories (%d files), %d errors"
      .format(elapsedSeconds, directoriesDeleted, filesDeleted, errors))
  }

  /**
   * Check if a directory name matches the expected date format (yyyy-MM-dd)
   */
  private fun isDateDirectory(name: String): Boolean {
    return try {
      LocalDate.parse(name, dateFormatter)
      true
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Count the number of files in a directory (recursive)
   */
  private fun countFilesInDirectory(directory: File): Int {
    return try {
      directory.walkTopDown().count { it.isFile }
    } catch (e: Exception) {
      logger.warn("Error counting files in directory: ${directory.absolutePath}", e)
      0
    }
  }
}