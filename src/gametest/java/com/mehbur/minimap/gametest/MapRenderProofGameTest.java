package com.mehbur.minimap.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

public class MapRenderProofGameTest implements FabricClientGameTest {
	private static final int QUARTER_TURN_TICKS = 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("map-proof-a");
			context.waitTicks(QUARTER_TURN_TICKS);
			context.takeScreenshot("map-proof-b");
		}
	}
}
