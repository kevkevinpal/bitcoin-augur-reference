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
    
    // Cleanup old files if retention period is configured
    cleanupOldFiles()
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
   * Clean up old mempool data files based on retention configuration
   */
  private fun cleanupOldFiles() {
    val retentionDays = config.retentionDays
    if (retentionDays == null || retentionDays <= 0) {
      return // No cleanup configured
    }

    val dataDir = config.getDataDirectoryFile()
    if (!dataDir.exists()) {
      return
    }

    val cutoffDate = LocalDate.now().minusDays(retentionDays.toLong())
    logger.debug("Cleaning up mempool data older than $retentionDays days (before $cutoffDate)")

    dataDir.listFiles()?.forEach { dateDir ->
      if (dateDir.isDirectory) {
        try {
          val dirDate = LocalDate.parse(dateDir.name, dateFormatter)
          if (dirDate.isBefore(cutoffDate)) {
            logger.info("Deleting old mempool data directory: ${dateDir.name}")
            deleteDirectoryRecursively(dateDir)
          }
        } catch (e: Exception) {
          logger.warn("Skipping directory with invalid date format: ${dateDir.name}", e)
        }
      }
    }
  }

  /**
   * Recursively delete a directory and all its contents
   */
  private fun deleteDirectoryRecursively(directory: File): Boolean {
    if (!directory.exists()) {
      return true
    }

    if (directory.isDirectory) {
      directory.listFiles()?.forEach { child ->
        if (!deleteDirectoryRecursively(child)) {
          logger.error("Failed to delete: ${child.absolutePath}")
          return false
        }
      }
    }

    val deleted = directory.delete()
    if (!deleted) {
      logger.error("Failed to delete: ${directory.absolutePath}")
    }
    return deleted
  }
}
