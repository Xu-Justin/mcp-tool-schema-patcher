# mcp-tool-schema-patcher-kotlin

Repairs Kotlin nullability in MCP tool input and output schemas.

## Why do we need this?

`mcp-annotations` generates schemas that can lose Kotlin `?` information.
Nullable parameters and properties become required, and nullable output properties reject `null`.

## How do we solve this?

`ToolSchemaNullabilityPatcher` reads the original Kotlin tool types and removes nullable
parameters and properties from `required`. Nullable output properties also accept `null`
through `anyOf`. Original specifications, tool metadata, and call handlers are preserved.
Java tools are returned unchanged.

Nullable inputs become optional; explicit `null` is not added to their allowed types.
Output null handling applies to object properties, not the return value itself or nullable
collection elements and map values. This patcher does not repair definition references.

## Example

```kotlin
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam

data class Customer(val name: String, val note: String? = null)

class MyTools {
  @McpTool(name = "customer", title = "Customer", description = "Echo a customer", generateOutputSchema = true)
  fun customer(customer: Customer, @McpToolParam(description = "Optional note") note: String?): Customer =
    customer.copy(note = note ?: customer.note)
}
```

**Before:** both the `note` parameter and `Customer.note` are required despite being nullable.
The output schema also rejects a customer whose `note` is `null`.

**After:** the nullable parameter and property can be omitted. The output property also
accepts explicit `null`. The non-nullable `customer` parameter and `name` property stay required.

```diff
inputSchema.required
- ["customer", "note"]
+ ["customer"]

inputSchema.properties.customer.required
- ["name", "note"]
+ ["name"]

outputSchema.required
- ["name", "note"]
+ ["name"]

outputSchema.properties.note
- {"type": "string"}
+ {"anyOf": [{"type": "string"}, {"type": "null"}]}
```

Only the schemas are shown below; handlers and tool metadata are unchanged.

<table>
<thead>
<tr><th>Before: nullability lost</th><th>After: nullability repaired</th></tr>
</thead>
<tbody>
<tr>
<td>
<pre><code>{
  "inputSchema": {
    "properties": {
      "customer": {
        "properties": {
          "name": {"type": "string"},
          "note": {"type": "string"}
        },
        "required": ["name", "note"],
        "type": "object"
      },
      "note": {
        "description": "Optional note",
        "type": "string"
      }
    },
    "required": ["customer", "note"],
    "type": "object"
  },
  "outputSchema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "properties": {
      "name": {"type": "string"},
      "note": {"type": "string"}
    },
    "required": ["name", "note"],
    "type": "object"
  }
}</code></pre>
</td>
<td>
<pre><code>{
  "inputSchema": {
    "properties": {
      "customer": {
        "properties": {
          "name": {"type": "string"},
          "note": {"type": "string"}
        },
        "required": ["name"],
        "type": "object"
      },
      "note": {
        "description": "Optional note",
        "type": "string"
      }
    },
    "required": ["customer"],
    "type": "object"
  },
  "outputSchema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "properties": {
      "name": {"type": "string"},
      "note": {
        "anyOf": [
          {"type": "string"},
          {"type": "null"}
        ]
      }
    },
    "required": ["name"],
    "type": "object"
  }
}</code></pre>
</td>
</tr>
</tbody>
</table>

## Usage

```kotlin
dependencies {
  implementation("io.github.xu-justin:mcp-tool-schema-patcher-kotlin:<version>")
}
```

```kotlin
import io.github.xujustin.mcpschema.ToolSchemaNullabilityPatcher
import org.springaicommunity.mcp.provider.tool.SyncStatelessMcpToolProvider

val toolObjects = listOf(MyTools())
val provider = SyncStatelessMcpToolProvider(toolObjects)
val specs = ToolSchemaNullabilityPatcher.patch(toolObjects, provider.toolSpecifications)
// Pass specs to the server builder's .tools(specs).
```

Supply the same tool objects used by the provider so the patcher can inspect their Kotlin types.

## Compatibility

| Component                 | Target / tested |
|---------------------------|-----------------|
| Java                      | 17+             |
| Spring Boot / Spring AI    | 3.5.x / 1.1.x   |
| `mcp-annotations`         | 0.9.x / 0.9.0   |
| `mcp-core`                | 1.0.x / 1.0.1   |
| Kotlin compiler / runtime | 1.9.25 baseline |
