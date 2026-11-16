package faygolover.rpculinary.config;

import faygolover.rpcore.api.config.SettingsBinder;
import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.core.Settings;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Настройки сервера ({@code serverconfig/rpculinary-server.toml}): по одной на каждое поле {@link Settings} с пометкой
 * {@link Settings.Cfg}. Имя в файле — имя поля через подчёркивания, раздел и описание — из пометки. Привязку ведёт
 * {@link SettingsBinder} RP Core; новая настройка — это поле с пометкой в {@link Settings}, здесь править нечего.
 */
public final class CulinaryConfig {
    private CulinaryConfig() {}

    /** Описание в файле пишется с пробелом в начале — так сложилось, и файлы на серверах уже такие. */
    private static final SettingsBinder<Settings> S = SettingsBinder.of(Settings.class, Settings::new)
            .bindAnnotated(Settings.Cfg.class, Settings.Cfg::section, c -> c.doc().isEmpty() ? "" : " " + c.doc());
    public static final ForgeConfigSpec SPEC = S.build();

    /** Перечитать конфиг в {@link Settings} (загрузка мира, правка файла). */
    public static void bake() {
        Settings s;
        try {
            s = S.bake();
        } catch (RuntimeException ex) {
            RpCulinary.LOGGER.error("RP Culinary: конфиг не прочитан: {}", ex.toString());
            return;
        }
        Settings.set(s);
    }

    /** Поменять числовую настройку на ходу (команда): в файл и сразу в {@link Settings}. */
    public static boolean setDouble(String field, double value) {
        if (!S.set(field, value)) return false;
        bake();
        return true;
    }
}
