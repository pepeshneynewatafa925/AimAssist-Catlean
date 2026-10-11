package su.catlean.example

import su.catlean.api.addon.CatLeanAddon
import su.catlean.api.addon.CatLeanApi
import su.catlean.example.aimassist.AimAssistModule

object ExampleAddon : CatLeanAddon {
    lateinit var api: CatLeanApi

    override fun onCatLeanAddon(api: CatLeanApi) {
        this.api = api


        api.registry.registerModule(AimAssistModule)


        api.logger("example").info("Example addon loaded")
    }
}
