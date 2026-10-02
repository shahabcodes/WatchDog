import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Wires in the Firebase BOM + the Firebase SDKs used across the apps.
 * The `google-services` plugin is applied by the application modules themselves,
 * since it requires a `google-services.json` to be present.
 */
class AndroidFirebaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies {
                val bom = libs.findLibrary("firebase-bom").get()
                add("implementation", platform(bom))

                add("implementation", libs.findLibrary("firebase-auth").get())
                add("implementation", libs.findLibrary("firebase-firestore").get())
                add("implementation", libs.findLibrary("firebase-storage").get())
                add("implementation", libs.findLibrary("firebase-messaging").get())
                add("implementation", libs.findLibrary("firebase-functions").get())
                add("implementation", libs.findLibrary("firebase-analytics").get())

                add("implementation", libs.findLibrary("kotlinx-coroutines-play-services").get())
            }
        }
    }
}
