package net.conczin.mca.expansion.townhall;

import java.util.Objects;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.nbt.CompoundTag;
import net.conczin.mca.client.gui.ResidentsScreen;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.TownHallJobsScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.VillageFinder;
import net.conczin.mca.expansion.economy.EconomyFeature;
import net.conczin.mca.expansion.minds.VillagerAttributes;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The town hall's panels: the overview (leader, chief, every tax) and the jobs panel, where the chief puts villagers
 * on job sites. Only a chief (mayor or above) may change anything; the server checks.
 */
public final class TownHallMessages {
    /** The lowest rank that counts as the village chief. */
    public static final Rank CHIEF = Rank.MAYOR;

    private TownHallMessages() {
    }

    /** A resident: its job, how well it fits it, and how well it would fit each profession of the village's job sites. */
    public record Resident(UUID id, String name, String profession, float aptitude, List<Float> fits, boolean pinned, boolean leader) {
        static final StreamCodec<FriendlyByteBuf, Resident> CODEC = StreamCodec.of((buf, r) -> {
            UUIDUtil.STREAM_CODEC.encode(buf, r.id);
            buf.writeUtf(r.name);
            buf.writeUtf(r.profession);
            buf.writeFloat(r.aptitude);
            buf.writeVarInt(r.fits.size());
            r.fits.forEach(buf::writeFloat);
            buf.writeBoolean(r.pinned);
            buf.writeBoolean(r.leader);
        }, buf -> {
            UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
            String name = buf.readUtf();
            String profession = buf.readUtf();
            float aptitude = buf.readFloat();
            int count = buf.readVarInt();
            List<Float> fits = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                fits.add(buf.readFloat());
            }
            return new Resident(id, name, profession, aptitude, fits, buf.readBoolean(), buf.readBoolean());
        });
    }

    /** A job site: where, which of the response's professions (index), and who works there (empty when free). */
    public record Site(BlockPos pos, int profession, String worker) {
        static final StreamCodec<FriendlyByteBuf, Site> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Site::pos,
                ByteBufCodecs.VAR_INT, Site::profession,
                ByteBufCodecs.STRING_UTF8, Site::worker,
                Site::new
        );
    }

    /** The taxes of the village: rate, how they are taken and what a season brings. */
    public record Taxes(float rate, boolean fromProduction, boolean hasStorage, float perSeason, int season, int nextIn,
                        boolean library, float births, float marriages) {
        static final StreamCodec<FriendlyByteBuf, Taxes> CODEC = StreamCodec.of((buf, t) -> {
            buf.writeFloat(t.rate);
            buf.writeBoolean(t.fromProduction);
            buf.writeBoolean(t.hasStorage);
            buf.writeFloat(t.perSeason);
            buf.writeVarInt(t.season);
            buf.writeVarInt(t.nextIn);
            buf.writeBoolean(t.library);
            buf.writeFloat(t.births);
            buf.writeFloat(t.marriages);
        }, buf -> new Taxes(buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readFloat()));
    }

    /** Client to server: the panels' data for the village the player is in. */
    public record Request() implements HandleablePayload {
        public static final CustomPacketPayload.Type<Request> TYPE = new CustomPacketPayload.Type<>(MCA.locate("town_hall_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public void handleServer(ServerPlayer player) {
            respond(player);
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return TYPE;
        }
    }

    /** Server to client: everything the panels show. */
    /**
     * Server to client: everything the panels show. {@code looks} holds, per resident loaded on the server, what it
     * takes to draw it (its saved data without brain, memories and inventory), keyed by its id.
     */
    public record Response(String village, int population, String leader, boolean chief, String rank, Taxes taxes,
                           List<String> professions, List<Resident> residents, List<Site> sites, CompoundTag looks) implements HandleablePayload {
        public static final CustomPacketPayload.Type<Response> TYPE = new CustomPacketPayload.Type<>(MCA.locate("town_hall_response"));
        public static final StreamCodec<FriendlyByteBuf, Response> STREAM_CODEC = StreamCodec.of((buf, r) -> {
            buf.writeUtf(r.village);
            buf.writeVarInt(r.population);
            buf.writeUtf(r.leader);
            buf.writeBoolean(r.chief);
            buf.writeUtf(r.rank);
            Taxes.CODEC.encode(buf, r.taxes);
            buf.writeCollection(r.professions, FriendlyByteBuf::writeUtf);
            buf.writeCollection(r.residents, (b, resident) -> Resident.CODEC.encode((FriendlyByteBuf) b, resident));
            buf.writeCollection(r.sites, (b, site) -> Site.CODEC.encode((FriendlyByteBuf) b, site));
            buf.writeNbt(r.looks);
        }, buf -> new Response(buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readUtf(), Taxes.CODEC.decode(buf),
                buf.readList(FriendlyByteBuf::readUtf),
                buf.readList(b -> Resident.CODEC.decode((FriendlyByteBuf) b)),
                buf.readList(b -> Site.CODEC.decode((FriendlyByteBuf) b)),
                Objects.requireNonNullElseGet(buf.readNbt(), CompoundTag::new)));

        @Override
        public void handle(Player player) {
            TownHallJobsScreen.receive(this);
            ResidentsScreen.receive(this);
        }

        @Override
        public CustomPacketPayload.Type<Response> type() {
            return TYPE;
        }
    }

    /** Client to server: put a villager on a job site, or with no site let the leader decide its job again. */
    public record Assign(UUID villager, Optional<BlockPos> site) implements HandleablePayload {
        public static final CustomPacketPayload.Type<Assign> TYPE = new CustomPacketPayload.Type<>(MCA.locate("town_hall_assign"));
        public static final StreamCodec<FriendlyByteBuf, Assign> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Assign::villager,
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Assign::site,
                Assign::new
        );

        @Override
        public void handleServer(ServerPlayer player) {
            Optional<Village> village = VillageFinder.forPlayer(player);
            if (village.isEmpty()) {
                return;
            }
            if (!Tasks.getRank(village.get(), player).isAtLeast(CHIEF)) {
                player.sendSystemMessage(Component.translatable("town_hall.not_chief"));
                return;
            }
            if (!(player.level().getEntity(villager) instanceof VillagerEntityMCA target)
                || village.get().getResidentsUUIDs().noneMatch(villager::equals)) {
                return;
            }
            TownHallData data = TownHallData.get(player.level());
            if (site.isEmpty()) {
                data.setPinned(village.get().getId(), villager, false);
            } else if (village.get().isWithinBorder(site.get(), Village.BORDER_MARGIN) && JobAssignments.assign(player.level(), target, site.get())) {
                data.setPinned(village.get().getId(), villager, true);
            } else {
                player.sendSystemMessage(Component.translatable("town_hall.jobs.taken"));
            }
            respond(player);
        }

        @Override
        public CustomPacketPayload.Type<Assign> type() {
            return TYPE;
        }
    }

    static void respond(ServerPlayer player) {
        Optional<Village> found = VillageFinder.forPlayer(player);
        if (found.isEmpty()) {
            return;
        }
        Village village = found.get();
        TownHallData data = TownHallData.get(player.level());
        Rank rank = Tasks.getRank(village, player);
        List<VillagerEntityMCA> residents = village.getResidents(player.level());
        List<JobAssignments.Site> sites = JobAssignments.sites(player.level(), village, residents);

        List<VillagerProfession> professions = sites.stream().map(JobAssignments.Site::profession).distinct().toList();
        List<String> professionIds = professions.stream().map(p -> String.valueOf(BuiltInRegistries.VILLAGER_PROFESSION.getKey(p))).toList();
        UUID leader = data.leader(village.getId()).orElse(null);
        boolean attributes = VillagerAttributes.enabled();
        List<Resident> residentInfo = residents.stream()
                .filter(v -> v.getAgeState().isAdult())
                .map(v -> {
                    boolean rolled = attributes && VillagerAttributes.isRolled(v);
                    List<Float> fits = professions.stream().map(p -> rolled ? VillagerAttributes.aptitude(v, p) : -1.0F).toList();
                    return new Resident(v.getUUID(), v.getName().getString(),
                            String.valueOf(BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getProfession())),
                            rolled ? VillagerAttributes.aptitude(v, v.getProfession()) : -1.0F, fits,
                            data.isPinned(village.getId(), v.getUUID()), v.getUUID().equals(leader));
                })
                .toList();
        List<Site> siteInfo = sites.stream()
                .map(site -> new Site(site.pos(), professions.indexOf(site.profession()),
                        site.worker().map(w -> w.getName().getString()).orElse("")))
                .toList();

        int season = Config.getInstance().taxSeason;
        long time = player.level().getGameTime() + village.getId();
        boolean library = village.hasBuilding("library");
        float perSeason = (float) (Config.getInstance().taxesFactor * village.getPopulation() * village.getTaxes() * (library ? 1.5 : 1.0));
        Taxes taxes = new Taxes(village.getTaxes(), EconomyFeature.enabled(), village.hasBuilding("storage"), perSeason, season,
                (int) (season - Math.floorMod(time, season)), library, village.getPopulationThreshold(), village.getMarriageThreshold());

        CompoundTag looks = new CompoundTag();
        for (VillagerEntityMCA resident : residents) {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
            resident.saveWithoutId(output);
            CompoundTag nbt = output.buildResult();
            nbt.remove("Brain");
            nbt.remove("Memories");
            nbt.remove("Inventory");
            looks.put(resident.getUUID().toString(), nbt);
        }

        Network.sendToPlayer(new Response(village.getName(), village.getPopulation(), data.leaderName(village.getId()),
                rank.isAtLeast(CHIEF), rank.name().toLowerCase(), taxes, professionIds, residentInfo, siteInfo, looks), player);
    }
}
