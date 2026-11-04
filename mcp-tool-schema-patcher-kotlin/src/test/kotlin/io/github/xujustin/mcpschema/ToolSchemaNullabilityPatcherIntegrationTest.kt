/*
 * Copyright 2026 mcp-tool-schema-patcher contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.xujustin.mcpschema

import io.modelcontextprotocol.json.McpJsonDefaults
import io.modelcontextprotocol.json.TypeRef
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springaicommunity.mcp.provider.tool.SyncStatelessMcpToolProvider

class ToolSchemaNullabilityPatcherIntegrationTest {

  @Test
  fun patchesProviderSchemaWithoutModifyingOriginal() {
    val toolObjects = listOf(MyTools())
    val provider = SyncStatelessMcpToolProvider(toolObjects)
    val specifications = provider.toolSpecifications
    val originalToolsJson = serializeTools(specifications)

    val patchedSpecifications = ToolSchemaNullabilityPatcher.patch(toolObjects, ToolSchemaDefsPatcher.patch(specifications))
    val patchedCustomerTool = patchedSpecifications.single().tool()

    // All fields exist and have values.
    assertToolCustomer(patchedCustomerTool, mapOf(
      "name" to "Justin",
      "note" to "Customer note",
      "items" to mapOf("data" to listOf(mapOf("id" to "item-1", "name" to "Item"))),
      "nested" to mapOf("data" to mapOf("data" to listOf(mapOf("id" to "item-1", "name" to "Item")))),
    ), "note" to "Customer note")

    // Nullable fields are explicitly null.
    assertToolCustomer(patchedCustomerTool, mapOf(
      "name" to "Justin",
      "note" to null,
      "items" to mapOf("data" to null),
      "nested" to mapOf("data" to mapOf("data" to listOf(mapOf("id" to "item-1", "name" to null)))),
    ), "note" to null)

    // Nullable fields are omitted.
    assertToolCustomer(patchedCustomerTool, mapOf(
      "name" to "Justin",
      "items" to emptyMap<String, Any?>(),
      "nested" to mapOf("data" to mapOf("data" to listOf(mapOf("id" to "item-1")))),
    ))

    assertEquals(originalToolsJson, serializeTools(specifications))
  }

  private fun assertToolCustomer(
    tool: McpSchema.Tool,
    customer: Map<String, Any?>,
    vararg parameters: Pair<String, Any?>,
  ) {
    val inputContent = mapOf("customer" to customer) + parameters
    val inputValidation = McpJsonDefaults.getSchemaValidator()
      .validate(toMap(tool.inputSchema()), inputContent)
    assertTrue(inputValidation.valid(), inputValidation.errorMessage())

    val outputValidation = McpJsonDefaults.getSchemaValidator()
      .validate(tool.outputSchema(), customer)
    assertTrue(outputValidation.valid(), outputValidation.errorMessage())
  }

  private fun serializeTools(specifications: List<SyncToolSpecification>): String =
    McpJsonDefaults.getMapper().writeValueAsString(specifications.map { it.tool() })

  private fun toMap(value: Any): Map<String, Any?> =
    McpJsonDefaults.getMapper().convertValue(value, object : TypeRef<Map<String, Any?>>() {})

  class MyTools {
    @McpTool(name = "customer", title = "Customer", description = "Echo a customer", generateOutputSchema = true)
    fun customer(customer: Customer, @McpToolParam(description = "Optional note") note: String?): Customer =
      customer.copy(note = note ?: customer.note)
  }

  data class Customer(
    val name: String,
    val note: String? = null,
    val items: Page<List<Item>>,
    val nested: Result<Page<List<Item>>>,
  )

  data class Item(val id: String, val name: String? = null)
  data class Page<T>(val data: T? = null)
  data class Result<T>(val data: T? = null)
}
