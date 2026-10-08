package faygolover.rpculinary.core;

/** Состав порции: ккал, белки, жиры, углеводы (г), витамины (условные единицы), вода (% полоски воды). */
public record Food(double kcal, double protein, double fat, double carbs, double vitamins, double water) {
    public static final Food ZERO = new Food(0, 0, 0, 0, 0, 0);

    public Food plus(Food o) {
        return new Food(kcal + o.kcal, protein + o.protein, fat + o.fat, carbs + o.carbs, vitamins + o.vitamins, water + o.water);
    }

    public Food scale(double k) {
        return new Food(kcal * k, protein * k, fat * k, carbs * k, vitamins * k, water * k);
    }

    /** Те же доли, но другая калорийность (порция блюда-блока, качество). */
    public Food withKcalScaled(double newKcal) {
        if (kcal <= 0) return new Food(newKcal, 0, 0, newKcal / 4, 0, water);
        return scale(newKcal / kcal);
    }

    public boolean isEmpty() {
        return kcal <= 0 && protein <= 0 && fat <= 0 && carbs <= 0 && vitamins <= 0 && water <= 0;
    }

    /** Калории по БЖУ, если не заданы: 4/9/4 ккал на грамм. */
    public Food withKcal() {
        return kcal > 0 ? this : new Food(protein * 4 + fat * 9 + carbs * 4, protein, fat, carbs, vitamins, water);
    }
}
