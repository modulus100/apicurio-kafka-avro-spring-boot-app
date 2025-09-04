plugins {
    id("buildlogic.java-common-conventions")
    id("org.springframework.boot") version "3.5.4"
    id("io.spring.dependency-management") version "1.1.6"
}

repositories {
    maven("https://packages.confluent.io/maven/")
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")

    // Confluent Schema Registry client + Avro
    implementation("io.confluent:kafka-schema-registry-client:8.0.0")
    implementation("org.apache.avro:avro:1.12.0")
    implementation("io.confluent:kafka-avro-serializer:8.0.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}

springBoot {
    mainClass.set("org.example.migrator.MigratorApplication")
}

