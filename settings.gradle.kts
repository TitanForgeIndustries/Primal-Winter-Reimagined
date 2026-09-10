pluginManagement {
    repositories {
        fun exclusiveMaven(url: String, filter: Action<InclusiveRepositoryContentDescriptor>) =
            exclusiveContent {
                forRepository { maven(url) }
                filter(filter)
            }

        exclusiveMaven("https://maven.minecraftforge.net") {
            includeGroupByRegex("net\\.minecraftforge.*")
        }
        exclusiveMaven("https://maven.parchmentmc.org") {
            includeGroupByRegex("org\\.parchmentmc.*")
        }
        exclusiveMaven("https://repo.spongepowered.org/repository/maven-public/") {
            includeGroupByRegex("org\\.spongepowered.*")
        }
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.spongepowered.mixin") {
                useModule("org.spongepowered:mixingradle:${requested.version}")
            }
        }
    }
}

rootProject.name = "PrimalWinter-1.20.1"
// This distribution targets Forge.  The shared sources are added to the Forge
// source set directly so Gradle does not try to run the obsolete vanilla
// compiler pipeline from the archived Common project.
include("Forge")
