package su.catlean.example

import net.minecraft.client.Minecraft

val player
    get() = Minecraft.getInstance().player!!

val world
    get() = Minecraft.getInstance().level!!

