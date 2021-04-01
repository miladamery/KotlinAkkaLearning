package akkadocumentation.iotexample

import org.junit.jupiter.api.Assertions.*
import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.actor.testkit.typed.javadsl.TestProbe
import org.junit.ClassRule
import org.junit.Test


class DeviceTest {

    companion object {
        @get:ClassRule
        @JvmStatic
        val testKit = TestKitJunitResource()
    }

    @Test
    fun testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
        var probe: TestProbe<Device.RespondTemperature> = testKit.createTestProbe(Device.RespondTemperature::class.java)
        var deviceActor = testKit.spawn(Device.create("group", "device"))
        deviceActor.tell(Device.ReadTemperature(42L, probe.ref))
        var response = probe.receiveMessage()
        assertEquals(42L, response.requestId)
        assertNull(response.value)
    }

    @Test
    fun testReplyWithLatestTemperatureReading() {
        var recordProbe = testKit.createTestProbe(Device.TemperatureRecorded::class.java)
        var readProbe = testKit.createTestProbe(Device.RespondTemperature::class.java)
        var deviceActor = testKit.spawn(Device.create("group", "device"))

        deviceActor.tell(Device.RecordTemperature(1L, 24.0, recordProbe.ref))
        assertEquals(1L, recordProbe.receiveMessage().requestId)

        deviceActor.tell(Device.ReadTemperature(2L, readProbe.ref))
        var respond1 = readProbe.receiveMessage()
        assertEquals(24.0, respond1.value)
        assertEquals(2L, respond1.requestId)

        deviceActor.tell(Device.RecordTemperature(3L, 55.0, recordProbe.ref))
        assertEquals(3L, recordProbe.receiveMessage().requestId)

        deviceActor.tell(Device.ReadTemperature(4L, readProbe.ref))
        var respond2 = readProbe.receiveMessage()
        assertEquals(4L, respond2.requestId)
        assertEquals(55.0, respond2.value)
    }

    @Test
    fun testReplyToRegistrationRequests() {
        val registeredProbe = testKit.createTestProbe(DeviceManager.DeviceRegistered::class.java)
        val deviceGroupActor = testKit.spawn(DeviceGroup.create("group"))

        deviceGroupActor.tell(DeviceManager.RequestTrackDevice("group", "device", registeredProbe.ref))
        val registered1 = registeredProbe.receiveMessage()

        deviceGroupActor.tell(DeviceManager.RequestTrackDevice("group", "device3", registeredProbe.ref))
        val registered2 = registeredProbe.receiveMessage()

        assertNotEquals(registered1.device, registered2.device)

        // Check that the device actors are working
        val temperatureRecordedProbe = testKit.createTestProbe(Device.TemperatureRecorded::class.java)
        registered1.device.tell(Device.RecordTemperature(0L, 1.0, temperatureRecordedProbe.ref))
        assertEquals(0L, temperatureRecordedProbe.receiveMessage().requestId)
        registered2.device.tell(Device.RecordTemperature(1L, 2.0, temperatureRecordedProbe.ref))
        assertEquals(1L, temperatureRecordedProbe.receiveMessage().requestId)

    }

    @Test
    fun testIgnoreWrongRegistrationRequests() {
        val deviceRegisteredProbe = testKit.createTestProbe(DeviceManager.DeviceRegistered::class.java)
        val groupActor = testKit.spawn(DeviceGroup.create("group"))
        groupActor.tell(DeviceManager.RequestTrackDevice("wrongGroup", "device1", deviceRegisteredProbe.ref))
        deviceRegisteredProbe.expectNoMessage()
    }

    @Test
    fun testListActiveDevices() {
        val registeredProbe = testKit.createTestProbe(DeviceManager.DeviceRegistered::class.java)
        val deviceGroupActor = testKit.spawn(DeviceGroup.create("group"))

        deviceGroupActor.tell(DeviceManager.RequestTrackDevice("group", "device1", registeredProbe.ref))
        registeredProbe.receiveMessage()

        deviceGroupActor.tell(DeviceManager.RequestTrackDevice("group", "device2", registeredProbe.ref))
        registeredProbe.receiveMessage()

        val deviceListProbe = testKit.createTestProbe(ReplyDeviceList::class.java)
        deviceGroupActor.tell(RequestDeviceList(0L, "group", deviceListProbe.ref))
        val reply = deviceListProbe.receiveMessage()

        assertEquals(0L, reply.requestId)
        assertEquals(setOf("device1", "device2"), reply.ids)
    }

    @Test
    fun testListActiveDevicesAfterOneShutDown() {
        val registeredProbe = testKit.createTestProbe(DeviceManager.DeviceRegistered::class.java)
        val deviceGroupActor = testKit.spawn(DeviceGroup.create("group"))

        deviceGroupActor.tell(DeviceManager.RequestTrackDevice("group", "device1", registeredProbe.ref))
        val registered1 = registeredProbe.receiveMessage()

        deviceGroupActor.tell(DeviceManager.RequestTrackDevice("group", "device2", registeredProbe.ref))
        val registered2 = registeredProbe.receiveMessage()

        val toShutDown = registered1.device

        val deviceListProbe = testKit.createTestProbe(ReplyDeviceList::class.java)
        deviceGroupActor.tell(RequestDeviceList(0L, "group", deviceListProbe.ref))
        val reply = deviceListProbe.receiveMessage()

        assertEquals(0L, reply.requestId)
        assertEquals(setOf("device1", "device2"), reply.ids)

        toShutDown.tell(Device.Passivate.INSTANCE)
        registeredProbe.expectTerminated(toShutDown, registeredProbe.remainingOrDefault)

        registeredProbe.awaitAssert {
            deviceGroupActor.tell(RequestDeviceList(1L, "group", deviceListProbe.ref))
            val replyDeviceList = deviceListProbe.receiveMessage()
            assertEquals(1L, replyDeviceList.requestId)
            assertEquals(setOf("device2"), replyDeviceList.ids)
        }
    }
}