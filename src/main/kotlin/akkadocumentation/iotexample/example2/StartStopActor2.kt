package akkadocumentation.iotexample.example2

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

class StartStopActor2 private constructor(actorContext: ActorContext<String>): AbstractBehavior<String>(actorContext) {

    init {
        println("second started")
    }

    companion object {
        fun create(): Behavior<String> {
            return Behaviors.setup { ctx -> StartStopActor2(ctx) }
        }
    }

    override fun createReceive(): Receive<String> {
        return newReceiveBuilder()
            .onSignal(PostStop::class.java) {onPostStop()}
            .build()
    }

    fun onPostStop(): Behavior<String> {
        println("second stopped")
        return this
    }
}