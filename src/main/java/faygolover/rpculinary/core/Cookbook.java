package faygolover.rpculinary.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import faygolover.rpculinary.RpCulinary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Поваренные книги (D3, вариант А, решения Алекса 03.11): таблица {@code assets/rpculinary/cookbook.json} ({@code tools/gen_cookbook.py}).
 * База (нарезка, тесто…) известна каждому повару; остальное — в книгах по разделам (выдаёт ГМ, раскладывают как лут). Рецепт из
 * книги переписывают в личную книгу рецептов (NBT {@code recipes} у неё): кнопка «Переписать», ожидание, навык не ниже нужного.
 * Сложные рецепты в книги не попадают — их ищут сами.
 */
public final class Cookbook {
    private Cookbook() {}

    /** Разделы в порядке обложек (предикат модели — (индекс + 1) / 100). */
    public static final List<String> SECTIONS = List.of("soups", "baking", "meat", "fish", "salads", "drinks", "butchery", "preserves", "vintner");
    public static final String SECTION = "section", RECIPES = "recipes";

    public record Book(int skill, String perk, Map<String, Integer> recipes) {}

    private static List<String> base;
    private static Map<String, Book> books;

    private static void load() {
        if (books != null) return;
        List<String> b = new ArrayList<>();
        Map<String, Book> m = new LinkedHashMap<>();
        try (InputStream in = Cookbook.class.getResourceAsStream("/assets/rpculinary/cookbook.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                root.getAsJsonArray("base").forEach(x -> b.add(x.getAsString()));
                for (var e : root.getAsJsonObject("books").entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    Map<String, Integer> r = new LinkedHashMap<>();
                    o.getAsJsonArray("recipes").forEach(x -> r.put(x.getAsJsonObject().get("id").getAsString(), x.getAsJsonObject().get("skill").getAsInt()));
                    m.put(e.getKey(), new Book(o.get("skill").getAsInt(), o.has("perk") ? o.get("perk").getAsString() : null, r));
                }
            }
        } catch (Exception ex) {
            RpCulinary.LOGGER.error("RP Culinary: не прочитан cookbook.json", ex);
        }
        base = List.copyOf(b);
        books = m;
    }

    public static List<String> base() {
        load();
        return base;
    }

    public static Book book(String section) {
        load();
        return books.get(section);
    }

    /** Нужный навык рецепта в книге раздела или -1, если его там нет. */
    public static int skill(String section, String recipe) {
        Book b = book(section);
        Integer s = b == null ? null : b.recipes().get(recipe);
        return s == null ? -1 : s;
    }

    public static String section(ItemStack book) {
        CompoundTag t = book.getTag();
        return t == null ? "" : t.getString(SECTION);
    }

    /** Записанное в личной книге рецептов. */
    public static Set<String> learned(ItemStack notebook) {
        Set<String> out = new LinkedHashSet<>();
        CompoundTag t = notebook.getTag();
        if (t != null) for (Tag x : t.getList(RECIPES, Tag.TAG_STRING)) out.add(x.getAsString());
        return out;
    }

    public static boolean write(ItemStack notebook, String recipe) {
        if (learned(notebook).contains(recipe)) return false;
        ListTag list = notebook.getOrCreateTag().getList(RECIPES, Tag.TAG_STRING);
        list.add(StringTag.valueOf(recipe));
        notebook.getTag().put(RECIPES, list);
        return true;
    }

    /** Время переписывания, тиков: 2 с + 0,5 с на каждый ингредиент. */
    public static int copyTicks(int ingredients) {
        return 40 + 10 * Math.max(1, ingredients);
    }
}
