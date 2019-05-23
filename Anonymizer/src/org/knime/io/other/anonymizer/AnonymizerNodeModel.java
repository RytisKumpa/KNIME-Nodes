package org.knime.io.other.anonymizer;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelString;


import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;



/**
 * This is the model implementation of Anonymizer.
 * This node generates universally unique identifier (UUID) tags or hash codes for each row in selected columns.  
 *
 * @author Rytis Kumpa
 */
public class AnonymizerNodeModel extends NodeModel {
    
    // the logger instance
    private static final NodeLogger logger = NodeLogger
            .getLogger(AnonymizerNodeModel.class);
        

	/*
	 * the settings keys that are used to retrieve and store the settings from the
	 * dialog or the settings file.
	 */
	static final String CFGKEY_SELECT = "Selected columns";
	
	static final String CFGKEY_APPEND = "Append identifier";
	
	static final String CFGKEY_FUNCTIONS = "Anonymization functions";

    
    // example value: the models count variable filled from the dialog 
    // and used in the models execution method. The default components of the
    // dialog work with "SettingsModels".
    
    private final SettingsModelFilterString m_filter = 
    		new SettingsModelFilterString(AnonymizerNodeModel.CFGKEY_SELECT);
    
    private final SettingsModelBoolean m_append = 
    		new SettingsModelBoolean(AnonymizerNodeModel.CFGKEY_APPEND, Boolean.FALSE);


	static final String m_warningMessage = "Incompatible anonymization function selected.";
    
    private final SettingsModelString m_functions = 
    		new SettingsModelString(AnonymizerNodeModel.CFGKEY_FUNCTIONS, m_warningMessage);

    /**
     * Constructor for the node model.
     */
    protected AnonymizerNodeModel() {
    
        // TODO one incoming port and one outgoing port is assumed
        super(1, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
            final ExecutionContext exec) throws Exception {
        
        BufferedDataTable inputTable = inData[0];
        
        // The values set by the user in the dialog panel     
        List<String> includeList = m_filter.getIncludeList();
        
        String anonymizationFunction = m_functions.getStringValue();
        
        Boolean appendBoolean = m_append.getBooleanValue();
        
        DataTableSpec inputTableSpec = inputTable.getDataTableSpec();
        
        DataTableSpec outTableSpec = createOutTableSpec(inputTable.getDataTableSpec(), includeList, appendBoolean);
        
        String[] inputTableColumnNames = inputTableSpec.getColumnNames();
        
        // the execution context will provide us with storage capacity, in this
        // case a data container to which we will add rows sequentially
        // Note, this container can also handle arbitrary big data tables, it
        // will buffer to disc if necessary.
        BufferedDataContainer container = exec.createDataContainer(outTableSpec);
        
        CloseableRowIterator rowIterator = inputTable.iterator();
        
        // creates a message digest instance if a hashing algorithm is selected.
        MessageDigest messageDigest = null;
        if (anonymizationFunction != "UUID") {
        	try {
				messageDigest = MessageDigest.getInstance(anonymizationFunction);
			} catch (NoSuchAlgorithmException e) {
				logger.info("Selected hashing algorithm unavailable.");
			}	
        }
        
        int currentRowCounter = 0;
        
        while (rowIterator.hasNext()) {
        	DataRow currentRow = rowIterator.next();
        	
        	int numberOfCells = currentRow.getNumCells();
        	
        	List<DataCell> cells = new ArrayList<>();
        	
        	for (int i = 0; i < numberOfCells; i++) {
        		DataCell cell = currentRow.getCell(i);
        		if(!cell.isMissing()) {
        			if (includeList.contains(inputTableColumnNames[i])) {
	        			DataCell anonymizedCell = null;
	        			// if UUID is selected, generate a UUID identifier and convert it into a StringCell
	        			if (anonymizationFunction == "UUID") {
	        				String newUUID = UUID.randomUUID().toString();
	        				if(appendBoolean) {
	        					anonymizedCell = new StringCell(cell.toString() + " - UUID: " + newUUID);
	        				} else {
								anonymizedCell = new StringCell(newUUID);
							}
	        			} else {
	        				// if a hashing algorithm is selected, hash the String value in the cell and convert the value into StringCell
							byte[] newHash = messageDigest.digest(cell.toString().getBytes());
							String hexString = javax.xml.bind.DatatypeConverter.printHexBinary(newHash);
							if (appendBoolean) {
								anonymizedCell = new StringCell(cell.toString() + " - Hash value: " + hexString);
							} else {
								anonymizedCell = new StringCell(hexString);
							}
						}
	        			cells.add(anonymizedCell);
	        		} else {
	        			cells.add(cell);
	        		}
        		}
        	}
        	
        	// update the row counter
        	currentRowCounter++;
        	exec.setProgress(currentRowCounter / (double) inputTable.size(), "Processing row " + currentRowCounter);
        	// append the row to the container
        	DataRow dataRow = new DefaultRow(currentRow.getKey(), cells);
        	container.addRowToTable(dataRow);
        	exec.checkCanceled();
        }
        
        
        // once we are done, we close the container and return its table
        container.close();
        BufferedDataTable out = container.getTable();
        return new BufferedDataTable[]{out};
    }


	/**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // TODO Code executed on reset.
        // Models build during execute are cleared here.
        // Also data handled in load/saveInternals will be erased here.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {
        
    	// check if the settings are available and return the DataTableSpec of the output table.
    	List<String> includeColumns = m_filter.getIncludeList();    	
    	DataTableSpec inputTableSpec = inSpecs[0];
    	Boolean appendBoolean = m_append.getBooleanValue();
        return new DataTableSpec[] {createOutTableSpec(inputTableSpec, includeColumns, appendBoolean)};
    }
    
    
    
    /**
     * Creates the output table specifications. The columns selected for processing 
     * are set as String Type and the rest are kept as in input table.
     * @param inTableSpec The input table specification.
     * @param includeList The list of columns selected to be processed.
     * @return DataTableSpec The output table specifications. 
     * @throws InvalidSettingsException
     */
    private DataTableSpec createOutTableSpec(final DataTableSpec inTableSpec, final List<String> includeList, final Boolean appendBoolean)
    		throws InvalidSettingsException {
    	List<DataColumnSpec> newColumnSpecs = new ArrayList<>();
    	
    	for(int i = 0; i < inTableSpec.getNumColumns(); i++) {
    		// we take the specs of the input table
    		DataColumnSpec currentColumnSpec = inTableSpec.getColumnSpec(i);
    		// if the column is in the includeList, we convert it to StringCell type column
    		if(includeList.contains(currentColumnSpec.getName())){
    			String columnName = currentColumnSpec.getName();
    			if (appendBoolean) {
    				columnName = "Anonymized(" + currentColumnSpec.getName() + ")";
    			}
    			DataColumnSpecCreator specCreator = new DataColumnSpecCreator(columnName, StringCell.TYPE);
    			newColumnSpecs.add(specCreator.createSpec());
    		} else {
    			// if the column is not in the includeList, we keep it as it was
    			DataColumnSpecCreator specCreator = new DataColumnSpecCreator(currentColumnSpec.getName(), currentColumnSpec.getType());
    			newColumnSpecs.add(specCreator.createSpec());
    		}
    	}
    	
    	DataColumnSpec[] newColumnSpecsArray = newColumnSpecs.toArray(new DataColumnSpec[newColumnSpecs.size()]);    	
    	return new DataTableSpec(newColumnSpecsArray);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {

        // TODO save user settings to the config object.
        m_filter.saveSettingsTo(settings);
        m_functions.saveSettingsTo(settings);
        m_append.saveSettingsTo(settings);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        // TODO load (valid) settings from the config object.
        // It can be safely assumed that the settings are valided by the 
        // method below.
        
    	m_filter.loadSettingsFrom(settings);
    	m_append.loadSettingsFrom(settings);
    	m_functions.loadSettingsFrom(settings);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
        // TODO check if the settings could be applied to our model
        // e.g. if the count is in a certain range (which is ensured by the
        // SettingsModel).
        // Do not actually set any values of any member variables.
    	m_filter.validateSettings(settings);
    	m_append.validateSettings(settings);
    	m_functions.validateSettings(settings);

    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        
        // TODO load internal data. 
        // Everything handed to output ports is loaded automatically (data
        // returned by the execute method, models loaded in loadModelContent,
        // and user settings set through loadSettingsFrom - is all taken care 
        // of). Load here only the other internals that need to be restored
        // (e.g. data used by the views).

    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
       
        // TODO save internal models. 
        // Everything written to output ports is saved automatically (data
        // returned by the execute method, models saved in the saveModelContent,
        // and user settings saved through saveSettingsTo - is all taken care 
        // of). Save here only the other internals that need to be preserved
        // (e.g. data used by the views).

    }

}

