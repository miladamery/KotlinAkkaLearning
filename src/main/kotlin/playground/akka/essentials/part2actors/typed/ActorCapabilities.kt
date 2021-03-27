package playground.akka.essentials.part2actors.typed

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

fun main() {
    val system = ActorSystem.create(CounterActor.create(), "CounterActor")
    (1..5).forEach { _ -> system.tell(CounterActor.Increment) }
    (1..3).forEach { _ -> system.tell(CounterActor.Decrement) }
    system.tell(CounterActor.Print)
}

class CounterActor private constructor(context: ActorContext<Command>, private val startVal: Int = 0) :
    AbstractBehavior<CounterActor.Command>(context) {

    companion object {
        fun create(startVal: Int = 0): Behavior<Command> {
            return Behaviors.setup { ctx -> CounterActor(ctx, startVal) }
        }
    }

    interface Command
    object Increment : Command
    object Decrement : Command
    object Print : Command

    override fun createReceive(): Receive<Command> {
        return newReceiveBuilder()
            .onMessage(Increment::class.java) { onIncrementCommand() }
            .onMessage(Decrement::class.java) { onDecrementCommand() }
            .onMessage(Print::class.java) { onPrintCommand() }
            .build()
    }

    private fun onIncrementCommand(): Behavior<Command> {
        return create(startVal + 1)
    }

    private fun onDecrementCommand(): Behavior<Command> {
        return create(startVal - 1)
    }

    private fun onPrintCommand(): Behavior<Command> {
        println("[counter] my current count is $startVal")
        return this
    }
}

class BankAccount private constructor(context: ActorContext<Command>, private val funds: Double = 0.0) :
    AbstractBehavior<BankAccount.Command>(context) {

    interface Command
    interface Reply
    data class Deposit(val amount: Double, val replyTo: ActorRef<Reply>) : Command
    data class Withdraw(val amount: Double, val replyTo: ActorRef<Reply>) : Command
    data class Statement(val replyTo: ActorRef<Reply>) : Command
    data class TransactionSuccess(val message: String) : Reply
    data class TransactionFailure(val message: String) : Reply
    data class FundsValue(val funds: Double): Reply
    companion object {
        fun create(funds: Double = 0.0): Behavior<Command> {
            return Behaviors.setup { ctx -> BankAccount(ctx, funds) }
        }
    }

    override fun createReceive(): Receive<Command> {
        return newReceiveBuilder()
            .onMessage(Deposit::class.java) { msg -> onDepositCommand(msg) }
            .onMessage(Withdraw::class.java) { msg -> onWithdrawCommand(msg) }
            .onMessage(Statement::class.java) {msg -> onStatementCommand(msg) }
            .build()
    }

    private fun onDepositCommand(deposit: Deposit): Behavior<Command> {
        if (deposit.amount < 0)
            deposit.replyTo.tell(TransactionFailure("Invalid deposit amount!"))
        deposit.replyTo.tell(TransactionSuccess("Successfully deposited amount"))
        return create(funds + deposit.amount)
    }

    private fun onWithdrawCommand(withdraw: Withdraw): Behavior<Command> {
        if (withdraw.amount < 0)
            withdraw.replyTo.tell(TransactionFailure("Invalid withdraw amount!"))
        if (withdraw.amount > funds)
            withdraw.replyTo.tell(TransactionFailure("Insufficient funds!"))
        return create(funds - withdraw.amount)
    }

    private fun onStatementCommand(statement: Statement): Behavior<Command> {
        println("Your balance is $funds")
        statement.replyTo.tell(FundsValue(funds))
        return this
    }
}