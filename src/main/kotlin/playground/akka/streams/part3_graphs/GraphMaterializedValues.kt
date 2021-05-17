package playground.akka.streams.part3_graphs

import akka.actor.ActorSystem
import akka.stream.FlowShape
import akka.stream.SinkShape
import akka.stream.javadsl.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

fun main() {
    val system = ActorSystem.create("GraphMaterializedValues")

    val wordSource = Source.fromIterator { listOf("Akka", "is", "awesome", "rock", "the", "jvm").iterator() }
    val printer = Sink.foreach<String>(::println)
    val counter = Sink.fold<Int, String>(0) { count: Int, _ -> count + 1 }

    /*
       A Composite component (sink)
       - prints out all strings which are lowercase
       - COUNTS the strings that are short ( < 5 characters )
     */

    // step 1
    val complexWordSink = Sink.fromGraph(
        GraphDSL.create(
            printer,
            counter,
            { pMatValue, cMatValue -> cMatValue }) { builder, printerShape, counterShape ->
            // step 2 - components
            val broadcast = builder.add(Broadcast.create<String>(2))
            val lowercaseFilter = builder.add(Flow.of(String::class.java).filter { it == it.toLowerCase() })
            val shortStringFilter = builder.add(Flow.of(String::class.java).filter { it.length < 5 })

            // step 3 - connections
            builder.from(broadcast).via<String>(lowercaseFilter).to(printerShape)
            builder.from(broadcast).via<String>(shortStringFilter).to(counterShape)

            // step 4 - the shape
            SinkShape(broadcast.`in`())
        }
    )

    val shortStringsCountFuture = wordSource.toMat(complexWordSink, Keep.right()).run(system)
    println("The total number of short string is: ${(shortStringsCountFuture as CompletableFuture).get()}")

    /**
     * Exercise
     * Hint: use a broadcast and a Sink.fold
     */
    fun <A, B> enhanceFlow(flow: Flow<A, B, Any>): Flow<A, B, CompletionStage<Int>> {
        val counterSink = Sink.fold(0) {count: Int, _: B -> count + 1}
        return Flow.fromGraph(
            GraphDSL.create(counterSink) { builder, counterShape ->

                val broadcast = builder.add(Broadcast.create<B>(2))
                val originalFlowShape = builder.add(flow)

                builder.from(originalFlowShape) to broadcast
                builder.from(broadcast).to(counterShape)

                FlowShape(originalFlowShape.`in`(), broadcast.out(1))
            }
        )
    }

    val simpleSource = Source.range(1, 42)
    val simpleFlow = Flow.of(Int::class.java).map { it }
    val simpleSink = Sink.ignore<Int>()
    //enhanceFlow<Int, Int>(simpleFlow)
    // val enhancedFlowCountFuture = simpleSource.viaMat(enhanceFlow(simpleFlow), Keep.right())


}
