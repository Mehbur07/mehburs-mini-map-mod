package com.mehbur.minimap.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaypointStoreTest {
	private static final Waypoint HOME = new Waypoint("minecraft:overworld", 10, 64, -20);
	private static final Waypoint FORTRESS = new Waypoint("minecraft:the_nether", -5, 40, 7);

	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("waypoints").resolve("server.json");
	}

	private void writeFile(String json) throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), json);
	}

	@Test
	void startsEmptyWithSevenSlots() {
		WaypointStore store = WaypointStore.load(file());

		for (int slot = 0; slot < 7; slot++) {
			assertNull(store.get(slot));
		}
		assertEquals(WaypointStore.NO_SLOT, store.active());
	}

	@Test
	void survivesReloadWithEmptySlotsInPlace() {
		WaypointStore store = WaypointStore.load(file());
		store.set(0, HOME);
		store.set(6, FORTRESS);
		store.setActive(6);

		WaypointStore reloaded = WaypointStore.load(file());

		assertEquals(HOME, reloaded.get(0));
		assertNull(reloaded.get(3));
		assertEquals(FORTRESS, reloaded.get(6));
		assertEquals(6, reloaded.active());
	}

	@Test
	void savingReplacesTheSlot() {
		WaypointStore store = WaypointStore.load(file());
		store.set(2, HOME);
		store.set(2, FORTRESS);

		assertEquals(FORTRESS, store.get(2));
	}

	@Test
	void clearingTheTargetEndsTheNavigation() {
		WaypointStore store = WaypointStore.load(file());
		store.set(2, HOME);
		store.setActive(2);

		store.clear(2);

		assertNull(store.get(2));
		assertEquals(WaypointStore.NO_SLOT, store.active());
	}

	@Test
	void clearingAnotherSlotKeepsTheNavigation() {
		WaypointStore store = WaypointStore.load(file());
		store.set(1, HOME);
		store.set(2, FORTRESS);
		store.setActive(2);

		store.clear(1);

		assertEquals(2, store.active());
	}

	@Test
	void aBrokenFileStartsEmpty() throws IOException {
		writeFile("{not json");

		assertNull(WaypointStore.load(file()).get(0));
	}

	@Test
	void aFileWithTooFewSlotsStillHasSeven() throws IOException {
		writeFile("{\"slots\":[{\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":2,\"z\":3}]}");

		WaypointStore store = WaypointStore.load(file());

		assertEquals(new Waypoint("minecraft:overworld", 1, 2, 3), store.get(0));
		assertNull(store.get(6));
	}

	@Test
	void aFileWithTooManySlotsKeepsTheFirstSeven() throws IOException {
		writeFile("{\"slots\":[null,null,null,null,null,null,null,{\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":2,\"z\":3}],\"active\":7}");

		WaypointStore store = WaypointStore.load(file());

		assertNull(store.get(6));
		assertEquals(WaypointStore.NO_SLOT, store.active());
	}

	@Test
	void aTargetThatIsNotSavedIsDropped() throws IOException {
		writeFile("{\"slots\":[null],\"active\":0}");

		assertEquals(WaypointStore.NO_SLOT, WaypointStore.load(file()).active());
	}

	@Test
	void aLocationWithoutDimensionIsDropped() throws IOException {
		writeFile("{\"slots\":[{\"x\":1,\"y\":2,\"z\":3}],\"active\":0}");

		WaypointStore store = WaypointStore.load(file());

		assertNull(store.get(0));
		assertEquals(WaypointStore.NO_SLOT, store.active());
	}
}
