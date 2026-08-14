package cn.dancingsnow.bdct

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.TypedDataComponent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.item.component.ItemLore
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RenderTooltipEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.client.settings.KeyConflictContext
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
import org.apache.commons.lang3.StringUtils
import thedarkcolour.kotlinforforge.neoforge.forge.LOADING_CONTEXT
import java.util.function.Consumer
import kotlin.math.ceil
import kotlin.math.max

@Mod(value = BetterDataComponentTooltip.MODID, dist = [Dist.CLIENT])
@EventBusSubscriber
object BetterDataComponentTooltip {
    const val MODID: String = "bdct"
    val CATEGORY = KeyMapping.Category(Identifier.fromNamespaceAndPath(MODID, "bdct"))
    val SHOW_TAGS: KeyMapping = KeyMapping(
        "key.bdct.show_tags",
        KeyConflictContext.GUI,
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_LCONTROL,
        CATEGORY
    )
    val SHOW_COMPONENTS: KeyMapping = KeyMapping(
        "key.bdct.show_components",
        KeyConflictContext.GUI,
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_LALT,
        CATEGORY
    )
    val HOLD_TO_SHOW_TAGS: Component = Component.translatable(
        "tooltip.bdct.show_tags",
        Component.empty().append("[").append(Component.keybind("key.bdct.show_tags")).append("]")
            .withStyle(ChatFormatting.YELLOW)
    ).withStyle(ChatFormatting.GRAY)

    val HOLD_TO_SHOW_COMPONENTS: Component = Component.translatable(
        "tooltip.bdct.show_components",
        Component.empty().append("[").append(Component.keybind("key.bdct.show_components")).append("]")
            .withStyle(ChatFormatting.YELLOW)
    ).withStyle(ChatFormatting.GRAY)

    init {
        var modContainer = LOADING_CONTEXT.activeContainer
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC)
        modContainer.registerExtensionPoint(IConfigScreenFactory::class.java, IConfigScreenFactory { mod, parent ->
            ConfigurationScreen(mod, parent)
        })
    }

    @SubscribeEvent
    fun onShowTooltip(event: ItemTooltipEvent) {
        val player = Minecraft.getInstance().player ?: return
        val toolTip = event.toolTip
        val itemStack = event.itemStack
        val key = BuiltInRegistries.ITEM.getKey(itemStack.getItem())
        val holder =
            player.connection.registryAccess().holderOrThrow(ResourceKey.create(Registries.ITEM, key))
        if (isKeyDown(SHOW_TAGS)) {
            holder.tags().map { obj: TagKey<Item> -> obj.location() }.sorted()
                .forEach { rl ->
                    toolTip.add(Component.literal("#$rl").withStyle(ChatFormatting.GRAY))
                }
        } else {
            toolTip.add(HOLD_TO_SHOW_TAGS)
        }
        if (isKeyDown(SHOW_COMPONENTS)) {
            val code = System.identityHashCode(itemStack)
            if (code != ComponentTooltipContext.hash) {
                ComponentTooltipContext.hash = code
                ComponentTooltipContext.page = 1
                ComponentTooltipContext.cachedComponents.clear()
                itemStack.components.forEach { component: TypedDataComponent<*>? ->
                    val componentType: DataComponentType<*> = component!!.type()
                    val typeKey = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(componentType)
                    if (Config.componentBlacklist.contains(typeKey.toString())) {
                        return@forEach
                    }
                    val value: Any = component.value()
                    val codec = componentType.codec() as Codec<Any?>?
                    val result = codec!!.encodeStart(NbtOps.INSTANCE, value)
                    result.ifSuccess { tag ->
                        when (value) {
                            is Component -> {
                                val c = Component.empty()
                                    .append(Component.literal("$typeKey: ").withColor(0xFF99FF))
                                    .append(value)
                                if (Config.showOriginalText) {
                                    c.append(" / ")
                                    c.append(
                                        Component.literal(tag.toJsonString())
                                            .withStyle(ChatFormatting.GRAY)
                                    )
                                }
                                ComponentTooltipContext.cachedComponents.add(c)
                            }

                            is ItemLore -> {
                                if (value.lines.isEmpty()) {
                                    ComponentTooltipContext.cachedComponents.add(
                                        Component.empty()
                                            .append(Component.literal("$typeKey: ").withColor(0xFF99FF))
                                            .append("[]")
                                    )
                                } else {
                                    ComponentTooltipContext.cachedComponents.add(
                                        Component.literal("$typeKey: ").withColor(0xFF99FF)
                                    )
                                }
                                for ((index, line) in value.lines.withIndex()) {
                                    val c = Component.empty()
                                        .append(Component.literal("  - ").withColor(0xFF99FF))
                                        .append(line)
                                    if (Config.showOriginalText && tag is ListTag) {
                                        c.append(" / ")
                                        c.append(Component.literal(tag.get(index).toJsonString()).withStyle(ChatFormatting.GRAY))
                                    }
                                    ComponentTooltipContext.cachedComponents.add(c)
                                }
                            }

                            is ItemContainerContents -> {
                                ComponentTooltipContext.cachedComponents.add(
                                    Component.literal("$typeKey: ").withColor(0xFF99FF)
                                )
                                for (stack in value.nonEmptyItems()) {
                                    addComponentsTooltip(2, stack.create())
                                }
                            }

                            else -> ComponentTooltipContext.cachedComponents.add(
                                Component.empty()
                                    .append(Component.literal("$typeKey: ").withColor(0xFF99FF))
                                    .append(NbtUtils.toPrettyComponent(tag))
                            )
                        }
                    }
                }
            }
            val chunked = ComponentTooltipContext.cachedComponents.chunked(Config.pageSize)
            val page = Math.clamp(ComponentTooltipContext.page.toLong(), 1, chunked.size)
            toolTip.addAll(chunked[page - 1])
            toolTip.add(Component.literal("Page $page/${chunked.size}"))
            ComponentTooltipContext.size = chunked.size
        } else {
            toolTip.add(HOLD_TO_SHOW_COMPONENTS)
        }
    }

    @SubscribeEvent
    fun onRenderTooltip(event: RenderTooltipEvent.Pre) {
        if (isKeyDown(SHOW_COMPONENTS)) {
            var width = 0
            var height = 0
            event.components.forEach {
                width = max(width, it.getWidth(event.font))
                height += it.getHeight(event.font)
            }
            event.x = (event.screenWidth - width) / 2
            event.y = (event.screenHeight - height) / 2
        }
    }

    @SubscribeEvent
    fun registerKeyMapping(event: RegisterKeyMappingsEvent) {
        event.register(SHOW_TAGS)
        event.register(SHOW_COMPONENTS)
    }

    @SubscribeEvent
    fun onMouseScroll(event: ScreenEvent.MouseScrolled.Pre) {
        if (isKeyDown(SHOW_COMPONENTS)) {
            if (event.scrollDeltaY < 0) {
                ComponentTooltipContext.page++
            }
            if (event.scrollDeltaY > 0) {
                ComponentTooltipContext.page--
            }
            ComponentTooltipContext.page =
                Math.clamp(ComponentTooltipContext.page.toLong(), 1, ComponentTooltipContext.size)
            event.isCanceled = true
        }
    }

    private fun isKeyDown(keyMapping: KeyMapping): Boolean {
        return InputConstants.isKeyDown(
            Minecraft.getInstance().window,
            keyMapping.key.value
        )
    }

    /**
     * 将数据组件编码后的 NBT 标签转为 JSON 字符串。
     * 文本组件在新版编码器下会折叠为字符串或展开为结构体，
     * 统一经 JsonOps 转换以展示其 JSON 形态。
     */
    private fun Tag.toJsonString(): String = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, this).toString()

    private fun addComponentsTooltip(indent: Int, itemStack: ItemStack) {
        val mc = Minecraft.getInstance()
        val font = mc.font

        val indentString = StringUtils.repeat(' ', indent) + " - "
        val indentWidth = font.width(indentString)
        val spaceWidth = font.width(" ")
        val spaceCount = ceil((indentWidth.toFloat() / spaceWidth).toDouble()).toInt()

        val key = BuiltInRegistries.ITEM.getKey(itemStack.item)

        ComponentTooltipContext.cachedComponents.add(
            Component.empty()
                .append(Component.literal(indentString + "id: ").withColor(0xFF99FF))
                .append(key.toString())
        )

        itemStack.components.forEach(Consumer { component: TypedDataComponent<*>? ->
            val componentType: DataComponentType<*> = component!!.type()
            val typeKey = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(componentType)
            val value: Any = component.value()
            val codec = componentType.codec() as Codec<Any?>?
            val result = codec!!.encodeStart(NbtOps.INSTANCE, value)
            result.ifSuccess { tag: Tag ->
                when (value) {
                    is Component -> {
                        val c = Component.empty()
                            .append(StringUtils.repeat(' ', spaceCount))
                            .append(Component.literal("$typeKey: ").withColor(0xFF99FF))
                            .append(value)
                        if (Config.showOriginalText) {
                            c.append(" / ")
                            c.append(Component.literal(tag.toJsonString()).withStyle(ChatFormatting.GRAY))
                        }
                        ComponentTooltipContext.cachedComponents.add(c)
                    }

                    is ItemLore -> {
                        ComponentTooltipContext.cachedComponents.add(
                            Component.empty()
                                .append(StringUtils.repeat(' ', spaceCount))
                                .append(Component.literal("$typeKey: ").withColor(0xFF99FF))
                                .append(if (value.lines.isEmpty()) "[]" else "")
                        )
                        for ((index, line) in value.lines.withIndex()) {
                            val c = Component.empty()
                                .append(StringUtils.repeat(' ', spaceCount))
                                .append(Component.literal("  - ").withColor(0xFF99FF))
                                .append(line)
                            if (Config.showOriginalText && tag is ListTag) {
                                c.append(" / ")
                                c.append(Component.literal(tag.get(index).toJsonString()).withStyle(ChatFormatting.GRAY))
                            }
                            ComponentTooltipContext.cachedComponents.add(c)
                        }
                    }

                    is ItemContainerContents -> {
                        ComponentTooltipContext.cachedComponents.add(
                            Component.empty()
                                .append(StringUtils.repeat(' ', spaceCount))
                                .append(Component.literal("$typeKey: ").withColor(0xFF99FF))
                        )
                        for (stack in value.nonEmptyItems()) {
                            addComponentsTooltip(indent + 4, stack.create())
                        }
                    }

                    else -> ComponentTooltipContext.cachedComponents.add(
                        Component.empty()
                            .append(StringUtils.repeat(' ', spaceCount))
                            .append(Component.literal("$typeKey: ").withColor(0xFF99FF))
                            .append(NbtUtils.toPrettyComponent(tag))
                    )
                }
            }
        })
    }
}
