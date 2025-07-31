package cn.dancingsnow.bdct

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.neoforge.common.ModConfigSpec

@EventBusSubscriber(modid = BetterDataComponentTooltip.MODID, value = [Dist.CLIENT])
object Config {
    private val BUILDER = ModConfigSpec.Builder()
    val PAGE_SIZE: ModConfigSpec.IntValue = BUILDER
        .comment("Tooltip Count page showed")
        .defineInRange("page_size", 5, 1, Int.MAX_VALUE)
    val COMPONENT_BLACKLIST: ModConfigSpec.ConfigValue<List<String>> = BUILDER
        .comment("Witch data component will not show in tooltip")
        .defineList("component_blacklist", listOf("minecraft:rarity"), { "minecraft:rarity" }) {
            if (it is String) {
                return@defineList runCatching {
                    BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ResourceLocation.parse(it))
                        ?: return@defineList false
                }.isSuccess
            }
            return@defineList false
        }
    val SHOW_ORIGINAL_TEXT: ModConfigSpec.BooleanValue = BUILDER
        .comment("Show Original Text json")
        .define("show_original_text", false)
    val SPEC: ModConfigSpec = BUILDER.build()

    var pageSize: Int = 0
    val componentBlacklist: MutableList<String> = mutableListOf()
    var showOriginalText: Boolean = false

    @SubscribeEvent
    fun onLoad(event: ModConfigEvent) {
        pageSize = PAGE_SIZE.asInt
        componentBlacklist.clear()
        componentBlacklist.addAll(COMPONENT_BLACKLIST.get())
        showOriginalText = SHOW_ORIGINAL_TEXT.asBoolean
    }
}
