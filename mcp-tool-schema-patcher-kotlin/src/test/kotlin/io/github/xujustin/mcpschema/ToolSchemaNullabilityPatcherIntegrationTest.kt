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

    val patchedSpecifications = ToolSchemaNullabilityPatcher.patch(toolObjects, specifications)
    val patchedCustomerTool = patchedSpecifications.single().tool()

    val inputValidation = McpJsonDefaults.getSchemaValidator()
      .validate(toMap(patchedCustomerTool.inputSchema()), getCustomerArguments())
    assertTrue(inputValidation.valid(), inputValidation.errorMessage())

    val outputValidation = McpJsonDefaults.getSchemaValidator()
      .validate(patchedCustomerTool.outputSchema(), getCustomerOutput())
    assertTrue(outputValidation.valid(), outputValidation.errorMessage())

    assertEquals(originalToolsJson, serializeTools(specifications))
  }

  private fun serializeTools(specifications: List<SyncToolSpecification>): String =
    McpJsonDefaults.getMapper().writeValueAsString(specifications.map { it.tool() })

  private fun getCustomerArguments(): Map<String, Any?> {
    val customer = Customer("Justin")
    return mapOf("customer" to toMap(customer).filterValues { it != null })
  }

  private fun getCustomerOutput(): Map<String, Any?> {
    val customer = Customer("Justin")
    return toMap(customer)
  }

  private fun toMap(value: Any): Map<String, Any?> =
    McpJsonDefaults.getMapper().convertValue(value, object : TypeRef<Map<String, Any?>>() {})

  class MyTools {
    @McpTool(name = "customer", title = "Customer", description = "Echo a customer", generateOutputSchema = true)
    fun customer(customer: Customer, @McpToolParam(description = "Optional note") note: String?): Customer =
      customer.copy(note = note ?: customer.note)
  }

  data class Customer(val name: String, val note: String? = null)
}
