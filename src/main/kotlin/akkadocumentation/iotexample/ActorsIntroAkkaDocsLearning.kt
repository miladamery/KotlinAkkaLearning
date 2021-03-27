package playground.akka.essentials.part2actors

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

fun main(args: Array<String>) {
    var actorSystem = ActorSystem.create(HelloWorldMain.create(), "Hello")
    actorSystem.tell(HelloWorldMain.SayHello("World"))
    actorSystem.tell(HelloWorldMain.SayHello("Akka"))
}

class HelloWorld(context: ActorContext<Greet>) : AbstractBehavior<HelloWorld.Greet>(context) {

    companion object {
        fun create(): Behavior<Greet> {
            return Behaviors.setup { ctx -> HelloWorld(ctx) }
        }
    }

    class Greet(_whom: String, _replyTo: ActorRef<Greeted>) {
        var whom = _whom
        var replyTo = _replyTo
    }

    class Greeted(_whom: String, _from: ActorRef<Greet>) {
        var whom = _whom
        var from = _from
    }

    override fun createReceive(): Receive<Greet> {
        return newReceiveBuilder()
            .onMessage(Greet::class.java) { g -> onGreet(g) }
            .build()
    }

    private fun onGreet(command: Greet): Behavior<Greet> {
        println("Hello ${command.whom}")
        command.replyTo.tell(Greeted(command.whom, context.self))
        return this
    }
}

class HelloWorldBot(_max: Int, context: ActorContext<HelloWorld.Greeted>?) :
    AbstractBehavior<HelloWorld.Greeted>(context) {
    private var max = _max
    private var greetingsCounter = 0

    companion object {
        fun create(max: Int): Behavior<HelloWorld.Greeted> {
            return Behaviors.setup { ctx -> HelloWorldBot(max, ctx) }
        }
    }

    override fun createReceive(): Receive<HelloWorld.Greeted> {
        return newReceiveBuilder()
            .onMessage(HelloWorld.Greeted::class.java) { g -> onGreeted(g) }
            .build()
    }

    private fun onGreeted(message: HelloWorld.Greeted): Behavior<HelloWorld.Greeted> {
        greetingsCounter++
        println("Greeting $greetingsCounter for ${message.whom}")
        if (greetingsCounter == max)
            return Behaviors.stopped()
        else {
            message.from.tell(HelloWorld.Greet(message.whom, context.self))
            return this
        }
    }
}

class HelloWorldMain(context: ActorContext<SayHello>?) : AbstractBehavior<HelloWorldMain.SayHello>(context) {

    private var greeter: ActorRef<HelloWorld.Greet> = context?.spawn(HelloWorld.create(), "greeter")!!

    class SayHello(_name: String) {
        var name = _name
    }

    companion object {
        fun create(): Behavior<SayHello> {
            return Behaviors.setup { ctx -> HelloWorldMain(ctx) }
        }
    }

    override fun createReceive(): Receive<SayHello> {
        return newReceiveBuilder()
            .onMessage(SayHello::class.java) { s -> onStart(s) }
            .build()
    }

    private fun onStart(command: SayHello): Behavior<SayHello> {
        var replyTo = context.spawn(HelloWorldBot.create(3), command.name)
        greeter.tell(HelloWorld.Greet(command.name, replyTo))
        return this
    }
}