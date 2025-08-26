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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xyz.block.augurref.config.PersistenceConfig
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MempoolCleanupServiceTest {

  @TempDir
  private lateinit var tempDir: File

  private lateinit var cleanupService: MempoolCleanupService
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  @BeforeEach
  fun setUp() {
    // Create a persistence config pointing to our temp directory
    val persistenceConfig = PersistenceConfig(
      dataDirectory = tempDir.absolutePath,
      retentionDays = 7 // 7 days retention for tests
    )
    
    cleanupService = MempoolCleanupService(persistenceConfig, cleanupIntervalMs = 100) // Fast interval for testing
  }

  @AfterEach
  fun tearDown() {
    cleanupService.stop()
  }

  @Test
  fun `should not start cleanup when retentionDays is 0`() {
    val disabledConfig = PersistenceConfig(
      dataDirectory = tempDir.absolutePath,
      retentionDays = 0
    )
    val disabledService = MempoolCleanupService(disabledConfig)
    
    // Should start without error but not actually schedule cleanup
    assertDoesNotThrow {
      disabledService.start()
      disabledService.stop()
    }
  }

  @Test
  fun `should not start cleanup when retentionDays is negative`() {
    val disabledConfig = PersistenceConfig(
      dataDirectory = tempDir.absolutePath,
      retentionDays = -1
    )
    val disabledService = MempoolCleanupService(disabledConfig)
    
    // Should start without error but not actually schedule cleanup
    assertDoesNotThrow {
      disabledService.start()
      disabledService.stop()
    }
  }

  @Test
  fun `should delete directories older than retention period`() {
    // Create test directories with different dates
    val today = LocalDate.now()
    val oldDate1 = today.minusDays(10) // Should be deleted (older than 7 days)
    val oldDate2 = today.minusDays(15) // Should be deleted
    val recentDate = today.minusDays(5) // Should be kept
    val todayDate = today // Should be kept

    val oldDir1 = createDateDirectory(oldDate1.format(dateFormatter))
    val oldDir2 = createDateDirectory(oldDate2.format(dateFormatter))
    val recentDir = createDateDirectory(recentDate.format(dateFormatter))
    val todayDir = createDateDirectory(todayDate.format(dateFormatter))

    // Add some test files to each directory
    createTestFile(oldDir1, "test1.json")
    createTestFile(oldDir2, "test2.json")
    createTestFile(recentDir, "test3.json")
    createTestFile(todayDir, "test4.json")

    // Verify all directories exist before cleanup
    assertTrue(oldDir1.exists())
    assertTrue(oldDir2.exists())
    assertTrue(recentDir.exists())
    assertTrue(todayDir.exists())

    // Trigger cleanup manually
    val cleanupMethod = MempoolCleanupService::class.java.getDeclaredMethod("performCleanup")
    cleanupMethod.isAccessible = true
    cleanupMethod.invoke(cleanupService)

    // Verify old directories are deleted
    assertFalse(oldDir1.exists(), "Directory older than retention period should be deleted")
    assertFalse(oldDir2.exists(), "Directory older than retention period should be deleted")
    
    // Verify recent directories are kept
    assertTrue(recentDir.exists(), "Directory within retention period should be kept")
    assertTrue(todayDir.exists(), "Today's directory should be kept")
  }

  @Test
  fun `should handle non-date directories gracefully`() {
    // Create some non-date directories
    val nonDateDir1 = File(tempDir, "not-a-date")
    val nonDateDir2 = File(tempDir, "invalid-format")
    val validDateDir = createDateDirectory(LocalDate.now().minusDays(10).format(dateFormatter))

    nonDateDir1.mkdirs()
    nonDateDir2.mkdirs()
    
    createTestFile(nonDateDir1, "file1.txt")
    createTestFile(nonDateDir2, "file2.txt")
    createTestFile(validDateDir, "file3.json")

    // Trigger cleanup
    val cleanupMethod = MempoolCleanupService::class.java.getDeclaredMethod("performCleanup")
    cleanupMethod.isAccessible = true
    cleanupMethod.invoke(cleanupService)

    // Non-date directories should be left alone
    assertTrue(nonDateDir1.exists(), "Non-date directory should not be deleted")
    assertTrue(nonDateDir2.exists(), "Non-date directory should not be deleted")
    
    // Valid old date directory should be deleted
    assertFalse(validDateDir.exists(), "Valid date directory older than retention should be deleted")
  }

  @Test
  fun `should handle missing data directory gracefully`() {
    val missingDirConfig = PersistenceConfig(
      dataDirectory = File(tempDir, "non-existent").absolutePath,
      retentionDays = 7
    )
    val serviceWithMissingDir = MempoolCleanupService(missingDirConfig)

    // Should not throw exception when data directory doesn't exist
    assertDoesNotThrow {
      val cleanupMethod = MempoolCleanupService::class.java.getDeclaredMethod("performCleanup")
      cleanupMethod.isAccessible = true
      cleanupMethod.invoke(serviceWithMissingDir)
    }
  }

  @Test
  fun `should start and stop service without errors`() {
    assertDoesNotThrow {
      cleanupService.start()
      Thread.sleep(50) // Brief pause to let timer start
      cleanupService.stop()
    }
  }

  @Test
  fun `should handle empty directories`() {
    val oldDate = LocalDate.now().minusDays(10)
    val emptyOldDir = createDateDirectory(oldDate.format(dateFormatter))

    assertTrue(emptyOldDir.exists())

    // Trigger cleanup
    val cleanupMethod = MempoolCleanupService::class.java.getDeclaredMethod("performCleanup")
    cleanupMethod.isAccessible = true
    cleanupMethod.invoke(cleanupService)

    // Empty old directory should still be deleted
    assertFalse(emptyOldDir.exists(), "Empty old directory should be deleted")
  }

  private fun createDateDirectory(dateString: String): File {
    val dir = File(tempDir, dateString)
    dir.mkdirs()
    return dir
  }

  private fun createTestFile(directory: File, filename: String): File {
    val file = File(directory, filename)
    file.writeText("test content")
    return file
  }
}