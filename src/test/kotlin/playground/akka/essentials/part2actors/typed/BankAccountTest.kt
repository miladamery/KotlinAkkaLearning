package playground.akka.essentials.part2actors.typed

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.actor.typed.ActorRef
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.Assertions.*

class BankAccountTest {

    companion object {
        @get:ClassRule
        @JvmStatic
        val testKit = TestKitJunitResource()
    }

    @Test
    fun `negetive deposit should return transaction failure`() {
        var probe = testKit.createTestProbe(BankAccount.TransactionFailure::class.java)
        val bankAccountActor: ActorRef<BankAccount.Command> = testKit.spawn(BankAccount.create())
        bankAccountActor.tell(BankAccount.Deposit(-1000.0, probe.ref as ActorRef<BankAccount.Reply>))
        assertTrue(probe.receiveMessage() is BankAccount.TransactionFailure)
    }

    @Test
    fun `positive deposit should return transaction success`() {
        var probe = testKit.createTestProbe(BankAccount.TransactionSuccess::class.java)
        val bankAccountActor: ActorRef<BankAccount.Command> = testKit.spawn(BankAccount.create())
        bankAccountActor.tell(BankAccount.Deposit(10000.0, probe.ref as ActorRef<BankAccount.Reply>))
        assertTrue(probe.receiveMessage() is BankAccount.TransactionSuccess)
    }

    @Test
    fun `given deposit should be the same as actor deposit`() {
        var probe = testKit.createTestProbe(BankAccount.FundsValue::class.java)
        var probe2 = testKit.createTestProbe(BankAccount.TransactionSuccess::class.java)
        val bankAccountActor: ActorRef<BankAccount.Command> = testKit.spawn(BankAccount.create())
        bankAccountActor.tell(BankAccount.Deposit(10000.0, probe2.ref as ActorRef<BankAccount.Reply>))
        bankAccountActor.tell(BankAccount.Statement(probe.ref as ActorRef<BankAccount.Reply>))
        val response = probe.receiveMessage()
        assertEquals(10000.0, response.funds)
    }
}