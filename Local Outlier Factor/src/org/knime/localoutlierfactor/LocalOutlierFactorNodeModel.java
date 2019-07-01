package org.knime.localoutlierfactor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.knime.base.util.kdtree.KDTree;
import org.knime.base.util.kdtree.KDTreeBuilder;
import org.knime.base.util.kdtree.NearestNeighbour;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.MissingValue;
import org.knime.core.data.MissingValueException;
import org.knime.core.data.StringValue;

import static org.knime.core.data.RowKey.createRowKey;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;


/**
 * This is the model implementation of LocalOutlierFactor. This node computes
 * the Local Outlier Factor for each point in a table and helps detect anomalous
 * points.
 *
 * @author Rytis Kumpa
 */
public class LocalOutlierFactorNodeModel extends NodeModel {

	static final String CFGKEY_NUMNEIGHBORS = "Number of neighbors";
	static final String CFGKEY_FILTER = "Include columns";

	static final int DEFAULT_NUM = 15;

	private final SettingsModelIntegerBounded m_numneigbors = new SettingsModelIntegerBounded(CFGKEY_NUMNEIGHBORS,
			DEFAULT_NUM, 1, Integer.MAX_VALUE);

	private final SettingsModelFilterString m_filterString = new SettingsModelFilterString(CFGKEY_FILTER);

	protected LocalOutlierFactorNodeModel() {
		super(2, 1);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
			throws Exception {

		DataTableSpec firstTableSpec = inData[0].getDataTableSpec();
		DataTableSpec secondTableSpec = inData[1].getDataTableSpec();

		BufferedDataTable firstTable = inData[0];
		
		BufferedDataTable secondTable = inData[1];
		
		if(firstTable.getDataTableSpec().getColumnNames().length == 0) {
			throw new Exception("Empty tables not supported.");
		}

		DataTableSpec outputSpec = configure(new DataTableSpec[] { firstTableSpec, secondTableSpec })[0];

		BufferedDataContainer container = exec.createDataContainer(outputSpec);
		
		// List of columns selected for processing and their respective IDs
		List<String> includeList = m_filterString.getIncludeList();
		Map<Integer, Integer> firstToSecondIDs = new HashMap<Integer, Integer>();
		
		//int[] includeListID = inSpec.columnsToIndices((String[]) includeList.stream().toArray(String[]::new));
		
		verifyInputTables(new DataTableSpec[] { firstTableSpec, secondTableSpec }, includeList, firstToSecondIDs);
		
		
		// Create K-D Tree for efficiently finding nearest neighbors;
		KDTreeBuilder<ArrayList<Double>> kdTree = createKDTree(firstTable, firstToSecondIDs.keySet(), exec);
		
		KDTree<ArrayList<Double>> finalKDTree = kdTree.buildTree();

		CloseableRowIterator inTableIterator = secondTable.iterator();
		long i = 0;

		// Iterate through each point and append the computed Local Outlier Factor.
		while (inTableIterator.hasNext()) {

			exec.checkCanceled();	
			exec.setProgress(0.1 + (double) i / secondTable.size() * 0.9 , "Processing row: " + i);

			DataRow currentRow = inTableIterator.next();
			ArrayList<Double> currentRowArrayList = new ArrayList<Double>();

			for (int firstTableIdx : firstToSecondIDs.keySet()) {
				currentRowArrayList.add(((DoubleCell) currentRow.getCell(firstToSecondIDs.get(firstTableIdx))).getDoubleValue());
			}

			double[] currentRowDoubleArray = currentRowArrayList.stream().mapToDouble(Double::doubleValue).toArray();

			double currentLRD = localReachabilityDensity(finalKDTree, currentRowDoubleArray,
					m_numneigbors.getIntValue());

			List<NearestNeighbour<ArrayList<Double>>> nearestNeighbours = finalKDTree
					.getKNearestNeighbours(currentRowDoubleArray, m_numneigbors.getIntValue());

			double neigborLRD = 0.0;
			for (NearestNeighbour<ArrayList<Double>> neighbour : nearestNeighbours) {
				double[] neigborData = neighbour.getData().stream().mapToDouble(Double::doubleValue).toArray();
				neigborLRD += localReachabilityDensity(finalKDTree, neigborData, m_numneigbors.getIntValue());
			}

			double LOF = neigborLRD / (double) m_numneigbors.getIntValue() / currentLRD;

			ArrayList<DataCell> currentRowCells = currentRow.stream()
					.collect(Collectors.toCollection(ArrayList<DataCell>::new));
			currentRowCells.add(new DoubleCell(LOF));
			container.addRowToTable(new DefaultRow(createRowKey(i++), currentRowCells));
		}
		
		inTableIterator.close();
		finalKDTree = null;
		container.close();
		BufferedDataTable out = container.getTable();
		return new BufferedDataTable[] { out };
	}
	
	/**
	 * Takes the values from the BufferedDataTable and fiores them in a KDTree
	 * @param table
	 * @param includeListID
	 * @param exec
	 * @return
	 * @throws CanceledExecutionException
	 */
	KDTreeBuilder<ArrayList<Double>> createKDTree(BufferedDataTable table, Set<Integer> includeListID, ExecutionContext exec)
			throws CanceledExecutionException {

		CloseableRowIterator inTableIterator = table.iterator();
		KDTreeBuilder<ArrayList<Double>> kdTree = new KDTreeBuilder<ArrayList<Double>>(includeListID.size());

		long i = 0;
		while (inTableIterator.hasNext()) {

			exec.checkCanceled();
			exec.setProgress(((double) i++) / (double) table.size() * 0.1, "Building KDTree.");
			DataRow currentRow = inTableIterator.next();
			ArrayList<Double> currentRowArrayList = new ArrayList<Double>();

			for (int idX : includeListID) {
				DataCell currentCell = currentRow.getCell(idX);
				if (!currentCell.isMissing()) {
					currentRowArrayList.add(((DoubleCell) currentCell).getDoubleValue());
				} else {
					throw new MissingValueException((MissingValue) currentRow);
				}
			}

			kdTree.addPattern(currentRowArrayList.stream().mapToDouble(Double::doubleValue).toArray(),
					currentRowArrayList);
		}
		inTableIterator.close();
		return kdTree;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset() {
	}
	
	private void verifyInputTables(final DataTableSpec[] inSpecs, List<String> includeList,
			Map<Integer, Integer> firstToSecondIDs) throws InvalidSettingsException {
		if (!inSpecs[0].containsCompatibleType(DoubleValue.class)) {
            throw new InvalidSettingsException(
                    "First input table does not contain a numeric column.");
        }
        if (!inSpecs[0].containsCompatibleType(StringValue.class)) {
            throw new InvalidSettingsException(
                    "First input table does not contain a class column of type "
                            + "string.");
        }
        
        for(String includeColumn : includeList) {
        	int IDinFirstTable = inSpecs[0].findColumnIndex(includeColumn);
        	int IDinSecondTable = inSpecs[1].findColumnIndex(includeColumn);
        	if(IDinSecondTable == -1) {
        		throw new InvalidSettingsException("Second input table does not contain a column: '"
                                                   + includeColumn + "'");
        	}
        	if(inSpecs[1].getColumnSpec(IDinSecondTable).getType().isCompatible(DoubleValue.class)) {
        		firstToSecondIDs.put(IDinFirstTable, IDinSecondTable);
        	} else {
        		throw new InvalidSettingsException("Column '" + includeColumn + "' from second table is not compatible "
                        + "with corresponding column '" + includeColumn + "' from first table.");
        	}	
        }		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {

		DataTableSpec inSpec = inSpecs[0];
		DataColumnSpec newColumnSpec = new DataColumnSpecCreator("Local Outlier Factor", DoubleCell.TYPE).createSpec();
		DataTableSpec appendedSpec = new DataTableSpec(newColumnSpec);
		DataTableSpec outSpec = new DataTableSpec(inSpec, appendedSpec);
		return new DataTableSpec[] { outSpec };
	}

	/**
	 * Computes the local reachability density for the particular query.
	 * @param tree The KDTree used to retrieve the nearest neighbors.
	 * @param query The point of which nearest neighbors are queried.
	 * @param numNeingbors The number of neighbors the point will be compared to.
	 * @return The double value of the computed value.
	 */
	private double localReachabilityDensity(KDTree<ArrayList<Double>> tree, double[] query, int numNeingbors) {

		double distance = 0.0;

		List<NearestNeighbour<ArrayList<Double>>> nearestNeighbours = tree.getKNearestNeighbours(query, numNeingbors);

		for (NearestNeighbour<ArrayList<Double>> neighbour : nearestNeighbours) {
			distance += neighbour.getDistance();
		}

		double localReachabilityDensity = 1 / (distance / (double) numNeingbors);

		return localReachabilityDensity;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) {
		
		m_numneigbors.saveSettingsTo(settings);
		m_filterString.saveSettingsTo(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
		
		m_numneigbors.loadSettingsFrom(settings);
		m_filterString.loadSettingsFrom(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {

		m_numneigbors.validateSettings(settings);
		m_filterString.validateSettings(settings);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {


	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {


	}

}
