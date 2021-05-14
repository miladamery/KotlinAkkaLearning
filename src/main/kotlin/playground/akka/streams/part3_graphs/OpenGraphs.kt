package playground.akka.streams.part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.FlowShape
import akka.stream.SinkShape
import akka.stream.SourceShape
import akka.stream.javadsl.*

class OpenGraphs

fun main() {
    val system = ActorSystem.create("openGraphs")

    /*
        A composite source that concatenates 2 sources
        - emits ALL elements from source 1
        - then ALL the elements from the second
     */

    val firstSource = Source.range(1, 10)
    val secondSource = Source.range(42, 1000)

    // step 1
    val sourceGraph = Source.fromGraph(
        GraphDSL.create { builder ->

            // step 2 - declaring components
            val concat = builder.add(Concat.create<Int>(2))
            val fSource = builder.add(firstSource)
            val sSource = builder.add(secondSource)

            // step 3 - tying them together
            builder.from(fSource).viaFanIn<Int>(concat)
            builder.from(sSource).viaFanIn<Int>(concat)


            SourceShape(concat.out())
        }
    )

    //sourceGraph.to(Sink.foreach(::println)).run(system)

    /*
        Complex Sink
     */
    val sink1 = Sink.foreach<Int> { println("Meaningful thing 1: $it") }
    val sink2 = Sink.foreach<Int> { println("Meaningful thing 2: $it") }

    val sinkGraph = Sink.fromGraph(
        GraphDSL.create { builder ->

            // add a broadcast
            val broadcast = builder.add(Broadcast.create<Int>(2))

            builder.from(broadcast).to(builder.add(sink1))
            builder.from(broadcast).to(builder.add(sink2))
            SinkShape(broadcast.`in`())
        }
    )

    //firstSource.to(sinkGraph).run(system)

    /**
     * Challenge - complex flow?
     * Write your own flow that's composed of two other flows
     * - one that adds to a number
     * - one that does number * 10
     */

    val incrementer = Flow.of(Int::class.java).map { it + 1 }
    val multiplier = Flow.of(Int::class.java).map { it * 10 }
    // step 1
    val flowGraph = Flow.fromGraph(
        GraphDSL.create { builder ->
            // everything operate on shapes

            // step 2 - define auxiliary SHAPES
            val incShape = builder.add(incrementer)
            val mulShape = builder.add(multiplier)

            // step 3 - connect the SHAPES
            builder
                .from(incShape)
                .via<Int>(mulShape)
            FlowShape(incShape.`in`(), mulShape.out()) // SHAPE
        }
    )

    // firstSource.via(flowGraph).to(Sink.foreach(::println)).run(system)

    /**
     * Exercise: Flow from a sink and a source?
     */
    fun <A, B> fromSinkAndSource(sink: Sink<A, Any>, source: Source<B, Any>): Flow<A, B, NotUsed> {
        return Flow.fromGraph(
            GraphDSL.create { builder ->
                // declare the SHAPES
                val sourceShape = builder.add(source)
                val sinkShape = builder.add(sink)

                FlowShape(sinkShape.`in`(), sourceShape.out())
            }
        )
    }

}