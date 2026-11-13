package dev.sovereigns.world;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintTagUtils;
import com.ldtteam.structurize.management.Manager;
import com.ldtteam.structurize.operations.PlaceStructureOperation;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent;
import com.minecolonies.api.util.CreativeBuildingStructureHandler;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The only place that creates MineColonies colonies. Recipe and reasoning: docs/NOTES.md, "MineColonies 1.1.1368".
 */
public final class ColonySpawner {
    public static final String TOWN_HALL_BLUEPRINT = "fundamentals/townhall1.blueprint";
    /** Max depth filled under the footprint when the ground dips. */
    private static final int MAX_FILL_DEPTH = 12;
    private static final Logger LOGGER = LogUtils.getLogger();

    private ColonySpawner() {
    }

    /** Stable fake owner per province (docs/DECISIONS.md: one MineColonies owner = one colony). */
    public static UUID rulerUuid(String provinceId) {
        return UUID.nameUUIDFromBytes(("sovereigns:ruler:" + provinceId).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a colony owned by a fake ruler and pastes a level-1 town hall standing on {@code feet}'s ground.
     * Throws IllegalStateException with a user-readable reason when the colony can't be created.
     */
    public static IColony spawn(ServerLevel level, BlockPos feet, String provinceId, String colonyName, String rulerName, String pack) {
        if (StructurePacks.getStructurePack(pack) == null) {
            throw new IllegalStateException("Unknown style pack: " + pack);
        }
        IColonyManager manager = IColonyManager.getInstance();
        if (manager.getColonyByPosFromWorld(level, feet) != null || !manager.isFarEnoughFromColonies(level, feet)) {
            throw new IllegalStateException("Too close to another colony");
        }
        // shortcut: synchronous blueprint read on the server thread, fine for a debug command; go async for materialization (stage 2).
        Blueprint blueprint = StructurePacks.getBlueprint(pack, TOWN_HALL_BLUEPRINT, level.registryAccess());
        if (blueprint == null) {
            throw new IllegalStateException("No " + TOWN_HALL_BLUEPRINT + " in pack " + pack);
        }

        BlockPos offset = blueprint.getPrimaryBlockOffset();
        int groundTop = feet.getY() - 1;
        // Same rule as the build tool: anchor sits "groundlevel"-tag blocks above ground; untagged blueprints stand on it.
        BlockPos anchor = new BlockPos(feet.getX(), groundTop + BlueprintTagUtils.getGroundAnchorOffset(blueprint, offset.getY() + 1), feet.getZ());
        BlockPos origin = anchor.subtract(offset);
        levelGround(level, origin, blueprint, groundTop);

        FakePlayer ruler = FakePlayerFactory.get(level, new GameProfile(rulerUuid(provinceId), rulerName));
        IColony colony = manager.createColony(level, anchor, ruler, colonyName, pack);
        if (colony == null) {
            throw new IllegalStateException("MineColonies refused to create the colony, see log");
        }

        // Mirrors AbstractBlockHut.setup (creative build tool) with no player: anchor block, building, level, then paste.
        BlockState hut = ModBlocks.blockHutTownHall.defaultBlockState();
        level.setBlockAndUpdate(anchor, hut);
        ((AbstractBlockHut<?>) hut.getBlock()).onBlockPlacedByBuildTool(level, anchor, hut, null, null, RotationMirror.NONE, pack, TOWN_HALL_BLUEPRINT);
        IBuilding townHall = colony.getServerBuildingManager().getBuilding(anchor);
        if (townHall != null) {
            townHall.setStructurePack(pack);
            townHall.setBlueprintPath(TOWN_HALL_BLUEPRINT);
            townHall.setBuildingLevel(1);
            townHall.setRotationMirror(RotationMirror.NONE);
            townHall.onUpgradeComplete(blueprint, 1);
        }
        // Not CreativeBuildingStructureHandler.loadAndPlaceStructureWithRotation: with a Future it checks hasBluePrint()
        // before the future is read and silently queues nothing (only the hut block appears).
        Manager.addToQueue(new PlaceStructureOperation(new StructurePlacer(
                new CreativeBuildingStructureHandler(level, anchor, blueprint, RotationMirror.NONE, true)), null));

        LOGGER.info("Colony {} '{}': town hall anchor {}, footprint origin {} size {}x{}x{}", colony.getID(), colonyName,
                anchor, origin, blueprint.getSizeX(), blueprint.getSizeY(), blueprint.getSizeZ());
        // The GUI path posts this, the API path does not; addons listen to it.
        IMinecoloniesAPI.getInstance().getEventBus().post(new ColonyCreatedModEvent(colony));
        return colony;
    }

    /** Clears the footprint above the ground and fills dips under it, so the paste neither floats nor gets buried. */
    private static void levelGround(ServerLevel level, BlockPos origin, Blueprint blueprint, int groundTop) {
        // shortcut: whole footprint in one tick (~30x30 columns); split across ticks with materialization (stage 2).
        int top = Math.max(origin.getY() + blueprint.getSizeY(), groundTop + 1);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = 0; x < blueprint.getSizeX(); x++) {
            for (int z = 0; z < blueprint.getSizeZ(); z++) {
                int wx = origin.getX() + x, wz = origin.getZ() + z;
                for (int y = groundTop + 1; y < top; y++) {
                    level.setBlock(p.set(wx, y, wz), Blocks.AIR.defaultBlockState(), 2);
                }
                level.setBlock(p.set(wx, groundTop, wz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = groundTop - 1; y > groundTop - MAX_FILL_DEPTH; y--) {
                    if (!level.getBlockState(p.set(wx, y, wz)).canBeReplaced()) {
                        break;
                    }
                    level.setBlock(p, Blocks.DIRT.defaultBlockState(), 2);
                }
            }
        }
    }
}
