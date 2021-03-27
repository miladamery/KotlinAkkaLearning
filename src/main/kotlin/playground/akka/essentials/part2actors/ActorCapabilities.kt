package playground.akka.essentials.part2actors

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props

fun main() {
    val system = ActorSystem.create("actorCapabilitiesDemo")
    val simpleActor = system.actorOf(SimpleActor.create(), "simpleActor")
    simpleActor.tell("hello, actor", ActorRef.noSender())

    // 1 - the messages can be of any type
    // a) messages must be IMMUTABLE - this is up to us to enforce this one
    // b) messages must be SERIALIZABLE
    // in practice use data classes

    simpleActor.tell(42, ActorRef.noSender())
    simpleActor.tell(SpecialMessage("some special content"), ActorRef.noSender())

    // 2 - actors have information about their context and about themselves
    // context.self === `this` in OOP
    simpleActor.tell(SendMessageToYourself("Im an actor"), ActorRef.noSender())

    // 3 - actors can REPLY to messages
    val alice = system.actorOf(SimpleActor.create(), "alice")
    val bob = system.actorOf(SimpleActor.create(), "bob")

    alice.tell(SayHiTo(bob), ActorRef.noSender())

    // 4 - dead letters

    // 5 - forwarding messages
    // D -> A -> B
    // forwarding = sending a message with ORIGINAL sender
    alice.tell(WirelessPhoneMessage("HI", bob), ActorRef.noSender())

    /**
     *  Excersices
     *
     *  1. a Counter actor
     *      - Increment
     *      - Decrement
     *      - Print
     *
     *  2. a Bank account as an actor
     *      receives
     *      - Deposite an amount
     *      - Withdraw an amount
     *      - Statement
     *      replies with
     *      - Success
     *      - Failure
     *
     *      interacti with some ither kind of actor
     */

    val counter = system.actorOf(CounterActor2.create(), "CounterActor")
    (1 .. 5).forEach { _ -> counter.tell(CounterActor2.Companion.Increment, ActorRef.noSender()) }
    (1 .. 3).forEach { _ -> counter.tell(CounterActor2.Companion.Decrement, ActorRef.noSender()) }
    counter.tell(CounterActor2.Companion.Print, ActorRef.noSender())

}

class SimpleActor() : AbstractActor() {

    companion object {
        fun create(): Props {
            return Props.create(SimpleActor::class.java)
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchEquals("HI!") { msg -> sender.tell("Hello, there!", self) }
            .match(String::class.java) { msg -> println("[${self}] i have received $msg") }
            .match(Integer::class.java) { msg -> println("[simple actor i have received a NUMBER: $msg") }
            .match(SpecialMessage::class.java) { msg -> println("[simple actor] i have received something SPECIAL: $msg") }
            .match(SendMessageToYourself::class.java) { msg -> self.tell(msg, ActorRef.noSender()) }
            .match(SayHiTo::class.java) { msg -> msg.ref.tell("HI!", self) }
            .match(WirelessPhoneMessage::class.java) { msg -> msg.ref.forward(msg.contents + "S", context) }
            .build()
    }

}

data class SpecialMessage(val contents: String)

data class SendMessageToYourself(val contents: String)

data class SayHiTo(val ref: ActorRef)

data class WirelessPhoneMessage(val contents: String, val ref: ActorRef)

class CounterActor() : AbstractActor() {
    private var counter = 0

    // DOMAIN of the counter
    companion object {

        fun create(): Props {
            return Props.create(CounterActor2::class.java)
        }

        object Increment
        object Decrement
        object Print
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(Increment::class.java) { counter++ }
            .match(Decrement::class.java) { counter-- }
            .match(Print::class.java) { println("[counter] my current count is $counter") }
            .build()
    }
}

class BankAccountActor private constructor() : AbstractActor() {
    data class Deposit(val amount: Double)
    data class Withdraw(val amount: Double)
    object Statement
    data class TransactionSuccess(val message: String)
    data class TransactionFailure(val message: String)
    companion object {
        fun create(): Props {
            return Props.create(BankAccountActor::class.java)
        }
    }

    private var funds = 0.0
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(Deposit::class.java) { msg -> onDepositCommand(msg) }
            .match(Withdraw::class.java) { msg -> onWithdrawCommand(msg) }
            .match(Statement::class.java) { sender.tell("Your balance is $funds", self) }
            .build()
    }

    private fun onDepositCommand(deposit: Deposit) {
        if (deposit.amount < 0)
            sender.tell(TransactionFailure("Invalid deposite amount"), self)
        this.funds += deposit.amount
        sender.tell(TransactionSuccess("Successfully deposited amount"), self)
    }

    private fun onWithdrawCommand(withdraw: Withdraw) {
        if (withdraw.amount < 0)
            sender.tell(TransactionFailure("Invalid withdraw amount"), self)
         if (funds - withdraw.amount >= 0) {
            this.funds -= withdraw.amount
            sender.tell(TransactionSuccess("Successfully withdraw amount."), self)
        } else {
            sender.tell(TransactionFailure("Insufficient funds!"), self)
        }
    }
}