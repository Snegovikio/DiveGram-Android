package org.telegram.ui;

public interface MainTabsActivityController {
    void setTabsVisible(boolean visible);
    void setStoriesMenu(boolean hasStories, float expansionProgress);
    void setSearchShown(boolean shown);
    void openSideMenu();
    void closeSideMenu();
    boolean isSideMenuOpen();
}
