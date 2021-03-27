package akkadocumentation.iotexample.example2

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

class StartStopActor1 private constructor(actorContext: ActorContext<String>): AbstractBehavior<String>(actorContext) {

    init {
        println("first started")
        context.spawn(StartStopActor2.create(), "second")
    }

    companion object {
        fun create(): Behavior<String> {
            return Behaviors.setup { ctx -> StartStopActor1(ctx) }
        }
    }

    override fun createReceive(): Receive<String> {
        return newReceiveBuilder()
            .onMessageEquals("stop") {Behaviors.stopped()}
            .onSignal(PostStop::class.java) {onPostStop()}
            .build()
    }

    fun onPostStop(): Behavior<String> {
        println("first stopped")
        return this
    }
}