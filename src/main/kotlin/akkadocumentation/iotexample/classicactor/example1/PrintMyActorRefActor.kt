package akkadocumentation.iotexample.classicactor.example1

import akka.actor.AbstractActor
import akka.actor.Props

class PrintMyActorRefActor: AbstractActor() {

    companion object {
        fun create(): Props {
            return Props.create(PrintMyActorRefActor::class.java)
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchEquals("printit") { printit() }
            .build()
    }

    private fun printit() {
        var secondRef = context.actorOf(Props.default(), "second-actor")
        println("Second: $secondRef")
    }
}