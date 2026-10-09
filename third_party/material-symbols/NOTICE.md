# Google Material Symbols

The `ms_*.xml` Android drawables in `app/src/main/res/drawable/` are derived from
**Material Symbols Outlined** by Google.

- Upstream: https://github.com/google/material-design-icons
- Browse: https://fonts.google.com/icons
- License: Apache License 2.0 (unmodified upstream license in [LICENSE](LICENSE))
- Retrieved: 2026-09-23
- Style: Outlined, optical size 24, weight 400, grade 0, fill 0.

## Conversion

For SVG-derived resources, path data is copied verbatim from the official 24px SVG files. Android
VectorDrawable has no negative-origin viewBox, so each resource preserves the
original 960 by 960 viewport and adds `translateY="960"` to represent the original
SVG `viewBox="0 -960 960 960"`. There is no path redrawing, non-uniform scaling,
or stroke-width adjustment. Intrinsic display size is 24dp; Compose applies the
current content tint.

## Sources

### Grace button suggestions added 2026-10-09

- `ms_format_list_bulleted.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/format_list_bulleted/materialsymbolsoutlined/format_list_bulleted_24px.svg
- `ms_power_settings_new.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/power_settings_new/materialsymbolsoutlined/power_settings_new_24px.svg
- `ms_bolt.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/bolt/materialsymbolsoutlined/bolt_24px.svg

Official SVG paths retained using the conversion above. Same Apache-2.0 license.

### Productivity icons added 2026-10-08

- `ms_widgets.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/widgets/materialsymbolsoutlined/widgets_24px.xml
- `ms_notifications.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/notifications/materialsymbolsoutlined/notifications_24px.xml
- `ms_visibility_off.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/visibility_off/materialsymbolsoutlined/visibility_off_24px.xml
- `ms_gesture.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/gesture/materialsymbolsoutlined/gesture_24px.xml
- `ms_vibration.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/vibration/materialsymbolsoutlined/vibration_24px.xml

Official Android vectors; paths and 960px viewports retained, tint supplied by Compose. Same Apache-2.0 license.

### Work profile icon added 2026-10-08

- `ms_work.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/work/materialsymbolsoutlined/work_24px.xml
  (Official Android vector; path and 960px viewport retained, tint supplied by Compose. Same Apache-2.0 license.)

### Popup icon added 2026-10-07

- `ms_outbound.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/outbound/materialsymbolsoutlined/outbound_24px.xml
  (Official Android vector; path and 960px viewport retained, tint supplied by Compose. Same Apache-2.0 license.)

### Recently installed folder icon added 2026-10-06

- `ms_history_2.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/history_2/materialsymbolsoutlined/history_2_24px.xml
  (Official Android vector; path and 960px viewport retained, tint supplied by Compose. Same Apache-2.0 license.)

### Weather icons added 2026-09-27

These resources use the official Android vectors at revision
[`bd8cb85bd4bad964fe6918f79665bb40c3a8efef`](https://github.com/google/material-design-icons/tree/bd8cb85bd4bad964fe6918f79665bb40c3a8efef).
They are Material Symbols Outlined, optical size 24, weight 400, grade 0,
fill 0, under the same Apache-2.0 license included above. Google's [Material
Symbols guide](https://developers.google.com/fonts/docs/material_symbols#licensing)
also documents the license.

The official Android `pathData`, 24dp dimensions and 960px viewport are
unchanged. The root `android:tint="?attr/colorControlNormal"` is removed and
the path fill is changed from white to black; Compose supplies the final
color. The home row uses its wallpaper-aware text color, while the agenda
uses `MaterialTheme.colorScheme.onSurface`. No icons are downloaded at runtime.

| Forecast | Resource / official Android source |
| --- | --- |
| Clear, day | [`ms_sunny.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/sunny/materialsymbolsoutlined/sunny_24px.xml) |
| Clear, night | [`ms_clear_night.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/clear_night/materialsymbolsoutlined/clear_night_24px.xml) |
| Partly cloudy, day | [`ms_partly_cloudy_day.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/partly_cloudy_day/materialsymbolsoutlined/partly_cloudy_day_24px.xml) |
| Partly cloudy, night | [`ms_partly_cloudy_night.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/partly_cloudy_night/materialsymbolsoutlined/partly_cloudy_night_24px.xml) |
| Cloudy | [`ms_cloud.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/cloud/materialsymbolsoutlined/cloud_24px.xml) |
| Rain | [`ms_rainy.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/rainy/materialsymbolsoutlined/rainy_24px.xml) |
| Snow | [`ms_weather_snowy.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/weather_snowy/materialsymbolsoutlined/weather_snowy_24px.xml) |
| Thunderstorm | [`ms_thunderstorm.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/thunderstorm/materialsymbolsoutlined/thunderstorm_24px.xml) |
| Fog | [`ms_foggy.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/foggy/materialsymbolsoutlined/foggy_24px.xml) |
| Wind | [`ms_air.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/air/materialsymbolsoutlined/air_24px.xml) |
| Hail | [`ms_weather_hail.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/weather_hail/materialsymbolsoutlined/weather_hail_24px.xml) |
| Sleet / mixed precipitation | [`ms_weather_mix.xml`](https://raw.githubusercontent.com/google/material-design-icons/bd8cb85bd4bad964fe6918f79665bb40c3a8efef/symbols/android/weather_mix/materialsymbolsoutlined/weather_mix_24px.xml) |

Day/night is independent of the UI theme. Unknown forecasts retain a neutral
em dash with the supplied accessible description.

### Other icons

App package row icon added 2026-10-06 (official Android vector retained, tint supplied by Compose):

- `ms_android.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/android/materialsymbolsoutlined/android_24px.xml

Search icons added 2026-10-05 (official SVG paths retained, same 24dp conversion):

- `ms_travel_explore.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/travel_explore/materialsymbolsoutlined/travel_explore_24px.svg
- `ms_more_vert.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/more_vert/materialsymbolsoutlined/more_vert_24px.svg

Icon designer entry added 2026-10-03:

- `ms_design_services.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/design_services/materialsymbolsoutlined/design_services_24px.xml
  (Official Android vector; path and 960px viewport retained, tint supplied by Compose.)

Clock-style reset button added 2026-10-02:

- `ms_restart_alt.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/restart_alt/materialsymbolsoutlined/restart_alt_24px.svg
  (Official SVG path retained; same 24dp conversion and Apache-2.0 license above.)

Favorite reorder handle added 2026-09-27:

- `ms_drag_indicator.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/drag_indicator/materialsymbolsoutlined/drag_indicator_24px.xml
  (Official Android vector; path and 960px viewport retained, tint supplied by Compose.)

Media controls added 2026-09-27 (same official Outlined 24px style):

- `ms_play_arrow.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/play_arrow/materialsymbolsoutlined/play_arrow_24px.svg
- `ms_pause.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/pause/materialsymbolsoutlined/pause_24px.svg
- `ms_skip_previous.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/skip_previous/materialsymbolsoutlined/skip_previous_24px.svg
- `ms_skip_next.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/skip_next/materialsymbolsoutlined/skip_next_24px.svg
- `ms_music_note.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/music_note/materialsymbolsoutlined/music_note_24px.svg

- `ms_account_balance.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/account_balance/materialsymbolsoutlined/account_balance_24px.svg
- `ms_person.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/person/materialsymbolsoutlined/person_24px.svg
- `ms_code.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/code/materialsymbolsoutlined/code_24px.svg
- `ms_link.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/link/materialsymbolsoutlined/link_24px.svg
- `ms_close.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/close/materialsymbolsoutlined/close_24px.svg

- `ms_star.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/star/materialsymbolsoutlined/star_24px.svg
- `ms_info.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/info/materialsymbolsoutlined/info_24px.svg
- `ms_hourglass_empty.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/hourglass_empty/materialsymbolsoutlined/hourglass_empty_24px.svg
- `ms_category.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/category/materialsymbolsoutlined/category_24px.svg
- `ms_delete.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/delete/materialsymbolsoutlined/delete_24px.svg
- `ms_expand_more.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/expand_more/materialsymbolsoutlined/expand_more_24px.svg
- `ms_settings.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/settings/materialsymbolsoutlined/settings_24px.svg
- `ms_open_in_new.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/open_in_new/materialsymbolsoutlined/open_in_new_24px.svg
- `ms_add.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/add/materialsymbolsoutlined/add_24px.svg
- `ms_edit.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/edit/materialsymbolsoutlined/edit_24px.svg
- `ms_apps.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/apps/materialsymbolsoutlined/apps_24px.svg
- `ms_check.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/check/materialsymbolsoutlined/check_24px.svg
- `ms_palette.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/palette/materialsymbolsoutlined/palette_24px.svg
- `ms_home.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/home/materialsymbolsoutlined/home_24px.svg
- `ms_search.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/search/materialsymbolsoutlined/search_24px.svg
