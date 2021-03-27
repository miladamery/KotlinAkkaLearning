package playground.akka.essentials.part2actors

import akka.actor.*
import akka.event.Logging
import akka.event.LoggingAdapter
import playground.akka.essentials.tell

fun main() {
    val system = ActorSystem.create("LoggingDemo")
    val actor = system.actorOf(Props.create(SimpleActorWithExplicitLogger::class.java))
    actor tell "Logging a simple message"

    /*val simplerActor = system.actorOf(Props.create(ActorWithLogging::class.java))
    simplerActor tell "Logging a simple message by extending a trait"*/
}

// #1 explicit logging
class SimpleActorWithExplicitLogger: AbstractActor() {
    private val logger: LoggingAdapter = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchAny() {
                logger.info(it.toString())
            }
            .build()
    }
}

// #2 - ActorLogging -- works only in scala
class ActorWithLogging: AbstractActor() {
    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchAny() {
                //log().info(it.toString())
            }
            .build()
    }

}