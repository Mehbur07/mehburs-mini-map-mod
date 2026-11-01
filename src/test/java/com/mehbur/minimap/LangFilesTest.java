package com.mehbur.minimap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LangFilesTest {
	private static Set<String> keys(String language) throws IOException {
		try (InputStream stream = LangFilesTest.class.getResourceAsStream("/assets/mehburs_minimap/lang/" + language + ".json");
			InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			return new Gson().fromJson(reader, Map.class).keySet();
		}
	}

	@Test
	void everyLanguageHasTheSameKeys() throws IOException {
		assertEquals(keys("en_us"), keys("tr_tr"));
	}
}
