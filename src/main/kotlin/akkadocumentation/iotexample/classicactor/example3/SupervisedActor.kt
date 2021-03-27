package akkadocumentation.iotexample.classicactor.example3

import akka.actor.AbstractActor
import akka.actor.Props
import scala.Option

class SupervisedActor private constructor(): AbstractActor() {

    companion object {
        fun create(): Props {
            return Props.create(SupervisedActor::class.java)
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchEquals("fail") { fail() }
            .build()
    }

    private fun fail() {
        println("supervised actor fails now")
        throw RuntimeException("I failed!")
    }

    override fun postStop() {
        println("second stopped")
        super.postStop()
    }

    override fun preRestart(reason: Throwable?, message: Option<Any>?) {
        println("second will be restarted")
        super.preRestart(reason, message)
    }
}