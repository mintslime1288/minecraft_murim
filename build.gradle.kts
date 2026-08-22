plugins {
    java
}

group = "com.murimrpg"
version = "0.1.0"

val paperApiVersion = "26.2.build.112-stable"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    implementation("org.xerial:sqlite-jdbc:3.53.2.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    testImplementation(platform("org.junit:junit-bom:5.14.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version,
        "paperApiVersion" to paperApiVersion
    )
    inputs.properties(properties)
    filesMatching("plugin.yml") {
        expand(properties)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.startsWith("sqlite-jdbc") }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}
