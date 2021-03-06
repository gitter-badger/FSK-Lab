package metadata;

public enum RakipRow {

	// Renamed to match meta object ids from EMF
	GENERAL_INFORMATION__NAME(2), GENERAL_INFORMATION__SOURCE(3), GENERAL_INFORMATION__IDENTIFIER(
			4), GENERAL_INFORMATION__CREATION_DATE(7), GENERAL_INFORMATION__MODIFICATION_DATE(
					8), GENERAL_INFORMATION__RIGHTS(9), GENERAL_INFORMATION__AVAILABILITY(10), GENERAL_INFORMATION__URL(
							11), GENERAL_INFORMATION_FORMAT(12), GENERAL_INFORMATION__LANGUAGE(
									25), GENERAL_INFORMATION__SOFTWARE(26), GENERAL_INFORMATION__LANGUAGE_WRITTEN_IN(
											27), GENERAL_INFORMATION__STATUS(33), GENERAL_INFORMATION__OBJECTIVE(
													34), GENERAL_INFORMATION__DESCRIPTION(35),

	MODEL_CATEGORY__MODEL_CLASS(28), MODEL_CATEGORY__MODEL_SUB_CLASS(29), MODEL_CATEGORY__MODEL_CLASS_COMMENT(
			30), MODEL_CATEGORY__BASIC_PROCESS(31),

	HAZARD_TYPE(46), HAZARD_NAME(47), HAZARD_DESCRIPTION(48), HAZARD_UNIT(49), HAZARD_ADVERSE_EFFECT(
			50), HAZARD_CONTAMINATION_SOURCE(51), HAZARD_BMD(52), // benchmark
																	// dose
	HAZARD_MRL(53), // maximum residue limit
	HAZARD_NOAEL(54), // No Observed Adverse Affect Level
	HAZARD_LOAEL(55), // Lowest Observed Adverse Effect Level
	HAZARD_AOEL(56), // Acceptable Operator Exposure Level
	HAZARD_ARFD(57), // Acute Reference Dose
	HAZARD_ADI(58), // Acceptable Daily Intake
	HAZARD_IND_SUM(59),

	POPULATION_GROUP_NAME(60), // Population name
	POPULATION_GROUP_TARGET(61), // Target population
	POPULATION_GROUP_SPAN(62), // Population span (years)
	POPULATION_GROUP_DESCRIPTION(63), // Population description
	POPULATION_GROUP_AGE(64), // Population age
	POPULATION_GROUP_GENDER(65), // Population gender
	POPULATION_GROUP_BMI(66), // Body mass index
	POPULATION_GROUP_DIET(67), // Special diet groups
	POPULATION_GROUP_PATTERN_CONSUMPTION(68), POPULATION_GROUP_REGION(69), POPULATION_GROUP_COUNTRY(
			70), POPULATION_GROUP_RISK(71), // Risk and population
											// factors
	POPULATION_GROUP_SEASON(72),

	STUDY_ID(77), STUDY_TITLE(78), STUDY_DESCRIPTION(79), STUDY_DESIGN_TYPE(80), STUDY_ASSAY_MEASUREMENTS_TYPE(
			81), STUDY_ASSAY_TECHNOLOGY_TYPE(82), STUDY_ASSAY_TECHNOLOGY_PLATFORM(83), STUDY_ACCREDITATION_PROCEDURE(
					84), STUDY_PROTOCOL_NAME(85), STUDY_PROTOCOL_TYPE(86), STUDY_PROTOCOL_DESCRIPTION(
							87), STUDY_PROTOCOL_URI(88), STUDY_PROTOCOL_VERSION(89), STUDY_PROTOCOL_PARAMETERS_NAME(
									90), STUDY_PROTOCOL_COMPONENTS_NAME(91), STUDY_PROTOCOL_COMPONENTS_TYPE(92),

	STUDY_SAMPLE_NAME(93), STUDY_SAMPLE_PROTOCOL(94), // Protocol of sample collection
	STUDY_SAMPLE_STRATEGY(95), // Sampling strategy
	STUDY_SAMPLE_TYPE(96), // Type of sampling program
	STUDY_SAMPLE_METHOD(97), // Sampling method
	STUDY_SAMPLE_PLAN(98), // Sampling plan
	STUDY_SAMPLE_WEIGHT(99), // Sampling weight
	STUDY_SAMPLE_SIZE(100), // Sampling size
	STUDY_SAMPLE_SIZE_UNIT(101), // Lot size unit
	STUDY_SAMPLE_POINT(102), // Sampling point

	DIETARY_ASSESSMENT_METHOD_TOOL(103), // Methodological tool to collect data
	DIETARY_ASSESSMENT_METHOD_1DAY(104), // Number of non-consecutive one-day
	DIETARY_ASSESSMENT_METHOD_SOFTWARE_TOOL(105), // Dietary software tool
	DIETARY_ASSESSMENT_METHOD_ITEMS(106), // Number of food items
	DIETARY_ASSESSMENT_METHOD_RECORD_TYPE(107), // Type of records
	DIETARY_ASSESSMENT_METHOD_DESCRIPTORS(108), // Food descriptors

	LABORATORY_ACCREDITATION(109), LABORATORY_NAME(110), LABORATORY_COUNTRY(111),

	ASSAY_NAME(112), // Assay name
	ASSAY_DESCRIPTION(113), // Assay description
	ASSAY_MOIST_PERC(114), // Percentage of moisture
	ASSAY_FAT_PERC(115), // Percentage of fat
	ASSAY_DETECTION_LIMIT(116), /// Limit of detection
	ASSAY_QUANTIFICATION_LIMIT(117), // Limit of quantification
	ASSAY_LEFT_CENSORED_DATA(118), // Left-censored data
	ASSAY_CONTAMINATION_RANGE(119), // Range of contamination
	ASSAY_UNCERTAINTY_VALUE(120); // Uncertainty value

	/** Actual row number. 0-based. */
	public final int num;

	/**
	 * @param num
	 *            1-based row number in spreadsheet. The actual number stored is
	 *            0-based.
	 */
	RakipRow(int num) {
		this.num = num - 1;
	}
}