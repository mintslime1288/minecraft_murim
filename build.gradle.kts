plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

group = "com.murimrpg"
version = "0.2.0"

base { archivesName = "MurimRPG-Fabric" }

repositories {
    mavenCentral()
    maven {
        name = "RedlanceMinecraft"
        url = uri("https://repo.redlance.org/public")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.158.0+26.2")
    implementation("com.zigythebird.playeranim:PlayerAnimationLibFabric:1.2.6+mc.26.2")
    runtimeOnly("com.zigythebird.bendable_cuboids:BendableCuboidsMerged:2.0.4+mc.26.2")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1") { exclude(group = "org.slf4j", module = "slf4j-api") }
    include("org.xerial:sqlite-jdbc:3.53.2.1")
    implementation("org.yaml:snakeyaml:2.5")
    include("org.yaml:snakeyaml:2.5")
    testImplementation(platform("org.junit:junit-bom:5.14.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}

tasks.test { useJUnitPlatform() }
