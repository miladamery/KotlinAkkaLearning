package akkadocumentation.iotexample.classicactor.example2

import akka.actor.AbstractActor
import akka.actor.Props

class StartStopActor2 private constructor() : AbstractActor() {

    init {
        println("second started")
    }

    companion object {
        fun create(): Props {
            return Props.create(StartStopActor2::class.java)
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .build()
    }

    override fun postStop() {
        println("second stopped")
        super.postStop()
    }
}