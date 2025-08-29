# Avro → Java code generation with Gradle

This document explains how to generate Java `SpecificRecord` classes from Avro schemas using the official Apache Avro Tools approach wired into Gradle (the method this repository uses).

## Avro schema locations

- Place your `.avsc` files under `src/main/avro/` in each module (e.g., `app-producer/src/main/avro/`, `app-consumer/src/main/avro/`).

## Apache Avro Tools (project setup)

This repository already uses Apache Avro Tools with a custom `JavaExec` task. Key points:

- Configuration creates an `avroTools` dependency on `org.apache.avro:avro-tools`.
- A `generateAvroJava` task invokes the Avro compiler (`org.apache.avro.tool.Main`).
- Generated sources go to `build/generated-src/avro` and are added to the `main` source set.
- `compileJava` depends on `generateAvroJava` so codegen runs automatically on build.

Example from `build.gradle.kts` (module-level):

```kotlin
repositories {
    mavenCentral()
}

configurations {
    create("avroTools")
}

dependencies {
    implementation("org.apache.avro:avro:1.12.0")
    "avroTools"("org.apache.avro:avro-tools:1.12.0")
}

val avroSrcDir = layout.projectDirectory.dir("src/main/avro")
val avroOutDir = layout.buildDirectory.dir("generated-src/avro")

// Task to generate Java from Avro schemas
tasks.register<JavaExec>("generateAvroJava") {
    group = "build"
    description = "Generate Avro SpecificRecord classes from src/main/avro"
    classpath = configurations.getByName("avroTools")
    mainClass.set("org.apache.avro.tool.Main")
    args("compile", "schema", avroSrcDir.asFile.absolutePath, avroOutDir.get().asFile.absolutePath)
    inputs.dir(avroSrcDir.asFile)
    outputs.dir(avroOutDir)
}

sourceSets {
    named("main") {
        java.srcDir(avroOutDir)
    }
}

tasks.named("compileJava") {
    dependsOn("generateAvroJava")
}
```

Usage:

```bash
# Generate in both producer and consumer modules
./gradlew :app-producer:generateAvroJava :app-consumer:generateAvroJava

# Or just compile (triggers generation via dependsOn)
./gradlew :app-producer:compileJava :app-consumer:compileJava
```

Docs:
- Apache Avro Tools: https://avro.apache.org/docs/current/getting-started-java/
- Avro compiler CLI usage: https://avro.apache.org/docs/current/learn/idl/

## Specific vs Generic records

- SpecificRecord (this setup): generate Java classes; strong typing in producer and consumer.
- GenericRecord: no codegen; works with dynamic schemas but less type safety.

To consume SpecificRecord, set:

```properties
specific.avro.reader=true
```

## Tips

- Keep Avro namespaces aligned with your Java package conventions; these become the generated class packages.
- Version your schemas carefully if compatibility mode is enforced in the registry.
- Commit `.avsc` files, not generated sources. Generated code resides under `build/`.
