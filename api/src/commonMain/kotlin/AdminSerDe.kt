package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.*
import kotlinx.serialization.modules.*

val adminSerDe = SerDe(
    actionSerializer = commonSerDe.actionSerializer,
    ctx = commonSerDe.ctx + SerializersModule {
        polymorphic<Action> {
            subclass(SwitchActiveScope.serializer())
            subclass(RenameScope.serializer())
            subclass(ToggleScope.serializer())
            subclass(DropScope.serializer())
            subclass(StartNewSession.serializer())
        }
    }
)
