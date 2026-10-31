package net.conczin.mca.client.gui;

import net.conczin.mca.client.gui.workorder.WorkPlanningScreen;
import net.conczin.mca.expansion.progression.ProgressPage;
import net.conczin.mca.expansion.progression.VillageGrowth;
import net.conczin.mca.server.world.data.workorder.WorkOrderPermissions;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * The village's management panel, opened from the City Records of the town hall: a radial menu (see
 * assets/mca/api/gui/radial_town_hall.json). The overview (leader, taxes) and jobs have panels of their own, the other
 * sections open the matching page of the blueprint.
 */
public class TownHallScreen extends AbstractDynamicScreen {
    private static final String LAYOUT = "radial_town_hall";
    /** Button of the panel, without "gui.button.town_hall.", to the blueprint page it opens. */
    private static final Map<String, String> PAGES = Map.of(
            "map", "map",
            "rules", "rules",
            "progress", ProgressPage.PAGE,
            "catalog", "catalog",
            "rank", "rank"
    );

    public TownHallScreen() {
        super(Component.translatable("gui.town_hall.title"));
    }

    @Override
    protected void init() {
        setLayout(LAYOUT);
    }

    @Override
    protected boolean useRadialLayout() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected boolean isButtonHidden(MCAButton button) {
        return switch (section(button)) {
            case "progress" -> !VillageGrowth.enabled();
            case "works" -> !WorkOrderPermissions.enabled();
            default -> false;
        };
    }

    @Override
    protected void buttonPressed(MCAButton button) {
        String section = section(button);
        if (button.identifier().equals("gui.button.backarrow")) {
            onClose();
        } else if (section.equals("overview")) {
            minecraft.gui.setScreen(new TownHallJobsScreen(TownHallJobsScreen.Mode.OVERVIEW));
        } else if (section.equals("residents")) {
            minecraft.gui.setScreen(new ResidentsScreen());
        } else if (section.equals("jobs")) {
            minecraft.gui.setScreen(new TownHallJobsScreen(TownHallJobsScreen.Mode.JOBS));
        } else if (section.equals("works")) {
            minecraft.gui.setScreen(new WorkPlanningScreen());
        } else if (PAGES.containsKey(section)) {
            minecraft.gui.setScreen(new BlueprintScreen(PAGES.get(section)));
        }
    }

    private static String section(MCAButton button) {
        String prefix = "gui.button.town_hall.";
        return button.identifier().startsWith(prefix) ? button.identifier().substring(prefix.length()) : "";
    }
}
