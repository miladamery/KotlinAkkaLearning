package akkadocumentation.iotexample.classicactor.example1

import akka.actor.AbstractActor
import akka.actor.Props

class Main: AbstractActor() {

    companion object {
        fun create(): Props {
            return Props.create(Main::class.java)
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchEquals("start") { start() }
            .build()
    }

    private fun start() {
        val firstRef = context.actorOf(PrintMyActorRefActor.create(), "first-actor")
        println("First: $firstRef")
        firstRef.tell("printit", firstRef)
    }
}