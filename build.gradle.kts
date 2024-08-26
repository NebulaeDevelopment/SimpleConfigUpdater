plugins {
    id("java")
    id("maven-publish")
}

version = project.properties["version"].toString()
group = project.properties["maven_group"].toString()

repositories {
    maven("https://libraries.minecraft.net") {
        name = "mojang-maven"

        content {
            includeGroup("com.mojang")
        }
    }

    mavenCentral()
}

dependencies {
    implementation("com.mojang:datafixerupper:${project.properties["datafixerupper_version"]}")
    implementation("org.jetbrains:annotations:${project.properties["jetbrains_annotations_version"]}")
}

tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:-unused")
    }

    jar {
        archiveBaseName = project.properties["archives_base_name"].toString()
    }
}