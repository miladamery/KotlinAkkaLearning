package akkadocumentation.iotexample.example1

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

class PrintMyActorRefActor private  constructor(context: ActorContext<String>): AbstractBehavior<String>(context) {

    companion object {
        fun create(): Behavior<String> {
            return Behaviors.setup { ctx -> PrintMyActorRefActor(ctx) }
        }
    }

    override fun createReceive(): Receive<String> {
        return newReceiveBuilder()
            .onMessageEquals("printit") { printIt() }
            .build()
    }

    private fun printIt(): Behavior<String> {
        var secondRef: ActorRef<String> = context.spawn(Behaviors.empty(), "second-actor")
        println("Second: $secondRef")
        return this
    }
}