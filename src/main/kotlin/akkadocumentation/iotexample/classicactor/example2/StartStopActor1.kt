package akkadocumentation.iotexample.classicactor.example2

import akka.actor.AbstractActor
import akka.actor.Props

class StartStopActor1 private constructor(): AbstractActor() {

    init {
        println("first started")
        context.actorOf(StartStopActor2.create(), "StartStopActor2")
    }

    companion object {
        fun create(): Props {
            return Props.create(StartStopActor1::class.java)
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchEquals("stop") {context.stop(self)}
            .build()
    }

    override fun postStop() {
        println("first stopped")
        super.postStop()
    }
}