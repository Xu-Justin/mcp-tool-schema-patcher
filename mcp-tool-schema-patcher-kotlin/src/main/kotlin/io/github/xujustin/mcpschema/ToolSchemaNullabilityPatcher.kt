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

import com.fasterxml.jackson.annotation.JsonProperty
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema
import org.springaicommunity.mcp.annotation.McpTool
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.kotlinFunction

/**
 * Repairs nullability in provider-generated Kotlin MCP tool schemas. Java tools are returned unchanged.
 * Leaves the original specifications unchanged, preserving tool metadata and call handlers.
 */
object ToolSchemaNullabilityPatcher {

  /**
   * Repairs schema nullability for the matching Kotlin tool methods. Java tools are returned unchanged.
   *
   * @param toolObjects original objects supplied to the annotation provider
   * @param specifications provider specifications, never modified
   * @return repaired specifications retaining tool metadata and call handlers
   */
  @JvmStatic
  fun patch(toolObjects: List<Any>, specifications: List<SyncToolSpecification>): List<SyncToolSpecification> {
    val mcpToolMethodsByName = toolObjects
      .flatMap { it.javaClass.methods.asIterable() }
      .filter { it.isAnnotationPresent(McpTool::class.java) }
      .associateBy { method ->
        val annotation = method.getAnnotation(McpTool::class.java)
        val toolName = annotation.name.ifBlank { method.name }
        toolName
      }
    return specifications.map { specification ->
      val method = mcpToolMethodsByName[specification.tool().name()] ?: return@map specification
      if (!method.declaringClass.isAnnotationPresent(Metadata::class.java)) {
        // Kotlin metadata is needed to inspect nullability; leave Java tools unchanged.
        return@map specification
      }
      val function = method.kotlinFunction ?: return@map specification
      val patchedTool = patchTool(specification.tool(), method, function)
      SyncToolSpecification(patchedTool, specification.callHandler())
    }
  }

  private fun patchTool(tool: McpSchema.Tool, method: Method, function: KFunction<*>): McpSchema.Tool {
    val inputSchema = tool.inputSchema()
    val properties = inputSchema.properties()?.let(::copyMap)
    val required = inputSchema.required()?.toMutableList()
    val defs = inputSchema.defs()?.let(::copyMap)
    val definitions = inputSchema.definitions()?.let(::copyMap)
    function.valueParameters.forEachIndexed { index, parameter ->
      val name = method.parameters[index].name
      val schema = properties?.get(name).asMutableMap() ?: return@forEachIndexed
      val context = Context(
        listOfNotNull(schema["\$defs"].asMutableMap(), defs),
        listOfNotNull(schema["definitions"].asMutableMap(), definitions),
        widenNullable = false,
      )
      patchNode(schema, parameter.type, context)
      if (parameter.type.isMarkedNullable) required?.remove(name)
    }
    val patchedInputSchema = McpSchema.JsonSchema(
      inputSchema.type(),
      properties,
      required?.takeIf { it.isNotEmpty() },
      inputSchema.additionalProperties(),
      defs,
      definitions,
    )
    val outputSchema = tool.outputSchema()?.let(::copyMap)?.also { schema ->
      val context = Context(
        listOfNotNull(schema["\$defs"].asMutableMap()),
        listOfNotNull(schema["definitions"].asMutableMap()),
        widenNullable = true,
      )
      patchNode(schema, function.returnType, context)
    }
    return McpSchema.Tool(
      tool.name(),
      tool.title(),
      tool.description(),
      patchedInputSchema,
      outputSchema,
      tool.annotations(),
      tool.meta(),
    )
  }

  private class Context(
    private val defs: List<Map<String, Any?>>,
    private val definitions: List<Map<String, Any?>>,
    val widenNullable: Boolean,
  ) {
    private val visitedRefs = mutableSetOf<String>()

    fun resolveOnce(ref: String): MutableMap<String, Any?>? {
      if (!visitedRefs.add(ref)) return null
      return when {
        ref.startsWith("#/\$defs/") -> defs.firstNotNullOfOrNull { it[ref.removePrefix("#/\$defs/")].asMutableMap() }
        ref.startsWith("#/definitions/") ->
          definitions.firstNotNullOfOrNull { it[ref.removePrefix("#/definitions/")].asMutableMap() }

        else -> null
      }
    }
  }

  private fun patchNode(node: MutableMap<String, Any?>, type: KType, context: Context) {
    (node["\$ref"] as? String)?.let { ref ->
      context.resolveOnce(ref)?.let { patchNode(it, type, context) }
      return
    }
    (node["anyOf"] as? List<*>)?.forEach { option -> option.asMutableMap()?.let { patchNode(it, type, context) } }
    val kClass = type.classifier as? KClass<*> ?: return
    val javaClass = kClass.java
    when {
      Collection::class.java.isAssignableFrom(javaClass) || javaClass.isArray -> {
        val itemType = type.arguments.firstOrNull()?.type ?: return
        node["items"].asMutableMap()?.let { patchNode(it, itemType, context) }
      }

      Map::class.java.isAssignableFrom(javaClass) -> {
        val valueType = type.arguments.getOrNull(1)?.type ?: return
        node["additionalProperties"].asMutableMap()?.let { patchNode(it, valueType, context) }
      }

      javaClass.isAnnotationPresent(Metadata::class.java) && !javaClass.isEnum -> patchObject(node, type, kClass, context)
    }
  }

  private fun patchObject(node: MutableMap<String, Any?>, type: KType, kClass: KClass<*>, context: Context) {
    val bindings = kClass.typeParameters.zip(type.arguments).mapNotNull { (parameter, argument) ->
      argument.type?.let { parameter to it }
    }.toMap()
    val properties = node["properties"].asMutableMap() ?: return

    @Suppress("UNCHECKED_CAST")
    val required = node["required"] as? MutableList<Any?>
    kClass.memberProperties.forEach { property ->
      val name = jsonName(kClass, property)
      val schema = properties[name].asMutableMap() ?: return@forEach
      val propertyType = resolveType(property.returnType, bindings)
      patchNode(schema, propertyType, context)
      if (propertyType.isMarkedNullable) {
        required?.remove(name)
        if (context.widenNullable) properties[name] = widenToNullable(schema)
      }
    }
    if (required?.isEmpty() == true) node.remove("required")
  }

  // Resolve wrapper properties such as Page<T>.data before descending into their schemas.
  private fun resolveType(type: KType, bindings: Map<KTypeParameter, KType>): KType {
    val classifier = type.classifier
    if (classifier is KTypeParameter) {
      val bound = bindings[classifier] ?: return type
      return bound.withNullability(type.isMarkedNullable || bound.isMarkedNullable)
    }
    if (classifier !is KClass<*> || type.arguments.isEmpty()) return type
    val arguments = type.arguments.map { argument ->
      argument.type?.let { KTypeProjection(argument.variance, resolveType(it, bindings)) } ?: argument
    }
    return if (arguments == type.arguments) type else classifier.createType(arguments, type.isMarkedNullable, type.annotations)
  }

  private fun jsonName(kClass: KClass<*>, property: KProperty1<*, *>): String {
    val annotation = property.javaField?.getAnnotation(JsonProperty::class.java)
      ?: property.javaGetter?.getAnnotation(JsonProperty::class.java)
      ?: kClass.primaryConstructor?.parameters
        ?.firstOrNull { it.name == property.name }
        ?.annotations?.filterIsInstance<JsonProperty>()?.firstOrNull()
    return annotation?.value?.takeIf { it.isNotEmpty() } ?: property.name
  }

  private fun widenToNullable(schema: MutableMap<String, Any?>): MutableMap<String, Any?> {
    val widened = linkedMapOf<String, Any?>()
    schema.remove("description")?.let { widened["description"] = it }
    widened["anyOf"] = mutableListOf(schema, linkedMapOf("type" to "null"))
    return widened
  }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asMutableMap() = this as? MutableMap<String, Any?>

private fun copyMap(source: Map<String, Any?>): MutableMap<String, Any?> =
  source.mapValuesTo(LinkedHashMap()) { copyValue(it.value) }

@Suppress("UNCHECKED_CAST")
private fun copyValue(value: Any?): Any? = when (value) {
  is Map<*, *> -> copyMap(value as Map<String, Any?>)
  is List<*> -> value.mapTo(ArrayList()) { copyValue(it) }
  else -> value
}
