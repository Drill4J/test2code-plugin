package com.epam.drill.plugins.test2code

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.*
import kotlin.coroutines.*

internal class Realtime<T>(handler: (Sequence<T>) -> Unit) {

    private val stream = TimeSpanEventBus<T>(delayMillis = 1500L)
    private val job = RealtimeWorker.launch {
        stream.collect { seq -> handler(seq) }
    }

    fun offer(value: T) {
        stream.offer(value)
    }

    fun close() {
        job.cancel()
        stream.close()

    }
}

internal object RealtimeWorker : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        Executors.newFixedThreadPool(4).asCoroutineDispatcher() + SupervisorJob()

    operator fun invoke(block: suspend () -> Unit) = launch { block() }
}

internal class TimeSpanEventBus<T>(
    delayMillis: Long,
    private val coroutineScope: CoroutineScope = GlobalScope,
    private val mainChannel: Channel<T> = Channel(),
    private val ticker: ReceiveChannel<Unit> = ticker(delayMillis, 150L)
) : Channel<T> by mainChannel, Flow<Sequence<T>> {

    override fun close(cause: Throwable?): Boolean = run {
        ticker.cancel()
        mainChannel.close(cause)
    }

    override suspend fun collect(collector: FlowCollector<Sequence<T>>) {
        mainChannel.consumeAsFlow().timeSpanChunked(ticker).collect(collector)
    }

    private fun <T> Flow<T>.timeSpanChunked(
        ticker: ReceiveChannel<Unit>
    ): Flow<Sequence<T>> = windowed(ticker) { it }

    private fun <T, R> Flow<T>.windowed(
        ticker: ReceiveChannel<Unit>,
        transform: suspend (Sequence<T>) -> R
    ): Flow<R> = channelFlow {
        val buffer = Channel<T>()
        coroutineScope.launch {
            ticker.consumeEach {
                if (!buffer.isEmpty) {
                    send(transform(buffer.takeAvailable()))
                }
            }
        }
        collect { value ->
            coroutineScope.launch {
                buffer.send(value)
            }
        }
    }
}

private suspend fun <T> ReceiveChannel<T>.takeAvailable() = sequence {
    while (poll()?.also { yield(it) } != null) Unit
}
