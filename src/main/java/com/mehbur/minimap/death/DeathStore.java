package com.mehbur.minimap.death;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The last death in one world or on one server, persisted as JSON.
 * Deaths arrive from two sources: the client sees its health reach zero at once, and the server reports the exact
 * position after respawning, which also covers deaths the client never saw, such as with instant respawn.
 */
public final class DeathStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("mehburs_minimap");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class Data {
		DeathPoint point;
		DeathPoint lastServerDeath;
		boolean markerRemoved;
	}

	private final Path path;
	private final Data data;
	/** The client saw a death that the server has not reported yet, so its report will be the same death. */
	private boolean awaitingServerReport;

	private DeathStore(Path path, Data data) {
		this.path = path;
		this.data = data;
	}

	/** Reads the file at {@code path}, starting empty when it is missing or unreadable. Never throws. */
	public static DeathStore load(Path path) {
		Data data = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				data = GSON.fromJson(reader, Data.class);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Could not read {}; forgetting the saved death", path, e);
			}
		}
		return new DeathStore(path, data == null ? new Data() : data);
	}

	/** The last death, or null if there has been none. */
	public DeathPoint point() {
		return data.point;
	}

	public boolean isMarkerRemoved() {
		return data.markerRemoved;
	}

	public void onClientDeath(DeathPoint point) {
		data.point = point;
		data.markerRemoved = false;
		awaitingServerReport = true;
		save();
	}

	/** Called every tick with what the server reports, which stays the same until the next death. */
	public void onServerDeath(DeathPoint point) {
		if (point.equals(data.lastServerDeath)) {
			return;
		}
		data.lastServerDeath = point;
		data.point = point;
		if (!awaitingServerReport) {
			data.markerRemoved = false;
		}
		awaitingServerReport = false;
		save();
	}

	/** Keeps the marker away until the next death. */
	public void removeMarker() {
		data.markerRemoved = true;
		save();
	}

	/** Writes through a temporary file so a crash mid-write cannot corrupt the existing one. */
	private void save() {
		try {
			Files.createDirectories(path.getParent());
			Path temp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8);
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.error("Could not save {}", path, e);
		}
	}
}
