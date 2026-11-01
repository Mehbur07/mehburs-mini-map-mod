package com.mehbur.minimap.config;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/** Stores RGB colors as readable {@code #RRGGBB} strings. */
final class HexColorAdapter extends TypeAdapter<Integer> {
	@Override
	public void write(JsonWriter out, Integer color) throws IOException {
		out.value(String.format("#%06X", color & 0xFFFFFF));
	}

	@Override
	public Integer read(JsonReader in) throws IOException {
		String value = in.nextString();
		if (!value.matches("#[0-9a-fA-F]{6}")) {
			throw new JsonParseException("Invalid color: " + value);
		}
		return Integer.parseInt(value.substring(1), 16);
	}
}
