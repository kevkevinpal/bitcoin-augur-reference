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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junitpioneer.jupiter.SetEnvironmentVariable
import org.junitpioneer.jupiter.junitpioneer.JunitPioneerExtension

@ExtendWith(JunitPioneerExtension::class)
class ConfigLoaderTest {

  @Test
  fun `should load default configuration`() {
    val config = ConfigLoader.loadConfig()
    
    assertNotNull(config)
    assertEquals("0.0.0.0", config.server.host)
    assertEquals(8080, config.server.port)
    assertEquals("mempool_data", config.persistence.dataDirectory)
    assertEquals(0, config.persistence.retentionDays) // Default should be 0 (disabled)
  }

  @Test
  @SetEnvironmentVariable(key = "AUGUR_RETENTION_DAYS", value = "30")
  fun `should override retentionDays from environment variable`() {
    val config = ConfigLoader.loadConfig()
    
    assertEquals(30, config.persistence.retentionDays)
  }

  @Test
  @SetEnvironmentVariable(key = "AUGUR_RETENTION_DAYS", value = "invalid")
  fun `should use default retentionDays when environment variable is invalid`() {
    val config = ConfigLoader.loadConfig()
    
    assertEquals(0, config.persistence.retentionDays) // Should fall back to default
  }

  @Test
  @SetEnvironmentVariable(key = "AUGUR_DATA_DIR", value = "/custom/path")
  @SetEnvironmentVariable(key = "AUGUR_RETENTION_DAYS", value = "14")
  fun `should override both persistence settings from environment variables`() {
    val config = ConfigLoader.loadConfig()
    
    assertEquals("/custom/path", config.persistence.dataDirectory)
    assertEquals(14, config.persistence.retentionDays)
  }

  @Test
  fun `should create default persistence config with correct values`() {
    val persistenceConfig = PersistenceConfig()
    
    assertEquals("mempool_data", persistenceConfig.dataDirectory)
    assertEquals(0, persistenceConfig.retentionDays)
    assertEquals("mempool_data", persistenceConfig.getDataDirectoryFile().path)
  }

  @Test
  fun `should create custom persistence config with retention enabled`() {
    val persistenceConfig = PersistenceConfig(
      dataDirectory = "/custom/data",
      retentionDays = 7
    )
    
    assertEquals("/custom/data", persistenceConfig.dataDirectory)
    assertEquals(7, persistenceConfig.retentionDays)
    assertEquals("/custom/data", persistenceConfig.getDataDirectoryFile().path)
  }
}