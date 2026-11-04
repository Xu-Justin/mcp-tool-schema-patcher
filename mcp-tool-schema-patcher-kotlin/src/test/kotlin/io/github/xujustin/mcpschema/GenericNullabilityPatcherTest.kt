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
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.provider.tool.SyncStatelessMcpToolProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class GenericNullabilityPatcherTest {

  data class Mapping(val providerHotelId: String, val providerHotelName: String? = null)
  data class Count<T>(val data: T? = null)
  data class Applicable<T>(val data: T? = null)
  data class Content(
    val hotelMappingStatuses: Count<List<Mapping>>,
    val nested: Applicable<Count<List<Mapping>>>,
  )

  class Tools {
    @McpTool(name = "generic_results", description = "Generic results", generateOutputSchema = true)
    fun results(content: Content): Content = content
  }

  @Test
  fun resolvesNestedGenericPropertiesWithoutWeakeningRequiredFields() {
    val tools = listOf(Tools())
    val original = SyncStatelessMcpToolProvider(tools).toolSpecifications
    val mapper = McpJsonDefaults.getMapper()
    val originalJson = mapper.writeValueAsString(original.single().tool())
    val patched = ToolSchemaNullabilityPatcher.patch(tools, ToolSchemaDefsPatcher.patch(original)).single().tool()
    val validator = McpJsonDefaults.getSchemaValidator()
    val nullableMapping = mapOf("providerHotelId" to "hotel-1", "providerHotelName" to null)
    fun content(mapping: Map<String, Any?>) = mapOf(
      "hotelMappingStatuses" to mapOf("data" to listOf(mapping)),
      "nested" to mapOf("data" to mapOf("data" to listOf(mapping))),
    )

    val output = validator.validate(patched.outputSchema(), content(nullableMapping))
    assertTrue(output.valid(), output.errorMessage())
    val inputSchema = mapper.convertValue(patched.inputSchema(), object : TypeRef<Map<String, Any?>>() {})
    val input = validator.validate(inputSchema, mapOf("content" to content(mapOf("providerHotelId" to "hotel-1"))))
    assertTrue(input.valid(), input.errorMessage())
    assertFalse(validator.validate(patched.outputSchema(), content(nullableMapping + ("providerHotelId" to null))).valid())
    assertEquals(mapper.writeValueAsString(original.single().tool()), originalJson)
  }
}
