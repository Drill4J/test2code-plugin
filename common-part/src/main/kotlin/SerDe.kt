package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.*
import kotlinx.serialization.modules.*

val commonSerDe = SerDe(
    actionSerializer = Action.serializer(),
    ctx = SerializersModule {
        polymorphic<Action> {
            addSubclass(StartSession.serializer())
            addSubclass(StopSession.serializer())
            addSubclass(CancelSession.serializer())
        }
        polymorphic<CoverMessage> {
            addSubclass(InitInfo.serializer())
            addSubclass(Initialized.serializer())

            addSubclass(SessionStarted.serializer())
            addSubclass(SessionCancelled.serializer())
            addSubclass(CoverDataPart.serializer())
            addSubclass(SessionFinished.serializer())
        }
    }
)
