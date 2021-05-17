package playground.akka.streams.part3_graphs

import akka.actor.ActorSystem
import akka.japi.Pair
import akka.stream.ClosedShape
import akka.stream.FanInShape2
import akka.stream.OverflowStrategy
import akka.stream.UniformFanInShape
import akka.stream.javadsl.*
import java.math.BigInteger

class GraphCycles {
}

fun main() {
    val system = ActorSystem.create("GraphCycles")

    val accelerator = GraphDSL.create { builder ->

        val sourceShape = builder.add(Source.range(1, 100))
        val mergeShape = builder.add(Merge.create<Int>(2))
        val incrementerShape = builder.add(Flow.of(Int::class.java).map {
            println("Accelerating $it")
            it + 1
        })

        builder.from(sourceShape).viaFanIn<Int>(mergeShape)
        builder.from(mergeShape).via<Int>(incrementerShape)
        builder.from(incrementerShape).viaFanIn<Int>(mergeShape)

        ClosedShape.getInstance()
    }

    //RunnableGraph.fromGraph(accelerator).run(system)
    // graph cycle deadlock!

    /*
        Solution 1: MergePreferred
     */
    val actualAccelerator = GraphDSL.create { builder ->

        val sourceShape = builder.add(Source.range(1, 100))
        val mergeShape = builder.add(MergePreferred.create<Int>(1))
        val incrementerShape = builder.add(Flow.of(Int::class.java).map {
            println("Accelerating $it")
            it + 1
        })

        builder.from(sourceShape).viaFanIn<Int>(mergeShape)
        builder.from(mergeShape).via<Int>(incrementerShape)
        builder.from(incrementerShape).toInlet(mergeShape.preferred())

        ClosedShape.getInstance()
    }
    //RunnableGraph.fromGraph(actualAccelerator).run(system)

    /*
        Solution 2: buffers
     */
    val bufferedRepeater = GraphDSL.create { builder ->

        val sourceShape = builder.add(Source.range(1, 100))
        val mergeShape = builder.add(Merge.create<Int>(2))
        val repeaterShape = builder.add(Flow.of(Int::class.java).buffer(10, OverflowStrategy.dropHead()).map {
            println("Accelerating $it")
            Thread.sleep(100)
            it
        })

        builder.from(sourceShape).viaFanIn<Int>(mergeShape)
        builder.from(mergeShape).via<Int>(repeaterShape)
        builder.from(repeaterShape).viaFanIn<Int>(mergeShape)

        ClosedShape.getInstance()
    }
    //RunnableGraph.fromGraph(bufferedRepeater).run(system)

    /*
        cycles risk deadlocking
        - add bounds to the number of elements in cycle
        trade off : boundedness vs liveness
     */

    /**
     * Challenge: create a Fan-in shape
     * - two inputs which will be fed with EXACTLY ONE number (1, 1)
     * - output will emit an INFINITE FIBONACCI SEQUENCE based off those 2 numbers
     * 1, 2, 3, 5, 8
     *
     * Hint: user ZipWith and cycles, MergePreferred
     */

    val myFibonacciSeqStaticGraph = GraphDSL.create { builder ->
        val n1Merge = builder.add(MergePreferred.create<Int>(1))
        val n2Merge = builder.add(MergePreferred.create<Int>(1))
        val broadcast = builder.add(Broadcast.create<Int>(2))
        val fib = builder.add(ZipWith.create<Int, Int, Int> { a, b -> a + b })
        val resBroadcast = builder.add(Broadcast.create<Int>(2))

        builder.from(n1Merge).toInlet(fib.in0())
        builder.from(n2Merge).viaFanOut<Int>(broadcast)

        builder.from(broadcast).toInlet(n1Merge.preferred())
        builder.from(broadcast).toInlet(fib.in1())

        builder.from(fib.out()).viaFanOut<Int>(resBroadcast)
        builder.from(resBroadcast.out(0)).toInlet(n2Merge.preferred())

        FanInShape2(n1Merge.`in`(0), n2Merge.`in`(0), resBroadcast.out(1))
    }

    val myFib = RunnableGraph.fromGraph(
        GraphDSL.create { builder ->
            val s1 = builder.add(Source.single(1))
            val s2 = builder.add(Source.single(1))
            val sink = builder.add(Sink.foreach<Int> { println("$it,") })
            val fibFan = builder.add(myFibonacciSeqStaticGraph)


            builder.from(s1).toInlet(fibFan.in0())
            builder.from(s2).toInlet(fibFan.in1())
            builder.from(fibFan.out()).to(sink)

            ClosedShape.getInstance()
        }
    )
    //myFib.run(system)

    val rtjvmFibGenerator = GraphDSL.create { builder ->
        val zip = builder.add(Zip.create<BigInteger, BigInteger>())
        val mergePreferred = builder.add(MergePreferred.create<Pair<BigInteger, BigInteger>>(1))
        val fiboLogic = builder.add(Flow.create<Pair<BigInteger, BigInteger>>().map {
            val last = it.first()
            val previous = it.second()
            Thread.sleep(100)
            Pair(last + previous, last)
        })
        val broadcast = builder.add(Broadcast.create<Pair<BigInteger, BigInteger>>(2))
        val extractLast = builder.add(Flow.create<Pair<BigInteger,BigInteger>>().map { it.first() })

        builder.from(zip.out()).toInlet(mergePreferred.`in`(0))
        builder.from(mergePreferred).via<Pair<BigInteger, BigInteger>>(fiboLogic).viaFanOut<Pair<BigInteger, BigInteger>>(broadcast)
        builder.from(broadcast).via<BigInteger>(extractLast)
        builder.from(broadcast).toInlet(mergePreferred.preferred())

        UniformFanInShape.create(extractLast.out(), listOf(zip.in0(), zip.in1()))
    }

    val rtjvmFib = RunnableGraph.fromGraph(
        GraphDSL.create { builder ->
            val s1 = builder.add(Source.single(BigInteger.ONE))
            val s2 = builder.add(Source.single(BigInteger.ONE))
            val sink = builder.add(Sink.foreach<BigInteger> { print("$it,") })
            val fibFan = builder.add(rtjvmFibGenerator)


            builder.from(s1).toInlet(fibFan.`in`(0))
            builder.from(s2).toInlet(fibFan.`in`(1))
            builder.from(fibFan.out()).to(sink)

            ClosedShape.getInstance()
        }
    )
    rtjvmFib.run(system)
}