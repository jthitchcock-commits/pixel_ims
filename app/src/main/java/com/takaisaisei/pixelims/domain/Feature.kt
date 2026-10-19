package com.takaisaisei.pixelims.domain

import android.os.PersistableBundle

/** Grouping used to render features under separate section headers. */
enum class FeatureCategory { IMS, NETWORK, COSMETIC }

/**
 * The single catalog of every IMS feature the app manages. Each entry owns all of its *behavior*:
 * - [key]: SharedPreferences key prefix (must stay stable - read by the broker at apply time);
 * - [default]: first-run / restore value;
 * - [userFacing]: whether it is shown as a toggle (hidden ones are still applied with their default);
 * - [overrides]: the CarrierConfigManager keys this feature drives;
 * - [category]: which UI section the feature belongs to;
 * - [exclusiveGroup]: features sharing a non-null group are mutually exclusive - enabling one
 *   turns the others off.
 */
enum class Feature(
    val key: String,
    val default: Boolean,
    val userFacing: Boolean,
    val overrides: List<Override>,
    val category: FeatureCategory = FeatureCategory.IMS,
    val exclusiveGroup: String? = null,
) {
    VOLTE(
        "volte", default = true, userFacing = true, overrides = listOf(
            BoolOverride("carrier_volte_available_bool"),
            BoolOverride("enhanced_4g_lte_on_by_default_bool"),
            BoolOverride("hide_enhanced_4g_lte_bool", inverted = true),
            BoolOverride("editable_enhanced_4g_lte_bool"),
            BoolOverride("carrier_volte_provisioned_bool"),
            BoolOverride("carrier_volte_provisioning_required_bool", const = false),
        )
    ),
    VONR(
        "vonr", default = true, userFacing = true, overrides = listOf(
            BoolOverride("vonr_enabled_bool"),
            BoolOverride("vonr_setting_visibility_bool"),
        )
    ),
    VOWIFI(
        "vowifi", default = true, userFacing = true, overrides = listOf(
            BoolOverride("carrier_wfc_ims_available_bool"),
            BoolOverride("carrier_default_wfc_ims_enabled_bool"),
            BoolOverride("carrier_wfc_ims_provisioned_bool"),
            BoolOverride("editable_wfc_mode_bool"),
            BoolOverride("editable_wfc_roaming_mode_bool"),
        )
    ),
    WFC_ROAMING(
        "wfc_roaming", default = true, userFacing = true, overrides = listOf(
            BoolOverride("carrier_default_wfc_ims_roaming_enabled_bool"),
        )
    ),
    SS_UT(
        "ss_ut", default = true, userFacing = true, overrides = listOf(
            BoolOverride("carrier_supports_ss_over_ut_bool"),
        )
    ),
    CROSS_SIM(
        "cross_sim", default = false, userFacing = false, overrides = listOf(
            BoolOverride("carrier_cross_sim_ims_available_bool"),
            BoolOverride("enable_cross_sim_calling_on_opportunistic_data_bool"),
        )
    ),
    SHOW_IMS(
        "show_ims", default = true, userFacing = false, overrides = listOf(
            BoolOverride("show_ims_registration_status_bool"),
        )
    ),
    ALLOW_APN(
        "allow_apn", default = false, userFacing = false, overrides = listOf(
            BoolOverride("allow_adding_apns_bool"),
        )
    ),

    FORCE_5G(
        "force_5g", default = false, userFacing = true,
        category = FeatureCategory.NETWORK, overrides = listOf(
            IntArrayOverride("carrier_nr_availabilities_int_array", intArrayOf(NR_NSA, NR_SA)),
            BoolOverride("hide_preferred_network_type_bool", inverted = true, onlyWhenEnabled = true),
        )
    ),

    COSMETIC_DATA_RAT_ICON(
        "cosmetic_data_rat_icon", default = false, userFacing = true,
        category = FeatureCategory.COSMETIC, overrides = listOf(
            BoolOverride("always_show_data_rat_icon_bool"),
        )
    ),
    COSMETIC_4G_FOR_LTE(
        "cosmetic_4g_for_lte", default = false, userFacing = true,
        category = FeatureCategory.COSMETIC, exclusiveGroup = "lte_label", overrides = listOf(
            BoolOverride("show_4g_for_lte_data_icon_bool"),
        )
    ),
    COSMETIC_4GLTE_FOR_LTE(
        "cosmetic_4glte_for_lte", default = false, userFacing = true,
        category = FeatureCategory.COSMETIC, exclusiveGroup = "lte_label", overrides = listOf(
            BoolOverride("show_4glte_for_lte_data_icon_bool"),
        )
    ),
    COSMETIC_VOWIFI_ICON(
        "cosmetic_vowifi_icon", default = false, userFacing = true,
        category = FeatureCategory.COSMETIC,
        overrides = listOf(
            BoolOverride("show_wifi_calling_icon_in_status_bar_bool"),
            IntChoice("wfc_spn_format_idx_int", default = 4, offValue = 0),
        ),
    ),
    ;

    /** The representative override read back to show the feature's effective on/off state. */
    val effectiveOverride: Override get() = overrides.first()

    /** The feature's user-selectable int override. */
    val intChoice: IntChoice? get() = overrides.filterIsInstance<IntChoice>().firstOrNull()

    /** Other features that must be off when this one is on (same non-null [exclusiveGroup]). */
    val exclusiveSiblings: List<Feature>
        get() = if (exclusiveGroup == null) emptyList()
        else entries.filter { it != this && it.exclusiveGroup == exclusiveGroup }

    companion object {
        /** Features rendered as toggles / status rows, in display order. */
        val displayed: List<Feature> get() = entries.filter { it.userFacing }

        fun byKey(key: String): Feature? = entries.firstOrNull { it.key == key }
    }
}

/** One CarrierConfigManager entry a [Feature] drives; each variant owns its value type via [CfgValue]. */
sealed interface Override {
    val key: String

    /**
     * The value to write for the feature's [enabled] state and any [user]-selected value, or null to
     * leave the key out of the override so the carrier's own value applies.
     */
    fun bundleValue(enabled: Boolean, user: CfgValue?): CfgValue?

    /** Whether the effective carrier config [bundle] has this override's key turned on. */
    fun isEnabledIn(bundle: PersistableBundle): Boolean = bundle.getBoolean(key, false)

    /** Non-null for user-editable overrides: the persisted value until the user picks another. */
    val editableDefault: CfgValue? get() = null
}

/**
 * A boolean key derived from the toggle.
 * - [inverted]: store the negated feature value (e.g. "hide_*" keys);
 * - [const]: store this fixed value regardless of the feature value;
 * - [onlyWhenEnabled]: only write the key while the feature is on, otherwise keep the carrier's value.
 */
data class BoolOverride(
    override val key: String,
    val inverted: Boolean = false,
    val const: Boolean? = null,
    val onlyWhenEnabled: Boolean = false,
) : Override {
    override fun bundleValue(enabled: Boolean, user: CfgValue?): CfgValue? =
        if (onlyWhenEnabled && !enabled) null
        else CfgValue.Bool(const ?: (enabled != inverted))
}

/**
 * An int array key written with [value] while the feature is on and left to the carrier when off.
 * Counts as enabled when the effective array is non-empty.
 */
class IntArrayOverride(
    override val key: String,
    val value: IntArray,
) : Override {
    override fun bundleValue(enabled: Boolean, user: CfgValue?): CfgValue? =
        if (enabled) CfgValue.IntArr(value) else null

    override fun isEnabledIn(bundle: PersistableBundle): Boolean =
        bundle.getIntArray(key)?.isNotEmpty() == true
}

/**
 * A user-selectable int key, editable while the feature is on.
 * - [default]: selection used until the user picks another value (feature ON);
 * - [offValue]: value written when the feature is OFF.
 */
data class IntChoice(
    override val key: String,
    val default: Int,
    val offValue: Int,
) : Override {
    override val editableDefault: CfgValue get() = CfgValue.IntVal(default)

    override fun bundleValue(enabled: Boolean, user: CfgValue?): CfgValue =
        CfgValue.IntVal(if (enabled) (user as? CfgValue.IntVal)?.v ?: default else offValue)
}

// CarrierConfigManager.CARRIER_NR_AVAILABILITY_* values.
private const val NR_NSA = 1
private const val NR_SA = 2
