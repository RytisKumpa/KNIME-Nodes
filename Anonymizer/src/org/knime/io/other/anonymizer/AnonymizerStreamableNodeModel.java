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
 * This is the model implementation of Anonymizer.
 * This node generates universally unique identifier (UUID) tags or hash codes for each row in selected columns.  
 *
 * @author Rytis Kumpa
 */
public class AnonymizerStreamableNodeModel extends SimpleStreamableFunctionNodeModel {

	// the logger instance
	private static final NodeLogger logger = NodeLogger.getLogger(AnonymizerStreamableNodeModel.class);

	/*
	 * the settings keys that are used to retrieve and store the settings from the
	 * dialog or the settings file.
	 */
	static final String CFGKEY_SELECT = "Selected columns";
	static final String CFGKEY_APPEND = "Append identifier";
	static final String CFGKEY_FUNCTIONS = "Anonymization functions";
	static final String CFGKEY_MAXVALUES = "Maximum number of unique values";

	private final SettingsModelFilterString m_filter = new SettingsModelFilterString(
			AnonymizerStreamableNodeModel.CFGKEY_SELECT);
	private final SettingsModelBoolean m_append = new SettingsModelBoolean(AnonymizerStreamableNodeModel.CFGKEY_APPEND,
			Boolean.FALSE);
	static final String m_warningMessage = "Incompatible anonymization function selected.";
	private final SettingsModelString m_functions = new SettingsModelString(
			AnonymizerStreamableNodeModel.CFGKEY_FUNCTIONS, m_warningMessage);
	private final SettingsModelInteger m_maxUnique = new SettingsModelInteger(AnonymizerStreamableNodeModel.CFGKEY_MAXVALUES, 100000);

	// The values set by the user in the dialog panel.
	List<String> includeList = null;
	String anonymizationFunction = null;
	Boolean appendBoolean = null;
	int maxUniqueValues = 0;
	MessageDigest messageDigest = null;

	// If the UUID function is chosen, the string value of a column will be mapped
	// to a specific UUID.
	private HashMap<String, String> UUIDHashMap = null;

	
	/**
     * {@inheritDoc}
     */
	@Override
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {

		// Retrieve the values set by the user in the dialog panel
		includeList = m_filter.getIncludeList();
		anonymizationFunction = m_functions.getStringValue();
		appendBoolean = m_append.getBooleanValue();
		maxUniqueValues = m_maxUnique.getIntValue();

		DataTableSpec inSpec = inSpecs[0];
		ColumnRearranger columnRearranger = createColumnRearranger(inSpec);
		return new DataTableSpec[] { columnRearranger.createSpec() };
	}

	
	/**
     * {@inheritDoc}
     */
	@Override
	protected ColumnRearranger createColumnRearranger(DataTableSpec spec) throws InvalidSettingsException {

		ColumnRearranger columnRearranger = new ColumnRearranger(spec);
		
		// Store the column indexes of the original data table for each selected include
		// column.
		HashMap<String, Integer> includeColumnIdX = new HashMap<String, Integer>();
		for (String includeColumn : includeList) {
			includeColumnIdX.put(includeColumn, columnRearranger.indexOf(includeColumn));
		}

		for (String includeColumn : includeList) {
			int idX = columnRearranger.indexOf(includeColumn);
			if (appendBoolean) {
				String newColumnName = includeColumn + " (" + anonymizationFunction + ")";
				DataColumnSpecCreator specCreator = new DataColumnSpecCreator(newColumnName, StringCell.TYPE);
				columnRearranger.insertAt(idX + 1, new SingleCellFactory(specCreator.createSpec()) {

					@Override
					public DataCell getCell(DataRow row) {
						DataCell cell = row.getCell(includeColumnIdX.get(includeColumn));
						return anonymizeCell(cell);
					}
				});
			} else {
				DataColumnSpecCreator specCreator = new DataColumnSpecCreator(includeColumn, StringCell.TYPE);
				columnRearranger.replace(new SingleCellFactory(specCreator.createSpec()) {

					@Override
					public DataCell getCell(DataRow row) {
						DataCell cell = row.getCell(idX);
						return anonymizeCell(cell);
					}
				}, idX);
			}
		}

		return columnRearranger;
	}

	
	/**
	 * This function anonymizes the input cell according to the anonymization algorithm selected.
	 * @param inputCell
	 * @return DataCell
	 */
	private DataCell anonymizeCell(DataCell inputCell) {
		DataCell anonymizedCell = null;
		if (!inputCell.isMissing()) {
			// if UUID is selected, generate a UUID identifier and convert it into a
			// StringCell
			if (anonymizationFunction == "UUID") {
				if (UUIDHashMap.containsKey(inputCell.toString())) {
					anonymizedCell = new StringCell(UUIDHashMap.get(inputCell.toString()));
				} else {
					String newUUID = UUID.randomUUID().toString();
					anonymizedCell = new StringCell(newUUID);
					UUIDHashMap.put(inputCell.toString(), newUUID);
					assert !(UUIDHashMap.size() > maxUniqueValues) : "The amount of unique values that can be mapped to UUIDs has been reached. Please try using another hashing function instead.";	
				}

			} else {
				// if a hashing algorithm is selected, hash the String value in the cell and
				// convert the value into StringCell
				byte[] newHash = messageDigest.digest(inputCell.toString().getBytes());
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

		// Creates a message digest instance if a hashing algorithm is selected.
		if (anonymizationFunction != "UUID") {
			if (messageDigest == null) {
				try {
					messageDigest = MessageDigest.getInstance(anonymizationFunction);
				} catch (NoSuchAlgorithmException e) {
					logger.info("Selected hashing algorithm unavailable.");
				}
			}
		}

		// Creates a new HashMap, where corresponding strings will be mapped to a specific UUID.
		if (anonymizationFunction == "UUID") {
			UUIDHashMap = new HashMap<String, String>();
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
