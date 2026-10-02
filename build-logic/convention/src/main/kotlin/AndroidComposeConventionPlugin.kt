import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Enables Jetpack Compose and wires in the Compose BOM + default Compose dependencies.
 * Apply on top of `fms.android.application` or `fms.android.library`.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            // buildFeatures.compose = true on whichever Android extension this module has.
            extensions.findByType(ApplicationExtension::class.java)?.apply {
                buildFeatures.compose = true
            }
            extensions.findByType(LibraryExtension::class.java)?.apply {
                buildFeatures.compose = true
            }

            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))

                libs.findBundle("compose").get().get().forEach { dep ->
                    add("implementation", dep)
                }

                add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))

                add("androidTestImplementation", libs.library("androidx-compose-ui-test-junit4"))
                add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
            }
        }
    }
}

private fun VersionCatalog.library(alias: String) = findLibrary(alias).get()
