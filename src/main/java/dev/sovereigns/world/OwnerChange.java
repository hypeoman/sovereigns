package dev.sovereigns.world;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Hands a colony between a player and a fake ruler (player defeat, docs/DECISIONS.md #8). MineColonies' setOwner drops
 * the previous owner from the colony entirely, so they end up neutral: no management rights left.
 */
public final class OwnerChange {
    private OwnerChange() {
    }

    public static void toRuler(ServerLevel level, IColony colony, String provinceId, String rulerName) {
        setOwner(colony, FakePlayerFactory.get(level, new GameProfile(ColonySpawner.rulerUuid(provinceId), rulerName)));
    }

    public static void toPlayer(IColony colony, Player player) {
        setOwner(colony, player);
    }

    private static void setOwner(IColony colony, Player owner) {
        IPermissions permissions = colony.getPermissions();
        permissions.setOwner(owner);
        // Without this, clients keep showing the old rights until they reconnect.
        colony.getPackageManager().sendPermissionsPackets();
    }
}
