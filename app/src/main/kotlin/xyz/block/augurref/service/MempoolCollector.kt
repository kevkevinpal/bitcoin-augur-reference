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
import xyz.block.augur.FeeEstimate
import xyz.block.augur.FeeEstimator
import xyz.block.augur.MempoolSnapshot
import xyz.block.augurref.bitcoin.BitcoinRpcClient
import xyz.block.augurref.persistence.MempoolPersistence
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.fixedRateTimer

/**
 * Collects mempool data regularly and calculates fee estimates
 */
class MempoolCollector(
  private val bitcoinClient: BitcoinRpcClient,
  private val persistence: MempoolPersistence,
  private val feeEstimator: FeeEstimator,
  private val collectionIntervalMs: Long = 30000,
) {
  private val logger = LoggerFactory.getLogger(MempoolCollector::class.java)
  private val latestFeeEstimate = AtomicReference<FeeEstimate?>(null)
  private var collectorTimer: java.util.Timer? = null

  /**
   * Start collecting mempool data at regular intervals
   */
  fun start() {
    logger.info("Starting mempool data collection every ${collectionIntervalMs}ms")

    collectorTimer = fixedRateTimer("mempool-collector", period = collectionIntervalMs) {
      try {
        updateFeeEstimates()
      } catch (e: Exception) {
        logger.error("Error updating fee estimates", e)
      }
    }
  }

  /**
   * Stop collecting mempool data
   */
  fun stop() {
    logger.info("Stopping mempool data collection")
    collectorTimer?.cancel()
    collectorTimer = null
  }

  /**
   * Get the latest fee estimate
   */
  fun getLatestFeeEstimate(): FeeEstimate? {
    return latestFeeEstimate.get()
  }

  /**
   * Get the latest fee estimate for block target
   */
  fun getLatestFeeEstimateForBlockTarget(numOfBlocks: Double): FeeEstimate? {
    // Fetch the last day's snapshots
    logger.debug("Fetching snapshots from the last day")
    val lastDaySnapshots = persistence.getSnapshots(
      LocalDateTime.now().minusDays(1),
      LocalDateTime.now(),
    )
    logger.debug("Retrieved ${lastDaySnapshots.size} snapshots from the last day")

    if (lastDaySnapshots.isNotEmpty()) {
      // Calculate fee estimate for x blocks
      logger.debug("Calculating fee estimates")
      val newFeeEstimate = feeEstimator.calculateEstimates(lastDaySnapshots, numOfBlocks)
      return newFeeEstimate
    } else {
      logger.warn("No snapshots available for fee estimation")
    }
    return FeeEstimate(
      estimates = emptyMap(),
      timestamp = Instant.EPOCH,
    )
  }

  /**
   * Collect mempool data, save snapshot, and update fee estimates
   */
  private fun updateFeeEstimates() {
    val startTime = System.currentTimeMillis()
    logger.debug("Collecting mempool data")

    // Get height and mempool data directly in Augur format
    val (blockHeight, transactions) = bitcoinClient.getHeightAndMempoolTransactions()
    logger.debug("Got mempool data: ${transactions.size} transactions at height $blockHeight")

    // Create snapshot
    val mempoolSnapshot = MempoolSnapshot.fromMempoolTransactions(
      transactions = transactions,
      blockHeight = blockHeight,
    )

    // Save snapshot
    persistence.saveSnapshot(mempoolSnapshot)
    logger.info("Mempool snapshot saved: ${transactions.size} transactions at height $blockHeight")

    // Fetch the last day's snapshots
    logger.debug("Fetching snapshots from the last day")
    val lastDaySnapshots = persistence.getSnapshots(
      LocalDateTime.now().minusDays(1),
      LocalDateTime.now(),
    )
    logger.debug("Retrieved ${lastDaySnapshots.size} snapshots from the last day")

    if (lastDaySnapshots.isNotEmpty()) {
      // Calculate and update fee estimate
      logger.debug("Calculating fee estimates")
      val newFeeEstimate = feeEstimator.calculateEstimates(lastDaySnapshots)
      latestFeeEstimate.set(newFeeEstimate)
      logger.info("Fee estimates updated")
    } else {
      logger.warn("No snapshots available for fee estimation")
    }
    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
    logger.info("Updating fee estimates finished in %.2f seconds".format(elapsedSeconds))
  }
}
