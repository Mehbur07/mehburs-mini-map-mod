package com.mehbur.minimap.death;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeathStoreTest {
	private static final DeathPoint FIRST = new DeathPoint("minecraft:overworld", 10, 64, -20);
	private static final DeathPoint SECOND = new DeathPoint("minecraft:the_nether", -5, 40, 7);

	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("deaths").resolve("death.json");
	}

	@Test
	void startsEmpty() {
		DeathStore store = DeathStore.load(file());

		assertNull(store.point());
		assertFalse(store.isMarkerRemoved());
	}

	@Test
	void aNewDeathReplacesTheOldOne() {
		DeathStore store = DeathStore.load(file());
		store.onClientDeath(FIRST);
		store.onClientDeath(SECOND);

		assertEquals(SECOND, store.point());
	}

	@Test
	void survivesReloadWithTheRemovedMarker() {
		DeathStore store = DeathStore.load(file());
		store.onClientDeath(FIRST);
		store.removeMarker();

		DeathStore reloaded = DeathStore.load(file());

		assertEquals(FIRST, reloaded.point());
		assertTrue(reloaded.isMarkerRemoved());
	}

	@Test
	void aNewDeathBringsTheMarkerBack() {
		DeathStore store = DeathStore.load(file());
		store.onClientDeath(FIRST);
		store.removeMarker();
		store.onClientDeath(SECOND);

		assertFalse(store.isMarkerRemoved());
	}

	@Test
	void theServerReportCorrectsTheSameDeathWithoutBringingTheMarkerBack() {
		DeathStore store = DeathStore.load(file());
		store.onClientDeath(FIRST);
		store.removeMarker();

		store.onServerDeath(SECOND);

		assertEquals(SECOND, store.point());
		assertTrue(store.isMarkerRemoved());
	}

	@Test
	void aDeathOnlyTheServerKnowsCountsAsNew() {
		DeathStore store = DeathStore.load(file());
		store.onClientDeath(FIRST);
		store.onServerDeath(FIRST);
		store.removeMarker();

		store.onServerDeath(SECOND);

		assertEquals(SECOND, store.point());
		assertFalse(store.isMarkerRemoved());
	}

	@Test
	void theOldReportDoesNotOverwriteAFreshDeath() {
		DeathStore store = DeathStore.load(file());
		store.onServerDeath(FIRST);
		store.onClientDeath(SECOND);

		store.onServerDeath(FIRST);

		assertEquals(SECOND, store.point());
	}

	@Test
	void theSameReportAfterRejoiningChangesNothing() {
		DeathStore store = DeathStore.load(file());
		store.onServerDeath(FIRST);
		store.removeMarker();

		DeathStore reloaded = DeathStore.load(file());
		reloaded.onServerDeath(FIRST);

		assertTrue(reloaded.isMarkerRemoved());
	}

	@Test
	void aBrokenFileStartsEmpty() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "{not json");

		assertNull(DeathStore.load(file()).point());
	}
}
