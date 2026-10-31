# mcp-tool-schema-patcher-core

Repairs references in MCP tool input schemas.

## Why do we need this?

`mcp-annotations` puts each parameter's `$defs` and `definitions` inside that parameter,
while `$ref` points to the input schema root. Clients cannot resolve those references.

## How do we solve this?

`ToolSchemaDefsPatcher` moves definitions to the root. It preserves existing root entries,
renames colliding definitions, and rewrites affected references, including references
between definitions. Output schemas, handlers, and tool metadata are preserved.
It copies input schemas without reflection or a Kotlin dependency.

Only parameter-local definitions are hoisted. External references are unchanged. Copies preserve key order. Retained handlers, metadata, and output schemas should be treated as read-only.

## Example

**Before:** `#/$defs/Address` looks for `Address` in `$defs` at the input schema root.
The definition is actually inside `properties.customer.$defs`, so the reference cannot resolve.
Nested `$defs` are allowed; the reference and definition locations do not match.

See the [JSON Schema guide to JSON Pointer](https://json-schema.org/understanding-json-schema/structuring#json-pointer) for how `$ref` locates a definition.

**After:** the patcher moves `Address` to the root `$defs`, where both references can find it.
Only the definition's location changes; both `$ref` values stay `#/$defs/Address`.

```diff
- inputSchema.properties.customer.$defs.Address
+ inputSchema.$defs.Address
```

<table>
<thead>
<tr><th>Before: reference cannot resolve</th><th>After: reference resolves</th></tr>
</thead>
<tbody>
<tr>
<td>
<pre><code>{
  "inputSchema": {
    "properties": {
      "customer": {
        "$defs": {
          "Address": {
            "properties": {
              "street": {"type": "string"}
            },
            "required": ["street"],
            "type": "object"
          }
        },
        "properties": {
          "billingAddress": {"$ref": "#/$defs/Address"},
          "shippingAddress": {"$ref": "#/$defs/Address"}
        },
        "required": ["billingAddress", "shippingAddress"],
        "type": "object"
      }
    },
    "required": ["customer"],
    "type": "object"
  }
}</code></pre>
</td>
<td>
<pre><code>{
  "inputSchema": {
    "$defs": {
      "Address": {
        "properties": {
          "street": {"type": "string"}
        },
        "required": ["street"],
        "type": "object"
      }
    },
    "properties": {
      "customer": {
        "properties": {
          "billingAddress": {"$ref": "#/$defs/Address"},
          "shippingAddress": {"$ref": "#/$defs/Address"}
        },
        "required": ["billingAddress", "shippingAddress"],
        "type": "object"
      }
    },
    "required": ["customer"],
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
  implementation("io.github.xu-justin:mcp-tool-schema-patcher-core:<version>")
}
```

```java
import io.github.xujustin.mcpschema.ToolSchemaDefsPatcher;
import org.springaicommunity.mcp.provider.tool.SyncStatelessMcpToolProvider;

List<Object> toolObjects = List.of(new MyTools());
var provider = new SyncStatelessMcpToolProvider(toolObjects);
var specs = ToolSchemaDefsPatcher.patch(provider.getToolSpecifications());
// Pass specs to the server builder's .tools(specs).
```

## Compatibility

| Component               | Target / tested |
|-------------------------|-----------------|
| Java                    | 17+             |
| Spring Boot / Spring AI | 3.5.x / 1.1.x   |
| `mcp-annotations`       | 0.9.x / 0.9.0   |
| `mcp-core`              | 1.0.x / 1.0.1   |
