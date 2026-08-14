package cn.dancingsnow.bdct.util

import net.neoforged.neoforge.common.ModConfigSpec
import kotlin.reflect.KProperty

operator fun <T : Any> ModConfigSpec.ConfigValue<T>.getValue(thisRef: Any?, property: KProperty<*>): T {
    return this.get()
}

operator fun <T: Any> ModConfigSpec.ConfigValue<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.set(value)
    this.save()
}