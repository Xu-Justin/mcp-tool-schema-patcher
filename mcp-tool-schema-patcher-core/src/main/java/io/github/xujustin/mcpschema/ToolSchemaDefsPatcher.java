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

import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repairs definition references in provider-generated MCP tool schemas.
 * Leaves the original specifications unchanged, preserving tool metadata and call handlers.
 */
public final class ToolSchemaDefsPatcher {
  private ToolSchemaDefsPatcher() {}

  /**
   * Repairs schemas in provider-generated MCP tool specifications.
   *
   * @param specifications provider specifications, never modified
   * @return new specifications retaining tool metadata and call handlers
   */
  public static List<SyncToolSpecification> patch(List<SyncToolSpecification> specifications) {
    return specifications.stream().map(specification -> {
      var tool = specification.tool();
      var patchedInputSchema = patchJsonSchema(tool.inputSchema());
      return new SyncToolSpecification(
          new McpSchema.Tool(
              tool.name(),
              tool.title(),
              tool.description(),
              patchedInputSchema,
              tool.outputSchema(),
              tool.annotations(),
              tool.meta()
          ),
          specification.callHandler()
      );
    }).toList();
  }

  private static McpSchema.JsonSchema patchJsonSchema(McpSchema.JsonSchema schema) {
    var properties = schema.properties() == null ? null : copyMap(schema.properties());
    var defs = schema.defs() == null ? new LinkedHashMap<String, Object>() : copyMap(schema.defs());
    var definitions = schema.definitions() == null ? new LinkedHashMap<String, Object>() : copyMap(schema.definitions());
    if (properties != null) {
      hoist(properties, defs, definitions);
    }
    return new McpSchema.JsonSchema(
        schema.type(),
        properties,
        schema.required() == null ? null : new ArrayList<>(schema.required()),
        schema.additionalProperties(),
        defs.isEmpty() && schema.defs() == null ? null : defs,
        definitions.isEmpty() && schema.definitions() == null ? null : definitions
    );
  }

  private static void hoist(
      Map<String, Object> properties,
      Map<String, Object> defs,
      Map<String, Object> definitions
  ) {
    var reservedDefs = reservedNames(properties, defs, "$defs");
    var reservedDefinitions = reservedNames(properties, definitions, "definitions");
    for (var property : properties.values()) {
      if (!(property instanceof Map<?, ?>)) {
        continue;
      }
      var schema = definitionMap(property);
      var localDefs = definitionMap(schema.get("$defs"));
      var localDefinitions = definitionMap(schema.get("definitions"));
      var defNames = allocateNames(localDefs, defs, reservedDefs);
      var definitionNames = allocateNames(localDefinitions, definitions, reservedDefinitions);
      var refs = new LinkedHashMap<String, String>();
      defNames.forEach((oldName, newName) ->
          refs.put(pointer("$defs", oldName), pointer("$defs", newName))
      );
      definitionNames.forEach((oldName, newName) ->
          refs.put(pointer("definitions", oldName), pointer("definitions", newName))
      );
      // Rewrite both namespaces before moving either: definitions can refer to one another.
      rewriteReferences(schema, refs);
      defNames.forEach((oldName, newName) -> defs.put(newName, localDefs.get(oldName)));
      definitionNames.forEach((oldName, newName) -> definitions.put(newName, localDefinitions.get(oldName)));
      schema.remove("$defs");
      schema.remove("definitions");
    }
  }

  private static Set<String> reservedNames(
      Map<String, Object> properties,
      Map<String, Object> root,
      String key
  ) {
    var reserved = new LinkedHashSet<>(root.keySet());
    for (var property : properties.values()) {
      reserved.addAll(definitionMap(definitionMap(property).get(key)).keySet());
    }
    return reserved;
  }

  private static Map<String, String> allocateNames(
      Map<String, Object> local,
      Map<String, Object> root,
      Set<String> reserved
  ) {
    var names = new LinkedHashMap<String, String>();
    for (var name : local.keySet()) {
      var target = name;
      if (root.containsKey(name)) {
        int suffix = 2;
        do {
          target = name + "_" + suffix++;
        } while (!reserved.add(target));
      }
      names.put(name, target);
    }
    return names;
  }

  private static String pointer(String key, String name) {
    return "#/" + key + "/" + name.replace("~", "~0").replace("/", "~1");
  }

  private static void rewriteReferences(Object value, Map<String, String> refs) {
    if (value instanceof Map<?, ?>) {
      definitionMap(value).replaceAll((key, child) -> {
        if ("$ref".equals(key) && child instanceof String ref) {
          return rewriteReference(ref, refs);
        }
        rewriteReferences(child, refs);
        return child;
      });
    } else if (value instanceof List<?> list) {
      list.forEach(child -> rewriteReferences(child, refs));
    }
  }

  private static String rewriteReference(String ref, Map<String, String> refs) {
    for (var replacement : refs.entrySet()) {
      var prefix = replacement.getKey();
      if (ref.equals(prefix) || ref.startsWith(prefix + "/")) {
        return replacement.getValue() + ref.substring(prefix.length());
      }
    }
    return ref;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> definitionMap(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
  }

  private static Map<String, Object> copyMap(Map<?, ?> source) {
    var copy = new LinkedHashMap<String, Object>();
    source.forEach((key, value) -> copy.put((String) key, copyValue(value)));
    return copy;
  }

  private static Object copyValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      return copyMap(map);
    }
    if (value instanceof List<?> list) {
      var copy = new ArrayList<>();
      list.forEach(item -> copy.add(copyValue(item)));
      return copy;
    }
    return value;
  }
}
