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

package io.github.xujustin.mcpschema;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.provider.tool.SyncStatelessMcpToolProvider;

import java.io.IOException;
import java.util.List;
import java.util.Map;

class ToolSchemaDefsPatcherIntegrationTest {

  @Test
  void patchesProviderSchemaWithoutModifyingOriginal() throws IOException {
    var toolObjects = List.<Object>of(new MyTools());
    var provider = new SyncStatelessMcpToolProvider(toolObjects);
    var specifications = provider.getToolSpecifications();
    var originalToolsJson = serializeTools(specifications);

    var patchedSpecifications = ToolSchemaDefsPatcher.patch(specifications);
    var patchedCustomerTool = patchedSpecifications.get(0).tool();

    assertToolCustomer(patchedCustomerTool, Map.of(
        "billingAddress", Map.of("street", "123 Main Street"),
        "shippingAddress", Map.of("street", "456 Oak Street")
    ));

    assertEquals(originalToolsJson, serializeTools(specifications));
  }

  private static void assertToolCustomer(McpSchema.Tool tool, Map<String, Object> customer) {
    assertTool(tool, Map.of("customer", customer), customer);
  }

  private static void assertTool(
      McpSchema.Tool tool,
      Map<String, Object> inputContent,
      Map<String, Object> outputContent
  ) {
    var inputValidation = McpJsonDefaults.getSchemaValidator()
        .validate(toMap(tool.inputSchema()), inputContent);
    assertTrue(inputValidation.valid(), inputValidation.errorMessage());

    var outputValidation = McpJsonDefaults.getSchemaValidator()
        .validate(tool.outputSchema(), outputContent);
    assertTrue(outputValidation.valid(), outputValidation.errorMessage());
  }

  private static String serializeTools(
      List<McpStatelessServerFeatures.SyncToolSpecification> specifications
  ) throws IOException {
    return McpJsonDefaults.getMapper().writeValueAsString(
        specifications.stream().map(McpStatelessServerFeatures.SyncToolSpecification::tool).toList()
    );
  }

  private static Map<String, Object> toMap(Object value) {
    return McpJsonDefaults.getMapper().convertValue(value, new TypeRef<>() {});
  }

  public static class MyTools {
    @McpTool(name = "customer", title = "Customer", description = "Echo a customer", generateOutputSchema = true)
    public Customer customer(Customer customer) {
      return customer;
    }
  }

  public static class Customer {
    public Address billingAddress;
    public Address shippingAddress;
  }

  public static class Address {
    public String street;
  }
}
