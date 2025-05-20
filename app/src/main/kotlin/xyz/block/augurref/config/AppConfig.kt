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

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class AppConfig(
  val server: ServerConfig = ServerConfig(),
  val bitcoinRpc: BitcoinRpcConfig = BitcoinRpcConfig(),
  val persistence: PersistenceConfig = PersistenceConfig(),
)

@Serializable
data class ServerConfig(
  val host: String = "0.0.0.0",
  val port: Int = 8080,
)

@Serializable
data class BitcoinRpcConfig(
  val url: String = "http://localhost:8332",
  val username: String = "",
  val password: String = "",
)

@Serializable
data class PersistenceConfig(
  val dataDirectory: String = "mempool_data",
) {
  fun getDataDirectoryFile(): File = File(dataDirectory)
}
