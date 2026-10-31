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
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  base
  kotlin("jvm") version "1.9.25" apply false
  id("org.jetbrains.dokka") version "2.0.0" apply false
  id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "jacoco")
  apply(plugin = "com.vanniktech.maven.publish")
  group = providers.gradleProperty("releaseGroup").getOrElse("io.github.xu-justin")
  version = rootProject.version
  repositories { mavenCentral() }

  configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.compilerArgs.add("-parameters")
  }
  dependencies {
    "testImplementation"(platform("org.junit:junit-bom:5.12.2"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
  }
  tasks.withType<AbstractArchiveTask>().configureEach {
    from(rootProject.file("LICENSE")) { into("META-INF") }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
  }
  tasks.withType<Test>().configureEach { useJUnitPlatform() }
  configure<JacocoPluginExtension> { toolVersion = "0.8.13" }
  tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports { xml.required.set(true); html.required.set(true) }
  }
  tasks.named("check") { dependsOn("jacocoTestReport") }
  configure<MavenPublishBaseExtension> {
    publishToMavenCentral(automaticRelease = false)
    signAllPublications()
    pom {
      name.set(project.name)
      description.set("MCP tool schema repairs for Spring AI 1.1.x")
      inceptionYear.set("2026")
      url.set("https://github.com/Xu-Justin/mcp-tool-schema-patcher")
      licenses { license { name.set("The Apache License, Version 2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0.txt") } }
      developers { developer { id.set("Xu-Justin"); name.set("mcp-tool-schema-patcher contributors"); url.set("https://github.com/Xu-Justin") } }
      scm {
        url.set("https://github.com/Xu-Justin/mcp-tool-schema-patcher")
        connection.set("scm:git:https://github.com/Xu-Justin/mcp-tool-schema-patcher.git")
        developerConnection.set("scm:git:ssh://git@github.com/Xu-Justin/mcp-tool-schema-patcher.git")
      }
    }
  }
}

tasks.named("check") { dependsOn(subprojects.map { it.tasks.named("check") }) }
tasks.named("assemble") { dependsOn(subprojects.map { it.tasks.named("assemble") }) }
