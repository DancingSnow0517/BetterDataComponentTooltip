package cn.dancingsnow.bdct

import cn.dancingsnow.bdct.util.getValue
import cn.dancingsnow.bdct.util.setValue
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.common.ModConfigSpec

object Config {
    private val BUILDER = ModConfigSpec.Builder()

    var pageSize by BUILDER
        .comment("Tooltip Count page showed")
        .defineInRange("page_size", 5, 1, Int.MAX_VALUE)
    var componentBlacklist by BUILDER
        .comment("Witch data component will not show in tooltip")
        .defineList("component_blacklist", listOf("minecraft:rarity"), { "minecraft:rarity" }) {
            return@defineList it is String && runCatching {
                BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(it)) ?: return@defineList false
            }.isSuccess
        }
    var showOriginalText by BUILDER
        .comment("Show Original Text json")
        .define("show_original_text", false)

    val SPEC: ModConfigSpec = BUILDER.build()
}
