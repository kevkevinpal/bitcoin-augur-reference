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

package xyz.block.augurref.persistence

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import xyz.block.augur.MempoolSnapshot
import xyz.block.augurref.config.PersistenceConfig
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Handles the persistence of mempool snapshots to disk
 */
class MempoolPersistence(private val config: PersistenceConfig) {
  private val logger = LoggerFactory.getLogger(MempoolPersistence::class.java)
  private val mapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  init {
    logger.debug("Initializing mempool persistence with data directory: ${config.dataDirectory}")
    config.getDataDirectoryFile().mkdirs()
  }

  /**
   * Save a snapshot to disk
   */
  fun saveSnapshot(snapshot: MempoolSnapshot) {
    val timestamp = snapshot.timestamp
    val localDateTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())

    val dateDir = File(config.getDataDirectoryFile(), localDateTime.format(dateFormatter))
    dateDir.mkdirs()

    val filename = "${snapshot.blockHeight}_${timestamp.epochSecond}.json"
    val file = File(dateDir, filename)

    logger.debug("Saving snapshot to ${file.absolutePath}")
    file.writeText(mapper.writeValueAsString(snapshot))
  }

  /**
   * Get snapshots from a date range
   */
  fun getSnapshots(startDate: LocalDateTime, endDate: LocalDateTime): List<MempoolSnapshot> {
    logger.debug("Fetching snapshots from $startDate to $endDate")
    val snapshots = mutableListOf<MempoolSnapshot>()

    var currentLocalDate = startDate.toLocalDate()
    val endLocalDate = endDate.toLocalDate()

    while (!currentLocalDate.isAfter(endLocalDate)) {
      val dateDir = File(config.getDataDirectoryFile(), currentLocalDate.format(dateFormatter))
      if (dateDir.exists()) {
        dateDir.listFiles()?.forEach { file ->
          if (file.extension == "json") {
            try {
              val snapshot: MempoolSnapshot = mapper.readValue(file.readText())
              val snapshotDateTime = LocalDateTime.ofInstant(
                snapshot.timestamp,
                ZoneId.systemDefault(),
              )

              if (!snapshotDateTime.isBefore(startDate) && !snapshotDateTime.isAfter(endDate)) {
                snapshots.add(snapshot)
              }
            } catch (e: Exception) {
              logger.error("Error reading snapshot file ${file.absolutePath}", e)
            }
          }
        }
      }
      currentLocalDate = currentLocalDate.plusDays(1)
    }

    logger.debug("Found ${snapshots.size} snapshots in date range")
    return snapshots.sortedBy { it.timestamp }
  }

  /**
   * Clean up old mempool data files based on the configured cleanup days
   */
  fun cleanupOldFiles() {
    if (config.cleanupDays <= 0) {
      logger.debug("Cleanup is disabled (cleanupDays=${config.cleanupDays})")
      return
    }

    val cutoffDate = LocalDateTime.now().minusDays(config.cleanupDays.toLong()).toLocalDate()
    logger.info("Starting cleanup of mempool data older than $cutoffDate (${config.cleanupDays} days)")

    val dataDir = config.getDataDirectoryFile()
    if (!dataDir.exists()) {
      logger.debug("Data directory does not exist: ${dataDir.absolutePath}")
      return
    }

    var deletedDirs = 0
    var deletedFiles = 0

    dataDir.listFiles()?.forEach { file ->
      if (file.isDirectory) {
        try {
          val dirDate = LocalDateTime.parse("${file.name}T00:00:00").toLocalDate()
          if (dirDate.isBefore(cutoffDate)) {
            // Count files before deletion
            val fileCount = file.listFiles()?.size ?: 0
            
            // Delete the entire date directory
            val deleted = file.deleteRecursively()
            if (deleted) {
              deletedDirs++
              deletedFiles += fileCount
              logger.debug("Deleted date directory: ${file.name} (contained $fileCount files)")
            } else {
              logger.warn("Failed to delete date directory: ${file.name}")
            }
          }
        } catch (e: Exception) {
          logger.warn("Skipping directory with invalid date format: ${file.name}", e)
        }
      }
    }

    logger.info("Cleanup completed: deleted $deletedDirs directories containing $deletedFiles files")
  }
}
