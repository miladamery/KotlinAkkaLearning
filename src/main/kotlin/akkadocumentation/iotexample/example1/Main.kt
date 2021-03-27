package akkadocumentation.iotexample.example1

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

class Main private constructor(context: ActorContext<String>) : AbstractBehavior<String>(context) {

    companion object {
        fun create(): Behavior<String> {
            return Behaviors.setup { ctx -> Main(ctx) }
        }
    }

    override fun createReceive(): Receive<String> {
        return newReceiveBuilder()
            .onMessageEquals("start") { start() }
            .build()
    }

    private fun start(): Behavior<String> {
        val firstRef: ActorRef<String> = context.spawn(PrintMyActorRefActor.create(), "first-actor")
        println("First: $firstRef")
        firstRef.tell("printit")
        return Behaviors.same()
    }
}