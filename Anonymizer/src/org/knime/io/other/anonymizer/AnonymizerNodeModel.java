package org.knime.io.other.anonymizer;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.data.container.SingleCellFactory;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.streamable.simple.SimpleStreamableFunctionNodeModel;

/**
 * This is the model implementation of Anonymizer. This node generates
 * universally unique identifier (UUID) tags or hash codes for each row in
 * selected columns.
 *
 * @author Rytis Kumpa
 */
public class AnonymizerNodeModel extends SimpleStreamableFunctionNodeModel {

	// the logger instance
	private static final NodeLogger logger = NodeLogger.getLogger(AnonymizerNodeModel.class);

	/*
	 * the settings keys that are used to retrieve and store the settings from the
	 * dialog or the settings file.
	 */
	static final String CFGKEY_SELECT = "Selected columns";
	static final String CFGKEY_APPEND = "Append identifier";
	static final String CFGKEY_FUNCTIONS = "Anonymization functions";
	static final String CFGKEY_MAXVALUES = "Maximum number of unique values";

	private final SettingsModelFilterString m_filter = new SettingsModelFilterString(AnonymizerNodeModel.CFGKEY_SELECT);
	private final SettingsModelBoolean m_append = new SettingsModelBoolean(AnonymizerNodeModel.CFGKEY_APPEND,
			Boolean.FALSE);
	static final String m_warningMessage = "Incompatible anonymization function selected.";
	private final SettingsModelString m_functions = new SettingsModelString(AnonymizerNodeModel.CFGKEY_FUNCTIONS,
			m_warningMessage);
	private final SettingsModelInteger m_maxUnique = new SettingsModelInteger(AnonymizerNodeModel.CFGKEY_MAXVALUES,
			1000000);

	// Message Digest class that provides Java hashing functions.
	private MessageDigest m_messageDigest = null;

	// If the UUID function is chosen, the string value of a column will be mapped
	// to a specific UUID.
	private HashMap<String, String> m_UUIDHashMap = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {

		// Retrieve the values set by the user in the dialog panel
		// m_includeList = m_filter.getIncludeList();
		// m_anonymizationFunction = m_functions.getStringValue();
		// m_appendBoolean = m_append.getBooleanValue();
		// m_maxUniqueValues = m_maxUnique.getIntValue();

		DataTableSpec inSpec = inSpecs[0];
		ColumnRearranger columnRearranger = createColumnRearranger(inSpec);
		return new DataTableSpec[] { columnRearranger.createSpec() };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected ColumnRearranger createColumnRearranger(DataTableSpec spec) throws InvalidSettingsException {

		// Retrieve the values set by the user in the dialog panel
		List<String> m_includeList = m_filter.getIncludeList();
		String m_anonymizationFunction = m_functions.getStringValue();
		Boolean m_appendBoolean = m_append.getBooleanValue();
		int m_maxUniqueValues = m_maxUnique.getIntValue();

		ColumnRearranger columnRearranger = new ColumnRearranger(spec);

		// Store the column indexes of the original data table for each selected include
		// column.
		HashMap<String, Integer> includeColumnIdX = new HashMap<String, Integer>();
		for (String includeColumn : m_includeList) {
			includeColumnIdX.put(includeColumn, columnRearranger.indexOf(includeColumn));
		}

		for (String includeColumn : m_includeList) {
			int idX = columnRearranger.indexOf(includeColumn);
			if (m_appendBoolean) {
				String newColumnName = includeColumn + " (" + m_anonymizationFunction + ")";
				DataColumnSpecCreator specCreator = new DataColumnSpecCreator(newColumnName, StringCell.TYPE);
				columnRearranger.insertAt(idX + 1, new SingleCellFactory(specCreator.createSpec()) {

					@Override
					public DataCell getCell(DataRow row) {
						DataCell cell = row.getCell(includeColumnIdX.get(includeColumn));
						return anonymizeCell(cell, m_anonymizationFunction, m_UUIDHashMap, m_messageDigest,
								m_maxUniqueValues);
					}
				});
			} else {
				DataColumnSpecCreator specCreator = new DataColumnSpecCreator(includeColumn, StringCell.TYPE);
				columnRearranger.replace(new SingleCellFactory(specCreator.createSpec()) {

					@Override
					public DataCell getCell(DataRow row) {
						DataCell cell = row.getCell(idX);
						return anonymizeCell(cell, m_anonymizationFunction, m_UUIDHashMap, m_messageDigest,
								m_maxUniqueValues);
					}
				}, idX);
			}
		}

		return columnRearranger;
	}


	/**
	 * This function anonymizes the input cell according to the anonymization
	 * algorithm selected.
	 * @param inputCell
	 * @param m_anonymizationFunction
	 * @param m_UUIDHashMap
	 * @param m_maxUniqueValues
	 * @return DataCell A DataCell with the anonymized content.
	 */
	private DataCell anonymizeCell(DataCell inputCell, String m_anonymizationFunction,
			HashMap<String, String> m_UUIDHashMap, MessageDigest m_messageDigest, int m_maxUniqueValues) {

		DataCell anonymizedCell = null;
		if (!inputCell.isMissing()) {
			// if UUID is selected, generate a UUID identifier and convert it into a
			// StringCell
			if (m_anonymizationFunction == "UUID") {
				if (m_UUIDHashMap.containsKey(inputCell.toString())) {
					anonymizedCell = new StringCell(m_UUIDHashMap.get(inputCell.toString()));
				} else {
					String newUUID = UUID.randomUUID().toString();
					anonymizedCell = new StringCell(newUUID);
					m_UUIDHashMap.put(inputCell.toString(), newUUID);
					if (m_UUIDHashMap.size() > m_maxUniqueValues) {
						throw new IllegalStateException("The amount of unique values that can be mapped to UUIDs has been reached. "
								+ "Please try using another hashing function instead.");
					}
				}

			} else {
				// if a hashing algorithm is selected, hash the String value in the cell and
				// convert the value into StringCell
				byte[] newHash = m_messageDigest.digest(inputCell.toString().getBytes());
				String hexString = javax.xml.bind.DatatypeConverter.printHexBinary(newHash);
				anonymizedCell = new StringCell(hexString);
			}
		} else {
			anonymizedCell = DataType.getMissingCell();
		}
		return anonymizedCell;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(BufferedDataTable[] inData, ExecutionContext exec) throws Exception {

		String m_anonymizationFunction = m_functions.getStringValue();

		// Creates a message digest instance if a hashing algorithm is selected.
		if (m_anonymizationFunction != "UUID") {
			if (m_messageDigest == null) {
				try {
					m_messageDigest = MessageDigest.getInstance(m_anonymizationFunction);
				} catch (NoSuchAlgorithmException e) {
					logger.info("Selected hashing algorithm unavailable.");
				}
			}
		}

		// Creates a new HashMap, where corresponding strings will be mapped to a
		// specific UUID.
		if (m_anonymizationFunction == "UUID") {
			m_UUIDHashMap = new HashMap<String, String>();
		}

		BufferedDataTable in = inData[0];

		ColumnRearranger r = createColumnRearranger(in.getDataTableSpec());
		BufferedDataTable out = exec.createColumnRearrangeTable(in, r, exec);
		return new BufferedDataTable[] { out };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(NodeSettingsWO settings) {
		m_filter.saveSettingsTo(settings);
		m_functions.saveSettingsTo(settings);
		m_append.saveSettingsTo(settings);
		m_maxUnique.saveSettingsTo(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(NodeSettingsRO settings) throws InvalidSettingsException {
		m_filter.validateSettings(settings);
		m_append.validateSettings(settings);
		m_functions.validateSettings(settings);
		m_maxUnique.validateSettings(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(NodeSettingsRO settings) throws InvalidSettingsException {
		m_filter.loadSettingsFrom(settings);
		m_append.loadSettingsFrom(settings);
		m_functions.loadSettingsFrom(settings);
		m_maxUnique.loadSettingsFrom(settings);

	}

}
