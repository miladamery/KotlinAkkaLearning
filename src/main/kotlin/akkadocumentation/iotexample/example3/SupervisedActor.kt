package akkadocumentation.iotexample.example3

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.PreRestart
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import java.lang.RuntimeException

class SupervisedActor private constructor(actorContext: ActorContext<String>) : AbstractBehavior<String>(actorContext) {

    companion object {
        fun create(): Behavior<String> {
            return Behaviors.setup { ctx -> SupervisedActor(ctx) }
        }
    }

    init {
        println("supervised actor started")
    }

    override fun createReceive(): Receive<String> {
        return newReceiveBuilder()
            .onMessageEquals("fail") { fail() }
            .onSignal(PostStop::class.java) { postStop() }
            .onSignal(PreRestart::class.java) { preRestart() }
            .build()
    }

    private fun fail(): Behavior<String> {
        println("supervised actor fails now")
        throw RuntimeException("I failed!")
    }

    private fun postStop(): Behavior<String> {
        println("second stopped")
        return this
    }

    private fun preRestart(): Behavior<String> {
        println("second will be restarted")
        return this
    }
}