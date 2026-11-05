package com.example.viewtxt

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE = "messages.ViewTxtBundle"

/**
 * 资源包访问入口，文案在 `src/main/resources/messages/ViewTxtBundle.properties` 中维护。
 */
internal object ViewTxtBundle {
    private val instance = DynamicBundle(ViewTxtBundle::class.java, BUNDLE)

    @JvmStatic
    fun message(
        @PropertyKey(resourceBundle = BUNDLE)
        key: String,
        vararg params: Any?,
    ): @Nls String = instance.getMessage(key, *params)

    @JvmStatic
    fun lazyMessage(
        @PropertyKey(resourceBundle = BUNDLE)
        key: String,
        vararg params: Any?,
    ): Supplier<@Nls String> = instance.getLazyMessage(key, *params)
}
