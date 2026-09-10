plugins {
    java
    idea
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    id("org.spongepowered.mixin") version "0.7-SNAPSHOT"
    id("org.parchmentmc.librarian.forgegradle") version "1.+"
}


// From gradle.properties
val modId: String by extra
val modGroup: String by extra

val minecraftVersion: String by extra
val forgeVersion: String by extra
val parchmentVersion: String by extra
val parchmentMinecraftVersion: String by extra

val shadowLibrary: Configuration by configurations.creating

configurations {
    implementation.get().extendsFrom(shadowLibrary)
}

base {
    archivesName.set("${modId}-forge-${minecraftVersion}")
}

repositories {
    maven("https://maven.parchmentmc.org")
}

dependencies {
    "minecraft"(group = "net.minecraftforge", name = "forge", version = "${minecraftVersion}-${forgeVersion}")

    if (System.getProperty("idea.sync.active") != "true") {
        annotationProcessor(group = "org.spongepowered", name = "mixin", version = "0.8.5", classifier = "processor")
    }
}

minecraft {
    mappings("parchment", "${parchmentMinecraftVersion}-${parchmentVersion}-${minecraftVersion}")

    runs {
        all {
            property("forge.logging.console.level", "debug")
            ideaModule("${project.name}.test")
            workingDirectory("run")

            mods.create(modId) {
                source(sourceSets.main.get())
            }
        }

        register("client") {}
        register("server") {
            arg("--nogui")
        }
    }
}

// ForgeGradle does not create a configured run directory before JavaExec starts.  Creating it
// here keeps runClient/runServer usable from a fresh checkout and avoids a platform-specific
// CreateProcess failure before Minecraft has a chance to report a real startup error.
tasks.withType<JavaExec>().configureEach {
    doFirst {
        workingDir.mkdirs()
    }
}

mixin {
    add(sourceSets.main.get(), "${modId}.refmap.json")

    config("${modId}.mixins.json")
    config("${modId}.common.mixins.json")
    config("${modId}.particlerain.mixins.json")
}

// Creates the 'reobfShadowJar' task
reobf {
    register("shadowJar")
}


// Workaround for a bug in Forge / Mixin gradle where the refmap won't be added to the jar unless Forge java compile is done
// From https://github.com/gamma-delta/HexMod/blob/main/Forge/build.gradle#L161
tasks.register("invalidateJavaForRefmap") {
    doFirst {
        tasks.compileJava {
            if (!didWork) {
                outputs.upToDateWhen { false }
            }
        }
    }
}

tasks.withType<JavaCompile> {
    shouldRunAfter(tasks.named("invalidateJavaForRefmap"))
}

sourceSets {
    main {
        java.srcDir("../Common/src/main/java")
        resources.srcDir("../Common/src/main/resources")
    }
}

tasks {
    jar {
        archiveClassifier.set("slim")
        finalizedBy("reobfJar")
    }

    shadowJar {
        archiveClassifier.set("")
        configurations = listOf(shadowLibrary)
        dependencies {
            exclude(dependency(KotlinClosure1<ResolvedDependency, Boolean>({
                moduleGroup != modGroup
            })))
        }
        finalizedBy("reobfShadowJar")
    }

    assemble {
        dependsOn(shadowJar)
    }

}

idea {
    module {
        for (fileName in listOf("run", "out", "logs")) {
            excludeDirs.add(file(fileName))
        }
    }
}
