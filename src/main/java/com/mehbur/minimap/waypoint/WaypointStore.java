package com.mehbur.minimap.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The saved locations of one world or one server, and which of them is being navigated to, persisted as JSON. */
public final class WaypointStore {
	public static final int SLOTS = 7;
	public static final int NO_SLOT = -1;

	private static final Logger LOGGER = LoggerFactory.getLogger("mehburs_minimap");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class Data {
		Waypoint[] slots = new Waypoint[SLOTS];
		int active = NO_SLOT;
	}

	private final Path path;
	private final Data data;

	private WaypointStore(Path path, Data data) {
		this.path = path;
		this.data = data;
	}

	/** Reads the file at {@code path}, starting empty when it is missing or unreadable. Never throws. */
	public static WaypointStore load(Path path) {
		Data data = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				data = GSON.fromJson(reader, Data.class);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Could not read {}; starting without saved locations", path, e);
			}
		}
		if (data == null) {
			data = new Data();
		}
		sanitize(data);
		return new WaypointStore(path, data);
	}

	private static void sanitize(Data data) {
		data.slots = data.slots == null ? new Waypoint[SLOTS] : Arrays.copyOf(data.slots, SLOTS);
		for (int slot = 0; slot < SLOTS; slot++) {
			if (data.slots[slot] != null && data.slots[slot].dimension() == null) {
				data.slots[slot] = null;
			}
		}
		if (data.active < 0 || data.active >= SLOTS || data.slots[data.active] == null) {
			data.active = NO_SLOT;
		}
	}

	/** The location in the slot, or null if it is empty. */
	public Waypoint get(int slot) {
		return data.slots[slot];
	}

	public void set(int slot, Waypoint waypoint) {
		data.slots[slot] = waypoint;
		save();
	}

	/** Also ends the navigation if it was leading there. */
	public void clear(int slot) {
		data.slots[slot] = null;
		if (data.active == slot) {
			data.active = NO_SLOT;
		}
		save();
	}

	/** The slot being navigated to, or {@link #NO_SLOT}. */
	public int active() {
		return data.active;
	}

	public void setActive(int slot) {
		data.active = slot;
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
