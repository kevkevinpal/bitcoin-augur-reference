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

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import xyz.block.augurref.config.PersistenceConfig
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MempoolPersistenceCleanupTest {

  @TempDir
  lateinit var tempDir: File

  private lateinit var persistence: MempoolPersistence
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  @BeforeEach
  fun setUp() {
    // Create test directories and files
    createTestDataStructure()
  }

  @AfterEach
  fun tearDown() {
    // Cleanup is handled by @TempDir
  }

  private fun createTestDataStructure() {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val twoDaysAgo = today.minusDays(2)
    val threeDaysAgo = today.minusDays(3)

    // Create date directories with test files
    createDateDir(today, 2)
    createDateDir(yesterday, 3)
    createDateDir(twoDaysAgo, 1)
    createDateDir(threeDaysAgo, 4)
  }

  private fun createDateDir(date: LocalDate, fileCount: Int) {
    val dateDir = File(tempDir, date.format(dateFormatter))
    dateDir.mkdirs()
    
    repeat(fileCount) { i ->
      val testFile = File(dateDir, "test_${i}.json")
      testFile.writeText("{\"test\": \"data\"}")
    }
  }

  @Test
  fun testCleanupDisabled() {
    val config = PersistenceConfig(
      dataDirectory = tempDir.absolutePath,
      cleanupDays = 0
    )
    persistence = MempoolPersistence(config)

    val initialDirCount = tempDir.listFiles()?.size ?: 0
    persistence.cleanupOldFiles()
    val finalDirCount = tempDir.listFiles()?.size ?: 0

    assertEquals(initialDirCount, finalDirCount, "No directories should be deleted when cleanup is disabled")
  }

  @Test
  fun testCleanupWithNegativeValue() {
    val config = PersistenceConfig(
      dataDirectory = tempDir.absolutePath,
      cleanupDays = -1
    )
    persistence = MempoolPersistence(config)

    val initialDirCount = tempDir.listFiles()?.size ?: 0
    persistence.cleanupOldFiles()
    val finalDirCount = tempDir.listFiles()?.size ?: 0

    assertEquals(initialDirCount, finalDirCount, "No directories should be deleted with negative cleanup days")
  }

  @Test
  fun testCleanupOldDirectories() {
    val config = PersistenceConfig(
      dataDirectory = tempDir.absolutePath,
      cleanupDays = 2
    )
    persistence = MempoolPersistence(config)

    val initialDirCount = tempDir.listFiles()?.size ?: 0
    assertEquals(4, initialDirCount, "Should start with 4 date directories")

    persistence.cleanupOldFiles()

    val remainingDirs = tempDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
    val expectedDirs = listOf(
      LocalDate.now().format(dateFormatter),
      LocalDate.now().minusDays(1).format(dateFormatter)
    ).sorted()

    assertEquals(expectedDirs, remainingDirs, "Should keep only directories from last 2 days")
  }

  @Test
  fun testCleanupPreservesRecentData() {
    val config = PersistenceConfig(
      dataDirectory = tempDir.absolutePath,
      cleanupDays = 1
    )
    persistence = MempoolPersistence(config)

    persistence.cleanupOldFiles()

    val remainingDirs = tempDir.listFiles()?.map { it.name } ?: emptyList()
    val todayDir = LocalDate.now().format(dateFormatter)

    assertTrue(remainingDirs.contains(todayDir), "Today's directory should be preserved")
    assertEquals(1, remainingDirs.size, "Should only preserve today's directory")
  }

  @Test
  fun testCleanupNonExistentDirectory() {
    val nonExistentDir = File(tempDir, "non_existent")
    val config = PersistenceConfig(
      dataDirectory = nonExistentDir.absolutePath,
      cleanupDays = 7
    )
    persistence = MempoolPersistence(config)

    // Should not throw exception
    assertDoesNotThrow {
      persistence.cleanupOldFiles()
    }
  }

  @Test
  fun testCleanupIgnoresInvalidDateDirectories() {
    // Create a directory with invalid date format
    val invalidDir = File(tempDir, "invalid-date")
    invalidDir.mkdirs()
    File(invalidDir, "test.json").writeText("{}")

    val config = PersistenceConfig(
      dataDirectory = tempDir.absolutePath,
      cleanupDays = 1
    )
    persistence = MempoolPersistence(config)

    persistence.cleanupOldFiles()

    // Invalid directory should still exist
    assertTrue(invalidDir.exists(), "Directory with invalid date format should be preserved")
  }

  @Test
  fun testCleanupIgnoresFiles() {
    // Create a file in the root data directory
    val rootFile = File(tempDir, "root_file.txt")
    rootFile.writeText("test")

    val config = PersistenceConfig(
      dataDirectory = tempDir.absolutePath,
      cleanupDays = 1
    )
    persistence = MempoolPersistence(config)

    persistence.cleanupOldFiles()

    // Root file should still exist
    assertTrue(rootFile.exists(), "Files in root directory should be preserved")
  }
}