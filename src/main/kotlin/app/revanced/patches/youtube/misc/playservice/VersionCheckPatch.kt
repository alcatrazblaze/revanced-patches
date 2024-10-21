package app.revanced.patches.youtube.misc.playservice

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.findElementByAttributeValueOrThrow
import kotlin.properties.Delegates

@Patch(description = "Uses the Play Store service version to find the major/minor version of the YouTube target app.")
internal object VersionCheckPatch : ResourcePatch() {

    var is_19_03_or_greater by Delegates.notNull<Boolean>()
    var is_19_04_or_greater by Delegates.notNull<Boolean>()
    var is_19_16_or_greater by Delegates.notNull<Boolean>()
    var is_19_17_or_greater by Delegates.notNull<Boolean>()
    var is_19_18_or_greater by Delegates.notNull<Boolean>()
    var is_19_23_or_greater by Delegates.notNull<Boolean>()
    var is_19_25_or_greater by Delegates.notNull<Boolean>()
    var is_19_26_or_greater by Delegates.notNull<Boolean>()
    var is_19_29_or_greater by Delegates.notNull<Boolean>()
    var is_19_32_or_greater by Delegates.notNull<Boolean>()
    var is_19_33_or_greater by Delegates.notNull<Boolean>()
    var is_19_34_or_greater by Delegates.notNull<Boolean>()
    var is_19_36_or_greater by Delegates.notNull<Boolean>()
    var is_19_41_or_greater by Delegates.notNull<Boolean>()

    override fun execute(context: ResourceContext) {

        // The app version is missing from the decompiled manifest,
        // so instead use the Google Play services version and compare against specific releases.
        val playStoreServicesVersion = context.document["res/values/integers.xml"].use { document ->
            document.documentElement.childNodes.findElementByAttributeValueOrThrow(
                "name",
                "google_play_services_version"
            ).textContent.toInt()
        }

        // All bug fix releases always seem to use the same play store version as the minor version.
        is_19_03_or_greater = 240402000 <= playStoreServicesVersion
        is_19_04_or_greater = 240502000 <= playStoreServicesVersion
        is_19_16_or_greater = 241702000 <= playStoreServicesVersion
        is_19_17_or_greater = 241802000 <= playStoreServicesVersion
        is_19_18_or_greater = 241902000 <= playStoreServicesVersion
        is_19_23_or_greater = 242402000 <= playStoreServicesVersion
        is_19_25_or_greater = 242599000 <= playStoreServicesVersion
        is_19_26_or_greater = 242705000 <= playStoreServicesVersion
        is_19_29_or_greater = 243005000 <= playStoreServicesVersion
        is_19_32_or_greater = 243199000 <= playStoreServicesVersion
        is_19_33_or_greater = 243405000 <= playStoreServicesVersion
        is_19_34_or_greater = 243499000 <= playStoreServicesVersion
        is_19_36_or_greater = 243705000 <= playStoreServicesVersion
        is_19_41_or_greater = 244305000 <= playStoreServicesVersion
    }
}
