package tv.projectivy.plugin.wallpaperprovider.api;

import tv.projectivy.plugin.wallpaperprovider.api.Event;
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper;

interface IWallpaperProviderService {
    List<Wallpaper> getWallpapers(in Event event);
    String getPreferences();
    void setPreferences(String params);
}
