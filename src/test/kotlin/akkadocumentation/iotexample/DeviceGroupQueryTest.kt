package akkadocumentation.iotexample

import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Duration

class DeviceGroupQueryTest {
    companion object {
        @get:ClassRule
        @JvmStatic
        val testKit = TestKitJunitResource()
    }

    @Test
    fun testReturnTemperatureValueForWorkingDevices() {
        val requester = testKit.createTestProbe(RespondAllTemperatures::class.java)
        val device1 = testKit.createTestProbe(Device.Command::class.java)
        val device2 = testKit.createTestProbe(Device.Command::class.java)

        val deviceIdToActor = mutableMapOf("device1" to device1.ref, "device2" to device2.ref)

        val queryActor =
            testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1L, requester.ref, Duration.ofSeconds(3)))

        device1.expectMessageClass(Device.ReadTemperature::class.java)
        device2.expectMessageClass(Device.ReadTemperature::class.java)

        queryActor.tell(DeviceGroupQuery.WrappedRespondTemperature(Device.RespondTemperature(0L, "device1", 1.0)))
        queryActor.tell(DeviceGroupQuery.WrappedRespondTemperature(Device.RespondTemperature(0L, "device2", 2.0)))

        val response = requester.receiveMessage()
        assertEquals(1L, response.requestId)

        val expectedTemperatures = mutableMapOf("device1" to Temperature(1.0), "device2" to Temperature(2.0))
        assertEquals(expectedTemperatures, response.temperatures)
    }

    @Test
    fun testReturnTemperatureNotAvailableForDevicesWithNoReadings() {
        val requester = testKit.createTestProbe(RespondAllTemperatures::class.java)
        val device1 = testKit.createTestProbe(Device.Command::class.java)
        val device2 = testKit.createTestProbe(Device.Command::class.java)

        val deviceIdToActor = mutableMapOf("device1" to device1.ref, "device2" to device2.ref)

        val queryActor =
            testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1L, requester.ref, Duration.ofSeconds(3)))

        assertEquals(0L, device1.expectMessageClass(Device.ReadTemperature::class.java).requestId)
        assertEquals(0L, device2.expectMessageClass(Device.ReadTemperature::class.java).requestId)

        queryActor.tell(DeviceGroupQuery.WrappedRespondTemperature(Device.RespondTemperature(0L, "device1", null)))
        queryActor.tell(DeviceGroupQuery.WrappedRespondTemperature(Device.RespondTemperature(0L, "device2", 2.0)))

        val response = requester.receiveMessage()
        assertEquals(1L, response.requestId)

        val expectedTemperatures =
            mutableMapOf("device1" to TemperatureNotAvailable.INSTANCE, "device2" to Temperature(2.0))
        assertEquals(expectedTemperatures, response.temperatures)
    }

    @Test
    fun testReturnDeviceNotAvailableIfDeviceStopsBeforeAnswering() {
        val requester = testKit.createTestProbe(RespondAllTemperatures::class.java)
        val device1 = testKit.createTestProbe(Device.Command::class.java)
        val device2 = testKit.createTestProbe(Device.Command::class.java)
        val deviceIdToActor = mutableMapOf("device1" to device1.ref, "device2" to device2.ref)

        val queryActor =
            testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1L, requester.ref, Duration.ofSeconds(3)))

        assertEquals(0L, device1.expectMessageClass(Device.ReadTemperature::class.java).requestId)
        assertEquals(0L, device2.expectMessageClass(Device.ReadTemperature::class.java).requestId)

        queryActor.tell(DeviceGroupQuery.WrappedRespondTemperature(Device.RespondTemperature(0L, "device1", 1.0)))
        device2.stop()

        val response = requester.receiveMessage()
        assertEquals(1L, response.requestId)

        val expectedTemperatures =
            mutableMapOf("device1" to Temperature(1.0), "device2" to DeviceNotAvailable.INSTANCE)
        assertEquals(expectedTemperatures, response.temperatures)
    }

    @Test
    fun testReturnTemperatureReadingEvenIfDeviceStopsAfterAnswering() {
        val requester = testKit.createTestProbe(RespondAllTemperatures::class.java)
        val device1 = testKit.createTestProbe(Device.Command::class.java)
        val device2 = testKit.createTestProbe(Device.Command::class.java)

        val deviceIdToActor = mutableMapOf("device1" to device1.ref, "device2" to device2.ref)

        val queryActor =
            testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1L, requester.ref, Duration.ofSeconds(3)))

        device1.expectMessageClass(Device.ReadTemperature::class.java)
        device2.expectMessageClass(Device.ReadTemperature::class.java)

        queryActor.tell(DeviceGroupQuery.WrappedRespondTemperature(Device.RespondTemperature(0L, "device1", 1.0)))
        queryActor.tell(DeviceGroupQuery.WrappedRespondTemperature(Device.RespondTemperature(0L, "device2", 2.0)))
        device2.stop()

        val response = requester.receiveMessage()
        assertEquals(1L, response.requestId)

        val expectedTemperatures = mutableMapOf("device1" to Temperature(1.0), "device2" to Temperature(2.0))
        assertEquals(expectedTemperatures, response.temperatures)
    }

    @Test
    fun testReturnDeviceTimedOutIfDecviceDoesNotAnswerInTime() {
        val requester = testKit.createTestProbe(RespondAllTemperatures::class.java)
        val device1 = testKit.createTestProbe(Device.Command::class.java)
        val device2 = testKit.createTestProbe(Device.Command::class.java)

        val deviceIdToActor = mutableMapOf("device1" to device1.ref, "device2" to device2.ref)

        val queryActor =
            testKit.spawn(DeviceGroupQuery.create(deviceIdToActor, 1L, requester.ref, Duration.ofMillis(200)))

        assertEquals(0L, device1.expectMessageClass(Device.ReadTemperature::class.java).requestId)
        assertEquals(0L, device2.expectMessageClass(Device.ReadTemperature::class.java).requestId)

        queryActor.tell(DeviceGroupQuery.WrappedRespondTemperature(Device.RespondTemperature(0L, "device1", 1.0)))
        // no reply from device2

        val response = requester.receiveMessage()
        assertEquals(1L, response.requestId)

        val expectedTemperatures =
            mutableMapOf("device1" to Temperature(1.0), "device2" to DeviceTimedOut.INSTANCE)
        assertEquals(expectedTemperatures, response.temperatures)
    }
}