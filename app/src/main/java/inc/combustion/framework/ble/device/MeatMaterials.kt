package inc.combustion.framework.ble.device

enum class MeatMaterials(
    val displayName: String,
    val diffusivity: Double,
    val waterContentPercent: Double
) {
    BEEF_BRISKET("Beef Brisket", 1.35e-7, 0.72),
    BEEF_STEAK("Beef Steak", 1.35e-7, 0.70),
    PORK_SHOULDER("Pork Shoulder", 1.33e-7, 0.68),
    PORK_LOIN("Pork Loin", 1.33e-7, 0.70),
    POULTRY("Poultry", 1.42e-7, 0.35),
    SALMON("Salmon", 1.30e-7, 0.65),
    OTHER("Other", 1.35e-7, 0.70)
}
