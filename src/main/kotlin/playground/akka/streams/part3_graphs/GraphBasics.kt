package playground.akka.streams.part3_graphs

import akka.actor.ActorSystem
import akka.japi.Pair
import akka.stream.ClosedShape
import akka.stream.javadsl.*
import java.time.Duration

class GraphBasics {
}

fun main() {
    val system = ActorSystem.create("graphBasics")

    val input = Source.range(1, 1000)
    val incrementer = Flow.of(Int::class.java).map { it + 1 } // hard computation
    val multiplier = Flow.of(Int::class.java).map { it * 10 } // hard computation
    val f = Flow.create<Pair<Int, Int>>().map { it }
    val output = Sink.foreach<Pair<Int, Int>> {
        println(it)
    }

    // setting up the fundamentals for graph
    val graph = RunnableGraph.fromGraph(
        GraphDSL.create { builder -> // builder = MUTABLE data structure
            // step 2 - add necessary components of this graph
            val broadcast = builder.add(Broadcast.create<Int>(2)) // fan-out operator
            val zip = builder.add(Zip.create<Int, Int>()) // fan-in operator
            val builderSource = builder.add(input).out()
            val sink = builder.add(output)
            // step 3 - tying up components
            /*
            This works too!
            builder.from(builderSource).viaFanOut<Int>(broadcast)
            builder.from(broadcast.out(0)).via<Int>(builder.add(incrementer)).toInlet(zip.in0())
            builder.from(broadcast.out(1)).via<Int>(builder.add(multiplier)).toInlet(zip.in1())
            builder.from(zip.out()).to(sink)*/
            builder
                .from(builderSource)
                .viaFanOut<Int>(broadcast)
                .via<Int>(builder.add(incrementer))
                .toInlet(zip.in0())
            builder
                .from(broadcast)
                .via<Int>(builder.add(multiplier))
                .toInlet(zip.in1())
            builder.from(zip.out()).to(sink)

            // step 4 - return a closed shape
            ClosedShape.getInstance() // FREEZE the builder's shape
        }
    )
    // graph.run(system)

    /**
     * exercise 1: feed a source into 2 sinks at the same time (hint: use a broadcast)
     */

    val exe1graph = RunnableGraph.fromGraph(
        GraphDSL.create { builder ->
            val source = builder.add(Source.range(1, 1000))
            val sink1 = builder.add(Sink.foreach<Int> { println("sink 1: $it") })
            val sink2 = builder.add(Sink.foreach<Int> { println("sink 2: $it") })
            val broadcast = builder.add(Broadcast.create<Int>(2))

            builder
                .from(source)
                .viaFanOut<Int>(broadcast)
                .to(sink1)
            builder
                .from(broadcast)
                .to(sink2)

            ClosedShape.getInstance()
        }
    )
    //exe1graph.run(system)

    val exe2Graph = RunnableGraph.fromGraph(
        GraphDSL.create { builder ->
            val firstSource = builder.add(Source.range(1, 1000).throttle(5, Duration.ofSeconds(1)))
            val secondSource = builder.add(Source.range(1001, 2000).throttle(2, Duration.ofSeconds(1)))

            val merge = builder.add(Merge.create<Int>(2))
            val balance = builder.add(Balance.create<Int>(2))
            val firstSink = builder.add(Sink.foreach<Int> { println("sink 1: $it") })
            val secondSink = builder.add(Sink.foreach<Int> { println("sink 2: $it") })

            builder.from(firstSource).viaFanIn<Int>(merge).toFanOut<Int>(balance)
            builder.from(secondSource).viaFanIn<Int>(merge)
            builder.from(balance).to(firstSink)
            builder.from(balance).to(secondSink)
            ClosedShape.getInstance()
        }
    )
    exe2Graph.run(system)
}