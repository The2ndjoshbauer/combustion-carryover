package inc.combustion.framework.ble.device

enum class MeatMaterials(
    val displayName: String,
    val diffusivity: Double,
    val waterContentPercent: Double,
    val thermalConductivity: Double
) {
    BEEF_BRISKET("Beef Brisket", 1.28e-7, 0.65, 0.45),
    BEEF_STEAK("Beef Steak", 1.35e-7, 0.73, 0.49),
    PORK_SHOULDER("Pork Shoulder", 1.30e-7, 0.68, 0.46),
    PORK_LOIN("Pork Loin", 1.33e-7, 0.71, 0.48),
    POULTRY("Poultry", 1.42e-7, 0.75, 0.51),
    SALMON("Salmon", 1.30e-7, 0.69, 0.46),
    OTHER("Other", 1.35e-7, 0.70, 0.48)
}
