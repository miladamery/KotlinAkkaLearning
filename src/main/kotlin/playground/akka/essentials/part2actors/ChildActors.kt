package playground.akka.essentials.part2actors

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props

fun main() {
    // Actors can create other actors
    val system = ActorSystem.create("ParentChildDemo")
    val parent = system.actorOf(Parent.create(), "parent")
    parent.tell(Parent.CreateChild("child"), ActorRef.noSender())
    parent.tell(Parent.TellChild("Hey Kid"), ActorRef.noSender())

    // Actor hierarchies
    // parent -> child  -> grandChild
    //        -> child2 ->

    /*
        Gaurdian actors (top-level)
        - /system = system guardian
        - /user = user-level guardian
        - / = the root guardian
     */

    /**
     * Actor Selection
     */
    val childSelection = system.actorSelection("/user/parent/child")
    childSelection.tell("I Found you", ActorRef.noSender())

    /**
     * Danger!
     * NEVER PASS MUTABLE ACTOR STATE, OTH `THIS` REFERENCE, TO CHILD ACTORS.
     *
     * NEVER IN YOUR LIFE.
     */
}

class Parent: AbstractActor() {
    companion object {
        fun create(): Props {
            return Props.create(Parent::class.java)
        }
    }
    data class CreateChild(val name: String)
    data class TellChild(val message: String)
    var child: ActorRef? = null
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(CreateChild::class.java) {
                println("${self.path()} creating child")
                var childRef = context.actorOf(Child.create(), it.name)
                context.become(withChild(childRef))
            }
            .build()
    }

    private fun withChild(childRef: ActorRef): Receive {
        return receiveBuilder()
            .match(TellChild::class.java) {
                childRef.forward(it, context)
            }
            .build()
    }
}

class Child: AbstractActor() {
    companion object {
        fun create(): Props {
            return Props.create(Child::class.java)
        }
    }
    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchAny {msg -> println("${self.path()} I got: $msg") }
            .build()
    }
}