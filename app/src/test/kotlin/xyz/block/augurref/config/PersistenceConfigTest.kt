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

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class PersistenceConfigTest {

  @Test
  fun testDefaultValues() {
    val config = PersistenceConfig()
    assertEquals("mempool_data", config.dataDirectory)
    assertEquals(0, config.cleanupDays) // Cleanup disabled by default
  }

  @Test
  fun testCustomValues() {
    val config = PersistenceConfig(
      dataDirectory = "custom_data",
      cleanupDays = 7
    )
    assertEquals("custom_data", config.dataDirectory)
    assertEquals(7, config.cleanupDays)
  }

  @Test
  fun testGetDataDirectoryFile() {
    val config = PersistenceConfig(dataDirectory = "test_dir")
    val file = config.getDataDirectoryFile()
    assertEquals(File("test_dir"), file)
    assertEquals("test_dir", file.name)
  }

  @Test
  fun testCleanupDisabledWithZero() {
    val config = PersistenceConfig(cleanupDays = 0)
    assertEquals(0, config.cleanupDays)
  }

  @Test
  fun testCleanupDisabledWithNegativeValue() {
    val config = PersistenceConfig(cleanupDays = -1)
    assertEquals(-1, config.cleanupDays)
  }

  @Test
  fun testCleanupEnabledWithPositiveValue() {
    val config = PersistenceConfig(cleanupDays = 30)
    assertEquals(30, config.cleanupDays)
  }
}