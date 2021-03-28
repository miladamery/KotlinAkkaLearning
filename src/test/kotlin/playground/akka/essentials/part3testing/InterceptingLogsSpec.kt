package playground.akka.essentials.part3testing

import akka.actor.AbstractActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.testkit.javadsl.TestKit
import com.typesafe.config.ConfigFactory
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

class InterceptingLogsSpec {

    companion object {
        lateinit var testKit: TestKit

        @BeforeClass
        @JvmStatic
        fun setup() {
            val system = ActorSystem.create("InterceptingLogsSpec", ConfigFactory.load().getConfig("interceptingLogMessages"))
            testKit = TestKit(system)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            TestKit.shutdownActorSystem(testKit.system)
        }

        data class Checkout(val item: String, val creditCard: String)
        data class AuthorizeCard(val creditCard: String)
        data class DispatchOrder(val item: String)
        object PaymentAccepted
        object PaymentDenied
        object OrderConfirmed

        class CheckoutActor : AbstractActor() {
            private val paymentManager = context.actorOf(PaymentManager.create())
            private val fulfillmentManager = context.actorOf(FulfillmentManager.create())

            companion object {
                fun create(): Props {
                    return Props.create(CheckoutActor::class.java)
                }
            }

            override fun createReceive(): Receive = awaitingCheckout()

            private fun awaitingCheckout(): Receive {
                return receiveBuilder()
                    .match(Checkout::class.java) {
                        paymentManager.tell(AuthorizeCard(it.creditCard), self)
                        context.become(pendingPayment(it.item))
                    }
                    .build()
            }

            private fun pendingPayment(item: String): Receive {
                return receiveBuilder()
                    .match(PaymentAccepted::class.java) {
                        fulfillmentManager.tell(DispatchOrder(item), self)
                        context.become(pendingFulfillment(item))
                    }
                    .match(PaymentDenied::class.java) {

                    }
                    .build()
            }

            private fun pendingFulfillment(item: String): Receive {
                return receiveBuilder()
                    .match(OrderConfirmed::class.java) {
                        context.become(awaitingCheckout())
                    }
                    .build()
            }
        }

        class PaymentManager : AbstractActor() {
            companion object {
                fun create(): Props {
                    return Props.create(PaymentManager::class.java)
                }
            }

            override fun createReceive(): Receive {
                return receiveBuilder()
                    .match(AuthorizeCard::class.java) {
                        if (it.creditCard.startsWith("0")) sender.tell(PaymentDenied, self)
                        else sender.tell(PaymentAccepted, self)
                    }
                    .build()
            }
        }

        class FulfillmentManager : AbstractActor() {
            private val logger: LoggingAdapter = Logging.getLogger(context.system, this)
            private var orderId = 43
            companion object {
                fun create(): Props {
                    return Props.create(FulfillmentManager::class.java)
                }
            }

            override fun createReceive(): Receive {
                return receiveBuilder()
                    .match(DispatchOrder::class.java) {
                        orderId += 1
                        logger.info("order $orderId for item ${it.item} has been dispatched.")
                        sender.tell(OrderConfirmed, self)
                    }
                    .build()
            }
        }
    }

    //@Test
    fun `A checkout flow should correctly log the dispatch of an order`() {
        testKit.run {

            val item = "Rock the jvm course"
            val creditCard = "1234-1234-1234-1234"
            akka.testkit.EventFilter.info(null, null, "", "Order [0-9]+ for item $item has been dispatched", 1)
                .intercept({
                    val checkoutActor = system.actorOf(CheckoutActor.create())
                    checkoutActor.tell(Checkout(item, creditCard), testActor)
                }, system)

            /*EventFilter(ActorKilledException::class.java, testKit.system)
                .matches("Order [0-9]+ for item $item has been dispatched")
                .occurrences(1)
                .intercept {
                    val checkoutActor = system.actorOf(CheckoutActor.create())
                    checkoutActor.tell(Checkout(item, creditCard), testActor)
                }*/
            /*LoggingTestKit
                .info()
                .withMessageRegex("Order [0-9]+ for item $item has been dispatched")
                .withOccurrences(1)
                .expect(Adapter.toTyped(system)) {
                    val checkoutActor = system.actorOf(CheckoutActor.create())
                    checkoutActor.tell(Checkout(item, creditCard), testActor)
                }*/
        }
    }
}