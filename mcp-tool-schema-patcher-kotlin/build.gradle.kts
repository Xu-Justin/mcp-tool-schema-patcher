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
plugins {
  kotlin("jvm")
  id("org.jetbrains.dokka")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17); javaParameters.set(true) } }
dependencies {
  testRuntimeOnly("io.modelcontextprotocol.sdk:mcp-json-jackson2:1.0.1")
  api(project(":mcp-tool-schema-patcher-core"))
  implementation(kotlin("reflect"))
  implementation("com.fasterxml.jackson.core:jackson-annotations:2.19.0")
  implementation("org.springaicommunity:mcp-annotations:0.9.0")
}

tasks.named("assemble") { dependsOn("dokkaJavadocJar") }
