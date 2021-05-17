package playground.akka.streams.part3_graphs

import akka.actor.ActorSystem
import akka.stream.BidiShape
import akka.stream.ClosedShape
import akka.stream.javadsl.*

fun main() {
    val system = ActorSystem.create("BidirectionalFlows")

    /*
        Example: Cryptography
        bidiflow
     */
    val bidiCryptoStaticGraph = GraphDSL.create { builder ->
        val encryptionFlowShape = builder.add(Flow.of(String::class.java).map { encrypt(3, it) })
        val decryptionFlowShape = builder.add(Flow.of(String::class.java).map { decrypt(3, it) })

        //BidiShape(encryptionFlowShape.`in`(), encryptionFlowShape.out(), decryptionFlowShape.`in`(), decryptionFlowShape.out())
        BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
    }

    val unencryptedStrings = listOf("akka", "is", "awesome", "testing", "bidirectional", "flows")
    val unencryptedSource = Source.fromIterator { unencryptedStrings.iterator() }
    val encryptedSource = Source.fromIterator { unencryptedStrings.map { encrypt(3, it) }.iterator() }

    val cryptoBidiGraph = RunnableGraph.fromGraph(
        GraphDSL.create { builder ->
            val unencryptedSourceShape = builder.add(unencryptedSource)
            val encryptedSourceShape = builder.add(encryptedSource)
            val bidi = builder.add(bidiCryptoStaticGraph)
            val encryptedSinkShape = builder.add(Sink.foreach<String> { println("Encrypted: $it") })
            val decryptedSinkShape = builder.add(Sink.foreach<String> { println("Decrypted: $it") })

            builder.from(unencryptedSourceShape) to bidi.in1()
            builder.from(bidi.out1()) to encryptedSinkShape
            builder.from(encryptedSourceShape) to bidi.in2()
            builder.from(bidi.out2()) to decryptedSinkShape

            ClosedShape.getInstance()
        }
    )

    cryptoBidiGraph.run(system)
}

fun encrypt(n: Int, s: String): String {
    return String(s.map { (it + n) }.toCharArray())
}

fun decrypt(n: Int, s: String): String {
    return String(s.map { (it - n) }.toCharArray())
}