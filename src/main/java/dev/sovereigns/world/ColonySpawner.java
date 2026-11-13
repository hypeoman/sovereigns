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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The only place that creates MineColonies colonies. Recipe and reasoning: docs/NOTES.md, "MineColonies 1.1.1368".
 */
public final class ColonySpawner {
    /** Max depth filled under the footprint when the ground dips. */
    private static final int MAX_FILL_DEPTH = 12;
    private static final Logger LOGGER = LogUtils.getLogger();

    private ColonySpawner() {
    }

    /** Stable fake owner per province (docs/DECISIONS.md: one MineColonies owner = one colony). */
    public static UUID rulerUuid(String provinceId) {
        return UUID.nameUUIDFromBytes(("sovereigns:ruler:" + provinceId).getBytes(StandardCharsets.UTF_8));
    }

    /** A hut to place: its MineColonies block and building level. */
    public record Hut(Block block, int level) {
        String label() {
            return ((AbstractBlockHut<?>) block).getBlueprintName() + " " + level;
        }
    }

    public record Spawned(IColony colony, List<String> placed, List<String> skipped) {
    }

    /** Stage 0.3 test town: worker huts, housing and a guard tower around the town hall. */
    public static List<Hut> debugTown() {
        return List.of(new Hut(ModBlocks.blockHutBuilder, 2), new Hut(ModBlocks.blockHutHome, 2), new Hut(ModBlocks.blockHutHome, 1),
                new Hut(ModBlocks.blockHutWareHouse, 1), new Hut(ModBlocks.blockHutDeliveryman, 1), new Hut(ModBlocks.blockHutFarmer, 1),
                new Hut(ModBlocks.blockHutLumberjack, 1), new Hut(ModBlocks.blockHutCook, 1), new Hut(ModBlocks.blockHutGuardTower, 2));
    }

    /**
     * Creates a colony owned by a fake ruler, with a town hall standing on {@code feet}'s ground and huts around it,
     * all pasted instantly in the given style. Huts the style has no blueprint for are skipped and reported.
     * Throws IllegalStateException with a user-readable reason when the colony can't be created.
     */
    public static Spawned spawn(ServerLevel level, BlockPos feet, String provinceId, String colonyName, String rulerName, String pack,
                                int hallLevel, List<Hut> huts) {
        if (StructurePacks.getStructurePack(pack) == null) {
            throw new IllegalStateException("Unknown style pack: " + pack);
        }
        IColonyManager manager = IColonyManager.getInstance();
        if (manager.getColonyByPosFromWorld(level, feet) != null || !manager.isFarEnoughFromColonies(level, feet)) {
            throw new IllegalStateException("Too close to another colony");
        }
        Hut hall = new Hut(ModBlocks.blockHutTownHall, hallLevel);
        // shortcut: synchronous blueprint reads on the server thread, fine for a debug command; go async for materialization (stage 2).
        String hallPath = HutBlueprints.path(pack, hall.block(), hallLevel, level.registryAccess());
        Blueprint hallPrint = hallPath == null ? null : StructurePacks.getBlueprint(pack, hallPath, true, level.registryAccess());
        if (hallPrint == null) {
            throw new IllegalStateException("No town hall " + hallLevel + " in pack " + pack);
        }

        List<Hut> found = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        List<Blueprint> prints = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Hut hut : huts) {
            String path = HutBlueprints.path(pack, hut.block(), hut.level(), level.registryAccess());
            Blueprint print = path == null ? null : StructurePacks.getBlueprint(pack, path, true, level.registryAccess());
            if (print == null) {
                skipped.add(hut.label());
                continue;
            }
            found.add(hut);
            paths.add(path);
            prints.add(print);
        }

        int groundTop = feet.getY() - 1;
        BlockPos hallOffset = hallPrint.getPrimaryBlockOffset();
        BlockPos hallOrigin = origin(hallPrint, feet.getX() - hallOffset.getX(), groundTop, feet.getZ() - hallOffset.getZ());
        List<TownLayout.Spot> spots = TownLayout.arrange(new TownLayout.Footprint(hallPrint.getSizeX(), hallPrint.getSizeZ()),
                prints.stream().map(b -> new TownLayout.Footprint(b.getSizeX(), b.getSizeZ())).toList());
        List<BlockPos> origins = new ArrayList<>();
        for (int i = 0; i < prints.size(); i++) {
            origins.add(origin(prints.get(i), hallOrigin.getX() + spots.get(i).x(), groundTop, hallOrigin.getZ() + spots.get(i).z()));
        }
        levelGround(level, hallOrigin, hallPrint, groundTop);
        for (int i = 0; i < prints.size(); i++) {
            levelGround(level, origins.get(i), prints.get(i), groundTop);
        }

        FakePlayer ruler = FakePlayerFactory.get(level, new GameProfile(rulerUuid(provinceId), rulerName));
        BlockPos hallAnchor = hallOrigin.offset(hallOffset);
        IColony colony = manager.createColony(level, hallAnchor, ruler, colonyName, pack);
        if (colony == null) {
            throw new IllegalStateException("MineColonies refused to create the colony, see log");
        }
        List<String> placed = new ArrayList<>();
        place(level, colony, pack, hall, hallPath, hallPrint, hallAnchor, placed);
        for (int i = 0; i < prints.size(); i++) {
            place(level, colony, pack, found.get(i), paths.get(i), prints.get(i), origins.get(i).offset(prints.get(i).getPrimaryBlockOffset()),
                    placed);
        }

        LOGGER.info("Colony {} '{}' ({}): town hall anchor {}, placed {}, skipped {}", colony.getID(), colonyName, pack, hallAnchor,
                placed, skipped);
        // The GUI path posts this, the API path does not; addons listen to it.
        IMinecoloniesAPI.getInstance().getEventBus().post(new ColonyCreatedModEvent(colony));
        return new Spawned(colony, placed, skipped);
    }

    /**
     * Footprint min corner for a blueprint standing on {@code groundTop}. MineColonies blueprints carry their own
     * underground layers (solid substitution); the top one must replace the ground surface. The "groundlevel" tag
     * gives their count; untagged ones (e.g. townhall1) are detected.
     */
    private static BlockPos origin(Blueprint blueprint, int x, int groundTop, int z) {
        int groundLevels = BlueprintTagUtils.getNumberOfGroundLevels(blueprint, undergroundLayers(blueprint));
        return new BlockPos(x, groundTop - groundLevels + 1, z);
    }

    /** Mirrors AbstractBlockHut.setup (creative build tool) with no player: anchor block, building, level, then paste. */
    private static void place(ServerLevel level, IColony colony, String pack, Hut hut, String path, Blueprint blueprint, BlockPos anchor,
                              List<String> placed) {
        BlockState state = hut.block().defaultBlockState();
        level.setBlockAndUpdate(anchor, state);
        ((AbstractBlockHut<?>) hut.block()).onBlockPlacedByBuildTool(level, anchor, state, null, null, RotationMirror.NONE, pack, path);
        IBuilding building = colony.getServerBuildingManager().getBuilding(anchor);
        if (building == null) {
            LOGGER.warn("Colony {}: {} at {} was not registered as a building", colony.getID(), hut.label(), anchor);
        } else {
            building.setStructurePack(pack);
            building.setBlueprintPath(path);
            building.setBuildingLevel(hut.level());
            building.setRotationMirror(RotationMirror.NONE);
            building.onUpgradeComplete(blueprint, hut.level());
            placed.add(hut.label());
        }
        // Not CreativeBuildingStructureHandler.loadAndPlaceStructureWithRotation: with a Future it checks hasBluePrint()
        // before the future is read and silently queues nothing (only the hut block appears).
        Manager.addToQueue(new PlaceStructureOperation(new StructurePlacer(
                new CreativeBuildingStructureHandler(level, anchor, blueprint, RotationMirror.NONE, true)), null));
    }

    /** Bottom layers without any air are underground; at least one so a blueprint with none still stands on the ground. */
    static int undergroundLayers(Blueprint blueprint) {
        for (int y = 0; y < blueprint.getSizeY(); y++) {
            for (int x = 0; x < blueprint.getSizeX(); x++) {
                for (int z = 0; z < blueprint.getSizeZ(); z++) {
                    if (blueprint.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        return Math.max(1, y);
                    }
                }
            }
        }
        return 1;
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
