package com.epam.drill.plugins.test2code.common.api

import com.epam.drill.plugin.api.*
import kotlinx.serialization.modules.*

val commonSerDe = SerDe(
    actionSerializer = Action.serializer(),
    ctx = SerializersModule {
        polymorphic<Action> {
            subclass(StartSession.serializer())
            subclass(StopSession.serializer())
            subclass(CancelSession.serializer())
        }
        polymorphic<CoverMessage> {
            subclass(InitInfo.serializer())
            subclass(InitDataPart.serializer())
            subclass(Initialized.serializer())

            subclass(SessionStarted.serializer())
            subclass(SessionCancelled.serializer())
            subclass(CoverDataPart.serializer())
            subclass(SessionFinished.serializer())
        }
    }
)
