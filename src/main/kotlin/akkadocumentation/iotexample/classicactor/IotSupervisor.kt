package akkadocumentation.iotexample.classicactor

import akka.actor.AbstractActor
import akka.actor.Props

class IotSupervisor private constructor(): AbstractActor() {

    companion object {
        fun create(): Props {
            return Props.create(IotSupervisor::class.java)
        }
    }

    init {
        println("IoT Application started")
    }

    override fun createReceive(): Receive {
        return receiveBuilder().build()
    }

    override fun postStop() {
        println("IoT Application stopped")
        super.postStop()
    }
}