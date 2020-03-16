package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.*
import kotlinx.serialization.modules.*

val adminSerDe = SerDe(
    actionSerializer = commonSerDe.actionSerializer,
    ctx = commonSerDe.ctx + SerializersModule {
        polymorphic<Action> {
            addSubclass(SwitchActiveScope.serializer())
            addSubclass(RenameScope.serializer())
            addSubclass(ToggleScope.serializer())
            addSubclass(DropScope.serializer())
            addSubclass(StartNewSession.serializer())
        }
    }
)
