package org.gtlcore.gtlcore.integration.ae2.wireless;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class WirelessAeSavedData extends SavedData {
    private static final String DATA_NAME = "gtlcore_wireless_ae_networks";
    private static final String TAG_NETWORKS = "networks";
    private static final String TAG_FREQUENCY = "frequency";
    private static final String TAG_CORE = "core";
    private static final String TAG_NAME = "name";
    private static final String TAG_MEMBERS = "members";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";
    private static final String TAG_SIDE = "side";

    private final Map<UUID, NetworkRecord> networks = new HashMap<>();

    public static WirelessAeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                WirelessAeSavedData::load,
                WirelessAeSavedData::new,
                DATA_NAME
        );
    }

    public static WirelessAeSavedData load(CompoundTag tag) {
        WirelessAeSavedData data = new WirelessAeSavedData();
        ListTag networksTag = tag.getList(TAG_NETWORKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < networksTag.size(); i++) {
            CompoundTag networkTag = networksTag.getCompound(i);
            if (!networkTag.hasUUID(TAG_FREQUENCY)) {
                continue;
            }

            UUID frequency = networkTag.getUUID(TAG_FREQUENCY);
            NetworkRecord record = data.network(frequency);
            record.core = readGlobalPos(networkTag.getCompound(TAG_CORE));
            record.name = networkTag.getString(TAG_NAME);

            ListTag membersTag = networkTag.getList(TAG_MEMBERS, Tag.TAG_COMPOUND);
            for (int j = 0; j < membersTag.size(); j++) {
                MemberKey member = readMemberKey(membersTag.getCompound(j));
                if (member != null) {
                    record.members.add(member);
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag networksTag = new ListTag();
        for (Map.Entry<UUID, NetworkRecord> entry : this.networks.entrySet()) {
            NetworkRecord record = entry.getValue();
            if (record.core == null) {
                continue;
            }

            CompoundTag networkTag = new CompoundTag();
            networkTag.putUUID(TAG_FREQUENCY, entry.getKey());
            networkTag.put(TAG_CORE, writeGlobalPos(record.core));
            networkTag.putString(TAG_NAME, record.name);

            ListTag membersTag = new ListTag();
            for (MemberKey member : record.members) {
                membersTag.add(writeMemberKey(member));
            }
            networkTag.put(TAG_MEMBERS, membersTag);
            networksTag.add(networkTag);
        }
        tag.put(TAG_NETWORKS, networksTag);
        return tag;
    }

    public void setCore(UUID frequency, GlobalPos core) {
        NetworkRecord record = network(frequency);
        if (!core.equals(record.core)) {
            record.core = core;
            setDirty();
        }
    }

    public GlobalPos getCore(UUID frequency) {
        NetworkRecord record = this.networks.get(frequency);
        return record == null ? null : record.core;
    }

    public void setNetworkName(UUID frequency, String name) {
        NetworkRecord record = network(frequency);
        String sanitized = sanitizeName(name, frequency);
        if (!sanitized.equals(record.name)) {
            record.name = sanitized;
            setDirty();
        }
    }

    public String getNetworkName(UUID frequency) {
        NetworkRecord record = this.networks.get(frequency);
        return record == null ? defaultName(frequency) : displayName(frequency, record.name);
    }

    public List<NetworkInfo> getNetworkInfo() {
        List<NetworkInfo> networks = new ArrayList<>();
        for (Map.Entry<UUID, NetworkRecord> entry : this.networks.entrySet()) {
            NetworkRecord record = entry.getValue();
            if (record.core != null) {
                networks.add(new NetworkInfo(entry.getKey(), displayName(entry.getKey(), record.name), record.core));
            }
        }
        networks.sort(Comparator.comparing(NetworkInfo::name, String.CASE_INSENSITIVE_ORDER));
        return networks;
    }

    public UUID getMemberNetwork(GlobalPos member) {
        return getMemberNetwork(new MemberKey(member, null));
    }

    public UUID getMemberNetwork(MemberKey member) {
        for (Map.Entry<UUID, NetworkRecord> entry : this.networks.entrySet()) {
            if (entry.getValue().members.contains(member)) {
                return entry.getKey();
            }
        }
        if (member.side() != null) {
            MemberKey blockMember = new MemberKey(member.pos(), null);
            for (Map.Entry<UUID, NetworkRecord> entry : this.networks.entrySet()) {
                if (entry.getValue().members.contains(blockMember)) {
                    return entry.getKey();
                }
            }
        } else {
            for (Map.Entry<UUID, NetworkRecord> entry : this.networks.entrySet()) {
                for (MemberKey candidate : entry.getValue().members) {
                    if (candidate.pos().equals(member.pos())) {
                        return entry.getKey();
                    }
                }
            }
        }
        return null;
    }

    public Collection<UUID> getFrequencies() {
        return new HashSet<>(this.networks.keySet());
    }

    public Set<MemberKey> getMembers(UUID frequency) {
        NetworkRecord record = this.networks.get(frequency);
        if (record == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(record.members);
    }

    public boolean addMember(UUID frequency, GlobalPos member) {
        return addMember(frequency, new MemberKey(member, null));
    }

    public boolean addMember(UUID frequency, MemberKey member) {
        NetworkRecord record = network(frequency);
        boolean added = record.members.add(member);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean removeMember(UUID frequency, GlobalPos member) {
        return removeMember(frequency, new MemberKey(member, null));
    }

    public boolean removeMember(UUID frequency, MemberKey member) {
        NetworkRecord record = this.networks.get(frequency);
        if (record == null) {
            return false;
        }
        boolean removed = record.members.remove(member);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public Set<UUID> removeMember(GlobalPos member) {
        return removeMembersAt(member);
    }

    public Set<UUID> removeMember(MemberKey member) {
        Set<UUID> removedFrom = new HashSet<>();
        for (Map.Entry<UUID, NetworkRecord> entry : this.networks.entrySet()) {
            if (entry.getValue().members.remove(member)) {
                removedFrom.add(entry.getKey());
            }
        }
        if (!removedFrom.isEmpty()) {
            setDirty();
        }
        return removedFrom;
    }

    public Set<UUID> removeMembersAt(GlobalPos member) {
        Set<UUID> removedFrom = new HashSet<>();
        for (Map.Entry<UUID, NetworkRecord> entry : this.networks.entrySet()) {
            boolean removed = entry.getValue().members.removeIf(candidate -> candidate.pos().equals(member));
            if (removed) {
                removedFrom.add(entry.getKey());
            }
        }
        if (!removedFrom.isEmpty()) {
            setDirty();
        }
        return removedFrom;
    }

    public void removeNetwork(UUID frequency) {
        if (this.networks.remove(frequency) != null) {
            setDirty();
        }
    }

    private NetworkRecord network(UUID frequency) {
        return this.networks.computeIfAbsent(frequency, ignored -> new NetworkRecord());
    }

    private static String sanitizeName(String name, UUID frequency) {
        String sanitized = name == null ? "" : name.trim();
        if (sanitized.isEmpty()) {
            return defaultName(frequency);
        }
        return sanitized.length() > 32 ? sanitized.substring(0, 32) : sanitized;
    }

    private static String displayName(UUID frequency, String name) {
        return sanitizeName(name, frequency);
    }

    private static String defaultName(UUID frequency) {
        return "Wireless " + frequency.toString().substring(0, 8);
    }

    private static CompoundTag writeGlobalPos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION, pos.dimension().location().toString());
        tag.putInt(TAG_X, pos.pos().getX());
        tag.putInt(TAG_Y, pos.pos().getY());
        tag.putInt(TAG_Z, pos.pos().getZ());
        return tag;
    }

    private static GlobalPos readGlobalPos(CompoundTag tag) {
        if (!tag.contains(TAG_DIMENSION)) {
            return null;
        }

        try {
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION,
                    new ResourceLocation(tag.getString(TAG_DIMENSION))
            );
            BlockPos pos = new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
            return GlobalPos.of(dimension, pos);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static CompoundTag writeMemberKey(MemberKey member) {
        CompoundTag tag = writeGlobalPos(member.pos());
        if (member.side() != null) {
            tag.putString(TAG_SIDE, member.side().getName());
        }
        return tag;
    }

    private static MemberKey readMemberKey(CompoundTag tag) {
        GlobalPos pos = readGlobalPos(tag);
        if (pos == null) {
            return null;
        }

        Direction side = null;
        if (tag.contains(TAG_SIDE)) {
            side = Direction.byName(tag.getString(TAG_SIDE));
        }
        return new MemberKey(pos, side);
    }

    private static final class NetworkRecord {
        private GlobalPos core;
        private String name = "";
        private final Set<MemberKey> members = new HashSet<>();
    }

    public record NetworkInfo(UUID frequency, String name, GlobalPos core) {
    }

    public record MemberKey(GlobalPos pos, Direction side) {
        public static MemberKey of(ResourceKey<Level> dimension, BlockPos pos, Direction side) {
            return new MemberKey(GlobalPos.of(dimension, pos), side);
        }

        public BlockPos blockPos() {
            return this.pos.pos();
        }
    }
}
