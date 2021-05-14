package playground.akka.streams.part3_graphs

import akka.actor.ActorSystem
import akka.stream.ClosedShape
import akka.stream.FanOutShape2
import akka.stream.UniformFanInShape
import akka.stream.javadsl.*
import java.util.*
import kotlin.math.max

fun main() {
    val system = ActorSystem.create("MoreOpenGraphs")

    val max3StaticGraph = GraphDSL.create { builder ->
        // step 2 - define aux SHAPES
        val max1 = builder.add(ZipWith.create<Int, Int, Int> { a, b -> max(a, b) })
        val max2 = builder.add(ZipWith.create<Int, Int, Int> { a, b -> max(a, b) })

        // step 3
        builder.from(max1.out()).toInlet(max2.in0())

        // step 4
        UniformFanInShape.create(max2.out(), listOf(max1.in0(), max1.in1(), max2.in1()))
    }

    val source1 = Source.range(1, 10)
    val source2 = Source.range(1, 10).map { 5 }
    val source3 = Source.range(5, 15)

    val sink = Sink.foreach<Int> { println("Max is $it") }

    val max3RunnableGraph = RunnableGraph.fromGraph(
        GraphDSL.create { builder ->
            val s1 = builder.add(source1)
            val s2 = builder.add(source2)
            val s3 = builder.add(source3)
            val sinkShape = builder.add(sink)
            val max3Shape = builder.add(max3StaticGraph)

            builder.from(s1).toFanIn<Int>(max3Shape)
            builder.from(s2).toFanIn<Int>(max3Shape)
            builder.from(s3).toFanIn<Int>(max3Shape)
            builder.from(max3Shape.out()).to(sinkShape)

            ClosedShape.getInstance()
        }
    )
    //max3RunnableGraph.run(system)

    // same for UniformFanOutShape

    /*
        Non-uniform shapes

        Processing bank transactions
        Txn suspicious if amount > 10000$

        Streams component for txns
        - output1: let the transaction go through
        - output2: suspicious txn ids
     */

    data class Transaction(val id: String, val source: String, val recipient: String, val amount: Int, val date: Date)

    val transactionSource = Source.fromIterator {
        listOf<Transaction>(
            Transaction("5273890572", "Paul", "Jim", 100, Date()),
            Transaction("3578902532", "Daniel", "Jim", 10000, Date()),
            Transaction("2547886566", "Jim", "Alice", 7000, Date()),
        ).iterator()
    }
    val bankProcessor = Sink.foreach<Transaction>(::println)
    val suspiciousAnalysisService = Sink.foreach<String> { txnId -> println("Suspicious transaction ID: $txnId") }

    val suspiciousTransactionStaticGraph = GraphDSL.create { builder ->
        // step 2 - define SHAPES
        val broadcast = builder.add(Broadcast.create<Transaction>(2))
        val suspiciousTxnFilter = builder.add(Flow.of(Transaction::class.java).filter { txn -> txn.amount >= 10000 })
        val txnIdExtractor = builder.add(Flow.of(Transaction::class.java).map { it.id })

        builder.from(broadcast.out(0)).via<Transaction>(suspiciousTxnFilter).via<String>(txnIdExtractor)

        // step 4
        FanOutShape2(broadcast.`in`(), broadcast.out(1), txnIdExtractor.out())
    }

    // step 1
    val susTxnRunnableGraph = RunnableGraph.fromGraph(
        GraphDSL.create { builder ->
            // step 2
            val src = builder.add(transactionSource)
            val susTxnShape = builder.add(suspiciousTransactionStaticGraph)
            val susSink = builder.add(suspiciousAnalysisService)
            val normalSink = builder.add(bankProcessor)

            builder
                .from(src)
                .toInlet(susTxnShape.`in`())
            builder
                .from(susTxnShape.out0())
                .to(normalSink)
            builder
                .from(susTxnShape.out1())
                .to(susSink)

            ClosedShape.getInstance()
        }
    )
    susTxnRunnableGraph.run(system)
}