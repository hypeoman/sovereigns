package dev.sovereigns.world;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * Fills every job slot of a colony with a new citizen, so an NPC colony works with no player to hire or supply it.
 * MineColonies itself only spawns initialCitizenAmount citizens (CitizenManager.onColonyTick), see docs/NOTES.md.
 */
public final class Settler {
    private Settler() {
    }

    /** Returns the number of citizens settled. */
    public static int settle(ServerLevel level, IColony colony, BlockPos spawn) {
        int settled = 0;
        for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
            for (IAssignsJob job : building.getModulesByType(IAssignsJob.class)) {
                while (!job.isFull()) {
                    ICitizenData citizen = colony.getCitizenManager().createAndRegisterCivilianData();
                    citizen.setGenderAndGenerateName(level.random.nextBoolean());
                    starterKit(citizen);
                    colony.getCitizenManager().spawnOrCreateCivilian(citizen, level, List.of(spawn), true);
                    if (!job.assignCitizen(citizen)) {
                        break; // the slot refused (e.g. a research-locked job); the citizen stays jobless
                    }
                    IMinecoloniesAPI.getInstance().getEventBus().post(new CitizenAddedModEvent(citizen, CitizenAddedModEvent.CitizenAddedSource.COMMANDS));
                    settled++;
                }
            }
        }
        return settled;
    }

    // shortcut: the same stone kit and bread for every job (no player brings tools); per-job kits from the sim economy in stage 2.
    private static void starterKit(ICitizenData citizen) {
        for (ItemStack stack : List.of(new ItemStack(Items.STONE_AXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SHOVEL),
                new ItemStack(Items.STONE_HOE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 32),
                new ItemStack(Items.BREAD, 16))) {
            ItemHandlerHelper.insertItem(citizen.getInventory(), stack, false);
        }
    }
}
