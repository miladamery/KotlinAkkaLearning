package playground.akka.streams.part5_advanced

import akka.actor.ActorSystem
import akka.stream.Attributes
import akka.stream.Outlet
import akka.stream.SourceShape
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import akka.stream.stage.AbstractOutHandler
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.OutHandler
import java.util.*

/**
 * Goal
 * Create our own components with custom logic
 * Master the GraphStage API
 */
class CustomOperators {
}

fun main() {
    val system = ActorSystem.create("CustomOperators")

    // 1 - a custom source which emits random numbers untill canceled
    val randomGeneratorSource = Source.fromGraph(RandomNumberGenerator(100))
    randomGeneratorSource.runWith(Sink.foreach(::println), system)
}

class RandomNumberGenerator(max: Int) : GraphStage<SourceShape<Int>>() {

    private val outPort = Outlet.create<Int>("randomGenerator")
    val random = Random()

    override fun shape(): SourceShape<Int> = SourceShape.of(outPort)

    override fun createLogic(inheritedAttributes: Attributes?): GraphStageLogic {
        return object: GraphStageLogic(shape()) {
            fun setHandler(){

            }
        }
    }
}

