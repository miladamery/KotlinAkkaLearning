package playground.akka.streams.part5_advanced

import akka.actor.ActorSystem
import akka.stream.*
import akka.stream.javadsl.*
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.stream.Stream

/**
 * Create components with arbitrary inputs and outputs
 *
 */
class CustomGraphShapes {
}

fun main() {
    val system = ActorSystem.create("CustomGraphShapes")

    // balance 2x3 shape
    val balance2x3Impl = GraphDSL.create { builder ->
        val merge = builder.add(Merge.create<Int>(2))
        val balance = builder.add(Balance.create<Int>(3))

        builder.from(merge.out()).toInlet(balance.`in`())

        Balance2x3(
            merge.`in`(0),
            merge.`in`(1),
            balance.out(0),
            balance.out(1),
            balance.out(2),
        )
    }

    val balance2x3Graph = RunnableGraph.fromGraph(
        GraphDSL.create { builder ->
            val slowSource = Source.fromJavaStream { Stream.iterate(1) { it + 1 } }.throttle(1, Duration.ofSeconds(1))
            val fastSource = Source.fromJavaStream { Stream.iterate(1) { it + 1 } }.throttle(2, Duration.ofSeconds(1))
            val slowSourceShape = builder.add(slowSource)
            val fastSourceShape = builder.add(fastSource)
            val sink1 = builder.add(createSink(1))
            val sink2 = builder.add(createSink(2))
            val sink3 = builder.add(createSink(3))
            val balance2x3 = builder.add(balance2x3Impl)

            builder.from(slowSourceShape).toInlet(balance2x3.in0)
            builder.from(fastSourceShape).toInlet(balance2x3.in1)
            builder.from(balance2x3.out0).to(sink1)
            builder.from(balance2x3.out1).to(sink2)
            builder.from(balance2x3.out2).to(sink3)

            ClosedShape.getInstance()
        }
    )
    balance2x3Graph.run(system)

    /**
     * Exercise: generalize the balance component, make it M x N
     */
}

class Balance2x3(
    val in0: Inlet<Int>,
    val in1: Inlet<Int>,
    val out0: Outlet<Int>,
    val out1: Outlet<Int>,
    val out2: Outlet<Int>
) :
    AbstractShape() {
    override fun deepCopy(): Shape {
        return Balance2x3(
            in0.carbonCopy(),
            in1.carbonCopy(),
            out0.carbonCopy(),
            out1.carbonCopy(),
            out2.carbonCopy()
        )
    }

    override fun allInlets(): MutableList<Inlet<*>> = mutableListOf(in0, in1)

    override fun allOutlets(): MutableList<Outlet<*>> = mutableListOf(out0, out1, out2)

}

fun createSink(index: Int): Sink<Int, CompletionStage<Int>> = Sink.fold(0) { count: Int, element: Int ->
    println("[sink $index] Received $element, current count is $count")
    count + 1
}