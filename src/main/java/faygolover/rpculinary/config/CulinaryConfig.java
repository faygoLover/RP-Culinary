package faygolover.rpculinary.config;

import faygolover.rpculinary.RpCulinary;
import faygolover.rpculinary.core.Settings;
import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Настройки сервера ({@code serverconfig/rpculinary-server.toml}): по одной на каждое поле {@link Settings} с пометкой
 * {@link Settings.Cfg}. Имя в файле — имя поля через подчёркивания, раздел и описание — из пометки.
 */
public final class CulinaryConfig {
    private CulinaryConfig() {}

    private record Bound(Field field, ForgeConfigSpec.ConfigValue<?> value) {}

    private static final List<Bound> BOUND = new ArrayList<>();
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        Settings def = new Settings();
        Map<String, List<Field>> sections = new LinkedHashMap<>();
        for (Field f : Settings.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            Settings.Cfg c = f.getAnnotation(Settings.Cfg.class);
            if (c != null) sections.computeIfAbsent(c.section(), k -> new ArrayList<>()).add(f);
        }
        try {
            for (var e : sections.entrySet()) {
                b.push(e.getKey());
                for (Field f : e.getValue()) {
                    String doc = f.getAnnotation(Settings.Cfg.class).doc();
                    if (!doc.isEmpty()) b.comment(" " + doc);
                    String key = snake(f.getName());
                    ForgeConfigSpec.ConfigValue<?> v = f.getType() == boolean.class
                            ? b.define(key, f.getBoolean(def))
                            : b.defineInRange(key, f.getDouble(def), -1e9, 1e9);
                    BOUND.add(new Bound(f, v));
                }
                b.pop();
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
        SPEC = b.build();
    }

    /** Перечитать конфиг в {@link Settings} (загрузка мира, правка файла). */
    public static void bake() {
        Settings s = new Settings();
        try {
            for (Bound bd : BOUND) {
                Object v = bd.value().get();
                if (bd.field().getType() == boolean.class) bd.field().setBoolean(s, (Boolean) v);
                else bd.field().setDouble(s, ((Number) v).doubleValue());
            }
        } catch (Exception ex) {
            RpCulinary.LOGGER.error("RP Culinary: конфиг не прочитан: {}", ex.toString());
            return;
        }
        Settings.set(s);
    }

    /** Поменять числовую настройку на ходу (команда): в файл и сразу в {@link Settings}. */
    @SuppressWarnings("unchecked")
    public static boolean setDouble(String field, double value) {
        for (Bound bd : BOUND) {
            if (!bd.field().getName().equals(field) || bd.field().getType() == boolean.class) continue;
            ((ForgeConfigSpec.ConfigValue<Double>) bd.value()).set(value);
            SPEC.save();
            bake();
            return true;
        }
        return false;
    }

    static String snake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char ch : camel.toCharArray()) {
            if (Character.isUpperCase(ch)) sb.append('_').append(Character.toLowerCase(ch));
            else sb.append(ch);
        }
        return sb.toString();
    }
}
